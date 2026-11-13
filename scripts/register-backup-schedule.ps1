<#
.SYNOPSIS
  注册/删除 jshz-PMO 每周自动备份计划任务（jshzPMOBackupWeekly）。

.DESCRIPTION
  - 默认动作：每周日 02:00，SYSTEM 身份，执行 scripts\backup.ps1 -Mode Backup -Keep 5
    （mysqldump 全量 + 照片库/知识文档库 zip + sha256 + manifest，保留最近 5 份）
  - 与 jshzPMOPruneMonthly（每月 1 日 03:30 空间清理）错开，避免同时跑。
  - 用 schtasks（PS 5.1 的 New-ScheduledTaskTrigger 不支持 -Monthly；Weekly 虽支持，但保持与清理任务一致的注册方式）。
  - 脚本自身不做提权，需在管理员 PowerShell 中运行。

.PARAMETER Unregister
  删除计划任务。

.PARAMETER DryRun
  只打印将执行的命令，不注册。

.PARAMETER Keep
  备份保留份数，默认 5（写入任务命令行）。

.PARAMETER Time
  执行时刻，默认 02:00。

.EXAMPLE
  .\scripts\register-backup-schedule.ps1 -DryRun
  .\scripts\register-backup-schedule.ps1
  .\scripts\register-backup-schedule.ps1 -Unregister
#>
[CmdletBinding()]
param(
    [switch]$Unregister,
    [switch]$DryRun,
    [int]$Keep = 5,
    [string]$Time = '02:00'
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$TaskName = 'jshzPMOBackupWeekly'
$backupScript = Join-Path $RepoRoot 'scripts\backup.ps1'

if (-not (Test-Path -LiteralPath $backupScript)) { throw "未找到备份脚本: $backupScript" }

if ($Unregister) {
    if ($DryRun) { Write-Host "[DRYRUN] 将删除计划任务 $TaskName"; exit 0 }
    & schtasks.exe /Delete /TN $TaskName /F | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "删除失败 exit=$LASTEXITCODE（任务可能不存在）" }
    Write-Host "[OK] 已删除计划任务 $TaskName" -ForegroundColor Green
    exit 0
}

$argLine = 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "{0}" -Mode Backup -Keep {1}' -f $backupScript, $Keep

Write-Host '计划任务定义：'
Write-Host "  名称: $TaskName"
Write-Host "  时间: 每周日 $Time"
Write-Host '  身份: SYSTEM（最高权限）'
Write-Host "  动作: $argLine"

if ($DryRun) { Write-Host '[DRYRUN] 未注册'; exit 0 }

& schtasks.exe /Create /TN $TaskName /TR $argLine /SC WEEKLY /D SUN /ST $Time /RU SYSTEM /RL HIGHEST /F | Out-Null
if ($LASTEXITCODE -ne 0) { throw "schtasks 注册失败 exit=$LASTEXITCODE" }

$task = Get-ScheduledTask -TaskName $TaskName
$info = Get-ScheduledTaskInfo -TaskName $TaskName
Write-Host ("[OK] 已注册计划任务 {0}（状态 {1}），下次运行: {2}" -f $TaskName, $task.State, $info.NextRunTime) -ForegroundColor Green
