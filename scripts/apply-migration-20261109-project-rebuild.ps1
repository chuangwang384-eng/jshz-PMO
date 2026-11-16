# 20261109 项目模块重置 + 新字段迁移（带备份，仅清理一次）
# 用法（管理员 PowerShell）：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-migration-20261109-project-rebuild.ps1
# 步骤：备份 -> 清测试数据（一次性，标记表守卫）-> 执行 DDL（幂等）-> 校验
# 回滚：备份表 *_bak_20261109 保留全部数据，可 INSERT 回原表；DDL 回滚见 SQL 文件头注释。
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common-paths.ps1')
$sqlPath = Join-Path $root 'database\migration\20261109_project_basic_fields.sql'
$logPath = Join-Path $root 'logs\migration-20261109-project-rebuild.log'
$mysqlExe = 'C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe'
$startBat = Join-Path $root 'start-backend.bat'
$marker = 'mig_20261109_project_reset_done'

function Write-Both([string]$msg, [string]$color = 'Gray') {
    Write-Host $msg -ForegroundColor $color
    Add-Content -LiteralPath $logPath -Value $msg -Encoding UTF8
}

function Invoke-Sql([string]$sql) {
    & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --raw expert_system -e $sql *>> $logPath
    if ($LASTEXITCODE -ne 0) { throw "SQL 执行失败：$sql（详见 $logPath）" }
}

function Invoke-Scalar([string]$sql) {
    (@(& $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names expert_system -e $sql) -join '').Trim()
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
    Write-Both '[DRY-RUN] 将执行：清理（一次性守卫）+ DDL 文件内容：' Cyan
    Get-Content -LiteralPath $sqlPath -Encoding UTF8 | ForEach-Object { Write-Both ('  ' + $_) }
    exit 0
}

# ---------- [1/4] 备份 + 清空测试数据（一次性，标记表守卫） ----------
$markerExists = (Invoke-Scalar "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='expert_system' AND TABLE_NAME='$marker';")
if ($markerExists -eq '1') {
    Write-Both '[1/4] 数据清理已执行过（标记表存在），跳过。仅重放幂等 DDL。' Yellow
}
else {
    $tables = @('project', 'project_executor', 'project_expert', 'project_supplier', 'project_doc', 'project_contract', 'project_phase_history')
    foreach ($t in $tables) {
        $bak = "${t}_bak_20261109"
        $cnt = Invoke-Scalar "SELECT COUNT(*) FROM ``$t``;"
        if ($cnt -ne '0') {
            Invoke-Sql "CREATE TABLE IF NOT EXISTS ``$bak`` LIKE ``$t``;"
            $bakEmpty = Invoke-Scalar "SELECT COUNT(*) FROM ``$bak``;"
            if ($bakEmpty -eq '0') {
                Invoke-Sql "INSERT INTO ``$bak`` SELECT * FROM ``$t``;"
                Write-Both ("  备份 ${t} -> ${bak}（$cnt 行）") Green
            }
            Invoke-Sql "DELETE FROM ``$t``;"
            Write-Both ("  清空 ${t}（$cnt 行）") Green
        }
        else {
            Write-Both ("  ${t} 已为空，跳过") DarkGray
        }
    }
    # 挂在项目上的合规执行记录（project_id 非空的行）
    $ce = Invoke-Scalar "SELECT COUNT(*) FROM compliance_execution WHERE project_id IS NOT NULL;"
    if ($ce -ne '0') {
        Invoke-Sql "CREATE TABLE IF NOT EXISTS compliance_execution_bak_20261109 LIKE compliance_execution;"
        $bakEmpty = Invoke-Scalar "SELECT COUNT(*) FROM compliance_execution_bak_20261109;"
        if ($bakEmpty -eq '0') { Invoke-Sql "INSERT INTO compliance_execution_bak_20261109 SELECT * FROM compliance_execution WHERE project_id IS NOT NULL;" }
        Invoke-Sql "DELETE FROM compliance_execution WHERE project_id IS NOT NULL;"
        Write-Both ("  清空 compliance_execution 挂项目行（$ce 行）") Green
    }
    # 项目相关操作日志
    $ol = Invoke-Scalar "SELECT COUNT(*) FROM operation_log WHERE target_type='PROJECT';"
    if ($ol -ne '0') {
        Invoke-Sql "CREATE TABLE IF NOT EXISTS operation_log_bak_20261109_project LIKE operation_log;"
        $bakEmpty = Invoke-Scalar "SELECT COUNT(*) FROM operation_log_bak_20261109_project;"
        if ($bakEmpty -eq '0') { Invoke-Sql "INSERT INTO operation_log_bak_20261109_project SELECT * FROM operation_log WHERE target_type='PROJECT';" }
        Invoke-Sql "DELETE FROM operation_log WHERE target_type='PROJECT';"
        Write-Both ("  清空 operation_log 项目日志（$ol 行）") Green
    }
    Invoke-Sql "CREATE TABLE IF NOT EXISTS ``$marker`` (id TINYINT NOT NULL PRIMARY KEY, done_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) COMMENT='20261109 项目测试数据清理标记（防重复执行）';"
    Write-Both '[1/4] 数据清理完成，已写入标记表。' Green
}

# ---------- [2/4] 执行 DDL（幂等） ----------
Write-Both '[2/4] 执行 DDL（新列 + project_assistant 表）...' Cyan
Get-Content -LiteralPath $sqlPath -Raw -Encoding UTF8 | & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --raw expert_system *>> $logPath
if ($LASTEXITCODE -ne 0) { throw "DDL 执行失败，退出码 $LASTEXITCODE（详见 $logPath）" }

# ---------- [3/4] 校验新列 ----------
Write-Both '[3/4] 校验新列 ...' Cyan
$cols = Invoke-Scalar "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='expert_system' AND TABLE_NAME='project' AND COLUMN_NAME IN ('team','city_id','meeting_start_date','meeting_end_date','client_company','client_name','remark');"
if ($cols -ne '7') { throw "校验失败：期望 7 个新列，实际 $cols" }
Write-Both "  新列数：$cols / 7" Green
$pa = Invoke-Scalar "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='expert_system' AND TABLE_NAME='project_assistant';"
if ($pa -ne '1') { throw "校验失败：project_assistant 表不存在" }
Write-Both '  project_assistant 表已存在' Green

# ---------- [4/4] 校验清空结果 ----------
Write-Both '[4/4] 校验清空结果 ...' Cyan
$left = Invoke-Scalar "SELECT (SELECT COUNT(*) FROM project) + (SELECT COUNT(*) FROM project_executor) + (SELECT COUNT(*) FROM project_expert) + (SELECT COUNT(*) FROM project_supplier) + (SELECT COUNT(*) FROM project_doc) + (SELECT COUNT(*) FROM project_phase_history) + (SELECT COUNT(*) FROM compliance_execution WHERE project_id IS NOT NULL);"
if ([int]$left -gt 0) { throw "校验失败：仍有 $left 行残留" }
Write-Both "  项目板块残留行数：$left" Green

Write-Both ('MIGRATION_OK ' + (Get-Date).ToString('s')) Green
Write-Both '  注意：项目接口行为变化需发布 1.0.37.23 后端（新 jar 读取新列）。' Yellow
exit 0
