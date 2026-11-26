# 20261109 项目流程重塑第一阶段「日程上传」（幂等，可重复执行）
# 用法（管理员 PowerShell）：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-migration-20261109-schedule-stage.ps1
# 内容：新表 project_schedule / project_speaker + AGENDA_UPLOAD 阶段种子（旧 6 阶段停用）+ type_id 置 NULL + 标记表
# 顺序：必须先迁移再蓝绿发布 1.0.37.24 后端（新代码读新表）。
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common-paths.ps1')
$sqlPath = Join-Path $root 'database\migration\20261109_project_schedule_stage.sql'
$logPath = Join-Path $root 'logs\migration-20261109-schedule-stage.log'
$mysqlExe = 'C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe'
$startBat = Join-Path $root 'start-backend.bat'

function Write-Both([string]$msg, [string]$color = 'Gray') {
    Write-Host $msg -ForegroundColor $color
    Add-Content -LiteralPath $logPath -Value $msg -Encoding UTF8
}

if (-not (Test-Path -LiteralPath $sqlPath)) { throw "缺少迁移脚本：$sqlPath" }
if (-not (Test-Path -LiteralPath $mysqlExe)) { throw "缺少 mysql 客户端：$mysqlExe" }
if (-not (Test-Path -LiteralPath $logPath)) { New-Item -ItemType File -Path $logPath -Force | Out-Null }

Write-Both ('=== 迁移开始 ' + (Get-Date).ToString('s') + ' ===') 'Cyan'

# ---------- 提权校验 ----------
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator) -and -not $DryRun) {
    throw '必须以管理员身份运行（需要读取 start-backend.bat 凭据并写库）'
}

# ---------- 凭据（不打印值） ----------
. (Join-Path $PSScriptRoot 'common-credentials.ps1')
$null = Use-DbPassword
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Write-Both '[OK] 凭据已注入会话（值不打印）' Green

if ($DryRun) {
    Write-Both '[DRY-RUN] 将执行：' Cyan
    Get-Content -LiteralPath $sqlPath -Encoding UTF8 | ForEach-Object { Write-Both ('  ' + $_) }
    exit 0
}

# ---------- 执行前状态 ----------
$db = 'expert_system'
$pre = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names $db -e "SELECT CONCAT('schedule=',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='project_schedule'),' speaker=',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='project_speaker'),' phase=',(SELECT COUNT(*) FROM project_phase WHERE name='日程上传'));"
Write-Both "  执行前：$pre"

Write-Both '[1/2] 执行迁移 SQL ...' Cyan
Get-Content -LiteralPath $sqlPath -Raw -Encoding UTF8 | & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --raw $db *>> $logPath
if ($LASTEXITCODE -ne 0) { throw "迁移 SQL 执行失败，退出码 $LASTEXITCODE（详见 $logPath）" }

# ---------- 校验 ----------
Write-Both '[2/2] 校验迁移结果 ...' Cyan
$chk = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names $db -e "SELECT CONCAT('schedule_cols=',(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='project_schedule'),' speaker_cols=',(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='project_speaker'),' agenda_phase=',(SELECT COUNT(*) FROM project_phase WHERE name='日程上传' AND is_active=1),' old_phases_off=',(SELECT COUNT(*) FROM project_phase WHERE name IN ('立项','预算','筹备','进行中','结算','已完成') AND is_active=0),' marker=',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='mig_20261109_schedule_stage_done'));"
Write-Both "  校验：$chk"
if ($chk -notlike 'schedule_cols=13 speaker_cols=12 agenda_phase=1 old_phases_off=6 marker=1') { throw "校验失败：$chk" }

Write-Both ('MIGRATION_OK ' + (Get-Date).ToString('s')) Green
Write-Both '  注意：需继续蓝绿发布 1.0.37.24 后端（新代码读新表；旧代码不感知新表，先迁移后发布安全）。' Yellow
exit 0
