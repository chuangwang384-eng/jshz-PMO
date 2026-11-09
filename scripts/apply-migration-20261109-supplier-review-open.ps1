# 20261109 供应商「服务评价」权限放开至全员（幂等，可重复执行）
# 用法（管理员 PowerShell）：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-migration-20261109-supplier-review-open.ps1
# 说明：仅改 permission / role_permission 数据，不动表结构；执行后再发布 1.0.37.22 后端以刷新权限缓存。
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
$root = 'D:\jshz-PMO'
$sqlPath = Join-Path $root 'database\migration\20261109_supplier_review_open.sql'
$logPath = Join-Path $root 'logs\migration-20261109-supplier-review-open.log'
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
$batText = Get-Content -LiteralPath $startBat -Raw -Encoding Default
$m = [regex]::Match($batText, '(?im)^[ \t]*set[ \t]+MYSQL_PASSWORD=(.*)$')
if (-not $m.Success) { throw 'start-backend.bat 缺少 MYSQL_PASSWORD' }
$pwd = $m.Groups[1].Value.Trim()
$quote = [char]34
if ($pwd.Length -ge 2 -and $pwd[0] -eq $quote -and $pwd[$pwd.Length - 1] -eq $quote) { $pwd = $pwd.Substring(1, $pwd.Length - 2) }
if ([string]::IsNullOrWhiteSpace($pwd)) { throw 'start-backend.bat 中 MYSQL_PASSWORD 为空' }
$env:MYSQL_PWD = $pwd
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Write-Both '[OK] 凭据已注入会话（值不打印）' Green

if ($DryRun) {
    Write-Both '[DRY-RUN] 将执行：' Cyan
    Get-Content -LiteralPath $sqlPath -Encoding UTF8 | ForEach-Object { Write-Both ('  ' + $_) }
    exit 0
}

# ---------- 执行前快照 ----------
$ruleQuery = "SELECT CONCAT(code,'|',method,'|',path) FROM permission WHERE method='POST' AND path='/api/supplier/*/reviews';"
$bindQuery = "SELECT CONCAT(r.code) FROM role_permission rp JOIN role r ON r.id=rp.role_id WHERE rp.permission_code='supplier:review' ORDER BY r.id;"

Write-Both '[1/3] 执行迁移 SQL ...' Cyan
$ruleBefore = (@(& $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names expert_system -e $ruleQuery) -join ' ').Trim()
Write-Both "  规则前：$ruleBefore"

Get-Content -LiteralPath $sqlPath -Raw -Encoding UTF8 | & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --raw expert_system *>> $logPath
if ($LASTEXITCODE -ne 0) { throw "迁移 SQL 执行失败，退出码 $LASTEXITCODE（详见 $logPath）" }

# ---------- 校验规则行 ----------
Write-Both '[2/3] 校验规则已切换到 supplier:review ...' Cyan
$ruleAfter = (@(& $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names expert_system -e $ruleQuery) -join ' ').Trim()
Write-Both "  规则后：$ruleAfter"
if ($ruleAfter -notlike 'supplier:review|*') { throw "校验失败：规则未切到 supplier:review（实际 $ruleAfter）" }
if ($ruleAfter -like '*supplier:manage*') { throw "校验失败：仍存在 supplier:manage 规则行（$ruleAfter），会导致非 ADMIN 先命中 403" }

# ---------- 校验角色绑定 ----------
Write-Both '[3/3] 校验角色绑定 ...' Cyan
$roles = (& $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names expert_system -e $bindQuery)
Write-Both ("  已绑定角色： " + ($roles -join ', '))
$expect = @('ADMIN', 'APPROVER', 'EMPLOYEE', 'KNOWLEDGE_MAINTAINER', 'PROJECT_OWNER')
$missing = $expect | Where-Object { $roles -notcontains $_ }
if ($missing) { throw ("校验失败：以下角色未绑定 supplier:review -> " + ($missing -join ', ')) }

Write-Both ('MIGRATION_OK ' + (Get-Date).ToString('s')) Green
Write-Both '  注意：权限规则与用户权限集有进程内缓存，需重启/蓝绿发布 ExpertSystemBackend 后生效。' Yellow
exit 0
