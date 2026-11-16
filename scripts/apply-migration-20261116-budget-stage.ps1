# 20261116 「会议预算制作」阶段迁移（幂等，可重复执行）
# 用法（管理员 PowerShell）：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-migration-20261116-budget-stage.ps1
# 内容：project_phase 新增「会议预算制作」(sort=98，位于日程上传之前) + 新表
#       project_budget / project_budget_item / project_budget_fee_standard（含收费标准种子）
# 顺序：先迁移再发布含本功能的后端版本。
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
# PS5.1 管道给原生命令默认 ASCII，中文会变 '?'（20261109 脚本同款修复）
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
. (Join-Path $PSScriptRoot 'common-paths.ps1')
$sqlPath = Join-Path $root 'database\migration\20261116_budget_stage.sql'
$logPath = Join-Path $root 'logs\migration-20261116-budget-stage.log'
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

# ---------- 读取凭据（公共库解析，不入日志） ----------
. (Join-Path $PSScriptRoot 'common-credentials.ps1')
$null = Use-DbPassword
$dbUser = 'root'
$dbName = 'expert_system'

if ($DryRun) {
  Write-Both '[DryRun] 仅校验脚本与凭据存在，不执行 SQL。' 'Yellow'
  exit 0
}

# ---------- 执行迁移 ----------
Get-Content -LiteralPath $sqlPath -Raw -Encoding UTF8 | & $mysqlExe --host=127.0.0.1 --port=3306 --user=$dbUser --default-character-set=utf8mb4 --batch --raw $dbName *>> $logPath
if ($LASTEXITCODE -ne 0) { throw "mysql 执行失败，ExitCode=$LASTEXITCODE，详见 $logPath" }
Write-Both 'SQL 执行完成。' 'Green'

# ---------- 校验 ----------
$check = & $mysqlExe --host=127.0.0.1 --port=3306 --user=$dbUser --default-character-set=utf8mb4 --batch --skip-column-names $dbName -e "SELECT COUNT(*) FROM project_phase WHERE name='会议预算制作' AND is_active=1; SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('project_budget','project_budget_item','project_budget_fee_standard'); SELECT COUNT(*) FROM project_budget_fee_standard;"
$check = @($check)
if ([int]$check[0] -lt 1) { throw '校验失败：project_phase 未找到「会议预算制作」' }
if ([int]$check[1] -ne 3) { throw "校验失败：新表数量不对（期望3，实际 $($check[1])）" }
if ([int]$check[2] -lt 13) { throw "校验失败：收费标准种子不足（期望>=13，实际 $($check[2])）" }
Write-Both ("校验通过：阶段已存在，3 张新表就绪，收费标准种子 $($check[2]) 条。") 'Green'
Write-Both '=== 迁移完成 ===' 'Cyan'
