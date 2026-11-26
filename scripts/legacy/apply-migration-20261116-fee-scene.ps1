# 20261116 收费标准线上/线下分场景迁移（幂等，可重复执行）
# 用法（管理员 PowerShell）：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-migration-20261116-fee-scene.ps1
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
# PS5.1 管道给原生命令默认 ASCII，中文会变 '?'（20261116 首轮迁移已踩坑）
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
. (Join-Path $PSScriptRoot 'common-paths.ps1')
. (Join-Path $PSScriptRoot 'common-credentials.ps1')
$sqlPath = Join-Path $root 'database\migration\20261116_budget_fee_scene.sql'
$logPath = Join-Path $root 'logs\migration-20261116-fee-scene.log'
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
  throw '必须以管理员身份运行'
}

$null = Use-DbPassword

if ($DryRun) {
  Write-Both '[DryRun] 仅校验脚本与凭据存在，不执行 SQL。' 'Yellow'
  exit 0
}

Get-Content -LiteralPath $sqlPath -Raw -Encoding UTF8 | & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --raw expert_system *>> $logPath
if ($LASTEXITCODE -ne 0) { throw "mysql 执行失败，ExitCode=$LASTEXITCODE，详见 $logPath" }
Write-Both 'SQL 执行完成。' 'Green'

$check = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names expert_system -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='project_budget_fee_standard' AND COLUMN_NAME='scene'; SELECT COUNT(*) FROM project_budget_fee_standard WHERE scene IN ('ONLINE','OFFLINE');"
$check = @($check)
if ([int]$check[0] -ne 1) { throw '校验失败：scene 列不存在' }
if ([int]$check[1] -lt 13) { throw "校验失败：scene 回填行数不足（期望>=13，实际 $($check[1])）" }
Write-Both ("校验通过：scene 列就绪，回填 $($check[1]) 行。") 'Green'
Write-Both '=== 迁移完成 ===' 'Cyan'
