#requires -Version 5.1
<#
.SYNOPSIS
  jshz-PMO 一键构建脚本（前端 npm run build + 后端 mvn package，隔离目录构建）

.DESCRIPTION
  版本号唯一真源为 D:\jshz-PMO\version.txt：
    - 前端：vite.config.js 构建期读取并注入 VITE_APP_VERSION
    - 后端：本脚本读取后以 -Drevision=<值> 注入 Maven（pom 中 <version>${revision}</version>），
            同时 -DgitShort=<短提交号> 写入 build-info，供 /health 返回 buildTime / gitShort

  关键约束（2026-11-06 改造）：
    不在 backend\ 原地构建。生产服务正在运行 backend\target\expert-system.jar，
    `mvn clean` 会因该 jar 被进程占用而失败（本次改造时已复现）。因此每次构建把 backend
    源码复制到 builds\<版本>-build-<时间戳>\backend 内构建，仓库内 backend\target 不再参与发布。

  产物：
    builds\<版本>-build-<时间戳>\
      ├─ backend\target\expert-system.jar   后端可执行包
      ├─ expert-system.jar                   发布用副本（deploy-backend.ps1 -JarPath 指向它）
      ├─ dist\                               前端静态产物（仅全量构建时）
      └─ build-info.txt                      版本/提交号/构建时间

  构建后校验（确定性断言，不通过即失败）：
    1. build-info.properties 的 version 必须等于 version.txt 的 revision
    2. 制品必须包含 BOOT-INF/（可启动 Spring Boot 包）

.PARAMETER BackendOnly
  只构建后端，跳过前端 npm run build。
  用于纯后端发布：避免重建 frontend\dist 动到线上正在服务的静态资源。

.EXAMPLE
  .\scripts\build-all.ps1

.EXAMPLE
  .\scripts\build-all.ps1 -BackendOnly
#>
[CmdletBinding()]
param(
    [switch]$BackendOnly
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'logging.ps1')
. (Join-Path $PSScriptRoot 'build-paths.ps1')
Enable-Utf8Console

$Root = $RepoRoot

Write-Host '========================================' -ForegroundColor Cyan
Write-Host (' jshz-PMO 一键构建（隔离目录，{0}）' -f $(if ($BackendOnly) { '仅后端' } else { '前端+后端' })) -ForegroundColor Cyan
Write-Host '========================================' -ForegroundColor Cyan

$revision = Get-ProjectRevision
$gitShort = Get-ProjectGitShort
if (-not $gitShort) { $gitShort = 'unknown' }
Write-Host "当前版本: $revision （gitShort=$gitShort）" -ForegroundColor Green

$stage = New-BuildStage -Slug 'build' -Revision $revision
Write-Host "构建目录: $stage" -ForegroundColor Green

$totalSteps = if ($BackendOnly) { 1 } else { 2 }

# [1/2] 前端构建
if ($BackendOnly) {
    Write-Host "`n== 前端构建已跳过（-BackendOnly） ==" -ForegroundColor Yellow
} else {
    Write-Host "`n== [1/$totalSteps] 前端构建 ==" -ForegroundColor Cyan
    Push-Location (Join-Path $Root 'frontend')
    try {
        npm run build
        if ($LASTEXITCODE -ne 0) { throw "前端构建失败，退出码 $LASTEXITCODE" }
    } finally { Pop-Location }
}

# [2/2] 后端构建（复制源码到隔离目录，避免占用生产运行中的 target\*.jar）
Write-Host "`n== [$totalSteps/$totalSteps] 后端构建（隔离目录） ==" -ForegroundColor Cyan
$srcBackend = Join-Path $Root 'backend'
$dstBackend = Join-Path $stage 'backend'
New-Item -ItemType Directory -Path $dstBackend -Force | Out-Null

& robocopy $srcBackend $dstBackend /E /XD target releases logs .git /XF '*.log' '*.dumpstream' /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "复制后端源码失败（robocopy exit=$LASTEXITCODE）" }

# pom 的资源过滤读取 ${project.basedir}/../version.txt，因此版本文件必须与 backend 同级
Copy-Item -LiteralPath (Join-Path $Root 'version.txt') -Destination (Join-Path $stage 'version.txt') -Force

& mvn -q -f (Join-Path $dstBackend 'pom.xml') clean package -DskipTests "-Drevision=$revision" "-DgitShort=$gitShort"
if ($LASTEXITCODE -ne 0) { throw "后端构建失败，退出码 $LASTEXITCODE" }

$builtJar = Join-Path $dstBackend 'target\expert-system.jar'
if (-not (Test-Path -LiteralPath $builtJar)) { throw "后端产物缺失: $builtJar" }

