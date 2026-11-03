#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$StagedDist,
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9._-]+$')][string]$ReleaseId,
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
$conf = 'D:\nginx\conf\conf.d\jshz-pmo.conf'
$text = [IO.File]::ReadAllText($conf)
$rootMatch = [regex]::Match($text, '(?m)^\s*root\s+([^;]+);')
if (-not $rootMatch.Success) { throw 'Expected one explicit site root.' }
$oldRoot = $rootMatch.Groups[1].Value.Trim().Trim('"')
$stage = (Resolve-Path -LiteralPath $StagedDist).Path
foreach ($item in @('index.html','assets')) {
    if (-not (Test-Path -LiteralPath (Join-Path $stage $item))) { throw "Missing staged item: $item" }
}
$target = Join-Path 'D:\jshz-PMO\frontend\releases' $ReleaseId
$rollback = $target + '.rollback'
if (Test-Path -LiteralPath $target) { throw 'Release directory already exists; choose a fresh immutable release ID.' }
if (Test-Path -LiteralPath $rollback) { throw 'Rollback release already exists; choose a fresh release ID.' }
Write-Output "Frontend plan: $oldRoot -> $target; preserve old hashed assets; graceful Nginx root switch; no dist rename or service restart."
if (-not $Apply) { Write-Output 'Plan only: no production writes.'; return }
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Requires elevated PowerShell.' }
New-Item -ItemType Directory -Path $target | Out-Null
Copy-Item -Path (Join-Path $stage '*') -Destination $target -Recurse
$oldAssets = Join-Path $oldRoot 'assets'
if (Test-Path -LiteralPath $oldAssets) {
    foreach ($file in Get-ChildItem -LiteralPath $oldAssets -File) {
        $destination = Join-Path (Join-Path $target 'assets') $file.Name
        if (Test-Path -LiteralPath $destination) {
            if ((Get-FileHash -LiteralPath $destination).Hash -ne (Get-FileHash -LiteralPath $file.FullName).Hash) {
                throw "Asset name collision; no traffic switched: $($file.Name)"
            }
        } else { Copy-Item -LiteralPath $file.FullName -Destination $destination }
    }
}
$metadata = 'D:\jshz-PMO\frontend\release-metadata'
New-Item -ItemType Directory -Path $metadata -Force | Out-Null
$backup = Join-Path $metadata ($ReleaseId + '.nginx-before.txt')
[IO.File]::WriteAllText($backup, $text)
New-Item -ItemType Directory -Path $rollback | Out-Null
Copy-Item -Path (Join-Path $target '*') -Destination $rollback -Recurse
# Old entry files with the union of old/new hashed assets support clients from either release.
foreach ($file in Get-ChildItem -LiteralPath $oldRoot -File) {
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $rollback $file.Name) -Force
}
$rollbackText = $text.Substring(0,$rootMatch.Index) + ($rootMatch.Value -replace [regex]::Escape($rootMatch.Groups[1].Value),$rollback.Replace('\','/')) + $text.Substring($rootMatch.Index+$rootMatch.Length)
[IO.File]::WriteAllText((Join-Path $metadata ($ReleaseId + '.rollback.conf.txt')), $rollbackText)
$next = $text.Substring(0,$rootMatch.Index) + ($rootMatch.Value -replace [regex]::Escape($rootMatch.Groups[1].Value),$target.Replace('\','/')) + $text.Substring($rootMatch.Index+$rootMatch.Length)
try {
    [IO.File]::WriteAllText($conf,$next)
    & (Join-Path $PSScriptRoot 'invoke-nginx-graceful.ps1')
    $web = New-Object Net.WebClient
    try { $served = $web.DownloadData('http://127.0.0.1/') } finally { $web.Dispose() }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = ([BitConverter]::ToString($sha.ComputeHash($served))).Replace('-','') } finally { $sha.Dispose() }
    if ($hash -ne (Get-FileHash -LiteralPath (Join-Path $target 'index.html')).Hash) { throw 'Served index hash does not match immutable release.' }
    Write-Output "Frontend switched and verified; prior root retained: $oldRoot; config backup: $backup"
} catch {
    [IO.File]::WriteAllText($conf,$rollbackText)
    & (Join-Path $PSScriptRoot 'invoke-nginx-graceful.ps1')
    throw
}
