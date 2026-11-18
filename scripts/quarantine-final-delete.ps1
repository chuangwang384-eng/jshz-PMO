<#
.SYNOPSIS
  A14 隔离区（temp\.quarantine-20261113）2026-11-20 最终删除确认工具。

.DESCRIPTION
  依据 docs/handover/tasks/20261113_temp_cleanup_audit.md（2026-11-13 执行的 temp/ 清理）：
  A 类 2861 文件（271.6 MB）+ C 类 51 文件（30.2 MB）移入 7 天观察隔离区，观察期至 2026-11-20。

  已知历史偏差（本工具如实建模，不视为本次阻断）：
  - A 类已于 2026-11-16 被 docs/handover/tasks/20261116_redundancy_cleanup.md 第 4 节
    「产物清理：隔离区旧构建目录」提前删除（271.6 MB），依据 manifest.csv 记录为可重建产物。
    因此本工具运行时 A\ 目录可能不存在，仅按实际剩余内容处置。

  流程（默认报告模式，加 -Execute 才删除）：
    阶段 1  现状校验：统计隔离区 A\C 实际文件数/大小，与 2026-11-13 审计基线比对；
            发现清单之外的"孤儿"内容（不在 manifest 中的顶层项）即阻断；
    阶段 2  观察期门禁：当前日期早于到期日（默认 2026-11-20）则中止删除，提示剩余天数（退出码 3）；
    阶段 3  引用检查：对将要删除的内容，全仓检索功能性引用（scripts/backend/frontend/database/根目录），
            命中即阻断并输出明细；文档类命中（docs/）仅提示；manifest 中 dst 越出隔离区即阻断；
            只读复核 supplier_image 导入仍生效（C 类素材删除前提，-NoDbCheck 可跳过）；
    阶段 4  删除：全部校验通过后逐项删除，失败即停（fail-fast）并如实报告已删/未删；
            删净后移除隔离区目录并复核，输出删除结果报告；
    阶段 5  文档同步：删除成功后自动更新 docs/handover/README.md 对应条目状态并附删除报告摘要。

  幂等性：隔离区目录已不存在时直接报告"已删除"并以退出码 0 结束；README 状态替换具有防重入判断。
  可追溯性：全程输出同步写入 logs\quarantine-final-delete-<时间戳>.log；
            删除前生成待删清单 CSV 到 logs\quarantine-20261113-purge-list-<时间戳>.csv。

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\quarantine-final-delete.ps1            # 报告模式（不删除）
  powershell -ExecutionPolicy Bypass -File scripts\quarantine-final-delete.ps1 -Execute   # 校验通过后执行删除

.NOTES
  退出码：0=成功/幂等跳过；1=校验异常阻断；2=前置缺失（隔离区结构异常/DB 复核失败）；3=观察期未到。
#>
[CmdletBinding()]
param(
    [switch]$Execute,
    [string]$DueDate = '2026-11-20',
    [switch]$NoDbCheck
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$quarantine = Join-Path $root 'temp\.quarantine-20261113'
$logsDir = Join-Path $root 'logs'
$readme = Join-Path $root 'docs\handover\README.md'

# 2026-11-13 审计基线（docs/handover/tasks/20261113_temp_cleanup_audit.md §2）
$baseline = @{
    AFiles = 2861
    ABytesMB = 271.6
    CFiles = 51
    CBytesMB = 30.2
}
# A 类提前处置的文档证据（20261116_redundancy_cleanup.md）
$aEarlyDeleteDoc = 'docs/handover/tasks/20261116_redundancy_cleanup.md'

if (-not (Test-Path -LiteralPath $logsDir)) { New-Item -ItemType Directory -Path $logsDir | Out-Null }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$logPath = Join-Path $logsDir "quarantine-final-delete-$stamp.log"
$purgeListPath = Join-Path $logsDir "quarantine-20261113-purge-list-$stamp.csv"
$inventoryPath = Join-Path $logsDir "quarantine-20261113-inventory-$stamp.csv"

function Log([string]$msg, [string]$color = 'Gray') {
    Write-Host $msg -ForegroundColor $color
    Add-Content -LiteralPath $logPath -Value $msg -Encoding UTF8
}
function LogRule { Log ('-' * 72) }

# ---------- [0] 幂等：隔离区已不存在 ----------
if (-not (Test-Path -LiteralPath $quarantine)) {
    Log "[幂等] 隔离区 $quarantine 已不存在（此前已删除），无需操作。" 'Green'
    Log "退出码 0（幂等跳过）。"
    exit 0
}

Log "A14 隔离区最终删除确认工具  时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Log "隔离区：$quarantine"
Log "模式：$(if ($Execute) { 'EXECUTE（校验通过后删除）' } else { 'REPORT（仅报告，不删除）' })"
Log "到期日：$DueDate"
LogRule

$blockers = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]

