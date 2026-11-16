#requires -Version 5.1
<#
.SYNOPSIS
  项目路径与版本号单一入口（构建目录约定 + 版本真源读取）。

.DESCRIPTION
  1. 构建目录统一收敛到 D:\jshz-PMO\builds：
       builds\<slug>-<yyyyMMdd_HHmmss>\   当前/后续发布构建
       builds\archive\<历史目录>\          历史构建快照（原 .build-* 迁移而来）
     禁止再在仓库根目录新建 .build-* 目录。

  2. 版本号唯一真源仍为 D:\jshz-PMO\version.txt（revision=...）。
     Maven 侧以 CI-friendly 方式注入：mvn -Drevision=<值>，pom 中 <version>${revision}</version>。
     本模块的 Get-ProjectRevision 供全部构建/发布脚本调用，避免各处重复解析。
#>

. (Join-Path $PSScriptRoot 'common-paths.ps1')
$script:RepoRoot      = $root
$script:BuildsRoot    = Join-Path $script:RepoRoot 'builds'
$script:BuildsArchive = Join-Path $script:BuildsRoot 'archive'
$script:VersionFile   = Join-Path $script:RepoRoot 'version.txt'

function Get-BuildsRoot {
    if (-not (Test-Path -LiteralPath $script:BuildsRoot)) {
        New-Item -ItemType Directory -Path $script:BuildsRoot -Force | Out-Null
    }
    return $script:BuildsRoot
}

function Get-BuildsArchive {
    if (-not (Test-Path -LiteralPath $script:BuildsArchive)) {
        New-Item -ItemType Directory -Path $script:BuildsArchive -Force | Out-Null
    }
    return $script:BuildsArchive
}

function Get-ProjectRevision {
    <#
      读取 version.txt 的 revision 值；文件缺失或格式非法时抛错（fail-fast，不静默兜底）。
    #>
    if (-not (Test-Path -LiteralPath $script:VersionFile)) {
        throw "缺少版本号唯一真源: $script:VersionFile"
    }
    $text = [System.IO.File]::ReadAllText($script:VersionFile, (New-Object System.Text.UTF8Encoding($false)))
    $m = [regex]::Match($text, '(?m)^\s*revision\s*=\s*(.+?)\s*$')
    if (-not $m.Success) { throw "version.txt 未找到 revision 键: $script:VersionFile" }
    $rev = $m.Groups[1].Value.Trim()
    if (-not $rev) { throw "version.txt 的 revision 为空: $script:VersionFile" }
    return $rev
}

function Get-ProjectGitShort {
    <#
      取当前仓库短提交号（7 位）。非 git 工作区或命令失败时返回空串，
      由调用方决定是否作为 -DgitShort 传入（空值在 /health 中显示 unknown）。
    #>
    try {
        $out = & git -C $script:RepoRoot rev-parse --short=7 HEAD 2>$null
        if ($LASTEXITCODE -eq 0 -and $out) { return ($out | Select-Object -First 1).Trim() }
    } catch {
    }
    return ''
}

function New-BuildStage {
    <#
      .SYNOPSIS 在 builds\ 下创建本次构建/发布的工作目录并返回绝对路径。
      .PARAMETER Slug 语义化名称，如 h1h2、dashboard-layout、compliance-visual
      .PARAMETER Revision 版本号（缺省取 version.txt）
      .EXAMPLE  $stage = New-BuildStage -Slug 'h1h2'
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Slug,
        [string]$Revision = ''
    )
    if (-not $Revision) { $Revision = Get-ProjectRevision }
    $stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
    $safe = ($Slug -replace '[^\w\.\-]', '-').Trim('-')
    $stage = Join-Path (Get-BuildsRoot) ("$Revision-$safe-$stamp")
    New-Item -ItemType Directory -Path $stage -Force | Out-Null
    return $stage
}

function Assert-UnderBuilds {
    <#
      .SYNOPSIS 校验路径位于 builds\ 之下（含 archive），用于替换旧的 .build-* 前缀护栏。
    #>
    param([Parameter(Mandatory = $true)][string]$Path)
    $root = [System.IO.Path]::GetFullPath($script:BuildsRoot).TrimEnd('\') + '\'
    $full = [System.IO.Path]::GetFullPath($Path)
    if (-not $full.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing operation outside builds root: $full"
    }
    return $full
}

function Get-SmokePasswordPath {
    <#
      .SYNOPSIS 受 DPAPI 保护的冒烟测试口令文件（随 project-config 构建快照归档移动）。
      .DESCRIPTION 3 个 release-*.ps1 依赖该文件校验制品；路径集中在此，迁移目录只需改一处。
    #>
    return (Join-Path (Join-Path (Get-BuildsArchive) '.build-project-config-production-20261104') 'smoke-password.dpapi')
}
