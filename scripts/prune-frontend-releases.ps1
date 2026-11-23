#requires -Version 5.1
<#
.SYNOPSIS
  frontend\ 历史快照清理脚本（发布目录 + 失效流程遗留目录）。

.DESCRIPTION
  前端实际发布链路是「构建产物复制到 releases\<ReleaseId> → 改 Nginx root 指针 → graceful reload」，
  而不是替换 dist。因此 frontend\ 下会不断堆积三类目录，本脚本按用途分别处理：

  1. releases\<ReleaseId>           正式快照（每个约 90~106 MB）
  2. releases\<ReleaseId>.rollback  上一版完整副本（与上一版快照内容相同，等于双倍占用）
     - 脚本每次发布都会复制一份，并被 release-metadata\<ReleaseId>.rollback.conf.txt 引用为回滚目标
  3. dist_old.* / dist-new.* / dist-green / dist-review-fixes / .build-*
     已失效的 dist 替换流程遗留，dist 不被 Nginx 服务，纯冗余

  保留规则（四重保护，任何一条命中都不删）：
  - Nginx 当前生效的 root（从 conf 中解析，只认未注释的 root 行）
  - 当前 root 对应的 releases\<ReleaseId>.rollback（回滚目标）
  - 按最后写入时间最近的 -KeepReleases 个**正式快照**（默认 5 个，含当前 root）
    「正式快照」= 存在 release-metadata\<ReleaseId>.nginx-before.txt。
    没有元数据的目录不占保留名额（如 1.0.34-20261106_165403 是失效的
    deploy-frontend.ps1 留下的 dist 备份，从未被 Nginx 服务过）。
  其余 releases 目录与全部失效流程遗留目录一律删除。

  为什么可以删旧快照：deploy-frontend-graceful.ps1 发布时会把上一版 root 的 assets 合并进新快照
  （保留旧 chunk 供已打开页面使用），当前快照 assets 数量呈累积增长（实测 1018 → 1060）。
  因此删除更早的快照不影响当前在线页面加载，仅失去「回滚到更早版本」的能力。

.PARAMETER FrontendDir
  frontend 目录，默认 <仓库根>\frontend（common-paths.ps1 单一真源，JSHZ_ROOT 可覆盖）

.PARAMETER NginxConf
  Nginx 站点配置，默认 D:\nginx\conf\conf.d\jshz-pmo.conf

.PARAMETER KeepReleases
  保留最近几个正式快照（含当前生效 root），默认 5

.PARAMETER KeepLegacyDist
  保留失效流程遗留目录不删（排查用），默认不保留

.PARAMETER DryRun
  只打印将删除的目录，不实际删除

.PARAMETER CleanupWorkers
  PowerShell 7 批量清理并发数，默认 1。并发模式跳过删除前逐文件体积统计。

.EXAMPLE
  .\scripts\prune-frontend-releases.ps1 -DryRun
  .\scripts\prune-frontend-releases.ps1
#>
[CmdletBinding()]
param(
    [string]$FrontendDir = '',
    [string]$NginxConf = 'D:\nginx\conf\conf.d\jshz-pmo.conf',
    [int]$KeepReleases = 5,
    [switch]$KeepLegacyDist,
    [switch]$DryRun,
    [ValidateRange(1, 12)][int]$CleanupWorkers = 1
)

$ErrorActionPreference = 'Stop'
# 仓库根单一真源（方案 C 二期，2026-11-19 收敛；JSHZ_ROOT 可覆盖）
. (Join-Path $PSScriptRoot 'common-paths.ps1')
. (Join-Path $PSScriptRoot 'frontend-shared-assets.ps1')
if ([string]::IsNullOrWhiteSpace($FrontendDir)) { $FrontendDir = Join-Path $root 'frontend' }
$script:DeletedCount = 0
$script:DeletedMB = 0

function Write-Ok([string]$m) { Write-Host "[OK]   $m" -ForegroundColor Green }
function Write-Info([string]$m) { Write-Host "       $m" -ForegroundColor DarkGray }
function Write-Warn2([string]$m) { Write-Host "[WARN] $m" -ForegroundColor Yellow }
function Write-Bad([string]$m) { Write-Host "[FAIL] $m" -ForegroundColor Red }

function Get-DirSizeMB([string]$Path) {
    try {
        $sum = (Get-ChildItem -LiteralPath $Path -Recurse -File -Force -ErrorAction SilentlyContinue |
                Measure-Object -Property Length -Sum).Sum
        if ($null -eq $sum) { return 0 }
        return [math]::Round($sum / 1MB, 1)
    } catch { return 0 }
}

