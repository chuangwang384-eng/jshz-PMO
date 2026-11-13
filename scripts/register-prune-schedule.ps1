#requires -Version 5.1
<#
.SYNOPSIS
  注册/更新「空间保留策略」月度计划任务（jshzPMOPruneMonthly）。

.DESCRIPTION
  每月固定日 03:30 以 SYSTEM 身份执行 scripts\prune-all.ps1：前端快照保留 5、builds 保留 5、
  temp 各类别保留 5、logs 按既有策略清理。日志落 logs\prune-<时间戳>.log。

  为什么用 SYSTEM：与 Nginx 平滑重载任务同风格，无需保存用户口令，SYSTEM 对 D:\jshz-PMO 有完全权限。

.PARAMETER TaskName
  任务名，默认 jshzPMOPruneMonthly

.PARAMETER DayOfMonth
  每月第几天执行，默认 1（1 号凌晨，避开周日 02:00 的备份任务）

.PARAMETER Time
  执行时间，默认 03:30

.PARAMETER Keep
  统一保留个数，默认 5

.PARAMETER Unregister
  删除任务

.PARAMETER DryRun
  只打印将注册的任务定义，不实际注册

.EXAMPLE
  .\scripts\register-prune-schedule.ps1 -DryRun
  .\scripts\register-prune-schedule.ps1
#>
[CmdletBinding()]
param(
    [string]$TaskName = 'jshzPMOPruneMonthly',
    [int]$DayOfMonth = 1,
    [string]$Time = '03:30',
    [int]$Keep = 5,
    [switch]$Unregister,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path $PSScriptRoot -Parent
$pruneAll = Join-Path $PSScriptRoot 'prune-all.ps1'

if (-not (Test-Path -LiteralPath $pruneAll)) { throw "未找到 $pruneAll" }

if ($Unregister) {
    if ($DryRun) { Write-Host "[DRYRUN] 将删除计划任务 $TaskName"; exit 0 }
    $principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw '需要管理员权限' }
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host "[OK] 已删除计划任务 $TaskName" -ForegroundColor Green
    exit 0
}

$argLine = '-NoProfile -ExecutionPolicy Bypass -File "{0}" -Keep {1}' -f $pruneAll, $Keep
Write-Host '计划任务定义：' -ForegroundColor Cyan
Write-Host "  名称: $TaskName"
Write-Host "  时间: 每月 $DayOfMonth 日 $Time"
Write-Host "  身份: SYSTEM（最高权限）"
Write-Host "  动作: powershell.exe $argLine"

if ($DryRun) { Write-Host '[DRYRUN] 未注册' -ForegroundColor Yellow; exit 0 }

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw '注册计划任务需要管理员权限（请以管理员运行 PowerShell 或通过提权编排执行）'
}

$at = [datetime]::ParseExact($Time, 'HH:mm', $null)
# PS 5.1 的 New-ScheduledTaskTrigger 不支持 -Monthly，改用 schtasks（/SC MONTHLY /D 日 /ST 时间 /RU SYSTEM /RL HIGHEST）
$tr = 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "{0}" -Keep {1}' -f $pruneAll, $Keep
& schtasks.exe /Create /TN $TaskName /TR $tr /SC MONTHLY /D $DayOfMonth /ST $Time /RU SYSTEM /RL HIGHEST /F | Out-Null
if ($LASTEXITCODE -ne 0) { throw "schtasks 注册失败 exit=$LASTEXITCODE" }

$info = Get-ScheduledTaskInfo -TaskName $TaskName
$task = Get-ScheduledTask -TaskName $TaskName
Write-Host ('[OK] 已注册计划任务 ' + $TaskName + '（状态 ' + $task.State + '），下次运行: ' + $info.NextRunTime) -ForegroundColor Green