# ---- 校验 1：build-info 版本号必须与 version.txt 一致 ----
$buildInfo = Join-Path $dstBackend 'target\classes\META-INF\build-info.properties'
if (-not (Test-Path -LiteralPath $buildInfo)) { throw "缺少 build-info.properties：$buildInfo" }
$biText = Read-Utf8Text $buildInfo
$biVersion = ([regex]::Match($biText, '(?m)^build\.version=(.+)$')).Groups[1].Value.Trim()
$biGit = ([regex]::Match($biText, '(?m)^build\.gitShort=(.+)$')).Groups[1].Value.Trim()
if ($biVersion -ne $revision) {
    throw "版本注入不一致：build-info=$biVersion，version.txt=$revision"
}
Write-Host "[OK] build-info 校验：version=$biVersion gitShort=$biGit" -ForegroundColor Green

# ---- 校验 2：制品必须是可启动 Spring Boot 包 ----
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($builtJar)
try {
    $hasBootInf = ($zip.Entries | Where-Object { $_.FullName -like 'BOOT-INF/*' } | Select-Object -First 1) -ne $null
    $logbackEntry = $zip.Entries | Where-Object { $_.FullName -eq 'BOOT-INF/classes/logback-spring.xml' } | Select-Object -First 1
    $hasLogback = $logbackEntry -ne $null
    $logbackText = ''
    if ($logbackEntry) {
        $sr = New-Object System.IO.StreamReader($logbackEntry.Open())
        $logbackText = $sr.ReadToEnd()
        $sr.Dispose()
    }
} finally { $zip.Dispose() }
if (-not $hasBootInf) { throw "制品不是可启动 Spring Boot 包（缺少 BOOT-INF/）: $builtJar" }
Write-Host '[OK] 制品形态校验：包含 BOOT-INF/（可执行 Boot 包）' -ForegroundColor Green
if ($hasLogback) {
    Write-Host '[OK] 日志配置校验：包含 logback-spring.xml（应用层轮转生效）' -ForegroundColor Green
} else {
    Write-Host '[WARN] 制品缺少 BOOT-INF/classes/logback-spring.xml，应用层日志轮转不会生效' -ForegroundColor Yellow
}

# ---- 校验 3：CONSOLE appender 必须显式声明 UTF-8 ----
# 踩坑记录（2026-11-06）：logback 的 ConsoleAppender 是自行把日志编码成字节后写入 System.out，
# 走 Charset.defaultCharset()（本机 file.encoding=GBK），JVM 参数 -Dsun.stdout.encoding 管不到它。
# 不显式声明时，NSSM 重定向的 expert-backend.out.log 会落成 GBK。实测同一事件：
# CONSOLE 落 GBK、APP_FILE 落 UTF-8。此处做确定性断言，防止回退。
if ($hasLogback) {
    $consoleBlock = [regex]::Match($logbackText, '<appender\s+name="CONSOLE".*?</appender>', 'Singleline')
    if (-not $consoleBlock.Success) {
        throw 'logback-spring.xml 未定义 CONSOLE appender，无法校验输出编码'
    }
    if ($consoleBlock.Value -notmatch '<charset>\s*UTF-8\s*</charset>') {
        throw 'CONSOLE appender 缺少 <charset>UTF-8</charset>：NSSM 重定向的 out.log 会落 GBK（详见 logback-spring.xml 注释）'
    }
    Write-Host '[OK] 日志编码校验：CONSOLE appender 显式 UTF-8（out.log 不会落 GBK）' -ForegroundColor Green
}

# ---- 整理产物到目录根 ----
$publishJar = Join-Path $stage 'expert-system.jar'
Copy-Item -LiteralPath $builtJar -Destination $publishJar -Force
$dist = Join-Path $Root 'frontend\dist'
if (-not $BackendOnly -and (Test-Path -LiteralPath $dist)) {
    Copy-Item -Path $dist -Destination (Join-Path $stage 'dist') -Recurse -Force
}
Write-Utf8Text (Join-Path $stage 'build-info.txt') @"
version=$revision
gitShort=$gitShort
builtAt=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
scope=$(if ($BackendOnly) { 'backend-only' } else { 'frontend+backend' })
jar=$publishJar
"@

Write-Host "`n构建完成" -ForegroundColor Green
Write-Host "  候选包: $publishJar" -ForegroundColor Green
if (-not $BackendOnly) { Write-Host "  前端产物: $stage\dist" -ForegroundColor Green }
Write-Host "发布命令: .\scripts\deploy-backend.ps1 -JarPath '$publishJar' -Version $revision -GracefulSwitch" -ForegroundColor DarkGray
