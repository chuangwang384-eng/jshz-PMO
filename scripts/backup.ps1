#requires -Version 5.1
<#
.SYNOPSIS
  jshz-PMO 一键备份与恢复演练脚本（单脚本三模式）

.DESCRIPTION
  -Mode Backup（默认）：mysqldump 全量导出 expert_system + 照片库/照片库_tmp/知识文档库 zip 归档
                         + 每产物 .sha256 + manifest.txt 汇总；保留最近 -Keep 份（超出的移入回收站）
  -Mode Verify：对最新（或 -From 指定）备份做 SHA256 复核 + zip 全量读取完整性校验
  -Mode Drill ：恢复演练——先校验，再重建测试库 expert_system_restore_test（仅此库！严禁触碰生产库），
                导入备份 SQL，与生产库对比表数量与每表行数，结论自动追加 docs\guides\2026-10-26-备份与恢复-演练文档.md
  日志：控制台输出 + logs\backup.log 追加（-NoLog 关闭文件日志）
  凭据：优先取环境变量 MYSQL_PASSWORD；未设置时从本地 start-backend.bat（gitignored）运行时解析，不落盘明文
  编码注意：本脚本含中文，必须以 UTF-8 with BOM 保存（PS5.1 约束）

.PARAMETER Mode
  Backup / Verify / Drill，默认 Backup。

.PARAMETER BackupDir
  备份根目录，默认 D:\jshz-PMO\backup（时间戳备份位于其下 backup_YYYYMMDD_HHmmss\）。

.PARAMETER Keep
  保留份数，默认 5；超出按时间戳最旧优先移入回收站（仅清理 backup_* 目录）。

.PARAMETER From
  Verify/Drill 指定备份目录路径；缺省取最新一份。

.PARAMETER NoLog
  关闭 logs\backup.log 文件写入（仅控制台输出）。

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File D:\jshz-PMO\scripts\backup.ps1
  powershell -NoProfile -ExecutionPolicy Bypass -File D:\jshz-PMO\scripts\backup.ps1 -Mode Verify
  powershell -NoProfile -ExecutionPolicy Bypass -File D:\jshz-PMO\scripts\backup.ps1 -Mode Drill
#>
[CmdletBinding()]
param(
    [ValidateSet('Backup', 'Verify', 'Drill')]
    [string]$Mode = 'Backup',
    [string]$BackupDir = 'D:\jshz-PMO\backup',
    [int]$Keep = 5,
    [string]$From = '',
    [switch]$NoLog
)

$ErrorActionPreference = 'Stop'

# ==================== 常量区 ====================
$ProjectRoot   = 'D:\jshz-PMO'
$LogFile       = Join-Path $ProjectRoot 'logs\backup.log'
$DrillDoc      = Join-Path $ProjectRoot 'docs\guides\2026-10-26-备份与恢复-演练文档.md'
$StartBat      = Join-Path $ProjectRoot 'start-backend.bat'

$DbHost   = '127.0.0.1'
$DbPort   = '3306'
$DbUser   = 'root'
$DbName   = 'expert_system'
# 测试库固定名（Drill 仅允许操作此库，防误配触达生产库）
$TestDb   = 'expert_system_restore_test'

$PhotoDir     = Join-Path $ProjectRoot '照片库'
$PhotoTmpDir  = Join-Path $ProjectRoot '照片库_tmp'
$DocDir       = Join-Path $ProjectRoot '知识文档库'

# ==================== 工具函数 ====================
function Log([string]$msg) {
    $line = '[{0}] {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $msg
    Write-Host $line
    if (-not $NoLog) {
        $logDir = Split-Path $LogFile -Parent
        if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
        Add-Content -Path $LogFile -Value $line -Encoding UTF8
    }
}

function Fail([string]$msg) {
    Log "[失败] $msg"
    Log "==== $Mode 模式中止（不产生半成品产物） ===="
    exit 1
}

