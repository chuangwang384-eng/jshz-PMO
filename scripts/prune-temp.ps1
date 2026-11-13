#requires -Version 5.1
<#
.SYNOPSIS
  temp\ 临时产物保留策略脚本（按类别保留最近 N 个）。

.DESCRIPTION
  temp\ 是临时工作区：隔离构建目录（verify-* / frontend-build-*）、历史临时脚本（apply-*.ps1 /
  patch-*.py / *.mjs）、旧 jar、构建输出日志等。无清理机制时实测已累积 273 项 / 571 MB，
  且每轮发布都会继续增加。

  保留规则（分类计数，各类保留最近 -Keep 个）：
  - 目录        build/verify 等临时产物
  - jar         旧可执行包
  - script      *.ps1 / *.psm1 / *.cmd / *.bat / *.py / *.mjs / *.sh
  - log         *.log / *.txt
  - other       其余文件（*.json / *.zip 等）

  双重保护（任一命中即不删）：
  1) 修改时间在 -MinAgeDays 天内（默认 2 天）→ 可能是进行中的工作，跳过；
  2) 文件名出现在仓库 docs\ 或 scripts\ 的文本里 → 视为被引用，跳过。

  当前脚本所在目录（temp 内自身）不会被删除；删除前校验路径必须位于 temp\ 下。

.PARAMETER TempDir
  temp 目录，默认 D:\jshz-PMO\temp

.PARAMETER Keep
  每个类别保留最近几个，默认 5

.PARAMETER MinAgeDays
  最近多少天内修改过的条目不删，默认 2

.PARAMETER DryRun
  只打印将删除的条目与可释放空间，不实际删除

.EXAMPLE
  .\scripts\prune-temp.ps1 -DryRun
  .\scripts\prune-temp.ps1 -Keep 5
#>
[CmdletBinding()]
param(
    [string]$TempDir = 'D:\jshz-PMO\temp',
    [int]$Keep = 5,
    [int]$MinAgeDays = 2,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path $PSScriptRoot -Parent
$script:DeletedCount = 0
$script:DeletedMB = 0
$script:SkippedRef = 0

function Write-Ok([string]$m) { Write-Host "[OK]   $m" -ForegroundColor Green }
function Write-Info([string]$m) { Write-Host "       $m" -ForegroundColor DarkGray }
function Write-Warn2([string]$m) { Write-Host "[WARN] $m" -ForegroundColor Yellow }
function Write-Bad([string]$m) { Write-Host "[FAIL] $m" -ForegroundColor Red }

function Get-ItemSizeMB($Item) {
    try {
        if ($Item.PSIsContainer) {
            $sum = (Get-ChildItem -LiteralPath $Item.FullName -Recurse -File -Force -ErrorAction SilentlyContinue |
                    Measure-Object -Property Length -Sum).Sum
        } else {
            $sum = $Item.Length
        }
        if ($null -eq $sum) { return 0 }
        return [math]::Round($sum / 1MB, 2)
    } catch { return 0 }
}

function Get-Category($Item) {
    if ($Item.PSIsContainer) { return '目录' }
    switch -Regex ($Item.Extension.ToLowerInvariant()) {
        '^\.jar$' { return 'jar' }
        '^\.(ps1|psm1|cmd|bat|py|mjs|sh)$' { return 'script' }
        '^\.(log|txt)$' { return 'log' }
        default { return 'other' }
    }
}

if (-not (Test-Path -LiteralPath $TempDir)) { Write-Bad "temp 目录不存在：$TempDir"; exit 1 }
if ($Keep -lt 1) { Write-Bad '-Keep 必须 >= 1'; exit 1 }

$tempFull = [IO.Path]::GetFullPath($TempDir).TrimEnd('\')
Write-Host ('temp 临时产物保留（每类 Keep=' + $Keep + '，MinAgeDays=' + $MinAgeDays + $(if ($DryRun) { '，DRYRUN 不删除' } else { '' }) + '）') -ForegroundColor Cyan
Write-Info "temp: $tempFull"

# 引用保护：把 scripts\ 与 docs\ 的文本拼成一个大串，按文件名做简单包含判断
$guardText = ''
foreach ($sub in @('scripts', 'docs')) {
    $p = Join-Path $RepoRoot $sub
    if (-not (Test-Path -LiteralPath $p)) { continue }
    $guardText += (Get-ChildItem -LiteralPath $p -Recurse -File -Force -Include *.ps1, *.md, *.cmd, *.json, *.yml, *.txt -ErrorAction SilentlyContinue |
                   ForEach-Object { try { [IO.File]::ReadAllText($_.FullName) } catch { '' } }) -join "`n"
    $guardText += "`n"
}
Write-Info ('引用索引长度: ' + $guardText.Length + ' 字符')

$cutoff = (Get-Date).AddDays(-1 * $MinAgeDays)
$items = @(Get-ChildItem -LiteralPath $tempFull -Force -ErrorAction SilentlyContinue |
           Where-Object { $_.FullName -ne $PSScriptRoot })

$groups = @{}
foreach ($it in $items) {
    $cat = Get-Category $it
    if (-not $groups.ContainsKey($cat)) { $groups[$cat] = New-Object System.Collections.Generic.List[object] }
    $groups[$cat].Add($it)
}

foreach ($cat in @('目录', 'jar', 'script', 'log', 'other')) {
    if (-not $groups.ContainsKey($cat)) { continue }
    $list = @($groups[$cat] | Sort-Object LastWriteTime -Descending)
    $keys = @($list | Select-Object -First $Keep | ForEach-Object { $_.Name })
    $rest = @($list | Select-Object -Skip $Keep)
    Write-Host ''
    Write-Info ("[$cat] 共 $($list.Count) 项，保留 $($keys.Count) 个：" + ($keys -join ', '))
    foreach ($it in $rest) {
        if ($it.LastWriteTime -gt $cutoff) { Write-Info ('  跳过（' + $MinAgeDays + ' 天内修改）: ' + $it.Name); continue }
        if ($guardText.Contains($it.Name)) { Write-Info ('  跳过（被 docs/scripts 引用）: ' + $it.Name); $script:SkippedRef++; continue }
        $full = [IO.Path]::GetFullPath($it.FullName)
        if (-not $full.StartsWith($tempFull + '\', [StringComparison]::OrdinalIgnoreCase)) { Write-Warn2 "  跳过越界路径：$full"; continue }
        $mb = Get-ItemSizeMB $it
        if ($DryRun) {
            Write-Info ('  [DRYRUN] 将删除 ' + $it.Name + '（' + $mb + ' MB）')
        } else {
            try {
                Remove-Item -LiteralPath $full -Recurse -Force -ErrorAction Stop
                Write-Ok ('  已删除 ' + $it.Name + '（' + $mb + ' MB）')
            } catch {
                Write-Warn2 ('  删除失败 ' + $it.Name + '：' + $_.Exception.Message); continue
            }
        }
        $script:DeletedCount++
        $script:DeletedMB += $mb
    }
}

Write-Host ''
if ($DryRun) {
    Write-Host ('[DRYRUN] 将删除 ' + $script:DeletedCount + ' 项，可释放约 ' + [math]::Round($script:DeletedMB, 1) + ' MB；因引用跳过 ' + $script:SkippedRef + ' 项') -ForegroundColor Yellow
} else {
    Write-Ok ('已删除 ' + $script:DeletedCount + ' 项，释放约 ' + [math]::Round($script:DeletedMB, 1) + ' MB；因引用跳过 ' + $script:SkippedRef + ' 项')
}
