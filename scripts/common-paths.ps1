#requires -Version 5.1
<#
.SYNOPSIS
  仓库根路径单一真源（2026-11-13 复用治理/方案 C 收敛）。

.DESCRIPTION
  以 dot-source 方式引入后即得 $root（仓库根绝对路径）。
  优先取环境变量 JSHZ_ROOT（机器迁移/演练目录场景），缺省本机值 D:\jshz-PMO。
  新运维脚本禁止再写死 $root 字面量，必须 dot-source 本文件；
  构建目录/版本号相关能力见 build-paths.ps1（内部亦引用本文件）。
#>

$ErrorActionPreference = 'Stop'

$root = if ($env:JSHZ_ROOT) { $env:JSHZ_ROOT.TrimEnd('\') } else { 'D:\jshz-PMO' }
if (-not (Test-Path -LiteralPath $root)) {
    throw "仓库根目录不存在: $root（请检查 JSHZ_ROOT 环境变量或本机部署位置）"
}
