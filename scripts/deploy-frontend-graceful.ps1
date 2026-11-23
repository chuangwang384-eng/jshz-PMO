#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$StagedDist,
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9._-]+$')][string]$ReleaseId,
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'deployment-http.ps1')
. (Join-Path $PSScriptRoot 'frontend-shared-assets.ps1')
# 仓库根单一真源（方案 C 二期，2026-11-19 收敛；JSHZ_ROOT 可覆盖）
. (Join-Path $PSScriptRoot 'common-paths.ps1')
$frontend = Join-Path $root 'frontend'
if ($Apply) {
    $principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Requires elevated PowerShell.' }
}
$lock = if ($Apply) { Enter-FrontendDeploymentLock $frontend }
try {
$conf = 'D:\nginx\conf\conf.d\jshz-pmo.conf'
$text = [IO.File]::ReadAllText($conf)
$rootMatch = [regex]::Match($text, '(?m)^\s*root\s+([^;]+);')
if (-not $rootMatch.Success) { throw 'Expected one explicit site root.' }
$oldRoot = $rootMatch.Groups[1].Value.Trim().Trim('"')
$stage = (Resolve-Path -LiteralPath $StagedDist).Path
foreach ($item in @('index.html','assets')) {
    if (-not (Test-Path -LiteralPath (Join-Path $stage $item))) { throw "Missing staged item: $item" }
}
$target = Join-Path (Join-Path $root 'frontend\releases') $ReleaseId
$rollback = $target + '.rollback'
if (Test-Path -LiteralPath $target) { throw 'Release directory already exists; choose a fresh immutable release ID.' }
if (Test-Path -LiteralPath $rollback) { throw 'Rollback release already exists; choose a fresh release ID.' }
Write-Output "Frontend plan: $oldRoot -> $target; preserve old hashed assets; graceful Nginx root switch; no dist rename or service restart."
if (-not $Apply) { Write-Output 'Plan only: no production writes.'; return }
New-SharedFrontendRelease $frontend $stage $oldRoot $target $rollback
$metadata = Join-Path $root 'frontend\release-metadata'
New-Item -ItemType Directory -Path $metadata -Force | Out-Null
$backup = Join-Path $metadata ($ReleaseId + '.nginx-before.txt')
[IO.File]::WriteAllText($backup, $text)
$rollbackText = $text.Substring(0,$rootMatch.Index) + ($rootMatch.Value -replace [regex]::Escape($rootMatch.Groups[1].Value),$rollback.Replace('\','/')) + $text.Substring($rootMatch.Index+$rootMatch.Length)
[IO.File]::WriteAllText((Join-Path $metadata ($ReleaseId + '.rollback.conf.txt')), $rollbackText)
$next = $text.Substring(0,$rootMatch.Index) + ($rootMatch.Value -replace [regex]::Escape($rootMatch.Groups[1].Value),$target.Replace('\','/')) + $text.Substring($rootMatch.Index+$rootMatch.Length)
try {
    [IO.File]::WriteAllText($conf,$next)
    & (Join-Path $PSScriptRoot 'invoke-nginx-graceful.ps1')
    $web = Get-DeploymentHttpResponse -Url 'http://127.0.0.1/'
    if($web.StatusCode -ne 200){throw 'Served frontend index did not return HTTP 200.'}
    $served=$web.ContentBytes
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = ([BitConverter]::ToString($sha.ComputeHash($served))).Replace('-','') } finally { $sha.Dispose() }
    if ($hash -ne (Get-FileHash -LiteralPath (Join-Path $target 'index.html')).Hash) { throw 'Served index hash does not match immutable release.' }
    Write-Output "Frontend switched and verified; prior root retained: $oldRoot; config backup: $backup"
} catch {
    [IO.File]::WriteAllText($conf,$rollbackText)
    & (Join-Path $PSScriptRoot 'invoke-nginx-graceful.ps1')
    throw
}
} finally {
    if ($lock) { $lock.Dispose() }
}
