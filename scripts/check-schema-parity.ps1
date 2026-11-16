#requires -Version 5.1
<#
.SYNOPSIS
  数据库结构单一可信来源校验：init.sql / migration / 生产库三方一致性（只读）。

.DESCRIPTION
  本项目数据库 DDL 的唯一权威来源是 database\init.sql（新环境执行它即可得到完整结构）。
  database\migration\*.sql 是既有库的增量历史，不得再成为新环境的建表来源。
  本脚本只读校验，用于发布前/巡检时防止三者漂移：

  校验 1（基线 vs 生产库）：init.sql 定义的表必须与生产库业务表完全一致
    - 忽略 *_bak_*（历史备份表）与 mig_*（迁移标记表），与 init.sql 头注释口径一致
    - 缺失 → 新环境建库后功能会缺表（HIGH）
    - 多余 → init.sql 定义的表在库中不存在（HIGH）

  校验 2（迁移 vs 基线）：任何 migration 定义的业务表必须已并入 init.sql
    - 未并入 → 新环境会缺表，且 db-migrate -Mode Bootstrap 会把该脚本误登记为已执行（HIGH）

  本脚本不写库、不改文件。凭据解析复用 scripts\common-credentials.ps1。

.PARAMETER DbName
  目标库名，默认 expert_system

.PARAMETER DbHost / DbPort / DbUser
  连接参数，默认 127.0.0.1:3306 root

.EXAMPLE
  .\scripts\check-schema-parity.ps1
  .\scripts\check-schema-parity.ps1 -NoDatabase   # 只做校验 2（文件级，无需数据库）
