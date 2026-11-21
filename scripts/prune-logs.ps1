#requires -Version 5.1
<#
.SYNOPSIS
  logs\ 目录日志保留策略执行脚本（可独立运行，也被 deploy-backend.ps1 阶段 11 调用）。

.DESCRIPTION
  logs\ 下有三类日志，管理者与保留策略不同，必须分开处理：

  1. NSSM 轮转产物  expert-backend.out-*.log / expert-backend.err-*.log
     NSSM 每次服务启动必定轮转一次（不看大小，AppRotateFiles=1），运行期间超过
     AppRotateBytes(10MB) 还会在线轮转（AppRotateOnline=1）。NSSM 自身**没有任何清理机制**，
     这些文件会随发布次数无限累积。本脚本按**个数**保留最近 N 个（默认各 10 个）。
     命名规则：原名-UTC时间戳.扩展名，例如 expert-backend.out-20261106T083210.791.log。

  2. 发布日志  deploy-backend.<yyyyMMdd_HHmmss>.log
     每次发布由 release-backend.ps1 生成一个（UTF-8）。按个数保留最近 N 个（默认 20 个）。

  3. logback 应用日志  expert-app.log / expert-app.<日期>.<序号>.log.gz
     由 logback 的 maxHistory=30 天、totalSizeCap=2GB 自行管理，**本脚本只统计不删除**，
     避免两套策略互相打架。

  当前活动文件 expert-backend.out.log / expert-backend.err.log / expert-app.log 永不删除。

.PARAMETER LogDir
  日志目录，默认 <仓库根>\logs（common-paths.ps1 单一真源，JSHZ_ROOT 可覆盖）

.PARAMETER KeepNssm
  NSSM out / err 轮转产物各自保留个数，默认 10

.PARAMETER KeepDeploy
  发布日志保留个数，默认 20

.PARAMETER DryRun
  只打印将删除的文件，不实际删除

.EXAMPLE
  .\scripts\prune-logs.ps1 -DryRun
  .\scripts\prune-logs.ps1
#>
[CmdletBinding()]
param(
    [string]$LogDir = '',
    [int]$KeepNssm = 10,
    [int]$KeepDeploy = 20,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
# 仓库根单一真源（方案 C 二期，2026-11-19 收敛；JSHZ_ROOT 可覆盖）
. (Join-Path $PSScriptRoot 'common-paths.ps1')
if ([string]::IsNullOrWhiteSpace($LogDir)) { $LogDir = Join-Path $root 'logs' }
$script:DeletedCount = 0
$script:DeletedBytes = 0

function Write-Ok([string]$m) { Write-Host "[OK]   $m" -ForegroundColor Green }
function Write-Info([string]$m) { Write-Host "       $m" -ForegroundColor DarkGray }
function Write-Warn2([string]$m) { Write-Host "[WARN] $m" -ForegroundColor Yellow }

function Invoke-Pruning([string]$Label, $Files, [int]$Keep) {
    $files = @($Files)
    if ($files.Count -eq 0) { Write-Info "${Label}：无文件"; return }
    if ($files.Count -le $Keep) {
        Write-Ok "${Label}：$($files.Count) 个，未超上限 $Keep"
        return
    }
    $sorted = @($files | Sort-Object LastWriteTime -Descending)
    $toRemove = @($sorted | Select-Object -Skip $Keep)
    $bytes = 0
    foreach ($f in $toRemove) { $bytes += $f.Length }
    foreach ($f in $toRemove) {
        if ($DryRun) {
            Write-Host "   [DRYRUN] 将删除 $($f.Name)（$([math]::Round($f.Length / 1KB, 1)) KB）" -ForegroundColor Yellow
        } else {
            Remove-Item -LiteralPath $f.FullName -Force
        }
    }
    $script:DeletedCount += $toRemove.Count
    $script:DeletedBytes += $bytes
    Write-Ok "${Label}：保留 $Keep 个，删除 $($toRemove.Count) 个（$([math]::Round($bytes / 1MB, 2)) MB）"
}

if (-not (Test-Path -LiteralPath $LogDir)) {
    Write-Warn2 "日志目录不存在：$LogDir"
    exit 0
}

Write-Host ('日志保留策略' + $(if ($DryRun) { '（DRYRUN，不删除任何文件）' } else { '' })) -ForegroundColor Cyan
Write-Info "目录: $LogDir   NSSM 保留 $KeepNssm 个/类   发布日志保留 $KeepDeploy 个"

$allFiles = Get-ChildItem -LiteralPath $LogDir -File -ErrorAction SilentlyContinue

# 1) NSSM 轮转产物（NSSM 启动即轮转，无自清理）
Invoke-Pruning 'NSSM out 轮转产物' ($allFiles | Where-Object { $_.Name -like 'expert-backend.out-*.log' }) $KeepNssm
Invoke-Pruning 'NSSM err 轮转产物' ($allFiles | Where-Object { $_.Name -like 'expert-backend.err-*.log' }) $KeepNssm

# 2) 发布日志
Invoke-Pruning '发布日志 deploy-backend.*' ($allFiles | Where-Object { $_.Name -like 'deploy-backend.*.log' }) $KeepDeploy

# 3) logback 归档（只报告，交给 logback 的 maxHistory/totalSizeCap）
$gz = @($allFiles | Where-Object { $_.Name -like 'expert-app.*.log.gz' })
$gzBytes = 0
foreach ($f in $gz) { $gzBytes += $f.Length }
Write-Info "logback 归档：$($gz.Count) 个，合计 $([math]::Round($gzBytes / 1MB, 2)) MB（由 maxHistory=30天 / totalSizeCap=2GB 管理，本脚本不删除）"

# 4) 当前活动文件（永不删除，仅确认存在）
foreach ($name in 'expert-backend.out.log', 'expert-backend.err.log', 'expert-app.log') {
    $p = Join-Path $LogDir $name
    if (Test-Path -LiteralPath $p) {
        $f = Get-Item -LiteralPath $p
        Write-Info "活动文件 $name = $([math]::Round($f.Length / 1MB, 2)) MB（不参与清理）"
    }
}

if ($DryRun) {
    Write-Host "DRYRUN 汇总：将删除 $script:DeletedCount 个文件，释放 $([math]::Round($script:DeletedBytes / 1MB, 2)) MB" -ForegroundColor Yellow
} else {
    Write-Host "清理完成：删除 $script:DeletedCount 个文件，释放 $([math]::Round($script:DeletedBytes / 1MB, 2)) MB" -ForegroundColor Green
}
exit 0