# ---------- [1] 现状校验 ----------
Log '[1/5] 现状校验：统计隔离区实际内容并与 2026-11-13 审计基线比对'

$inventory = New-Object System.Collections.Generic.List[object]
foreach ($cls in 'A', 'C') {
    $dir = Join-Path $quarantine $cls
    $files = @()
    if (Test-Path -LiteralPath $dir) {
        $files = Get-ChildItem -LiteralPath $dir -Recurse -File -Force
    }
    foreach ($f in $files) {
        $inventory.Add([pscustomobject]@{ class = $cls; path = $f.FullName; bytes = $f.Length; mtime = $f.LastWriteTime })
    }
    $sum = ($files | Measure-Object Length -Sum).Sum
    $mb = if ($sum) { [math]::Round($sum / 1MB, 1) } else { 0 }
    Log ("  {0} 类：实际 {1} 文件 / {2} MB（审计基线 {3} 文件 / {4} MB）" -f $cls, $files.Count, $mb, $baseline."$($cls)Files", $baseline."$($cls)BytesMB")

    if ($cls -eq 'A') {
        if (-not (Test-Path -LiteralPath $dir)) {
            $warnings.Add("A 类目录不存在（0 文件）——与审计基线 2861 文件/271.6 MB 不符；已有文档证据：$aEarlyDeleteDoc 记录 A 类于 2026-11-16 提前处置。本次按实际剩余内容处置。")
        } elseif ($files.Count -ne $baseline.AFiles) {
            $blockers.Add("A 类实际 $($files.Count) 文件与基线 $($baseline.AFiles) 不一致且目录仍存在：隔离区在观察期内被意外改动，须先人工核实。")
        }
    } else {
        if (-not (Test-Path -LiteralPath $dir)) {
            $blockers.Add('C 类目录不存在：与审计基线（51 文件在隔离区）不符，隔离区被意外改动，须先人工核实。')
        } elseif ($files.Count -ne $baseline.CFiles) {
            $blockers.Add("C 类实际 $($files.Count) 文件与审计基线 $($baseline.CFiles) 不一致：隔离区在观察期内被增删，须先人工核实。")
        } elseif ($mb -ne $baseline.CBytesMB) {
            $blockers.Add("C 类实际 $mb MB 与审计基线 $($baseline.CBytesMB) MB 不一致：内容被改动，须先人工核实。")
        }
    }
}

# manifest.csv 本体也要一并删除，纳入统计
$manifestPath = Join-Path $quarantine 'manifest.csv'
$manifestBytes = 0
if (Test-Path -LiteralPath $manifestPath) {
    $manifestBytes = (Get-Item -LiteralPath $manifestPath).Length
    $inventory.Add([pscustomobject]@{ class = 'META'; path = $manifestPath; bytes = $manifestBytes; mtime = (Get-Item -LiteralPath $manifestPath).LastWriteTime })
}
Log ("  manifest.csv：{0} 字节（随隔离区一并删除）" -f $manifestBytes)

# 孤儿检查：隔离区顶层不得存在 manifest 清单之外的未知内容
$manifestEntries = @()
if (Test-Path -LiteralPath $manifestPath) {
    $manifestEntries = Import-Csv -LiteralPath $manifestPath -Delimiter '|' -Encoding UTF8
}
$knownTops = @('A', 'C', 'manifest.csv') | ForEach-Object { $_.ToLowerInvariant() }
$actualTops = Get-ChildItem -LiteralPath $quarantine -Force | ForEach-Object { $_.Name.ToLowerInvariant() }
$orphans = $actualTops | Where-Object { $knownTops -notcontains $_ }
if ($orphans) {
    foreach ($o in $orphans) {
        $blockers.Add("隔离区存在清单（manifest.csv）之外的未知顶层项『$o』：非 A14 清单内容，禁止随隔离区删除，须人工确认后移出或单独登记。")
    }
}

