# 20261116 AI 助手迁移（幂等，可重复执行）
# 用法（管理员 PowerShell）：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-migration-20261116-ai-assistant.ps1
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
. (Join-Path $PSScriptRoot 'common-paths.ps1')
. (Join-Path $PSScriptRoot 'common-credentials.ps1')
$sqlPath = Join-Path $root 'database\migration\20261116_ai_assistant.sql'
$logPath = Join-Path $root 'logs\migration-20261116-ai-assistant.log'
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

$check = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names expert_system -e "SELECT COUNT(*) FROM ai_assistant_entry; SELECT COUNT(*) FROM permission WHERE code='ai:manage'; SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.id=rp.role_id WHERE rp.permission_code='ai:manage' AND r.code='ADMIN';"
$check = @($check)
if ([int]$check[0] -lt 20) { throw "校验失败：知识条目不足（期望>=20，实际 $($check[0])）" }
if ([int]$check[1] -lt 3) { throw "校验失败：ai:manage 权限行不足（期望>=3，实际 $($check[1])）" }
if ([int]$check[2] -lt 1) { throw '校验失败：ai:manage 未绑定 ADMIN' }
Write-Both ("校验通过：条目 $($check[0]) 条，权限 $($check[1]) 行，ADMIN 已绑定。") 'Green'
Write-Both '=== 迁移完成 ===' 'Cyan'
