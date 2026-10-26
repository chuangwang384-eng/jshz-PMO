#requires -Version 5.1
<#
.SYNOPSIS
  R-2 前端回滚脚本（jshz-PMO 专家库系统）

.DESCRIPTION
  按 docs/2026-10-24-发布运维体系与UI偏移修复-design.md §4.2.2 改造：
    1) 定位 frontend\releases\ 下最新版本（支持 -Version 指定版本）
    2) 当前 dist 存档为 dist_broken.<时间戳>（backup\frontend\ 下）
    3) 恢复目标版本到 dist
    4) 自检 GET http://127.0.0.1/ 可访问（200）
    5) 兼容旧 backup\frontend\dist_backup_* 作为兜底来源

.PARAMETER Version
  指定要恢复的版本号（如 1.0.5，匹配 releases\1.0.5-*）；缺省取 releases 下最新版本。

.PARAMETER DryRun
  仅打印将执行的动作与检查点，不移动、不改任何目录。

.PARAMETER DistRoot
  生产 dist 目录，默认 D:\jshz-PMO\frontend\dist（可用临时目录演练）。

.PARAMETER ReleasesRoot
  版本归档根目录，默认 D:\jshz-PMO\frontend\releases。

.EXAMPLE
  .\scripts\rollback-frontend.ps1 -DryRun
  .\scripts\rollback-frontend.ps1
  .\scripts\rollback-frontend.ps1 -Version 1.0.5
#>
[CmdletBinding()]
param(
    [string]$Version = '',
    [switch]$DryRun,
    [string]$DistRoot = 'D:\jshz-PMO\frontend\dist',
    [string]$ReleasesRoot = 'D:\jshz-PMO\frontend\releases'
)

$ErrorActionPreference = 'Stop'
$FrontendDir = Split-Path $DistRoot -Parent
$BackupRoot  = 'D:\jshz-PMO\backup\frontend'

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

# ---------- 主流程 ----------
Write-Host '========================================' -ForegroundColor Cyan
Write-Host ' R-2 前端回滚（jshz-PMO）' -ForegroundColor Cyan
if ($DryRun) { Write-Host ' DRYRUN 模式：仅打印动作/检查点，不产生任何变更' -ForegroundColor Yellow }
Write-Host '========================================' -ForegroundColor Cyan
Write-Info "指定版本   : $($(if ($Version) { $Version } else { '（最新）' }))"
Write-Info "DistRoot    : $DistRoot"
Write-Info "ReleasesRoot: $ReleasesRoot"

# ---------- [1/5] 定位目标版本 ----------
Write-Step '[1/5] 定位目标版本'
$Target = $null
if (Test-Path $ReleasesRoot) {
    if ($Version) {
        $Target = Get-ChildItem -Path $ReleasesRoot -Directory -Filter "$Version-*" |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if (-not $Target) { throw "releases 下未找到版本 $Version 的归档目录" }
    } else {
        $Target = Get-ChildItem -Path $ReleasesRoot -Directory |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
    }
}
# 兜底：旧 backup\frontend\dist_backup_* 来源
if (-not $Target) {
    if (Test-Path $BackupRoot) {
        $Target = Get-ChildItem -Path $BackupRoot -Directory -Filter 'dist_backup_*' |
            Sort-Object Name -Descending | Select-Object -First 1
        if ($Target) { Write-Info '使用旧备份来源: backup\frontend\dist_backup_*（兜底）' }
    }
}
if (-not $Target) { throw '未找到任何可恢复版本（releases 与 backup\frontend 均无可用备份）' }
Write-Info "恢复来源: $($Target.FullName)"

# ---------- [2/5] 当前 dist 存档为 dist_broken ----------
Write-Step '[2/5] 存档当前（异常）dist'
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$BrokenDir = Join-Path $BackupRoot "dist_broken_$stamp"
Invoke-Action "Rename-Item $DistRoot -> $BrokenDir" {
    if (Test-Path $DistRoot) {
        New-Item -ItemType Directory -Path $BackupRoot -Force | Out-Null
        Move-Item -Path $DistRoot -Destination $BrokenDir -Force
    } else {
        Write-Info '当前无 dist 目录，跳过 broken 存档'
    }
}

# ---------- [3/5] 恢复目标版本到 dist ----------
Write-Step '[3/5] 恢复目标版本到 dist'
try {
    Invoke-Action "恢复 $($Target.FullName) 内容 -> $DistRoot" {
        New-Item -ItemType Directory -Path $DistRoot -Force | Out-Null
        Copy-Item -Path (Join-Path $Target.FullName '*') -Destination $DistRoot -Recurse -Force
        Write-Ok "已恢复: $DistRoot"
    }
} catch {
    Write-Fail "恢复失败: $_"
    Invoke-Action '回滚恢复动作：dist_broken -> dist（撤销第二步存档）' {
        if (Test-Path $BrokenDir) {
            if (Test-Path $DistRoot) { Remove-Item -Path $DistRoot -Recurse -Force -ErrorAction SilentlyContinue }
            Move-Item -Path $BrokenDir -Destination $DistRoot -Force
        }
    }
    Write-Fail '回滚中止，已保留原 dist'
    exit 1
}

# ---------- [4/5] 自检 ----------
Write-Step '[4/5] 自检（GET http://127.0.0.1/ 可访问）'
$ok = Invoke-Check 'GET http://127.0.0.1/ 应 200（站点可访问）' {
    $wc = New-Object System.Net.WebClient
    try { $null = $wc.DownloadData('http://127.0.0.1/') } finally { $wc.Dispose() }
}
if (-not $ok) { Write-Fail '自检失败，请人工确认站点状态' }

# ---------- [5/5] 摘要 ----------
Write-Step '[5/5] 回滚摘要'
Write-Info "恢复来源 : $($Target.FullName)"
Write-Info "异常存档 : $BrokenDir"
Write-Info '说明: 未执行 nginx reload（NSSM 约束），dist 即取即用，无需重启 Nginx'
if ($DryRun) { Write-Host 'DRYRUN 结束：未执行任何目录变更' -ForegroundColor Yellow }