# 探测 MySQL 客户端（mysqldump 与 mysql 同目录）
function Find-MySqlTools {
    $candidates = @()
    $candidates += (Get-ChildItem 'C:\Program Files\MySQL\*\bin\mysqldump.exe' -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -ExpandProperty FullName)
    $cmd = Get-Command mysqldump.exe -ErrorAction SilentlyContinue
    if ($cmd) { $candidates += $cmd.Source }
    if (-not $candidates.Count) { return $null }
    $dump = $candidates[0]
    $mysqlExe = Join-Path (Split-Path $dump -Parent) 'mysql.exe'
    if (-not (Test-Path $mysqlExe)) { return $null }
    return @{ Dump = $dump; MySql = $mysqlExe }
}

# 凭据：环境变量优先，其次本地 start-backend.bat 运行时解析（不落盘）
function Resolve-DbPassword {
    if ($env:MYSQL_PASSWORD) { return $env:MYSQL_PASSWORD }
    if (Test-Path $StartBat) {
        $bat = Get-Content $StartBat -Raw
        $m = [regex]::Match($bat, '(?m)^\s*set\s+MYSQL_PASSWORD=(.+?)\s*$')
        if ($m.Success) {
            Log 'MYSQL_PASSWORD 环境变量未设置，已从本地 start-backend.bat 运行时解析（不落盘）'
            return $m.Groups[1].Value.Trim()
        }
    }
    return $null
}

# 最新一份备份目录（或 -From 指定）
function Resolve-BackupSource {
    if ($From) {
        if (-not (Test-Path $From)) { Fail "指定的备份目录不存在: $From" }
        return (Get-Item $From).FullName
    }
    $latest = Get-ChildItem -Path $BackupDir -Directory -Filter 'backup_*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if (-not $latest) { Fail "备份目录下未找到任何 backup_* 备份: $BackupDir" }
    return $latest.FullName
}

# SHA256 校验文件生成（与 sha256sum 同格式：<hash>  <文件名>）
function Write-Sha256([string]$file) {
    $hash = (Get-FileHash -Path $file -Algorithm SHA256).Hash.ToLower()
    "$hash  $([IO.Path]::GetFileName($file))" | Out-File -FilePath "$file.sha256" -Encoding ascii -NoNewline
    Add-Content -Path "$file.sha256" -Value ''
    return $hash
}

# 校验单个产物：SHA256 复核；zip 额外做全量读取（逐 entry 读流触发 CRC 校验）
function Test-Artifact([string]$file) {
    if (-not (Test-Path $file)) { return @{ Ok = $false; Detail = '文件不存在' } }
    $shaFile = "$file.sha256"
    if (-not (Test-Path $shaFile)) { return @{ Ok = $false; Detail = '缺少 .sha256 校验文件' } }
    $expected = (Get-Content $shaFile -First 1) -replace '\s+$',''
    $expected = ($expected -split '\s+')[0]
    $actual = (Get-FileHash -Path $file -Algorithm SHA256).Hash.ToLower()
    if ($actual -ne $expected) { return @{ Ok = $false; Detail = "SHA256 不一致 expected=$expected actual=$actual" } }
    if ($file -like '*.zip') {
        try {
            Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
            $zip = [IO.Compression.ZipFile]::OpenRead($file)
            try {
                $sink = New-Object IO.MemoryStream
                foreach ($entry in $zip.Entries) {
                    $s = $entry.Open()
                    try { $s.CopyTo($sink) } finally { $s.Dispose() }
                }
            } finally { $zip.Dispose() }
        } catch {
            return @{ Ok = $false; Detail = "zip 读取校验失败: $($_.Exception.Message)" }
        }
    }
    return @{ Ok = $true; Detail = 'SHA256/内容校验通过' }
}

# 对某备份目录全量校验；任一失败返回 $false
function Test-BackupSet([string]$dir) {
    $files = Get-ChildItem -Path $dir -File | Where-Object { $_.Name -match '^(expert_system_.*\.sql|照片库\.zip|照片库_tmp\.zip|知识文档库\.zip)$' }
    if (-not $files.Count) { Log '[校验] 未找到任何备份产物'; return $false }
    $allOk = $true
    foreach ($f in $files) {
        $r = Test-Artifact $f.FullName
        if ($r.Ok) { Log ("[校验][OK] {0}: {1}" -f $f.Name, $r.Detail) }
        else { Log ("[校验][FAIL] {0}: {1}" -f $f.Name, $r.Detail); $allOk = $false }
    }
    return $allOk
}

