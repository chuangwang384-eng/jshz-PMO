# 20261112 存储管理权限码 storage:manage（幂等，可重复执行）
# 用法（管理员 PowerShell）：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-migration-20261112-storage-manage-permission.ps1
# 内容：permission 菜单 1 行 + 接口 6 行（GET stats/project-orphans/purge-preview，POST 3 条）；绑定 ADMIN 角色。
# 顺序：先迁移再蓝绿发布 1.0.38.16 后端（新接口要求该权限码；旧代码不感知，先迁移安全）。
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
$root = 'D:\jshz-PMO'
$sqlPath = Join-Path $root 'database\migration\20261112_add_storage_manage_permission.sql'
$logPath = Join-Path $root 'logs\migration-20261112-storage-manage-permission.log'
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

$db = 'expert_system'
$preSql = "SELECT CONCAT('menu=',(SELECT COUNT(*) FROM permission WHERE code='storage:manage' AND type=1),' api=',(SELECT COUNT(*) FROM permission WHERE code='storage:manage' AND type=2),' admin_bind=',(SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.id=rp.role_id WHERE r.code='ADMIN' AND rp.permission_code='storage:manage'));"
$pre = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names $db -e $preSql
Write-Both "  执行前：$pre"

Write-Both '[1/2] 执行迁移 SQL ...' Cyan
Get-Content -LiteralPath $sqlPath -Raw -Encoding UTF8 | & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --raw $db *>> $logPath
if ($LASTEXITCODE -ne 0) { throw "迁移 SQL 执行失败，退出码 $LASTEXITCODE（详见 $logPath）" }

Write-Both '[2/2] 校验迁移结果 ...' Cyan
$chk = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names $db -e $preSql
Write-Both "  校验：$chk"
if ($chk -notlike 'menu=1 api=6 admin_bind=1') { throw "校验失败：$chk（期望 menu=1 api=6 admin_bind=1）" }

Write-Both ('MIGRATION_OK ' + (Get-Date).ToString('s')) Green
Write-Both '  注意：需继续蓝绿发布 1.0.38.16 后端（新接口要求 storage:manage 权限码）。' Yellow
exit 0
