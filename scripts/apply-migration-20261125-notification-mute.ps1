# 20261125 消息免打扰偏好表 notification_mute（幂等，可重复执行）
# 用法（管理员 PowerShell）：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-migration-20261125-notification-mute.ps1
# 内容：1 张新表（含 uk_user_type 唯一键与 sys_user 外键）。需求 D 的存储前提。
# 顺序：先迁移再蓝绿发布 1.0.142 后端；旧代码不感知该表，先迁移安全。
# 注意：含 FOREIGN KEY，若被后端连接池元数据锁阻塞，参考 1.0.137 经验低峰执行或先清理空闲连接。
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common-paths.ps1')
$sqlPath = Join-Path $root 'database\migration\20261125_add_notification_mute.sql'
$logPath = Join-Path $root 'logs\migration-20261125-notification-mute.log'
$mysqlExe = 'C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe'

function Write-Both([string]$msg, [string]$color = 'Gray') {
    Write-Host $msg -ForegroundColor $color
    Add-Content -LiteralPath $logPath -Value $msg -Encoding UTF8
}

if (-not (Test-Path -LiteralPath $sqlPath)) { throw "缺少迁移脚本：$sqlPath" }
if (-not (Test-Path -LiteralPath $mysqlExe)) { throw "缺少 mysql 客户端：$mysqlExe" }
if (-not (Test-Path -LiteralPath $logPath)) { New-Item -ItemType File -Path $logPath -Force | Out-Null }

Write-Both ('=== 迁移开始 ' + (Get-Date).ToString('s') + ' ===') 'Cyan'

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator) -and -not $DryRun) {
    throw '必须以管理员身份运行（需要读取 start-backend.bat 凭据并写库）'
}

. (Join-Path $PSScriptRoot 'common-credentials.ps1')
$null = Use-DbPassword
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Write-Both '[OK] 凭据已注入会话（值不打印）' Green

if ($DryRun) {
    Write-Both '[DRY-RUN] 将执行：' Cyan
    Get-Content -LiteralPath $sqlPath -Encoding UTF8 | ForEach-Object { Write-Both ('  ' + $_) }
    exit 0
}

$db = 'expert_system'
$preSql = "SELECT CONCAT('tables=',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='expert_system' AND table_name='notification_mute'));"
$pre = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names $db -e $preSql
Write-Both "  执行前：$pre"

Write-Both '[1/2] 执行迁移 SQL ...' Cyan
Get-Content -LiteralPath $sqlPath -Raw -Encoding UTF8 | & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --raw $db *>> $logPath
if ($LASTEXITCODE -ne 0) { throw "迁移 SQL 执行失败，退出码 $LASTEXITCODE（详见 $logPath）" }

Write-Both '[2/2] 校验迁移结果 ...' Cyan
$chk = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names $db -e $preSql
Write-Both "  校验：$chk"
if ($chk -notlike 'tables=1') { throw "校验失败：$chk（期望 tables=1）" }

$chk2 = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names $db -e "SHOW COLUMNS FROM notification_mute;"
Write-Both ("  列清单：" + (($chk2 | ForEach-Object { ($_ -split "`t")[0] }) -join ','))
Write-Both ('MIGRATION_OK ' + (Get-Date).ToString('s')) Green
Write-Both '  注意：需继续蓝绿发布 1.0.142 后端（免打扰读写与 SSE 在新包中）。' Yellow
exit 0
