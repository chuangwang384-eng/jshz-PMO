#requires -Version 5.1
<#
.SYNOPSIS
  R-2 前端原子发布脚本（jshz-PMO 专家库系统）

.DESCRIPTION
  按 docs/2026-10-24-发布运维体系与UI偏移修复-design.md §4.2.1 原子化改造：
    1) build 输出到临时目录 frontend\dist-new.<时间戳>（vite --outDir），不直接覆盖 dist
    2) 校验临时目录内 index.html 与 assets/ 存在
    3) 备份旧 dist 到 frontend\releases\<版本号>-<时间戳>\（版本号取项目根 version.txt revision + 构建时间戳）
    4) 原子替换: dist -> dist_old.<时间戳>, dist-new.<时间戳> -> dist（任一步失败恢复旧目录）
    5) 不再执行 nginx -s reload（NSSM 约束），dist 即取即用，不重启 Nginx
    6) 发布后自检 GET http://127.0.0.1/ 返回新 index.html（对比 SHA256）

.PARAMETER DryRun
  仅打印将执行的动作与检查点，不构建、不移动、不改任何目录。

.PARAMETER DistRoot
  生产 dist 目录，默认 D:\jshz-PMO\frontend\dist（可用临时目录演练）。

.PARAMETER ReleasesRoot
  版本归档根目录，默认 D:\jshz-PMO\frontend\releases。

.EXAMPLE
  .\scripts\deploy-frontend.ps1 -DryRun
  .\scripts\deploy-frontend.ps1
  .\scripts\deploy-frontend.ps1 -DistRoot D:\tmp\demo\dist -ReleasesRoot D:\tmp\demo\releases
#>
[CmdletBinding()]
param(
    [switch]$DryRun,
    [string]$DistRoot = 'D:\jshz-PMO\frontend\dist',
    [string]$ReleasesRoot = 'D:\jshz-PMO\frontend\releases'
)

$ErrorActionPreference = 'Stop'
$FrontendDir = Split-Path $DistRoot -Parent

# ---------- 版本号（唯一真源 D:\jshz-PMO\version.txt revision） ----------
$VersionFile = Join-Path (Split-Path $FrontendDir -Parent) 'version.txt'
$Version = 'unknown'
if (Test-Path $VersionFile) {
    try {
        $m = [regex]::Match((Get-Content $VersionFile -Raw -Encoding UTF8), '(?m)^revision\s*=\s*(.+)\s*$')
        if ($m.Success) { $Version = $m.Groups[1].Value.Trim() }
    } catch { $Version = 'unknown' }
}

# ---------- 工具函数 ----------
function Write-Step([string]$msg) { Write-Host "`n== $msg ==" -ForegroundColor Cyan }
function Write-Info([string]$msg) { Write-Host "   - $msg" }
function Write-Ok([string]$msg)   { Write-Host "   [OK] $msg" -ForegroundColor Green }
function Write-Fail([string]$msg) { Write-Host "   [FAIL] $msg" -ForegroundColor Red }

function Invoke-Action([string]$desc, [scriptblock]$action) {
    if ($DryRun) { Write-Host "   [DRYRUN][动作] $desc" -ForegroundColor Yellow; return }
    Write-Host "   [执行] $desc" -ForegroundColor DarkGray
    & $action
}
function Invoke-Check([string]$desc, [scriptblock]$check) {
    if ($DryRun) { Write-Host "   [DRYRUN][检查] $desc" -ForegroundColor Yellow; return $true }
    try { & $check; Write-Ok $desc; return $true } catch { Write-Fail "$desc -> $_"; return $false }
}
function Get-FileSha256([string]$path) {
    $sha = [Security.Cryptography.SHA256]::Create()
    return ([BitConverter]::ToString($sha.ComputeHash([IO.File]::ReadAllBytes($path))) -replace '-', '').ToLower()
}
function Get-UrlSha256([string]$url) {
    $wc = New-Object System.Net.WebClient
    try {
        $sha = [Security.Cryptography.SHA256]::Create()
        return ([BitConverter]::ToString($sha.ComputeHash($wc.DownloadData($url))) -replace '-', '').ToLower()
    } finally { $wc.Dispose() }
}

# ---------- 主流程 ----------
Write-Host '========================================' -ForegroundColor Cyan
Write-Host ' R-2 前端原子发布（jshz-PMO）' -ForegroundColor Cyan
if ($DryRun) { Write-Host ' DRYRUN 模式：仅打印动作/检查点，不产生任何变更' -ForegroundColor Yellow }
Write-Host '========================================' -ForegroundColor Cyan
Write-Info "版本        : $Version"
Write-Info "DistRoot    : $DistRoot"
Write-Info "ReleasesRoot: $ReleasesRoot"

