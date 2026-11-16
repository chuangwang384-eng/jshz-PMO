<#
.SYNOPSIS
  jshz-PMO 数据库迁移管理：状态查看 / 执行待迁移 / 基线回填（schema_migration 版本表）。

.DESCRIPTION
  规则：
  - 所有结构、权限、字典变更都必须落成 database\migration\<yyyyMMdd>_<描述>.sql（幂等可重复执行）。
  - 本脚本用 SHA256 校验脚本内容，把「已执行」记录到 expert_system.schema_migration。
  - 已登记脚本若内容被改动 → 判定为「漂移（DRIFT）」，只告警不自动重跑。

  三种模式：
  -Mode Status（默认）：列出已执行 / 待执行 / 漂移 / 失败，不写库。
  -Mode Apply        ：按文件名顺序执行所有未登记的脚本，成功后登记（SHA256 + 时间 + 执行人）。
  -Mode Bootstrap    ：创建 schema_migration 并把「现有脚本 + init.sql 基线」全部登记为已执行
                       （用于接管历史库或新环境由 init.sql 建库后初始化版本台账）。

  凭据：优先环境变量 MYSQL_PASSWORD，缺省从本地 start-backend.bat（gitignored）运行时解析，不落盘。
  日志：logs\db-migrate-<yyyyMMdd_HHmmss>.log（-NoLog 可关闭）。

.EXAMPLE
  .\scripts\db-migrate.ps1
  .\scripts\db-migrate.ps1 -Mode Apply
  .\scripts\db-migrate.ps1 -Mode Bootstrap
#>
[CmdletBinding()]
param(
    [ValidateSet('Status', 'Apply', 'Bootstrap')]
    [string]$Mode = 'Status',
    [string]$DbName = 'expert_system',
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$DbUser = 'root',
    [switch]$NoLog
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$MigrationDir = Join-Path $RepoRoot 'database\migration'
$InitSql = Join-Path $RepoRoot 'database\init.sql'
$LogDir = Join-Path $RepoRoot 'logs'
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir -Force | Out-Null }
$LogFile = Join-Path $LogDir ("db-migrate-{0}.log" -f (Get-Date -Format 'yyyyMMdd_HHmmss'))

function Log([string]$msg) {
    $line = "[{0}] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $msg
    Write-Host $line
    if (-not $NoLog) { Add-Content -LiteralPath $LogFile -Value $line -Encoding UTF8 }
}

function Fail([string]$msg) {
    Log "[失败] $msg"
    exit 1
}

# ---------- MySQL 客户端与凭据 ----------
function Find-MySqlClient {
    $cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(Get-ChildItem 'C:\Program Files\MySQL\*\bin\mysql.exe' -ErrorAction SilentlyContinue |
                   Sort-Object FullName -Descending)
    if ($candidates.Count -gt 0) { return $candidates[0].FullName }
    return ''
}

# 凭据解析（方案 A，2026-11-13 收敛：实现统一在 scripts/common-credentials.ps1）
. (Join-Path $PSScriptRoot 'common-credentials.ps1')

$mysql = Find-MySqlClient
if (-not $mysql) { Fail '未找到 mysql.exe（已探测 PATH 与 C:\Program Files\MySQL\*\bin）' }
$password = Resolve-DbPassword
if (-not $password) { Fail '未解析到 MYSQL_PASSWORD：请设置环境变量或确认 start-backend.bat 存在' }
$env:MYSQL_PWD = $password