# 运行 mysql（返回 stdout 行数组；出错时抛出）
# PS5.1 坑：Start-Process -ArgumentList 数组元素含空格不会自动加引号，须手工拼接引号
function Join-Args([string[]]$SqlArgs) {
    return (($SqlArgs | ForEach-Object { if ($_ -match '\s') { '"{0}"' -f $_ } else { $_ } }) -join ' ')
}
function Invoke-MySqlQuery([string[]]$SqlArgs) {
    $outFile = [IO.Path]::GetTempFileName()
    $p = Start-Process -FilePath $script:Tools.MySql -ArgumentList (Join-Args $SqlArgs) -RedirectStandardOutput $outFile -NoNewWindow -Wait -PassThru
    if ($p.ExitCode -ne 0) {
        Remove-Item $outFile -Force -ErrorAction SilentlyContinue
        throw "mysql 调用失败(exit=$($p.ExitCode)): $($SqlArgs -join ' ')"
    }
    $lines = @(Get-Content $outFile)
    Remove-Item $outFile -Force -ErrorAction SilentlyContinue
    return $lines
}

# ==================== 主流程 ====================
Log "==== jshz-PMO 备份脚本启动（Mode=$Mode Keep=$Keep） ===="

# --- 公共预检：客户端工具 + 凭据 + 连通性 ---
$script:Tools = Find-MySqlTools
if (-not $script:Tools) { Fail '未找到 mysqldump/mysql 客户端（已探测 C:\Program Files\MySQL\*\bin 与 PATH）' }
Log "客户端: $($script:Tools.Dump)"

$dbPwd = Resolve-DbPassword
if (-not $dbPwd) { Fail '未获得数据库密码：请设置 MYSQL_PASSWORD 环境变量，或确保 start-backend.bat 存在' }
$env:MYSQL_PWD = $dbPwd

try {
    Invoke-MySqlQuery @('-h', $DbHost, '-P', $DbPort, '-u', $DbUser, '--default-character-set=utf8mb4', '-N', '-e', 'SELECT 1') | Out-Null
} catch {
    Fail "MySQL 连接预检失败: $($_.Exception.Message)"
}
Log 'MySQL 连接预检通过'