# manifest 中 A/C 条目的目标路径必须仍在隔离区内（防清单指向外部导致误删）。
# 注：action=delete 的审计条目（如 login.json，dst=(purged)）与 B 类归档条目不在此校验范围。
$badDst = $manifestEntries | Where-Object {
    $_.class -in 'A', 'C' -and $_.action -eq 'move' -and $_.dst -and
    (-not $_.dst.ToLowerInvariant().StartsWith($quarantine.ToLowerInvariant() + '\'))
} | Select-Object -ExpandProperty dst
if ($badDst) {
    $blockers.Add("manifest 中 $($badDst.Count) 条 A/C 记录的目标路径越出隔离区（防误删护栏）：$($badDst -join '; ')")
}

if ($warnings.Count) { foreach ($w in $warnings) { Log "  [WARN] $w" 'Yellow' } }
if ($blockers.Count) {
    Log "  [阻断] 现状校验发现 $($blockers.Count) 项异常：" 'Red'
    foreach ($b in $blockers) { Log "    - $b" 'Red' }
    Log '未执行任何删除（异常时不产生部分删除）。'
    exit 1
}
Log '  现状校验通过。'
LogRule

# ---------- [2] 观察期门禁 ----------
Log '[2/5] 观察期门禁'
$due = [datetime]::ParseExact($DueDate, 'yyyy-MM-dd', $null)
$today = Get-Date -Date (Get-Date -Format 'yyyy-MM-dd')
if ($today -lt $due) {
    $remain = ($due - $today).Days
    Log "  当前日期 $(Get-Date -Format 'yyyy-MM-dd') 早于到期日 $DueDate：7 天观察期未满，剩余 $remain 天。" 'Yellow'
    Log "  按规程中止删除。请于 $DueDate（含当日）之后重新运行（报告模式预检，再加 -Execute 执行）。"
    Log "  本次已生成现状清单：$inventoryPath"
    $inventory | Export-Csv -LiteralPath $inventoryPath -NoTypeInformation -Encoding UTF8
    exit 3
}
Log "  观察期已到期（到期日 $DueDate）。"
LogRule

# ---------- [3] 引用检查 ----------
Log '[3/5] 引用检查（针对将要删除的内容）'

# 3.1 待删内容 = A 目录（若在）+ C 目录 + manifest.csv
$pendingTops = @()
foreach ($d in 'A', 'C') {
    if (Test-Path -LiteralPath (Join-Path $quarantine $d)) { $pendingTops += (Get-Item (Join-Path $quarantine $d)) }
}
$pendingTops += (Get-Item -LiteralPath $manifestPath)

# 3.2 检索词：隔离区目录名 + 剩余顶层项文件/目录名（排除 A/C 单字符目录名，避免全库误命中）
$terms = @('quarantine-20261113')
foreach ($t in $pendingTops) {
    if ($t.Name -notin 'manifest.csv', 'A', 'C') { $terms += $t.Name }
}
$terms = $terms | Where-Object { $_.Length -ge 3 } | Select-Object -Unique

# 3.3 搜索范围：功能性代码/脚本/根目录文件 + docs（文档命中仅提示）
$searchRoots = @(
    (Join-Path $root 'scripts'),
    (Join-Path $root 'backend\src'),
    (Join-Path $root 'frontend\src'),
    (Join-Path $root 'database'),
    (Join-Path $root 'docs')
)
$rootFiles = Get-ChildItem -LiteralPath $root -File -Force | Select-Object -ExpandProperty FullName

$codeHits = New-Object System.Collections.Generic.List[string]
$docHits = New-Object System.Collections.Generic.List[string]
foreach ($term in $terms) {
    foreach ($dir in $searchRoots) {
        if (-not (Test-Path -LiteralPath $dir)) { continue }
        $hits = Get-ChildItem -LiteralPath $dir -Recurse -File -Force -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '\\\.git\\|\\node_modules\\|\\dist\\|\\dist_old' -and $_.FullName -ne $PSCommandPath } |
            Select-String -Pattern ([regex]::Escape($term)) -SimpleMatch -List
        foreach ($h in $hits) {
            $rel = $h.Path.Substring($root.Length + 1)
            $line = $h.Line.Trim()
            if ($line.Length -gt 120) { $line = $line.Substring(0, 120) + '…' }
            if ($rel -like 'docs\*') { $docHits.Add("[${rel}:$($h.LineNumber)] $term → $line") }
            else { $codeHits.Add("[${rel}:$($h.LineNumber)] $term → $line") }
        }
    }
    foreach ($f in $rootFiles) {
        $hit = Select-String -LiteralPath $f -Pattern ([regex]::Escape($term)) -SimpleMatch -List
        if ($hit) {
            $line = $hit.Line.Trim(); if ($line.Length -gt 120) { $line = $line.Substring(0, 120) + '…' }
            $codeHits.Add("[$(Split-Path -Leaf $f):$($hit.LineNumber)] $term → $line")
        }
    }
}

if ($docHits.Count) {
    Log "  文档类命中 $($docHits.Count) 处（docs/ 为历史记录或 docs/data 已留档副本，不构成功能性引用）：" 'DarkCyan'
    foreach ($h in $docHits) { Log "    $h" 'DarkCyan' }
}
if ($codeHits.Count) {
    foreach ($h in $codeHits) {
        $blockers.Add("功能性引用命中，待删内容仍被引用：$h")
    }
}

# 3.4 DB 只读复核：C 类素材（供应商图片导入）删除前提 = 导入数据仍在生产库
if ($NoDbCheck) {
    Log '  [WARN] 按参数跳过 DB 复核（-NoDbCheck）。' 'Yellow'
} else {
    $mysql = Get-ChildItem 'C:\Program Files\MySQL\*\bin\mysql.exe' -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    $credPs = Join-Path $PSScriptRoot 'common-credentials.ps1'
    if (-not $mysql -or -not (Test-Path -LiteralPath $credPs)) {
        $warnings.Add('未找到 mysql.exe 或 common-credentials.ps1，跳过 DB 复核（建议人工执行 SELECT COUNT(*) FROM supplier_image 确认仍为 121 行）。')
        Log '  [WARN] 未找到 mysql 客户端/凭据脚本，跳过 DB 复核。' 'Yellow'
    } else {
        . $credPs
        $dbPwd = Resolve-DbPassword
        if (-not $dbPwd) {
            $blockers.Add('无法解析数据库密码（Resolve-DbPassword 为空），DB 复核未执行：不满足 C 类删除前提校验。')
        } else {
            $env:MYSQL_PWD = $dbPwd
            $res = (& $mysql --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --skip-column-names expert_system -e "SELECT CONCAT('rows=',(SELECT COUNT(*) FROM supplier_image),' suppliers=',(SELECT COUNT(DISTINCT supplier_id) FROM supplier_image));") -join ''
            Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
            Log "  supplier_image 复核：$res（预期 rows=121 suppliers=120）" 'DarkCyan'
            if ($res -ne 'rows=121 suppliers=120') {
                $blockers.Add("supplier_image 实测 [$res] 与审计记录（121 行/120 家）不一致：供应商导入状态有变化，C 类素材处置前提不成立，须人工核实。")
            }
        }
    }
}

if ($warnings.Count) { foreach ($w in $warnings) { Log "  [WARN] $w" 'Yellow' } }
if ($blockers.Count) {
    Log "  [阻断] 引用检查发现 $($blockers.Count) 项异常：" 'Red'
    foreach ($b in $blockers) { Log "    - $b" 'Red' }
    Log '未执行任何删除（异常时不产生部分删除）。'
    Log "现状清单已生成：$inventoryPath"
    $inventory | Export-Csv -LiteralPath $inventoryPath -NoTypeInformation -Encoding UTF8
    exit 1
}
Log '  引用检查通过：待删内容无功能性引用，全部前置校验通过。'
LogRule

# ---------- [4] 删除 ----------
$purgeFiles = @()
foreach ($t in $pendingTops) {
    if ($t.PSIsContainer) {
        $purgeFiles += Get-ChildItem -LiteralPath $t.FullName -Recurse -File -Force | ForEach-Object { $_.FullName }
    } else {
        $purgeFiles += $t.FullName
    }
}
$purgeFiles = $purgeFiles | Select-Object -Unique
$purgeBytes = ($purgeFiles | ForEach-Object { (Get-Item -LiteralPath $_ -Force).Length } | Measure-Object -Sum).Sum

if (-not $Execute) {
    Log '[4/5] 报告模式：全部校验已通过，未执行删除。'
    Log ("  待删内容：{0} 文件 / {1:N1} MB（含 manifest.csv）" -f $purgeFiles.Count, ($purgeBytes / 1MB))
    Log "  执行命令：powershell -ExecutionPolicy Bypass -File scripts\quarantine-final-delete.ps1 -Execute"
    Log "  待删清单已生成：$purgeListPath"
    Log "  现状清单已生成：$inventoryPath"
    ($purgeFiles | ForEach-Object { [pscustomobject]@{ path = $_; bytes = (Get-Item -LiteralPath $_ -Force).Length } }) |
        Export-Csv -LiteralPath $purgeListPath -NoTypeInformation -Encoding UTF8
    $inventory | Export-Csv -LiteralPath $inventoryPath -NoTypeInformation -Encoding UTF8
    exit 0
}

Log '[4/5] 执行删除（fail-fast：任一项失败即停止）'
$sw = [System.Diagnostics.Stopwatch]::StartNew()
($purgeFiles | ForEach-Object { [pscustomobject]@{ path = $_; bytes = (Get-Item -LiteralPath $_ -Force).Length } }) |
    Export-Csv -LiteralPath $purgeListPath -NoTypeInformation -Encoding UTF8

$deleted = 0; $deletedBytes = 0; $failed = $null
foreach ($p in $purgeFiles) {
    try {
        $b = (Get-Item -LiteralPath $p -Force).Length
        Remove-Item -LiteralPath $p -Force
        $deleted++; $deletedBytes += $b
    } catch {
        $failed = $p
        break
    }
}

if ($failed) {
    Log "  [失败] 删除中止于第 $($deleted + 1) 项：$failed" 'Red'
    Log "  已删除 $deleted 个文件 / $([math]::Round($deletedBytes / 1MB, 1)) MB；剩余 $(($purgeFiles.Count - $deleted)) 个文件未删除（保留在隔离区，可重跑本工具续删）。" 'Red'
    Log '未移除隔离区目录（内容未删净）。'
    exit 1
}

foreach ($t in $pendingTops | Where-Object { $_.PSIsContainer }) {
    Remove-Item -LiteralPath $t.FullName -Recurse -Force
}
Remove-Item -LiteralPath $quarantine -Recurse -Force

if (Test-Path -LiteralPath $quarantine) {
    Log '  [失败] 隔离区目录删除后仍存在（可能被外部进程占用），请人工核实。' 'Red'
    exit 1
}
$sw.Stop()
Log ("  删除完成：{0} 文件 / {1:N1} MB，耗时 {2:N1}s，目录已移除。" -f $deleted, ($deletedBytes / 1MB), $sw.Elapsed.TotalSeconds) 'Green'
LogRule

# ---------- [5] 文档同步：更新 docs/handover/README.md ----------
Log '[5/5] 文档同步：docs/handover/README.md'
$oldStatus = '已执行（待 2026-11-20 最终删除确认）'
$newStatus = '已执行（2026-11-20 最终删除完成）'
$summary = "；**最终删除报告（$(Get-Date -Format 'yyyy-MM-dd')）**：删 $deleted 文件 / $([math]::Round($deletedBytes / 1MB, 1)) MB（C 类 51 文件/30.2 MB + manifest.csv；A 类 2861 文件/271.6 MB 已于 2026-11-16 提前处置，见 $aEarlyDeleteDoc），隔离区目录已移除，报告见 logs/quarantine-final-delete-*.log"

if (-not (Test-Path -LiteralPath $readme)) {
    Log '  [WARN] 未找到 docs/handover/README.md，跳过状态更新（请人工同步）。' 'Yellow'
} else {
    $content = Get-Content -LiteralPath $readme -Raw -Encoding UTF8
    if ($content.Contains($newStatus)) {
        Log '  状态已是最终删除完成（幂等跳过）。'
    } elseif (-not $content.Contains($oldStatus)) {
        Log '  [WARN] README 中未找到目标状态文本，未自动更新（请人工同步，避免误改）。' 'Yellow'
    } else {
        $content = $content.Replace($oldStatus + '：', $newStatus + $summary + '：')
        if ($content -notmatch [regex]::Escape($newStatus)) {
            # 状态文本不以"："结尾的兜底替换（仅替换状态词本身，摘要由人工补充）
            $content = $content.Replace($oldStatus, $newStatus + $summary)
        }
        Set-Content -LiteralPath $readme -Value $content -Encoding UTF8 -NoNewline
        Log '  已更新：状态改为「已执行（2026-11-20 最终删除完成）」并附删除报告摘要。' 'Green'
    }
}

LogRule
Log "删除报告：文件数 $deleted，释放 $([math]::Round($deletedBytes / 1MB, 1)) MB，时间戳 $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Log "日志：$logPath"
Log "待删清单：$purgeListPath"
exit 0