#>
[CmdletBinding()]
param(
    [string]$DbName = 'expert_system',
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$DbUser = 'root',
    [switch]$NoDatabase
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$InitSql = Join-Path $RepoRoot 'database\init.sql'
$MigrationDir = Join-Path $RepoRoot 'database\migration'

$script:Failed = 0
function Write-Ok([string]$m) { Write-Host "[OK]   $m" -ForegroundColor Green }
function Write-Info([string]$m) { Write-Host "       $m" -ForegroundColor DarkGray }
function Write-Warn2([string]$m) { Write-Host "[WARN] $m" -ForegroundColor Yellow }
function Write-Bad([string]$m) { Write-Host "[FAIL] $m" -ForegroundColor Red; $script:Failed++ }

if (-not (Test-Path -LiteralPath $InitSql)) { Write-Host "[失败] 未找到 init.sql：$InitSql" -ForegroundColor Red; exit 2 }

$tablePattern = [regex]'(?i)CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+`?(\w+)`?'
function Get-SqlTables([string]$path) {
    # 先剔除整行注释，避免注释里的 “SHOW CREATE TABLE 导出…” 之类文本被误识别为表名
    $lines = [IO.File]::ReadAllLines($path) | Where-Object { $_.TrimStart() -notlike '--*' }
    $text = $lines -join "`n"
    $set = New-Object System.Collections.Generic.HashSet[string]
    foreach ($m in $tablePattern.Matches($text)) {
        $name = $m.Groups[1].Value.ToLowerInvariant()
        if ($name -eq 'if' -or $name -eq 'exists') { continue }
        [void]$set.Add($name)
    }
    return $set
}

function Test-IgnoredTable([string]$name) {
    return ($name -like '*_bak_*' -or $name -like 'mig_*')
}

Write-Host '数据库结构单一可信来源校验（只读）' -ForegroundColor Cyan
Write-Info "init.sql: $InitSql"

# ---------- 校验 2：migration 定义的业务表必须都已并入 init.sql ----------
$initTables = Get-SqlTables $InitSql
Write-Info ("init.sql 定义表数: " + $initTables.Count)

$migrationTables = @{}
foreach ($f in Get-ChildItem -LiteralPath $MigrationDir -Filter '*.sql' -File | Sort-Object Name) {
    foreach ($t in (Get-SqlTables $f.FullName)) {
        if ((Test-IgnoredTable $t) -or $initTables.Contains($t)) { continue }
        if (-not $migrationTables.ContainsKey($t)) { $migrationTables[$t] = @() }
        $migrationTables[$t] += $f.Name
    }
}
if ($migrationTables.Count -eq 0) {
    Write-Ok '校验 2：所有迁移定义的业务表均已并入 init.sql 基线'
} else {
    Write-Bad ("校验 2：有 " + $migrationTables.Count + ' 张业务表只存在于 migration、未并入 init.sql（新环境会缺表）')
    foreach ($t in ($migrationTables.Keys | Sort-Object)) {
        Write-Info ("  缺少基线: " + $t + "  <- " + ($migrationTables[$t] -join ', '))
    }
}

if ($NoDatabase) {
    Write-Host ''
    if ($script:Failed -gt 0) { Write-Host "[结果] 校验失败 $($script:Failed) 项（已跳过数据库校验）" -ForegroundColor Red; exit 1 }
    Write-Ok '文件级校验通过（已按 -NoDatabase 跳过数据库校验）'
    exit 0
}

# ---------- 校验 1：init.sql 与生产库业务表一致 ----------
function Find-MySqlClient {
    $cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(Get-ChildItem 'C:\Program Files\MySQL\*\bin\mysql.exe' -ErrorAction SilentlyContinue |
                    Sort-Object FullName -Descending)
    if ($candidates.Count -gt 0) { return $candidates[0].FullName }
    return ''
}

$mysql = Find-MySqlClient
if (-not $mysql) { Write-Warn2 '未找到 mysql.exe，跳过校验 1（设置 -NoDatabase 可显式跳过）'; exit $(if ($script:Failed -gt 0) { 1 } else { 0 }) }

. (Join-Path $PSScriptRoot 'common-credentials.ps1')
$password = Resolve-DbPassword
if (-not $password) { Write-Warn2 '未解析到 MYSQL_PASSWORD，跳过校验 1'; exit $(if ($script:Failed -gt 0) { 1 } else { 0 }) }
$env:MYSQL_PWD = $password

try {
    $rows = & $mysql @('-h', $DbHost, '-P', "$DbPort", '-u', $DbUser, '--batch', '--skip-column-names',
        '--default-character-set=utf8mb4', '-e', "SELECT table_name FROM information_schema.tables WHERE table_schema='$DbName' AND table_type='BASE TABLE';") 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($rows -join ' ') }

    $live = New-Object System.Collections.Generic.HashSet[string]
    foreach ($r in $rows) {
        $name = "$r".Trim().ToLowerInvariant()
        if (-not $name -or (Test-IgnoredTable $name)) { continue }
        [void]$live.Add($name)
    }

    $missingInInit = @($live | Where-Object { -not $initTables.Contains($_) } | Sort-Object)
    $missingInDb = @($initTables | Where-Object { -not $live.Contains($_) } | Sort-Object)

    Write-Info ("生产库业务表数: " + $live.Count)

    if ($missingInInit.Count -eq 0 -and $missingInDb.Count -eq 0) {
        Write-Ok '校验 1：init.sql 与生产库业务表清单一致'
    } else {
        if ($missingInInit.Count -gt 0) {
            Write-Bad ("校验 1：库中存在但 init.sql 未定义 " + $missingInInit.Count + ' 张（新环境缺表）')
            foreach ($t in $missingInInit) { Write-Info ("  库有基线无: " + $t) }
        }
        if ($missingInDb.Count -gt 0) {
            Write-Bad ("校验 1：init.sql 定义但库中不存在 " + $missingInDb.Count + ' 张（基线含幻影表）')
            foreach ($t in $missingInDb) { Write-Info ("  基线有库无: " + $t) }
        }
    }
} finally {
    $env:MYSQL_PWD = $null
}

Write-Host ''
if ($script:Failed -gt 0) { Write-Host "[结果] 校验失败 $($script:Failed) 项" -ForegroundColor Red; exit 1 }
Write-Ok '全部校验通过：init.sql 为唯一 DDL 可信来源'
exit 0