$global:RESULT = 0  # 成功默认 0；Fail 直接 exit 1，Drill 有差异时显式置 1
try {
    switch ($Mode) {
        'Backup' {
            if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null }
            $stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
            $dest = Join-Path $BackupDir "backup_$stamp"
            New-Item -ItemType Directory -Path $dest -Force | Out-Null
            $sw = [Diagnostics.Stopwatch]::StartNew()
            Log "备份目录: $dest"

            # 1) 数据库全量导出（-r 原生写文件，避免 PowerShell 管道重编码；不带 --databases，导入时可指定目标库）
            $sqlFile = Join-Path $dest "expert_system_$stamp.sql"
            Log '正在导出数据库 expert_system ...'
            $p = Start-Process -FilePath $script:Tools.Dump `
                -ArgumentList (Join-Args @('-h', $DbHost, '-P', $DbPort, '-u', $DbUser, `
                    '--single-transaction', '--routines', '--triggers', '--hex-blob', '--set-gtid-purged=OFF', `
                    '--default-character-set=utf8mb4', '-r', $sqlFile, $DbName)) `
                -NoNewWindow -Wait -PassThru
            if ($p.ExitCode -ne 0 -or -not (Test-Path $sqlFile) -or ((Get-Item $sqlFile).Length -lt 1KB)) {
                Fail "mysqldump 失败(exit=$($p.ExitCode)) 或导出文件异常"
            }
            $sqlSize = [math]::Round((Get-Item $sqlFile).Length / 1KB, 1)
            Log "数据库导出完成: expert_system_$stamp.sql ($sqlSize KB)"

            # 2) 目录归档（照片库_tmp 允许缺失/为空，跳过不致命；其余致命）
            $artifacts = @(@{ Path = $sqlFile; Name = "expert_system_$stamp.sql"; Fatal = $true })
            $dirTargets = @(
                @{ Dir = $PhotoDir;    Zip = '照片库.zip';     Fatal = $true },
                @{ Dir = $PhotoTmpDir; Zip = '照片库_tmp.zip'; Fatal = $false },
                @{ Dir = $DocDir;      Zip = '知识文档库.zip'; Fatal = $true }
            )
            foreach ($t in $dirTargets) {
                $zipPath = Join-Path $dest $t.Zip
                if (-not (Test-Path $t.Dir)) {
                    if ($t.Fatal) { Fail "目录不存在: $($t.Dir)" } else { Log "[跳过] $($t.Dir) 不存在（tmp 允许缺失）"; continue }
                }
                $hasContent = @(Get-ChildItem -Path $t.Dir -Recurse -File -ErrorAction SilentlyContinue).Count -gt 0
                if (-not $hasContent) {
                    if ($t.Fatal) { Fail "目录为空: $($t.Dir)" } else { Log "[跳过] $($t.Dir) 为空目录（tmp 允许为空）"; continue }
                }
                Log "正在压缩 $($t.Dir) -> $($t.Zip) ..."
                try {
                    Compress-Archive -Path (Join-Path $t.Dir '*') -DestinationPath $zipPath -CompressionLevel Optimal -ErrorAction Stop
                    $artifacts += @{ Path = $zipPath; Name = $t.Zip; Fatal = $t.Fatal }
                    $kb = [math]::Round((Get-Item $zipPath).Length / 1KB, 1)
                    Log "压缩完成: $($t.Zip) ($kb KB)"
                } catch {
                    if ($t.Fatal) { Fail "压缩失败 $($t.Dir): $($_.Exception.Message)" }
                    else { Log "[跳过] 压缩失败（tmp 非致命）: $($_.Exception.Message)" }
                }
            }

            # 3) SHA256 + manifest
            $manifest = @()
            $manifest += "backup_time=$stamp"
            $manifest += "script_mode=Backup"
            foreach ($a in $artifacts) {
                $hash = Write-Sha256 $a.Path
                $kb = [math]::Round((Get-Item $a.Path).Length / 1KB, 1)
                $manifest += ("file={0}`tsize_kb={1}`tsha256={2}" -f $a.Name, $kb, $hash)
            }
            $sw.Stop()
            $manifest += ("duration_sec={0}" -f [math]::Round($sw.Elapsed.TotalSeconds, 1))
            $manifest | Out-File -FilePath (Join-Path $dest 'manifest.txt') -Encoding UTF8
            Log ("manifest.txt 已写入；本份备份耗时 {0} 秒，产物 {1} 个" -f [math]::Round($sw.Elapsed.TotalSeconds,1), $artifacts.Count)

            # 4) 保留策略：仅清理 backup_* 时间戳目录，超出 -Keep 的最旧目录移入回收站
            $all = @(Get-ChildItem -Path $BackupDir -Directory -Filter 'backup_*' | Sort-Object Name -Descending)
            if ($all.Count -gt $Keep) {
                Add-Type -AssemblyName Microsoft.VisualBasic
                $toRemove = $all | Select-Object -Skip $Keep
                foreach ($d in $toRemove) {
                    try {
                        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($d.FullName, 'OnlyErrorDialogs', 'SendToRecycleBin')
                        Log "保留策略: 已移入回收站 $($d.Name)"
                    } catch {
                        Log "保留策略: 移除失败（跳过，不影响备份结果） $($d.FullName): $($_.Exception.Message)"
                    }
                }
            }
            Log "==== Backup 完成（$dest） ===="
        }

        'Verify' {
            $src = Resolve-BackupSource
            Log "校验目标: $src"
            $ok = Test-BackupSet $src
            if ($ok) { Log '==== Verify 通过：全部产物 SHA256 与内容校验一致 ====' }
            else { Fail 'Verify 未通过：存在校验失败的产物，请勿用于恢复' }
        }

        'Drill' {
            if ($TestDb -ne 'expert_system_restore_test') { Fail '测试库名异常，拒绝执行（安全护栏）' }
            $src = Resolve-BackupSource
            Log "演练目标备份: $src"
            $sw = [Diagnostics.Stopwatch]::StartNew()

            # 1) 先校验，失败绝不导入
            if (-not (Test-BackupSet $src)) { Fail '备份校验未通过，演练中止（未触碰任何数据库）' }
            Log '备份校验通过，开始重建测试库'

            # 2) 重建测试库（仅 expert_system_restore_test；DROP/CREATE 限定库名字面量）
            Invoke-MySqlQuery @('-h', $DbHost, '-P', $DbPort, '-u', $DbUser, '--default-character-set=utf8mb4', '-e',
                "DROP DATABASE IF EXISTS ``$TestDb``; CREATE DATABASE ``$TestDb`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;") | Out-Null
            Log "测试库已重建: $TestDb"

            # 3) 导入（字节流重定向，绕过 PowerShell 管道编码）
            $sqlFile = Get-ChildItem -Path $src -File -Filter 'expert_system_*.sql' | Select-Object -First 1
            if (-not $sqlFile) { Fail '备份目录中未找到 SQL 文件' }
            Log "正在导入 $($sqlFile.Name) -> $TestDb ..."
            $p = Start-Process -FilePath $script:Tools.MySql `
                -ArgumentList (Join-Args @('-h', $DbHost, '-P', $DbPort, '-u', $DbUser, '--default-character-set=utf8mb4', $TestDb)) `
                -RedirectStandardInput $sqlFile.FullName -NoNewWindow -Wait -PassThru
            if ($p.ExitCode -ne 0) { Fail "SQL 导入失败(exit=$($p.ExitCode))，测试库保留供排查（生产库未受影响）" }
            Log 'SQL 导入完成'

            # 4) 对比：表集合 + 每表行数（COUNT(*) 精确值）
            $prodTables = @(Invoke-MySqlQuery @('-h', $DbHost, '-P', $DbPort, '-u', $DbUser, '--default-character-set=utf8mb4', '-N', '-e',
                "SELECT table_name FROM information_schema.tables WHERE table_schema='$DbName' AND table_type='BASE TABLE' ORDER BY table_name;"))
            $testTables = @(Invoke-MySqlQuery @('-h', $DbHost, '-P', $DbPort, '-u', $DbUser, '--default-character-set=utf8mb4', '-N', '-e',
                "SELECT table_name FROM information_schema.tables WHERE table_schema='$TestDb' AND table_type='BASE TABLE' ORDER BY table_name;"))
            $diffs = @()
            $onlyProd = Compare-Object $prodTables $testTables | Where-Object { $_.SideIndicator -eq '<=' } | ForEach-Object { $_.InputObject }
            $onlyTest = Compare-Object $prodTables $testTables | Where-Object { $_.SideIndicator -eq '=>' } | ForEach-Object { $_.InputObject }
            foreach ($t in $onlyProd) { $diffs += "表 $t 仅存在于生产库" }
            foreach ($t in $onlyTest) { $diffs += "表 $t 仅存在于测试库" }
            # 单条 UNION ALL 查询一次性取回全部表行数，避免逐表启动 mysql 进程和累计误差。
            $prodSelects = @()
            foreach ($t in $prodTables) {
                $literal = $t.Replace("'", "''")
                $ident = '`' + $t.Replace('`', '``') + '`'
                $prodSelects += "SELECT '$literal' AS table_name, COUNT(*) AS row_count FROM ``$DbName``.$ident"
            }
            if (-not $prodSelects.Count) { Fail '生产库未读取到任何基础表' }
            $prodSql = ($prodSelects -join ' UNION ALL ') + ' ORDER BY table_name;'
            $prodCountLines = @(Invoke-MySqlQuery @('-h', $DbHost, '-P', $DbPort, '-u', $DbUser, '--default-character-set=utf8mb4', '-N', '-B', '-e', $prodSql))
            $prodCounts = @{}
            foreach ($line in $prodCountLines) {
                $p = $line -split "`t"
                if ($p.Count -ge 2) { $prodCounts[$p[0]] = [long]$p[1] }
            }

            $testSelects = @()
            foreach ($t in $testTables) {
                $literal = $t.Replace("'", "''")
                $ident = '`' + $t.Replace('`', '``') + '`'
                $testSelects += "SELECT '$literal' AS table_name, COUNT(*) AS row_count FROM ``$TestDb``.$ident"
            }
            if (-not $testSelects.Count) { Fail '测试库未读取到任何基础表' }
            $testSql = ($testSelects -join ' UNION ALL ') + ' ORDER BY table_name;'
            $testCountLines = @(Invoke-MySqlQuery @('-h', $DbHost, '-P', $DbPort, '-u', $DbUser, '--default-character-set=utf8mb4', '-N', '-B', '-e', $testSql))
            $testCounts = @{}
            foreach ($line in $testCountLines) {
                $p = $line -split "`t"
                if ($p.Count -ge 2) { $testCounts[$p[0]] = [long]$p[1] }
            }

            if ($prodCounts.Count -ne $prodTables.Count) { $diffs += "生产库行数结果不完整: 表=$($prodTables.Count) 结果=$($prodCounts.Count)" }
            if ($testCounts.Count -ne $testTables.Count) { $diffs += "测试库行数结果不完整: 表=$($testTables.Count) 结果=$($testCounts.Count)" }
            $totalRowsProd = [long](($prodCounts.Values | Measure-Object -Sum).Sum)
            $totalRowsTest = [long](($testCounts.Values | Measure-Object -Sum).Sum)
            foreach ($t in $prodTables) {
                if ($onlyTest -contains $t) { continue }
                $cp = if ($prodCounts.ContainsKey($t)) { [long]$prodCounts[$t] } else { 0 }
                $ct = if ($testCounts.ContainsKey($t)) { [long]$testCounts[$t] } else { 0 }
                if ($cp -ne $ct) { $diffs += "表 $t 行数不一致: 生产=$cp 测试=$ct" }
            }
            $sw.Stop()

            # 5) 结论 + 自动落档 docs\guides\2026-10-26-备份与恢复-演练文档.md
            $pass = ($diffs.Count -eq 0)
            $verdict = '失败'
            if ($pass) { $verdict = '通过' }
            Log ("演练结论: {0}（表 生产={1} 测试={2}；行数 生产={3} 测试={4}；耗时 {5} 秒）" -f $verdict, $prodTables.Count, $testTables.Count, $totalRowsProd, $totalRowsTest, [math]::Round($sw.Elapsed.TotalSeconds,1))
            foreach ($d in $diffs) { Log "  [差异] $d" }

            $docDir = Split-Path $DrillDoc -Parent
            if (-not (Test-Path $docDir)) { New-Item -ItemType Directory -Path $docDir -Force | Out-Null }
            $record = @()
            $record += ''
            $record += ('### {0} 自动演练记录（{1}）' -f $(if ($pass) {'✅ 通过'} else {'❌ 失败'}), (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
            $record += ('- 备份来源: `{0}`' -f $src)
            $record += ('- 对比结果: 生产 {0} 表 / {1} 行 vs 测试 {2} 表 / {3} 行，耗时 {4} 秒' -f $prodTables.Count, $totalRowsProd, $testTables.Count, $totalRowsTest, [math]::Round($sw.Elapsed.TotalSeconds,1))
            if ($diffs.Count) { foreach ($d in $diffs) { $record += ('- 差异: {0}' -f $d) } }
            $record += ('- 说明: 演练仅操作测试库 {0}；生产库未受影响；测试库保留至下次演练自动重建。' -f $TestDb)
            Add-Content -Path $DrillDoc -Value ($record -join "`r`n") -Encoding UTF8
            Log "演练结论已追加至 $DrillDoc"

            if ($pass) { Log '==== Drill 演练通过 ====' } else { $global:RESULT = 1; Log '==== Drill 演练失败（差异见上），请人工核查 ====' }
        }
    }
} catch {
    Fail "未预期异常: $($_.Exception.Message)"
}

exit $global:RESULT
