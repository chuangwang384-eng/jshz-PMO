#requires -Version 5.1
<#
.SYNOPSIS
  builds\ 构建产物保留策略脚本（保留最近 N 个构建目录）。

.DESCRIPTION
  builds\ 是构建/发布唯一工作区（scripts\build-paths.ps1 定义）。每次 build-all.ps1 都会新建
  <版本>-build-<时间戳>\ 目录（后端编译产物 + 前端 dist，单目录数百 MB），历史无清理机制，
  实测已累积 108 个目录 / 16.5 GB（不含 archive）。

  本脚本按「最后写入时间」保留最近 -Keep 个一级目录，其余删除：
  - archive\ 永不触碰（只读留档，其中 .build-project-config-production-20261104\smoke-password.dpapi
    被 3 个 release-*.ps1 引用，删除会导致发布脚本失效）。
  - 删除前校验目标路径必须位于 builds\ 下且不等于 builds\archive。
  - 只处理一级子目录，不递归进入 archive。

  为什么可以删：构建产物可由 builds\README.md 记录的同一命令重建，发布只认最新候选包；
  回滚依赖 backend\releases 与 frontend\releases，不依赖 builds\。

.PARAMETER BuildsRoot
  builds 目录，默认 <仓库根>\builds（common-paths.ps1 单一真源，JSHZ_ROOT 可覆盖）

.PARAMETER Keep
  保留最近几个构建目录，默认 5

.PARAMETER DryRun
  只打印将删除的目录与可释放空间，不实际删除

.EXAMPLE
  .\scripts\prune-builds.ps1 -DryRun
  .\scripts\prune-builds.ps1 -Keep 5
#>
[CmdletBinding()]
param(
    [string]$BuildsRoot = '',
    [int]$Keep = 5,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
# 仓库根单一真源（方案 C 二期，2026-11-19 收敛；JSHZ_ROOT 可覆盖）
. (Join-Path $PSScriptRoot 'common-paths.ps1')
if ([string]::IsNullOrWhiteSpace($BuildsRoot)) { $BuildsRoot = Join-Path $root 'builds' }
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

if (-not (Test-Path -LiteralPath $BuildsRoot)) { Write-Bad "builds 目录不存在：$BuildsRoot"; exit 1 }
if ($Keep -lt 1) { Write-Bad '-Keep 必须 >= 1（拒绝清空 builds）'; exit 1 }

$rootFull = [IO.Path]::GetFullPath($BuildsRoot).TrimEnd('\')
$archivePath = Join-Path $rootFull 'archive'

Write-Host ('builds 构建产物保留（Keep=' + $Keep + $(if ($DryRun) { '，DRYRUN 不删除' } else { '' }) + '）') -ForegroundColor Cyan
Write-Info "builds: $rootFull"

$dirs = @(Get-ChildItem -LiteralPath $rootFull -Directory -Force -ErrorAction SilentlyContinue |
          Where-Object { $_.FullName -ne $archivePath } |
          Sort-Object LastWriteTime -Descending)

if ($dirs.Count -eq 0) { Write-Ok '无可处理的构建目录'; exit 0 }

$keepDirs = @($dirs | Select-Object -First $Keep)
$targets = @($dirs | Select-Object -Skip $Keep)

Write-Info ("保留 " + $keepDirs.Count + " 个：" + (($keepDirs | ForEach-Object { $_.Name }) -join ', '))
if ($archivePath -and (Test-Path -LiteralPath $archivePath)) {
    Write-Info ('archive\ 不计入保留名额、永不删除（' + (Get-DirSizeMB $archivePath) + ' MB）')
}

if ($targets.Count -eq 0) { Write-Ok "当前 $($dirs.Count) 个构建目录，未超上限 $Keep"; exit 0 }

Write-Host ''
foreach ($d in $targets) {
    $full = [IO.Path]::GetFullPath($d.FullName)
    if (-not $full.StartsWith($rootFull + '\', [StringComparison]::OrdinalIgnoreCase)) {
        Write-Warn2 "跳过越界路径：$full"; continue
    }
    if ($full -eq $archivePath) { Write-Warn2 '跳过 archive'; continue }
    $mb = Get-DirSizeMB $full
    if ($DryRun) {
        Write-Info ('[DRYRUN] 将删除 ' + $d.Name + '（' + $mb + ' MB，' + $d.LastWriteTime.ToString('yyyy-MM-dd HH:mm') + '）')
    } else {
        try {
            Remove-Item -LiteralPath $full -Recurse -Force -ErrorAction Stop
            Write-Ok ('已删除 ' + $d.Name + '（' + $mb + ' MB）')
        } catch {
            Write-Warn2 ('删除失败 ' + $d.Name + '：' + $_.Exception.Message); continue
        }
    }
    $script:DeletedCount++
    $script:DeletedMB += $mb
}

Write-Host ''
if ($DryRun) {
    Write-Host ('[DRYRUN] 将删除 ' + $script:DeletedCount + ' 个构建目录，可释放约 ' + [math]::Round($script:DeletedMB, 1) + ' MB') -ForegroundColor Yellow
} else {
    Write-Ok ('已删除 ' + $script:DeletedCount + ' 个构建目录，释放约 ' + [math]::Round($script:DeletedMB, 1) + ' MB')
}
