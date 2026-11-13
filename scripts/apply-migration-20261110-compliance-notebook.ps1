# 20261110 企业合规记事本（幂等，可重复执行）
# 用法（管理员 PowerShell）：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-migration-20261110-compliance-notebook.ps1
# 内容：compliance_item 加 withdrawn 三列+索引；新表 compliance_item_hide / compliance_project_doc_binding /
#       compliance_project_todo + mig_20261110_notebook_done 标记表。
# 顺序：必须先迁移再蓝绿发布 1.0.37.34 后端（新代码读新列/新表；旧代码不感知，先迁移后发布安全）。
# ⚠️ 旧执行四表清理（20261110_compliance_legacy_cleanup.sql）不在本脚本内，需 Owner 单独批准后手工执行。
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
$root = 'D:\jshz-PMO'
$sqlPath = Join-Path $root 'database\migration\20261110_compliance_notebook.sql'
$logPath = Join-Path $root 'logs\migration-20261110-compliance-notebook.log'
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

$db = 'expert_system'
$pre = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names $db -e "SELECT CONCAT('hide=',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='compliance_item_hide'),' binding=',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='compliance_project_doc_binding'),' todo=',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='compliance_project_todo'),' withdrawn_col=',(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='compliance_item' AND column_name='withdrawn'));"
Write-Both "  执行前：$pre"

Write-Both '[1/2] 执行迁移 SQL ...' Cyan
Get-Content -LiteralPath $sqlPath -Raw -Encoding UTF8 | & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --raw $db *>> $logPath
if ($LASTEXITCODE -ne 0) { throw "迁移 SQL 执行失败，退出码 $LASTEXITCODE（详见 $logPath）" }

Write-Both '[2/2] 校验迁移结果 ...' Cyan
$chk = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names $db -e "SELECT CONCAT('hide=',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='compliance_item_hide'),' binding=',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='compliance_project_doc_binding'),' todo_cols=',(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='compliance_project_todo'),' withdrawn_col=',(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='compliance_item' AND column_name='withdrawn'),' marker=',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='mig_20261110_notebook_done'));"
Write-Both "  校验：$chk"
if ($chk -notlike 'hide=1 binding=1 todo_cols=16 withdrawn_col=1 marker=1') { throw "校验失败：$chk" }

Write-Both ('MIGRATION_OK ' + (Get-Date).ToString('s')) Green
Write-Both '  注意：需继续蓝绿发布 1.0.37.34 后端（新代码读新列/新表）。旧执行清理脚本另行批准执行。' Yellow
exit 0