if (-not (Test-Path -LiteralPath $FrontendDir)) { Write-Bad "frontend 目录不存在：$FrontendDir"; exit 1 }
$ReleasesDir = Join-Path $FrontendDir 'releases'
if (-not (Test-Path -LiteralPath $ReleasesDir)) { Write-Bad "releases 目录不存在：$ReleasesDir"; exit 1 }
if (Get-Item -LiteralPath $FrontendDir,$ReleasesDir | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) {
    throw '前端工作目录或 releases 为重解析点，拒绝清理'
}
$lock = if (!$DryRun) { Enter-FrontendDeploymentLock $FrontendDir }
try {

# ---- 1) 解析 Nginx 当前生效 root（只认未注释行，跳过 HTTPS 预留块里的注释行）----
if (-not (Test-Path -LiteralPath $NginxConf)) { Write-Bad "Nginx 配置不存在：$NginxConf（无法确定当前 root，拒绝执行）"; exit 1 }
$activeRoot = $null
foreach ($line in Get-Content -LiteralPath $NginxConf) {
    if ($line -match '^\s*root\s+(.+?);') { $activeRoot = $Matches[1].Trim(); break }
}
if ([string]::IsNullOrWhiteSpace($activeRoot)) {
    Write-Bad '未能在 Nginx 配置中解析出 root 指令（拒绝执行，避免误删在线版本）'
    exit 1
}
$activeRootNorm = [IO.Path]::GetFullPath($activeRoot.Trim('"'))
$activeReleaseId = Split-Path $activeRootNorm -Leaf
$activeReleasePath = Join-Path $ReleasesDir $activeReleaseId
if ($activeRootNorm -ne [IO.Path]::GetFullPath($activeReleasePath) -or !(Test-Path -LiteralPath $activeReleasePath)) {
    Write-Bad "Nginx root 指向的目录不存在：$activeRoot（配置与磁盘不一致，先排查再清理）"
    exit 1
}

Write-Host ("前端历史目录清理" + $(if ($DryRun) { '（DRYRUN，不删除任何目录）' } else { '' })) -ForegroundColor Cyan
Write-Info "frontend: $FrontendDir"
Write-Info "当前 root: $activeRoot"
Write-Info "保留最近 $KeepReleases 个正式快照（含当前 root）+ 当前回滚目标"

# ---- 2) 计算保留集合 ----
$keep = New-Object System.Collections.Generic.HashSet[string]
[void]$keep.Add($activeReleaseId)
$formalActiveId = $activeReleaseId -replace '\.rollback$', ''
$activeRollbackId = "$formalActiveId.rollback"
if (Test-Path -LiteralPath (Join-Path $ReleasesDir $activeRollbackId)) { [void]$keep.Add($activeRollbackId) }
if (Test-Path -LiteralPath (Join-Path $ReleasesDir $formalActiveId)) { [void]$keep.Add($formalActiveId) }

$allReleaseDirs = @(Get-ChildItem -LiteralPath $ReleasesDir -Directory -Force -ErrorAction SilentlyContinue)
$metadataDir = Join-Path $FrontendDir 'release-metadata'
foreach ($record in 'nginx-before.txt','rollback.conf.txt') {
    $recordPath = Join-Path $metadataDir "$formalActiveId.$record"
    if (!(Test-Path -LiteralPath $recordPath)) { continue }
    $beforeRoot = [regex]::Match((Get-Content -LiteralPath $recordPath -Raw), '(?m)^\s*root\s+([^;]+);')
    if ($beforeRoot.Success) {
        $prior = [IO.Path]::GetFullPath($beforeRoot.Groups[1].Value.Trim().Trim('"'))
        if ((Split-Path -Parent $prior) -eq [IO.Path]::GetFullPath($ReleasesDir) -and (Test-Path -LiteralPath $prior)) {
            [void]$keep.Add((Split-Path -Leaf $prior))
        }
    }
}

# 「正式快照」判定：必须有 release-metadata\<name>.nginx-before.txt。
# 依据：deploy-frontend-graceful.ps1 每次切换都会写这对元数据；而没有元数据的目录
# （如 1.0.34-20261106_165403）是已失效的 deploy-frontend.ps1 留下的 dist 备份，
# 从未被 Nginx 服务过，不能占用 KeepReleases 名额。
$formalDirs = @($allReleaseDirs | Where-Object {
        $_.Name -notlike '*.rollback' -and
        (Test-Path -LiteralPath (Join-Path $metadataDir ($_.Name + '.nginx-before.txt')))
    } | Sort-Object LastWriteTime -Descending)

$formalAlreadyKept = @($formalDirs | Where-Object { $keep.Contains($_.Name) }).Count
$extraKeep = @($formalDirs | Where-Object { !$keep.Contains($_.Name) } |
               Select-Object -First ([math]::Max(0, $KeepReleases - $formalAlreadyKept)))
foreach ($d in $extraKeep) { [void]$keep.Add($d.Name) }

Write-Info ("保留清单: " + (($keep | Sort-Object) -join ', '))
Write-Host ''

# ---- 3) 收集删除候选（releases 内非保留目录）----
$formalNames = New-Object System.Collections.Generic.HashSet[string]
foreach ($d in $formalDirs) { [void]$formalNames.Add($d.Name) }

$targets = New-Object System.Collections.Generic.List[object]
foreach ($d in $allReleaseDirs) {
    if ($keep.Contains($d.Name)) { continue }
    # 有发布元数据的才算正式快照，其余（dist 备份残留）标注为遗留
    $kind = if ($formalNames.Contains($d.Name) -or $d.Name -like '*.rollback') { 'releases' } else { 'legacy' }
    $targets.Add([pscustomobject]@{ Kind = $kind; Path = $d.FullName; Name = $d.Name })
}

# ---- 4) 失效 dist 替换流程遗留目录 ----
if (-not $KeepLegacyDist) {
    $legacyPatterns = @('dist_old.*', 'dist-new.*', 'dist-green', 'dist-review-fixes', '.build-*')
    foreach ($p in $legacyPatterns) {
        foreach ($d in @(Get-ChildItem -LiteralPath $FrontendDir -Directory -Force -Filter $p -ErrorAction SilentlyContinue)) {
            if ($d.Name -eq 'dist') { continue }
            $targets.Add([pscustomobject]@{ Kind = 'legacy'; Path = $d.FullName; Name = $d.Name })
        }
    }
}

# ---- 5) 安全兜底：路径必须在 frontend\releases 或 frontend 一级目录下 ----
foreach ($t in $targets) {
    $full = [System.IO.Path]::GetFullPath($t.Path)
    $underReleases = $full.StartsWith([System.IO.Path]::GetFullPath($ReleasesDir) + '\', [StringComparison]::OrdinalIgnoreCase)
    $underFrontend = $full.StartsWith([System.IO.Path]::GetFullPath($FrontendDir) + '\', [StringComparison]::OrdinalIgnoreCase)
    if (-not ($underReleases -or $underFrontend) -or ((Get-Item -LiteralPath $full).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        Write-Bad "路径越界或为重解析点，拒绝删除：$full"; exit 1
    }
    if ($full.TrimEnd('\') -eq [System.IO.Path]::GetFullPath($FrontendDir).TrimEnd('\')) { Write-Bad '拒绝删除 frontend 根目录本身'; exit 1 }
}

# ---- 6) 执行 ----
Write-Host ("将清理 $($targets.Count) 个目录：") -ForegroundColor Yellow
if (!$DryRun -and $CleanupWorkers -gt 1) {
    if ($PSVersionTable.PSVersion.Major -lt 7) { throw '并发清理需要 PowerShell 7' }
    $targets | ForEach-Object -ThrottleLimit $CleanupWorkers -Parallel {
        $candidate = $_
        try {
            $target = Get-Item -LiteralPath $candidate.Path -ErrorAction Stop
            if ($target.FullName -ne $candidate.Path -or ($target.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
                throw "Unsafe target: $($target.FullName)"
            }
            Remove-Item -LiteralPath $target.FullName -Recurse -Force -ErrorAction Stop
            [pscustomobject]@{ Name = $candidate.Name; Error = $null }
        } catch {
            [pscustomobject]@{ Name = $candidate.Name; Error = $_.Exception.Message }
        }
    } | ForEach-Object {
        if ($_.Error) { Write-Bad "删除失败 $($_.Name)：$($_.Error)" }
        else { Write-Info "[删除] $($_.Name)"; $script:DeletedCount++ }
    }
} else {
foreach ($t in $targets) {
    $mb = Get-DirSizeMB $t.Path
    $tag = if ($t.Kind -eq 'releases') { '快照' } else { '遗留' }
    if ($DryRun) {
        Write-Host ("   [DRYRUN] ($tag) $($t.Name)  $mb MB") -ForegroundColor Yellow
    } else {
        try {
            Remove-Item -LiteralPath $t.Path -Recurse -Force -ErrorAction Stop
            Write-Host ("   [删除] ($tag) $($t.Name)  $mb MB") -ForegroundColor DarkGray
            $script:DeletedCount++
            $script:DeletedMB += $mb
        } catch {
            Write-Bad "删除失败 $($t.Name)：$($_.Exception.Message)"
        }
    }
    if ($DryRun) { $script:DeletedMB += $mb; $script:DeletedCount++ }
}
}

Write-Host ''
if ($DryRun) {
    Write-Ok ("预演：将删除 $($script:DeletedCount) 个目录，文件逻辑大小约 $([math]::Round($script:DeletedMB,1)) MB（未实际删除）")
} else {
    if ($CleanupWorkers -gt 1) { Write-Ok "完成：删除 $($script:DeletedCount) 个目录（并发模式未统计逻辑大小）" }
    else {
    Write-Ok ("完成：删除 $($script:DeletedCount) 个目录，文件逻辑大小约 $([math]::Round($script:DeletedMB,1)) MB")
    }
    $left = @(Get-ChildItem -LiteralPath $ReleasesDir -Directory -Force -ErrorAction SilentlyContinue)
    Write-Info ("releases 剩余 $($left.Count) 个目录：" + (($left.Name | Sort-Object) -join ', '))
}
$remaining = if ($DryRun) { @($allReleaseDirs | Where-Object { $keep.Contains($_.Name) }) }
             else { @(Get-ChildItem -LiteralPath $ReleasesDir -Directory -Force) }
Remove-ExpiredFrontendAssets $FrontendDir @($remaining) -DryRun:$DryRun
if ($script:DeletedCount -lt $targets.Count -and !$DryRun) { exit 1 }
} finally {
    if ($lock) { $lock.Dispose() }
}
exit 0