function Invoke-MySqlQuery([string]$sql) {
    $out = & $mysql @('-h', $DbHost, '-P', "$DbPort", '-u', $DbUser, '--batch', '--skip-column-names',
        '--default-character-set=utf8mb4', $DbName, '-e', $sql) 2>&1
    if ($LASTEXITCODE -ne 0) { Fail ("SQL 执行失败: " + ($out -join ' ')) }
    return @($out | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Invoke-SqlFile([string]$path) {
    $errFile = [IO.Path]::GetTempFileName()
    $p = Start-Process -FilePath $mysql `
        -ArgumentList @('-h', $DbHost, '-P', "$DbPort", '-u', $DbUser, '--default-character-set=utf8mb4', $DbName) `
        -RedirectStandardInput $path -RedirectStandardError $errFile -NoNewWindow -Wait -PassThru
    $errText = if (Test-Path $errFile) { (Get-Content $errFile -Raw -Encoding UTF8) } else { '' }
    Remove-Item $errFile -Force -ErrorAction SilentlyContinue
    if ($p.ExitCode -ne 0) { return @{ ok = $false; error = $errText.Trim() } }
    if ($errText -match '(?i)ERROR \d+') { return @{ ok = $false; error = $errText.Trim() } }
    return @{ ok = $true; error = '' }
}

function Get-FileSha256([string]$path) {
    return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Test-RegistryExists {
    $r = Invoke-MySqlQuery "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DbName' AND table_name='schema_migration';"
    return ($r[0] -eq '1')
}

function Get-Registry {
    if (-not (Test-RegistryExists)) { return @{} }
    $rows = Invoke-MySqlQuery 'SELECT script, checksum, status FROM schema_migration;'
    $map = @{}
    foreach ($row in $rows) {
        $f = $row -split "`t"
        if ($f.Count -ge 3) { $map[$f[0]] = @{ checksum = $f[1]; status = $f[2] } }
    }
    return $map
}

function Initialize-Registry {
    $ddl = @'
CREATE TABLE IF NOT EXISTS `schema_migration` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `script`      VARCHAR(191) NOT NULL COMMENT '迁移文件名（database/migration 下，含日期前缀）',
  `checksum`    CHAR(64)     NOT NULL COMMENT '脚本文件 SHA256（漂移检测）',
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'APPLIED' COMMENT 'APPLIED/BASELINE/FAILED',
  `applied_at`  DATETIME     DEFAULT NULL COMMENT '执行时间',
  `applied_by`  VARCHAR(64)  DEFAULT NULL COMMENT '执行人（本机账号）',
  `note`        VARCHAR(500) DEFAULT NULL COMMENT '备注（回填来源/说明）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登记时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_script` (`script`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库迁移版本表';
'@
    $tmp = [IO.Path]::GetTempFileName() + '.sql'
    [IO.File]::WriteAllText($tmp, $ddl, (New-Object System.Text.UTF8Encoding($false)))
    $res = Invoke-SqlFile $tmp
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    if (-not $res.ok) { Fail "创建 schema_migration 失败: $($res.error)" }
}

function Add-RegistryRow([string]$script, [string]$checksum, [string]$status, [string]$appliedAt, [string]$note) {
    $at = if ($appliedAt) { "'$appliedAt'" } else { 'NULL' }
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("INSERT INTO schema_migration (script, checksum, status, applied_at, applied_by, note) VALUES (")
    [void]$sb.Append("'$($script -replace "'", "''")', '$checksum', '$status', $at, '$env:USERNAME', ")
    [void]$sb.Append("'$($note -replace "'", "''")') ")
    [void]$sb.Append('ON DUPLICATE KEY UPDATE checksum=VALUES(checksum), status=VALUES(status), ')
    [void]$sb.Append('applied_at=VALUES(applied_at), applied_by=VALUES(applied_by), note=VALUES(note);')
    $tmp = [IO.Path]::GetTempFileName() + '.sql'
    [IO.File]::WriteAllText($tmp, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
    $res = Invoke-SqlFile $tmp
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    if (-not $res.ok) { Fail "登记迁移失败（$script）: $($res.error)" }
}

# ---------- 主流程 ----------
if (-not (Test-Path $MigrationDir)) { Fail "未找到迁移目录: $MigrationDir" }
$scripts = @(Get-ChildItem -LiteralPath $MigrationDir -Filter '*.sql' -File | Sort-Object Name)
Log "==== db-migrate 启动（Mode=$Mode，库=$DbName，脚本=$($scripts.Count) 个） ===="

$registry = Get-Registry
$pending = @()
$drift = @()
foreach ($s in $scripts) {
    $hash = Get-FileSha256 $s.FullName
    if (-not $registry.ContainsKey($s.Name)) {
        $pending += [pscustomobject]@{ Name = $s.Name; Path = $s.FullName; Hash = $hash }
    } elseif ($registry[$s.Name].checksum -ne $hash) {
        $drift += [pscustomobject]@{ Name = $s.Name; Recorded = $registry[$s.Name].checksum; Current = $hash }
    }
}

switch ($Mode) {
    'Status' {
        if (-not (Test-RegistryExists)) {
            Log '[提示] schema_migration 表不存在：先执行 -Mode Bootstrap（历史库接管）或确认库已存在'
        }
        $applied = @($scripts | Where-Object { $registry.ContainsKey($_.Name) }).Count
        Log ("已登记: {0} 个；待执行: {1} 个；漂移: {2} 个" -f $applied, $pending.Count, $drift.Count)
        if ($pending.Count) { Log '--- 待执行（按文件名顺序）---'; $pending | ForEach-Object { Log ("  PENDING  " + $_.Name) } }
        if ($drift.Count) { Log '--- 已执行但脚本内容被改动（漂移，需人工确认）---'; $drift | ForEach-Object { Log ("  DRIFT    " + $_.Name) } }
        if (-not $pending.Count -and -not $drift.Count) { Log '[OK] 迁移状态一致，无待执行脚本' }
    }
    'Apply' {
        Initialize-Registry
        $registry = Get-Registry
        if ($drift.Count) {
            Log '[中止] 存在漂移脚本，先人工确认后再执行：'
            $drift | ForEach-Object { Log ("  DRIFT " + $_.Name) }
            exit 1
        }
        $todo = @($scripts | Where-Object { -not $registry.ContainsKey($_.Name) })
        if (-not $todo.Count) { Log '[OK] 无待执行脚本'; break }
        foreach ($s in $todo) {
            Log ("执行: " + $s.Name)
            $res = Invoke-SqlFile $s.FullName
            $hash = Get-FileSha256 $s.FullName
            if ($res.ok) {
                Add-RegistryRow $s.Name $hash 'APPLIED' (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') 'db-migrate Apply'
                Log ("  [OK] 已执行并登记 " + $s.Name)
            } else {
                Add-RegistryRow $s.Name $hash 'FAILED' (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ("执行失败: " + $res.error)
                Fail ("执行失败，已停止后续脚本：" + $s.Name + " -> " + $res.error)
            }
        }
        Log '[OK] 待执行脚本已全部应用'
    }
    'Bootstrap' {
        Initialize-Registry
        $registry = Get-Registry
        $created = 0
        foreach ($s in $scripts) {
            if ($registry.ContainsKey($s.Name)) { continue }
            $hash = Get-FileSha256 $s.FullName
            Add-RegistryRow $s.Name $hash 'APPLIED' $s.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss') `
                '回填：接管历史库，按现有结构与脚本落盘时间登记（非精确执行时刻）'
            $created++
        }
        if (Test-Path $InitSql) {
            $initHash = Get-FileSha256 $InitSql
            Add-RegistryRow 'init.sql' $initHash 'BASELINE' $null '基线：database\init.sql 全量结构，不计入待执行'
        }
        Log ("[OK] Bootstrap 完成，新登记 {0} 个脚本 + init.sql 基线" -f $created)
        $registry = Get-Registry
        $still = @($scripts | Where-Object { -not $registry.ContainsKey($_.Name) })
        Log ("核验：未登记脚本 {0} 个" -f $still.Count)
    }
}

Log ("日志: " + $LogFile)
$env:MYSQL_PWD = $null
