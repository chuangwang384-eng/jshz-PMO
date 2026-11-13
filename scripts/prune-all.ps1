#requires -Version 5.1
<#
.SYNOPSIS
  空间保留策略统一入口：前端快照 / builds / temp / logs 一次执行（供月度计划任务调用）。

.DESCRIPTION
  串联四个保留脚本，各自内置安全护栏（Nginx 当前 root、archive 只读留档、引用保护、路径越界校验）：
  1. prune-frontend-releases.ps1  frontend\releases 保留最近 N 个正式快照 + 当前回滚副本
  2. prune-builds.ps1             builds\ 保留最近 N 个构建目录（archive\ 永不删除）
  3. prune-temp.ps1               temp\ 各类别保留最近 N 个（引用/近期文件跳过）
  4. prune-logs.ps1               logs\ NSSM 轮转产物与发布日志按个数保留

  输出写入 logs\prune-<yyyyMMdd_HHmmss>.log（UTF-8），任一环节抛错则最终退出码 1，
  但不影响其余环节继续执行（尽量把能清的清掉，同时保留失败信息）。

.PARAMETER Keep
  统一保留个数（前端快照 / builds / temp 各类别），默认 5

.PARAMETER MinAgeDays
  temp 目录近期文件保护天数，默认 2

.PARAMETER SkipLogs
  跳过 prune-logs（默认包含）

.PARAMETER DryRun
  只打印将删除的条目，不实际删除

.EXAMPLE
  .\scripts\prune-all.ps1 -DryRun
  .\scripts\prune-all.ps1 -Keep 5
#>
[CmdletBinding()]
param(
    [int]$Keep = 5,
    [int]$MinAgeDays = 2,
    [switch]$SkipLogs,
    [switch]$DryRun
)

$ErrorActionPreference = 'Continue'
$RepoRoot = Split-Path $PSScriptRoot -Parent
$logsDir = Join-Path $RepoRoot 'logs'
if (-not (Test-Path -LiteralPath $logsDir)) { New-Item -ItemType Directory -Path $logsDir -Force | Out-Null }
$logFile = Join-Path $logsDir ('prune-' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '.log')

$script:Failures = @()

$banner = ('空间保留策略执行 ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' Keep=' + $Keep + $(if ($DryRun) { ' DRYRUN' } else { '' }))
Add-Content -LiteralPath $logFile -Value $banner -Encoding UTF8
Write-Host $banner -ForegroundColor Cyan

Write-Host ''
Write-Host '==== 前端快照（frontend\releases） ====' -ForegroundColor Cyan
try {
    $a = @{ KeepReleases = $Keep }
    if ($DryRun) { $a['DryRun'] = $true }
    & (Join-Path $PSScriptRoot 'prune-frontend-releases.ps1') @a 2>&1 | Tee-Object -FilePath $logFile -Append
} catch {
    Write-Host ("[FAIL] 前端快照 : " + $_.Exception.Message) -ForegroundColor Red
    Add-Content -LiteralPath $logFile -Value ("[FAIL] 前端快照 : " + $_.Exception.Message) -Encoding UTF8
    $script:Failures += '前端快照'
}

Write-Host ''
Write-Host '==== 构建产物（builds\） ====' -ForegroundColor Cyan
try {
    $a = @{ Keep = $Keep }
    if ($DryRun) { $a['DryRun'] = $true }
    & (Join-Path $PSScriptRoot 'prune-builds.ps1') @a 2>&1 | Tee-Object -FilePath $logFile -Append
    if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) { $script:Failures += "构建产物 (exit=$LASTEXITCODE)" }
} catch {
    Write-Host ("[FAIL] 构建产物 : " + $_.Exception.Message) -ForegroundColor Red
    Add-Content -LiteralPath $logFile -Value ("[FAIL] 构建产物 : " + $_.Exception.Message) -Encoding UTF8
    $script:Failures += '构建产物'
}

Write-Host ''
Write-Host '==== 临时产物（temp\） ====' -ForegroundColor Cyan
try {
    $a = @{ Keep = $Keep; MinAgeDays = $MinAgeDays }
    if ($DryRun) { $a['DryRun'] = $true }
    & (Join-Path $PSScriptRoot 'prune-temp.ps1') @a 2>&1 | Tee-Object -FilePath $logFile -Append
    if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) { $script:Failures += "临时产物 (exit=$LASTEXITCODE)" }
} catch {
    Write-Host ("[FAIL] 临时产物 : " + $_.Exception.Message) -ForegroundColor Red
    Add-Content -LiteralPath $logFile -Value ("[FAIL] 临时产物 : " + $_.Exception.Message) -Encoding UTF8
    $script:Failures += '临时产物'
}

if (-not $SkipLogs) {
    Write-Host ''
    Write-Host '==== 日志（logs\） ====' -ForegroundColor Cyan
    try {
        $a = @{}
        if ($DryRun) { $a['DryRun'] = $true }
        & (Join-Path $PSScriptRoot 'prune-logs.ps1') @a 2>&1 | Tee-Object -FilePath $logFile -Append
        if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) { $script:Failures += "日志 (exit=$LASTEXITCODE)" }
    } catch {
        Write-Host ("[FAIL] 日志 : " + $_.Exception.Message) -ForegroundColor Red
        Add-Content -LiteralPath $logFile -Value ("[FAIL] 日志 : " + $_.Exception.Message) -Encoding UTF8
        $script:Failures += '日志'
    }
}

# 自管日志：prune-*.log 只保留最近 12 份，避免月度任务自身堆积
try {
    $old = @(Get-ChildItem -LiteralPath $logsDir -File -Filter 'prune-*.log' -ErrorAction SilentlyContinue |
             Where-Object { $_.FullName -ne $logFile } |
             Sort-Object LastWriteTime -Descending | Select-Object -Skip 11)
    foreach ($f in $old) { if (-not $DryRun) { Remove-Item -LiteralPath $f.FullName -Force -ErrorAction SilentlyContinue } }
} catch { }

Write-Host ''
Write-Host ("日志: " + $logFile) -ForegroundColor DarkGray
if ($script:Failures.Count -gt 0) {
    Write-Host ('[FAIL] 有 ' + $script:Failures.Count + ' 个环节失败：' + ($script:Failures -join '; ')) -ForegroundColor Red
    exit 1
}
if ($DryRun) {
    Write-Host '[OK] DRYRUN 完成，未删除任何文件' -ForegroundColor Yellow
} else {
    Write-Host '[OK] 保留策略执行完成' -ForegroundColor Green
}
exit 0
