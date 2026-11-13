# 20261109 供应商行价补充「报价日期 / 有效期至」两列（幂等，可重复执行）
# 用法（管理员 PowerShell）：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-migration-20261109-price-quote-date.ps1
# 说明：仅新增可空列，旧代码兼容；执行后再发布 1.0.37.21 后端（旧 jar 不读新列，先加列安全）。
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
$root = 'D:\jshz-PMO'
$sqlPath = Join-Path $root 'database\migration\20261109_supplier_price_quote_date.sql'
$logPath = Join-Path $root 'logs\migration-20261109-price-quote-date.log'
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
# PS 5.1 默认把管道写入原生程序的编码当 ASCII，会把中文 COMMENT 变成 ?，这里强制 UTF-8（无 BOM）
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Write-Both '[OK] 凭据已注入会话（值不打印）' Green

if ($DryRun) {
    Write-Both '[DRY-RUN] 将执行：' Cyan
    Get-Content -LiteralPath $sqlPath -Encoding UTF8 | ForEach-Object { Write-Both ('  ' + $_) }
    exit 0
}

# ---------- 执行迁移 ----------
Write-Both '[1/2] 执行迁移 SQL ...' Cyan
$before = (@(& $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names expert_system `
    -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='expert_system' AND TABLE_NAME='supplier_price_item' AND COLUMN_NAME IN ('quote_date','valid_until');") -join '').Trim()
Write-Both "  执行前列数：$before"

Get-Content -LiteralPath $sqlPath -Raw -Encoding UTF8 | & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --raw expert_system *>> $logPath
if ($LASTEXITCODE -ne 0) { throw "迁移 SQL 执行失败，退出码 $LASTEXITCODE（详见 $logPath）" }

# ---------- 校验 ----------
Write-Both '[2/2] 校验列已存在 ...' Cyan
$after = (@(& $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names expert_system `
    -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='expert_system' AND TABLE_NAME='supplier_price_item' AND COLUMN_NAME IN ('quote_date','valid_until');") -join '').Trim()
Write-Both "  执行后列数：$after"
if ([int]$after -ne 2) { throw "校验失败：期望 2 列，实际 $after" }

$cols = (& $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names expert_system `
    -e "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='expert_system' AND TABLE_NAME='supplier_price_item' AND COLUMN_NAME IN ('quote_date','valid_until') ORDER BY COLUMN_NAME;")
Write-Both ($cols -join ' | ')

Write-Both ('MIGRATION_OK ' + (Get-Date).ToString('s')) Green
exit 0
