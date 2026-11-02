#requires -Version 5.1
<#
.SYNOPSIS
  jshz-PMO 一键构建脚本（前端 npm run build + 后端 mvn package）

.DESCRIPTION
  版本号唯一真源为项目根 D:\jshz-PMO\version.txt：
    - 前端 vite.config.js 构建期读取并注入 VITE_APP_VERSION
    - 后端 properties-maven-plugin 读取并生成 version.properties（health 返回 version）
  构建产物：frontend\dist、backend\target\expert-system.jar

.EXAMPLE
  .\scripts\build-all.ps1
#>
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent

Write-Host '========================================' -ForegroundColor Cyan
Write-Host ' jshz-PMO 一键构建（前端+后端）' -ForegroundColor Cyan
Write-Host '========================================' -ForegroundColor Cyan

$VersionFile = Join-Path $Root 'version.txt'
if (-not (Test-Path $VersionFile)) { throw "缺少版本号唯一真源: $VersionFile" }
$m = [regex]::Match((Get-Content $VersionFile -Raw -Encoding UTF8), '(?m)^revision\s*=\s*(.+)\s*$')
if (-not $m.Success) { throw "version.txt 未找到 revision 键: $VersionFile" }
$revision = $m.Groups[1].Value.Trim()
Write-Host "当前版本: $revision" -ForegroundColor Green

# [1/2] 前端构建
Write-Host "`n== [1/2] 前端构建 ==" -ForegroundColor Cyan
Push-Location (Join-Path $Root 'frontend')
try {
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "前端构建失败，退出码 $LASTEXITCODE" }
} finally { Pop-Location }

# [2/2] 后端构建
Write-Host "`n== [2/2] 后端构建 ==" -ForegroundColor Cyan
Push-Location (Join-Path $Root 'backend')
try {
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "后端构建失败，退出码 $LASTEXITCODE" }
} finally { Pop-Location }

$jar = Join-Path $Root 'backend\target\expert-system.jar'
if (-not (Test-Path $jar)) { throw "后端产物缺失: $jar" }
Write-Host "`n构建完成：frontend\dist + $jar" -ForegroundColor Green