$stamp  = Get-Date -Format 'yyyyMMdd_HHmmss'
$distNew = Join-Path $FrontendDir "dist-new.$stamp"
$distOld = Join-Path $FrontendDir "dist_old.$stamp"
$relDir  = Join-Path $ReleasesRoot "$Version-$stamp"

# ---------- [1/6] 构建到临时目录 ----------
Write-Step '[1/6] 构建到临时目录（不直接覆盖 dist）'
Invoke-Action "npm run build -- --outDir $distNew（工作目录 $FrontendDir）" {
    Push-Location $FrontendDir
    try {
        cmd /c "npm run build -- --outDir ""$distNew"" 2>&1"
        if ($LASTEXITCODE -ne 0) { throw "npm run build 失败，退出码 $LASTEXITCODE" }
    } finally { Pop-Location }
}

# ---------- [2/6] 校验临时构建产物 ----------
Write-Step '[2/6] 校验临时构建产物'
$okIndex = Invoke-Check "index.html 存在: $distNew\index.html" {
    if (-not (Test-Path (Join-Path $distNew 'index.html'))) { throw 'index.html 缺失' }
}
$okAssets = Invoke-Check "assets/ 存在: $distNew\assets" {
    if (-not (Test-Path (Join-Path $distNew 'assets'))) { throw 'assets/ 缺失' }
}
if (-not ($okIndex -and $okAssets)) {
    Write-Fail '构建产物校验失败，中止（未改动任何目录）'
    exit 1
}

# ---------- [3/6] 备份旧 dist 到 releases ----------
Write-Step '[3/6] 备份旧 dist -> releases'
Invoke-Action "备份 $DistRoot -> $relDir" {
    if (Test-Path $DistRoot) {
        New-Item -ItemType Directory -Path $relDir -Force | Out-Null
        Copy-Item -Path (Join-Path $DistRoot '*') -Destination $relDir -Recurse -Force
        Write-Ok "旧版已归档: $relDir"
    } else {
        Write-Info '当前无 dist 目录，跳过旧版备份'
    }
}

# ---------- [4/6] 原子替换 ----------
Write-Step '[4/6] 原子替换（任一步失败自动恢复旧目录）'
try {
    Invoke-Action "Rename-Item $DistRoot -> $distOld" {
        if (Test-Path $DistRoot) { Rename-Item -Path $DistRoot -NewName (Split-Path $distOld -Leaf) -Force }
    }
    Invoke-Action "Rename-Item $distNew -> $DistRoot" {
        Rename-Item -Path $distNew -NewName (Split-Path $DistRoot -Leaf) -Force
    }
} catch {
    Write-Fail "原子替换失败: $_"
    Invoke-Action '恢复：dist_old -> dist（撤销第一步改名，清理残留）' {
        if (Test-Path $distOld) {
            if (Test-Path $DistRoot) { Remove-Item -Path $DistRoot -Recurse -Force -ErrorAction SilentlyContinue }
            Rename-Item -Path $distOld -NewName (Split-Path $DistRoot -Leaf) -Force
        }
        if (Test-Path $distNew) { Remove-Item -Path $distNew -Recurse -Force -ErrorAction SilentlyContinue }
    }
    Write-Fail '发布中止，已恢复旧 dist'
    exit 1
}

# ---------- [5/6] 发布后自检 ----------
Write-Step '[5/6] 发布自检（GET http://127.0.0.1/ 对比新 index.html 哈希）'
$okCheck = Invoke-Check 'GET http://127.0.0.1/ 返回新 index.html（SHA256 与本地一致）' {
    $localSha  = Get-FileSha256 (Join-Path $DistRoot 'index.html')
    $remoteSha = Get-UrlSha256 'http://127.0.0.1/'
    if ($localSha -ne $remoteSha) { throw "哈希不一致: local=$localSha remote=$remoteSha" }
}
if (-not $okCheck) { Write-Fail '自检失败，请人工确认站点；如需回退执行 rollback-frontend.ps1' }

# ---------- [6/6] 摘要 ----------
Write-Step '[6/6] 发布摘要'
Write-Info "发布版本 : $Version"
Write-Info "旧版备份 : $relDir"
Write-Info "生产目录 : $DistRoot"
Write-Info '说明: 未执行 nginx reload（NSSM 约束），dist 即取即用，无需重启 Nginx'
if ($DryRun) { Write-Host 'DRYRUN 结束：未执行任何构建/目录变更' -ForegroundColor Yellow }
