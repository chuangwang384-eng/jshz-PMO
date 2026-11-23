#requires -Version 5.1
<#
.SYNOPSIS
  jshz-PMO 全栈一键发布编排（预检 / 发布 / 回滚），PowerShell 5.1。

.DESCRIPTION
  本脚本不重复实现发布逻辑，只把仓库既有、已验证的子脚本按正确顺序串起来，
  并补齐「发布前护栏 + 发布后验证 + 状态记录 + 回滚」这四件编排层的事：

    Preflight（默认，只读）
      版本真源 -> git 未提交清单（仅警告）-> 迁移状态 -> schema parity ->
      当前 /health、Nginx root、首页状态 -> ReleaseId 冲突 -> 打印执行计划
    Publish（需 -Yes 且管理员）
      版本同步(version.txt / package.json) -> backup.ps1 全量备份 ->
      build-all.ps1 隔离目录全量构建 -> 制品校验（含可选 jar 类校验） ->
      release-backend.ps1 后端蓝绿 -> deploy-frontend-graceful.ps1 前端快照切换 ->
      发布后验证（/health 版本、Nginx root、index hash、主 bundle 版本、API 存活） ->
      写 logs/release-state.json（回滚依据）
    Rollback（需 -Yes 且管理员）
      前端：写回 frontend/release-metadata/<ReleaseId>.nginx-before.txt 并 graceful reload
      后端：仅当 -IncludeBackend 时，用 backend/releases/<旧版本>-*/previous-expert-system.jar
            重新走蓝绿发布（数据库为加法迁移，不回滚，旧包不读写新列）

  事实源与既有约定（见 AGENTS.md §0 / docs/deployment.md）：
    - 版本号唯一真源 version.txt（revision=），frontend/package.json 须同步
    - 构建产物只在 builds/<版本>-build-<时间戳>/，禁止在 backend/ 原地 mvn clean package
    - 前端线上 root 指向 frontend/releases/<ReleaseId>/，不是 frontend/dist
    - 日志只在 logs/，发布日志由 deploy-backend.ps1 自动追加 docs/deployment.md 表格

  已知环境坑（已在脚本内处理）：
    1. 本机 PowerShell 执行策略禁止直接跑 .ps1 -> 配套 release-full.cmd（Bypass）或手工加参数
    2. 环境变量 HTTPS_PROXY/https_proxy 大小写重复会让 Start-Process 抛
       "已添加项。字典中的关键字" -> 启动前统一清理代理变量（子进程同样继承已清理环境）
    3. 子脚本多以 exit <code> 结束 -> 一律用独立 powershell.exe 子进程调用，避免终止当前宿主
    4. 中文输出经 cmd 重定向落盘 -> 子脚本自带 Enable-Utf8Console，父脚本用 Read-Utf8Text 读取

.PARAMETER Mode
  Preflight（默认）/ Publish / Rollback。

.PARAMETER Version
  目标版本号，如 1.0.98。Publish 时若与 version.txt 不同，自动同步两处版本文件（先备份到 logs/）。

.PARAMETER Slug
  ReleaseId 后缀，默认 project-type-finance；最终 ReleaseId = "<Version>-<Slug>"。

.PARAMETER Yes
  发布/回滚的显式批准开关。Preflight 不需要；不给则 Publish/Rollback 只打印计划并退出。

.PARAMETER SkipBackup
  跳过 backup.ps1 全量备份（不推荐）。

.PARAMETER SkipVersionBump
  不自动同步版本文件；version.txt / package.json 必须已与 -Version 一致，否则中止。

.PARAMETER ReleaseId
  Rollback 指定要回滚的发布 ID；缺省读 logs/release-state.json 的最近一次发布。

.PARAMETER IncludeBackend
  Rollback 时一并回滚后端二进制（高风险，需人工确认数据兼容性）。

.PARAMETER KeepVersions
  归档保留数量，默认 5。

.PARAMETER NginxConf
  Nginx 站点配置路径，默认 D:\nginx\conf\conf.d\jshz-pmo.conf（与既有部署脚本一致）。
  机器迁移或隔离演练时可覆盖；脚本只读取/回滚该文件，其他子脚本仍用各自默认路径。

.PARAMETER ExpectJarClass
  制品内必须存在的 BOOT-INF/classes 相对路径（默认校验本次项目类型功能两个类）。
  后续复用发布时可用 -ExpectJarClass @() 清空，或换成自家类路径。

.EXAMPLE
  # 1) 只读预检（无需管理员）
  powershell -NoProfile -ExecutionPolicy Bypass -File D:\jshz-PMO\scripts\release-full.ps1 -Mode Preflight -Version 1.0.98

.EXAMPLE
  # 2) 正式发布（管理员 PowerShell）
  powershell -NoProfile -ExecutionPolicy Bypass -File D:\jshz-PMO\scripts\release-full.ps1 -Mode Publish -Version 1.0.98 -Slug project-type-finance -Yes

.EXAMPLE
  # 3) 回滚前端（管理员 PowerShell）
  powershell -NoProfile -ExecutionPolicy Bypass -File D:\jshz-PMO\scripts\release-full.ps1 -Mode Rollback -Yes
#>
[CmdletBinding()]
param(
    [ValidateSet('Preflight', 'Publish', 'Rollback')]
    [string]$Mode = 'Preflight',
    [string]$Version = '',
    [string]$Slug = 'project-type-finance',
    [switch]$Yes,
    [switch]$SkipBackup,
    [switch]$SkipVersionBump,
    [string]$ReleaseId = '',
    [switch]$IncludeBackend,
    [int]$KeepVersions = 5,
    [string]$NginxConf = 'D:\nginx\conf\conf.d\jshz-pmo.conf',
    [string[]]$ExpectJarClass = @(
        'BOOT-INF/classes/com/jshz/expert/entity/ProjectStageForm.class',
        'BOOT-INF/classes/com/jshz/expert/mapper/ProjectStageFormMapper.class'
    )
)

$ErrorActionPreference = 'Stop'

# ---------- 公共库（路径/版本真源/UTF-8 日志/本地 HTTP 探针） ----------
. (Join-Path $PSScriptRoot 'common-paths.ps1')
. (Join-Path $PSScriptRoot 'build-paths.ps1')
. (Join-Path $PSScriptRoot 'logging.ps1')
. (Join-Path $PSScriptRoot 'deployment-http.ps1')
. (Join-Path $PSScriptRoot 'frontend-shared-assets.ps1')
# 不调用 Enable-Utf8Console：它把 Console.OutputEncoding 切成 UTF-8 后，
# 在代码页 936 的控制台里 Write-Host 中文会显示乱码（发布脚本需要人能直接读输出）。
# 日志一律由 Add-Utf8Line / Write-Utf8Text 独立写 UTF-8 无 BOM；子进程输出经
# Invoke-Capture 的 UTF-8/GBK 双解码兜底后再写入主日志，编码同样统一。

$stamp       = Get-Date -Format 'yyyyMMdd_HHmmss'
$startIso    = (Get-Date).ToString('o')
$logDir      = Join-Path $root 'logs'
$logFile     = Join-Path $logDir "release-full-$stamp.log"
$stateFile   = Join-Path $logDir 'release-state.json'
$releasesDir = Join-Path $root 'frontend\releases'
$metadataDir = Join-Path $root 'frontend\release-metadata'
$buildsDir   = Join-Path $root 'builds'
$scriptsDir  = $PSScriptRoot

if (-not (Test-Path -LiteralPath $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

# ---------- 环境护栏 ----------
function Clear-ProxyEnvDuplicates {
    # 本机曾出现 HTTPS_PROXY 与 https_proxy 同时存在，导致子脚本 Start-Process 抛
    # "已添加项。字典中的关键字" 而整个发布失败。清理当前进程，子进程继承已清理环境。
    foreach ($name in @('HTTPS_PROXY', 'https_proxy', 'HTTP_PROXY', 'http_proxy', 'ALL_PROXY', 'all_proxy')) {
        Remove-Item -Path ("Env:" + $name) -ErrorAction SilentlyContinue
    }
}
Clear-ProxyEnvDuplicates

function Test-Admin {
    $principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Write-Step([string]$msg) { Write-Host "`n==== $msg ====" -ForegroundColor Cyan; Add-Utf8Line -Path $logFile -Line ("==== $msg ====") }
function Write-Info([string]$msg) { Write-Host "   - $msg";                       Add-Utf8Line -Path $logFile -Line ("   - $msg") }
function Write-Ok([string]$msg)   { Write-Host "   [OK] $msg" -ForegroundColor Green; Add-Utf8Line -Path $logFile -Line ("   [OK] $msg") }
function Write-Warn([string]$msg) { Write-Host "   [WARN] $msg" -ForegroundColor Yellow; Add-Utf8Line -Path $logFile -Line ("   [WARN] $msg") }
function Fail([string]$msg)       { Write-Host "   [FAIL] $msg" -ForegroundColor Red; Add-Utf8Line -Path $logFile -Line ("   [FAIL] $msg"); throw $msg }

function Invoke-Capture {
    <# 以独立 powershell.exe 子进程调用子脚本，输出重定向到独立 UTF-8 日志后读回。
       避免子脚本内 exit 终止当前宿主，也避免原生 stderr 变成 ErrorRecord 抛错。 #>
    param(
        [Parameter(Mandatory = $true)][string]$Script,
        [string]$Arguments = ''
    )
    $full = Join-Path $scriptsDir $Script
    if (-not (Test-Path -LiteralPath $full)) { throw "缺少子脚本: $full" }
    $childLog = Join-Path $logDir ("release-child-$stamp-" + [guid]::NewGuid().ToString('N').Substring(0, 6) + '.log')
    $cmd = 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "' + $full + '" ' + $Arguments + ' >> "' + $childLog + '" 2>&1'
    & cmd.exe /c $cmd
    $code = $LASTEXITCODE
    $bytes = [IO.File]::ReadAllBytes($childLog)
    $utf8Text = [Text.Encoding]::UTF8.GetString($bytes)
    # 部分子脚本（db-migrate / parity / backup）未调用 Enable-Utf8Console，输出经 cmd
    # 重定向落盘是 GBK；UTF-8 解码会出现替换字符 U+FFFD，此时改按 936 解码，
    # 保证中文关键词（迁移状态一致 / 全部校验通过）可被可靠匹配。
    if ($utf8Text.Contains([char]0xFFFD)) {
        $childText = [Text.Encoding]::GetEncoding(936).GetString($bytes)
    } else {
        $childText = $utf8Text
    }
    Add-Utf8Text -Path $logFile -Text ("--- $Script $Arguments (exit=$code) ---`r`n" + $childText + "`r`n")
    Remove-Item -LiteralPath $childLog -Force -ErrorAction SilentlyContinue
    return [pscustomobject]@{ Output = [string]$childText; ExitCode = $code }
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$Desc,
        [Parameter(Mandatory = $true)][string]$Script,
        [string]$Arguments = '',
        [int]$TailLines = 25
    )
    Write-Step $Desc
    $r = Invoke-Capture -Script $Script -Arguments $Arguments
    $lines = @($r.Output -split "`r?`n" | Where-Object { $_.Trim() -ne '' })
    if ($TailLines -gt 0 -and $lines.Count -gt $TailLines) { $lines = $lines | Select-Object -Last $TailLines }
    foreach ($l in $lines) { Write-Host "   | $l" }
    if ($r.ExitCode -ne 0) { Fail "$Desc 失败（exit=$($r.ExitCode)），详见 $logFile" }
    Write-Ok "$Desc 完成"
    # 不返回对象：调用处未接收时避免把 pscustomobject 打印到控制台
}

function Get-Health {
    $r = Get-DeploymentHttpResponse -Url 'http://127.0.0.1:8080/health' -TimeoutSec 10
    if ($r.StatusCode -ne 200) { throw "8080 /health 返回 $($r.StatusCode)" }
    return ($r.Content | ConvertFrom-Json)
}

function Get-NginxRoot {
    $text = [IO.File]::ReadAllText($NginxConf)
    $m = [regex]::Match($text, '(?m)^\s*root\s+([^;]+);')
    if (-not $m.Success) { throw "Nginx 配置未找到唯一 root: $NginxConf" }
    return ($m.Groups[1].Value.Trim().Trim('"')).Replace('/', '\')
}

function Get-SiteIndex {
    return (Get-DeploymentHttpResponse -Url 'http://127.0.0.1/' -TimeoutSec 10)
}

function Get-FileSha([string]$path) { return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }

function Save-State($state) {
    $json = ($state | ConvertTo-Json -Depth 6)
    Write-Utf8Text -Path $stateFile -Text $json
    Write-Utf8Text -Path (Join-Path $logDir ("release-state-$stamp.json")) -Text $json
}

function Get-PackageVersion {
    $pkg = Read-Utf8Text (Join-Path $root 'frontend\package.json')
    $m = [regex]::Match($pkg, '"version"\s*:\s*"([^"]+)"')
    if (-not $m.Success) { throw 'frontend/package.json 未找到 version 字段' }
    return $m.Groups[1].Value.Trim()
}

# ---------- 预检（只读；仅在 logs/ 写检查日志） ----------
function Invoke-Preflight {
    Write-Step '预检 0/6 环境与版本真源'
    Write-Info "仓库根        : $root"
    Write-Info "PS 版本       : $($PSVersionTable.PSVersion)"
    Write-Info "管理员        : $(Test-Admin)"
    Write-Info "模式          : $Mode"
    $currentRev = Get-ProjectRevision
    $pkgVer = Get-PackageVersion
    $targetVer = if ($Version) { $Version } else { $currentRev }
    Write-Info "version.txt   : $currentRev"
    Write-Info "package.json  : $pkgVer"
    Write-Info "目标版本      : $targetVer"
    if ($currentRev -ne $pkgVer) { Fail "版本真源不一致：version.txt=$currentRev，package.json=$pkgVer" }
    if ($Version -and $Version -ne $currentRev) {
        Write-Warn "Publish 时将自动同步 version.txt 与 package.json 到 $Version（备份到 logs/version-backup-$stamp）"
    }

    Write-Step '预检 1/6 git 工作区（仅警告，不自动提交）'
    try {
        $gitLines = @(& git -C $root status --short 2>$null)
        Write-Info "未提交/未跟踪条目: $($gitLines.Count)"
        $gitLines | Select-Object -First 8 | ForEach-Object { Write-Info "  $_" }
        if ($gitLines.Count -gt 8) { Write-Info "  ...（其余 $($gitLines.Count - 8) 条略）" }
        Write-Warn '发布使用工作区当前文件；请确认以上改动均为本次拟发布内容，且不覆盖他人未提交改动'
    } catch { Write-Warn "git status 读取失败：$($_.Exception.Message)" }

    Write-Step '预检 2/6 数据库迁移状态（只读）'
    $mig = Invoke-Capture -Script 'db-migrate.ps1' -Arguments '-Mode Status'
    if ($mig.Output -match 'PENDING' -or $mig.Output -match 'DRIFT') { Fail "存在待执行或漂移的迁移，先处理再发布：`n$($mig.Output)" }
    if ($mig.Output -notmatch '迁移状态一致' -and $mig.Output -notmatch '待执行:\s*0') { Fail "无法确认迁移状态一致（输出可能被编码干扰）：`n$($mig.Output)" }
    Write-Ok '迁移状态一致（无待执行 / 无漂移）'

    Write-Step '预检 3/6 init.sql / migration / 生产库 一致性（只读）'
    $parity = Invoke-Capture -Script 'check-schema-parity.ps1'
    if ($parity.Output -notmatch '全部校验通过') { Fail "schema parity 未通过：`n$($parity.Output)" }
    Write-Ok 'schema parity 全部校验通过'

    Write-Step '预检 4/6 当前运行时（后端 / Nginx / 首页）'
    $health = Get-Health
    $oldBackendVersion = [string]$health.data.version
    $oldRoot = Get-NginxRoot
    $site = Get-SiteIndex
    if ($site.StatusCode -ne 200) { Fail "站点首页返回 $($site.StatusCode)" }
    Write-Info "后端 /health   : status=$($health.data.status) version=$oldBackendVersion gitShort=$($health.data.gitShort)"
    Write-Info "Nginx root     : $oldRoot"
    Write-Info "首页 HTTP      : $($site.StatusCode)"
    if ($health.data.status -ne 'UP') { Fail '当前后端不健康，中止发布' }

    Write-Step '预检 5/6 ReleaseId 冲突'
    $rid = if ($ReleaseId) { $ReleaseId } else { "$targetVer-$Slug" }
    $targetRoot = Join-Path $releasesDir $rid
    if (Test-Path -LiteralPath $targetRoot) { Fail "前端快照已存在，禁止重复发布：$targetRoot（请换 -Slug 或先回滚）" }
    if (Test-Path -LiteralPath ($targetRoot + '.rollback')) { Fail "回滚快照已存在：$targetRoot.rollback" }
    Write-Ok "ReleaseId 可用: $rid"

    Write-Step '预检 6/6 执行计划'
    Write-Info "1. backup.ps1 -Mode Backup -Keep $KeepVersions$(if ($SkipBackup) { '（已跳过）' })"
    Write-Info '2. build-all.ps1（前端 npm run build + 后端隔离目录 mvn package）'
    Write-Info "3. release-backend.ps1 -JarPath <builds/$targetVer-build-*/expert-system.jar> -Version $targetVer"
    Write-Info "4. deploy-frontend-graceful.ps1 -StagedDist <stage/dist> -ReleaseId $rid -Apply"
    Write-Info '5. 发布后验证 + 写 logs/release-state.json'
    Write-Info "6. 回滚依据：frontend/release-metadata/$rid.nginx-before.txt / backend/releases/<旧版本>-*/previous-expert-system.jar"

    return @{
        Version           = $targetVer
        ReleaseId         = $rid
        TargetRoot        = $targetRoot
        OldRoot           = $oldRoot
        OldBackendVersion = $oldBackendVersion
        NginxBefore       = Join-Path $metadataDir ($rid + '.nginx-before.txt')
    }
}

# ---------- 版本文件同步 ----------
function Sync-VersionFiles([string]$target) {
    $current = Get-ProjectRevision
    if ($current -eq $target -and (Get-PackageVersion) -eq $target) { Write-Ok "版本文件已是 $target"; return }
    if ($SkipVersionBump) { Fail "版本文件与 $target 不一致，且指定 -SkipVersionBump" }
    $backupDir = Join-Path $logDir "version-backup-$stamp"
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $root 'version.txt') -Destination (Join-Path $backupDir 'version.txt') -Force
    Copy-Item -LiteralPath (Join-Path $root 'frontend\package.json') -Destination (Join-Path $backupDir 'package.json') -Force
    Write-Utf8Text -Path (Join-Path $root 'version.txt') -Text ("revision=$target`r`n")
    $pkgPath = Join-Path $root 'frontend\package.json'
    $pkgText = [IO.File]::ReadAllText($pkgPath, [Text.Encoding]::UTF8)
    $rx = New-Object System.Text.RegularExpressions.Regex('("version"\s*:\s*")[^"]+(")')
    $pkgNew = $rx.Replace($pkgText, '${1}' + $target + '${2}', 1)
    if ($pkgNew -eq $pkgText) { Fail 'package.json 版本替换未生效' }
    Write-Utf8Text -Path $pkgPath -Text $pkgNew
    Write-Ok "版本文件已同步到 $target（原文件备份: $backupDir）"
    Write-Warn '版本文件属工作区改动，发布后请自行按主题提交（本脚本不执行任何 git 操作）'
}

# ---------- 制品校验 ----------
function Test-BuildArtifacts([string]$stageDir, [string]$target) {
    $jar = Join-Path $stageDir 'expert-system.jar'
    $dist = Join-Path $stageDir 'dist'
    $info = Join-Path $stageDir 'build-info.txt'
    foreach ($p in @($jar, (Join-Path $dist 'index.html'), (Join-Path $dist 'assets'), $info)) {
        if (-not (Test-Path -LiteralPath $p)) { Fail "构建产物缺失: $p" }
    }
    $infoText = Read-Utf8Text $info
    $builtVer = ([regex]::Match($infoText, '(?m)^version=(.+)$')).Groups[1].Value.Trim()
    if ($builtVer -ne $target) { Fail "build-info.txt 版本不一致: $builtVer != $target" }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $names = @($zip.Entries | ForEach-Object { $_.FullName })
        if (-not ($names -contains 'BOOT-INF/')) {
            if (-not ($names | Where-Object { $_ -like 'BOOT-INF/*' } | Select-Object -First 1)) { Fail 'jar 不是可启动 Spring Boot 包' }
        }
        $bi = $zip.Entries | Where-Object { $_.FullName -like '*META-INF/build-info.properties' } | Select-Object -First 1
        if (-not $bi) { Fail 'jar 缺少 build-info.properties' }
        $sr = New-Object IO.StreamReader($bi.Open())
        try { $biText = $sr.ReadToEnd() } finally { $sr.Dispose() }
        $jarVer = ([regex]::Match($biText, '(?m)^build\.version=(.+)$')).Groups[1].Value.Trim()
        if ($jarVer -ne $target) { Fail "jar build.version=$jarVer != $target" }
        foreach ($cls in $ExpectJarClass) {
            if (-not ($names -contains $cls)) { Fail "jar 缺少期望的类: $cls（本次功能可能未进入制品）" }
            Write-Ok "jar 含 $cls"
        }
    } finally { $zip.Dispose() }
    return @{ Jar = $jar; Dist = $dist }
}

# ---------- 发布后验证 ----------
function Invoke-PostVerify([string]$target, [string]$rid, [string]$targetRoot) {
    Write-Step '发布后验证 1/5 后端 /health 版本'
    $deadline = (Get-Date).AddSeconds(90)
    $health = $null
    do {
        try {
            $h = Get-Health
            if ($h.data.status -eq 'UP' -and [string]$h.data.version -eq $target) { $health = $h; break }
        } catch { }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    if (-not $health) { Fail "8080 /health 未在 90s 内返回 UP 且 version=$target" }
    Write-Ok "后端 version=$($health.data.version) gitShort=$($health.data.gitShort) buildTime=$($health.data.buildTime)"

    Write-Step '发布后验证 2/5 经 Nginx 的关键 API 存活'
    $api = Get-DeploymentHttpResponse -Url 'http://127.0.0.1/api/health' -TimeoutSec 10
    if ($api.StatusCode -ne 200) { Fail "经 Nginx /api/health 返回 $($api.StatusCode)" }
    $probe = Get-DeploymentHttpResponse -Url 'http://127.0.0.1/api/expert/list' -TimeoutSec 10
    if ($probe.StatusCode -notin @(200, 401, 403)) { Fail "业务探测 /api/expert/list 返回 $($probe.StatusCode)" }
    Write-Ok "/api/health=200，/api/expert/list=$($probe.StatusCode)（401/403 视为鉴权拦截=服务在线）"

    Write-Step '发布后验证 3/5 Nginx root 指针'
    $liveRoot = Get-NginxRoot
    if ($liveRoot.TrimEnd('\') -ne ([IO.Path]::GetFullPath($targetRoot)).TrimEnd('\')) {
        Fail "Nginx root 未指向新快照：`n  实际: $liveRoot`n  期望: $targetRoot"
    }
    Write-Ok "Nginx root = $liveRoot"

    Write-Step '发布后验证 4/5 站点首页与 index hash（连续 3 次）'
    $expectedHash = Get-FileSha (Join-Path $targetRoot 'index.html')
    for ($i = 1; $i -le 3; $i++) {
        $site = Get-SiteIndex
        if ($site.StatusCode -ne 200) { Fail "第 $i 次首页请求返回 $($site.StatusCode)" }
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $hash = ([BitConverter]::ToString($sha.ComputeHash($site.ContentBytes))).Replace('-', '') } finally { $sha.Dispose() }
        if ($hash -ne $expectedHash) { Fail "第 $i 次首页内容不是新快照（index hash 不匹配）" }
    }
    Write-Ok '首页 200×3，且响应内容 hash 与新快照 index.html 一致'

    Write-Step '发布后验证 5/5 主 bundle 携带目标版本'
    $idx = Read-Utf8Text (Join-Path $targetRoot 'index.html')
    $m = [regex]::Match($idx, 'src="/assets/([^"]+\.js)"')
    if (-not $m.Success) { Fail 'index.html 未找到主 bundle 引用' }
    $bundlePath = Join-Path (Join-Path $targetRoot 'assets') $m.Groups[1].Value
    if (-not (Test-Path -LiteralPath $bundlePath)) { Fail "主 bundle 不存在: $bundlePath" }
    $bundle = Read-Utf8Text $bundlePath
    if ($bundle -notmatch [regex]::Escape("v$target")) { Fail "主 bundle 未包含 v$target（前端构建可能读到了旧 version.txt）" }
    $servedBundle = Get-DeploymentHttpResponse -Url ('http://127.0.0.1/assets/' + [uri]::EscapeDataString($m.Groups[1].Value))
    if ($servedBundle.StatusCode -ne 200) { Fail "线上主 bundle 返回 $($servedBundle.StatusCode)" }
    $bundleSha = [Security.Cryptography.SHA256]::Create()
    try { $servedHash = ([BitConverter]::ToString($bundleSha.ComputeHash($servedBundle.ContentBytes))).Replace('-', '') }
    finally { $bundleSha.Dispose() }
    if ($servedHash -ne (Get-FileSha $bundlePath)) { Fail '线上主 bundle 与快照文件不一致' }
    Write-Ok "线上主 bundle $($m.Groups[1].Value) 含 v$target 且哈希一致"
}

# ==================== 主流程 ====================
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host ' jshz-PMO 全栈一键发布编排（release-full.ps1）' -ForegroundColor Cyan
Write-Host " 模式=$Mode  日志=$logFile" -ForegroundColor Cyan
Write-Host '============================================================' -ForegroundColor Cyan
Add-Utf8Line -Path $logFile -Line "release-full start: mode=$Mode stamp=$stamp"

try {
    if ($Mode -eq 'Preflight') {
        $p = Invoke-Preflight
        Write-Host "`n[预检通过] 未修改任何生产配置/服务/代码。确认无误后执行 Publish（管理员 + -Yes）。" -ForegroundColor Green
        exit 0
    }

    if ($Mode -eq 'Publish') {
        if (-not $Yes) {
            Write-Host "`n[仅计划] Publish 需要显式 -Yes 批准（正式环境发布）。当前未做任何变更。" -ForegroundColor Yellow
            $p = Invoke-Preflight
            Write-Host "`n批准后的命令：" -ForegroundColor Yellow
            Write-Host "  powershell -NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Mode Publish -Version $($p.Version) -Slug $Slug -Yes" -ForegroundColor Yellow
            exit 0
        }
        if (-not (Test-Admin)) { Fail 'Publish 必须以管理员身份运行（蓝绿发布需操作 NSSM 服务与 Nginx 配置）' }

        $p = Invoke-Preflight
        $target = $p.Version
        $rid = $p.ReleaseId
        $state = [ordered]@{
            mode = 'Publish'; version = $target; releaseId = $rid; slug = $Slug
            status = 'RUNNING'; step = 'preflight'; started = $startIso
            oldRoot = $p.OldRoot; oldBackendVersion = $p.OldBackendVersion
            targetRoot = $p.TargetRoot; nginxBefore = $p.NginxBefore
            stageDir = ''; backupDir = ''; jar = ''; logFile = $logFile
        }
        Save-State $state

        Write-Step '发布 1/6 版本文件同步'
        Sync-VersionFiles $target
        $state.step = 'version-sync'; Save-State $state

        Write-Step '发布 2/6 全量备份'
        $backupDir = ''
        if ($SkipBackup) {
            Write-Warn '已跳过 backup.ps1（-SkipBackup）'
        } else {
            $backupStart = Get-Date
            Invoke-Checked -Desc 'backup.ps1 全量备份' -Script 'backup.ps1' -Arguments ("-Mode Backup -Keep $KeepVersions") -TailLines 12
            $backupDir = Get-ChildItem -Path (Join-Path $root 'backup') -Directory -Filter 'backup_*' -ErrorAction SilentlyContinue |
                Where-Object { $_.CreationTime -ge $backupStart.AddSeconds(-2) } |
                Sort-Object Name -Descending | Select-Object -First 1
            if (-not $backupDir) { Fail '备份完成但未找到新建的 backup_* 目录' }
            $backupDir = $backupDir.FullName
            Write-Ok "备份目录: $backupDir"
        }
        $state.backupDir = [string]$backupDir; $state.step = 'backup'; Save-State $state

        Write-Step '发布 3/6 隔离目录全量构建'
        $buildStart = Get-Date
        Invoke-Checked -Desc 'build-all.ps1（前端 + 后端）' -Script 'build-all.ps1' -TailLines 20
        $stage = Get-ChildItem -Path $buildsDir -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "$target-build-*" -and $_.CreationTime -ge $buildStart.AddSeconds(-2) } |
            Sort-Object CreationTime -Descending | Select-Object -First 1
        if (-not $stage) { Fail "未定位到本次构建目录（$target-build-*）" }
        $art = Test-BuildArtifacts -StageDir $stage.FullName -Target $target
        Write-Ok "构建目录: $($stage.FullName)"
        $state.stageDir = $stage.FullName; $state.jar = $art.Jar; $state.step = 'build'; Save-State $state

        Write-Step '发布 4/6 后端蓝绿发布'
        Invoke-Checked -Desc 'release-backend.ps1（蓝绿不停机）' -Script 'release-backend.ps1' `
            -Arguments ('-JarPath "' + $art.Jar + '" -Version ' + $target + ' -KeepVersions ' + $KeepVersions) -TailLines 30
        $state.step = 'backend'; Save-State $state

        Write-Step '发布 5/6 前端 graceful 快照切换'
        Invoke-Checked -Desc 'deploy-frontend-graceful.ps1（root 指针切换）' -Script 'deploy-frontend-graceful.ps1' `
            -Arguments ('-StagedDist "' + $art.Dist + '" -ReleaseId ' + $rid + ' -Apply') -TailLines 20
        $state.step = 'frontend'; Save-State $state

        Write-Step '发布 6/6 发布后验证'
        Invoke-PostVerify -Target $target -Rid $rid -TargetRoot $p.TargetRoot
        $state.status = 'SUCCESS'; $state.step = 'verified'; $state.finished = (Get-Date).ToString('o')
        Save-State $state
        try {
            Invoke-Checked -Desc '前端快照与过期共享资源回收' -Script 'prune-frontend-releases.ps1' `
                -Arguments ('-KeepReleases 5 -KeepLegacyDist -NginxConf "' + $NginxConf + '"') -TailLines 12
        } catch {
            Write-Warn "发布已验证，前端清理待重试: $($_.Exception.Message)"
        }

        Write-Host "`n======== 发布完成：version=$target  ReleaseId=$rid ========" -ForegroundColor Green
        Write-Host "  日志      : $logFile" -ForegroundColor Green
        Write-Host "  状态文件  : $stateFile" -ForegroundColor Green
        Write-Host "  旧后端版本: $($p.OldBackendVersion)   旧前端 root: $($p.OldRoot)" -ForegroundColor Green
        Write-Host "  回滚命令  : powershell -NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Mode Rollback -Yes" -ForegroundColor Green
        Write-Host "  业务验收  : 编辑项目选项目类型 -> 会前财务资料上传阶段按类型填写表单 -> 保存回显" -ForegroundColor Green
        exit 0
    }

    if ($Mode -eq 'Rollback') {
        if (-not (Test-Path -LiteralPath $stateFile)) { Fail "缺少状态文件 $stateFile，请用 -ReleaseId 指定发布 ID" }
        $state = (Read-Utf8Text $stateFile) | ConvertFrom-Json
        $rid = if ($ReleaseId) { $ReleaseId } else { [string]$state.releaseId }
        if ($rid -notmatch '^[a-zA-Z0-9._-]+$' -or $rid -in @('.', '..')) { Fail '无法确定有效 ReleaseId' }
        $before = Join-Path $metadataDir ($rid + '.nginx-before.txt')
        if (-not (Test-Path -LiteralPath $before)) { Fail "缺少发布前 Nginx 配置备份: $before" }
        $frontend = Join-Path $root 'frontend'
        $managedRollback = Join-Path (Join-Path $frontend 'releases') "$rid.rollback"
        if (Test-Path -LiteralPath (Join-Path $managedRollback $FrontendAssetManifest)) {
            $before = Join-Path $metadataDir "$rid.rollback.conf.txt"
            $recorded = [regex]::Match((Read-Utf8Text $before), '(?m)^\s*root\s+([^;]+);')
            if (!$recorded.Success -or [IO.Path]::GetFullPath($recorded.Groups[1].Value.Trim().Trim('"')) -ne $managedRollback) {
                Fail "共享资源回滚配置与快照不一致: $before"
            }
        }

        Write-Step "回滚计划：ReleaseId=$rid"
        Write-Info "发布版本      : $($state.version)"
        Write-Info "旧前端 root   : $([regex]::Match((Read-Utf8Text $before), '(?m)^\s*root\s+([^;]+);').Groups[1].Value.Trim())"
        Write-Info "旧后端版本    : $($state.oldBackendVersion)"
        Write-Info "IncludeBackend: $IncludeBackend"
        if (-not $Yes) {
            Write-Host "`n[仅计划] Rollback 需要 -Yes 批准，当前未做任何变更。" -ForegroundColor Yellow
            exit 0
        }
        if (-not (Test-Admin)) { Fail 'Rollback 需管理员权限（改 Nginx 配置并 graceful reload）' }

        $frontendLock = Enter-FrontendDeploymentLock $frontend
        try {
        $beforeText = Read-Utf8Text $before
        $rm = [regex]::Match($beforeText, '(?m)^\s*root\s+([^;]+);')
        if (-not $rm.Success) { Fail "$before 内未找到 root 指令" }
        $oldRoot = ($rm.Groups[1].Value.Trim().Trim('"')).Replace('/', '\')
        if (-not (Test-Path -LiteralPath (Join-Path $oldRoot 'index.html'))) { Fail "旧快照目录不可用: $oldRoot" }
        $currentRoot = [regex]::Match((Read-Utf8Text $NginxConf), '(?m)^\s*root\s+([^;]+);')
        if (!$currentRoot.Success) { Fail '当前 Nginx 配置缺少 root' }
        Update-FrontendRollbackAssets $frontend ($currentRoot.Groups[1].Value.Trim().Trim('"')) $oldRoot

        $confBackup = Join-Path $logDir "nginx-conf-before-rollback-$stamp.txt"
        Copy-Item -LiteralPath $NginxConf -Destination $confBackup -Force
        Write-Ok "当前 Nginx 配置已备份: $confBackup"
        try {
        Write-Utf8Text -Path $NginxConf -Text $beforeText
        Invoke-Checked -Desc 'invoke-nginx-graceful.ps1（graceful reload）' -Script 'invoke-nginx-graceful.ps1' -TailLines 12

        $expectedHash = Get-FileSha (Join-Path $oldRoot 'index.html')
        $site = Get-SiteIndex
        if ($site.StatusCode -ne 200) { Fail "回滚后首页返回 $($site.StatusCode)" }
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $hash = ([BitConverter]::ToString($sha.ComputeHash($site.ContentBytes))).Replace('-', '') } finally { $sha.Dispose() }
        if ($hash -ne $expectedHash) { Fail '回滚后首页内容与旧快照不一致' }
        Write-Ok "前端已回滚到 $oldRoot，首页 200 且 index hash 一致"
        } catch {
            $rollbackError = $_
            try {
                Copy-Item -LiteralPath $confBackup -Destination $NginxConf -Force
                Invoke-Checked -Desc '恢复回滚前 Nginx 配置' -Script 'invoke-nginx-graceful.ps1' -TailLines 12
            } catch {
                Write-Warn "恢复回滚前配置失败: $($_.Exception.Message)"
            }
            throw $rollbackError
        }
        } finally {
            $frontendLock.Dispose()
        }

        if ($IncludeBackend) {
            $oldVer = [string]$state.oldBackendVersion
            $arch = Get-ChildItem -Path (Join-Path $root 'backend\releases') -Directory -Filter "$oldVer-*" -ErrorAction SilentlyContinue |
                Sort-Object CreationTime -Descending | Select-Object -First 1
            if (-not $arch) { Fail "未找到旧版本 $oldVer 的后端归档目录" }
            $oldJar = Join-Path $arch.FullName 'previous-expert-system.jar'
            if (-not (Test-Path -LiteralPath $oldJar)) { Fail "归档旧 jar 不存在: $oldJar" }
            Write-Warn "即将把后端二进制回退到 $oldVer（$oldJar）；数据库迁移为加法，旧包不读写新列，不回滚库"
            Invoke-Checked -Desc "后端回退到 $oldVer" -Script 'release-backend.ps1' `
                -Arguments ('-JarPath "' + $oldJar + '" -Version ' + $oldVer + ' -KeepVersions ' + $KeepVersions) -TailLines 25
            $h = Get-Health
            if ([string]$h.data.version -ne $oldVer) { Fail "后端回退后 /health version=$($h.data.version)，期望 $oldVer" }
            Write-Ok "后端已回退到 $oldVer"
        } else {
            Write-Info '后端未回滚（-IncludeBackend 未指定）。如需回退后端，追加 -IncludeBackend 重跑本命令。'
        }

        $rb = [ordered]@{
            mode = 'Rollback'; releaseId = $rid; version = [string]$state.version
            status = 'ROLLED_BACK'; started = $startIso; finished = (Get-Date).ToString('o')
            oldRoot = $oldRoot; oldBackendVersion = [string]$state.oldBackendVersion
            includeBackend = [bool]$IncludeBackend; logFile = $logFile
        }
        # 单独落盘，不覆盖 logs/release-state.json（保留发布成功记录供追溯）
        Write-Utf8Text -Path (Join-Path $logDir "rollback-state-$stamp.json") -Text ($rb | ConvertTo-Json -Depth 6)
        Write-Host "`n======== 回滚完成 ========" -ForegroundColor Green
        exit 0
    }
} catch {
    $msg = $_.Exception.Message
    Write-Host "`n[中止] $msg" -ForegroundColor Red
    Add-Utf8Line -Path $logFile -Line ("[ABORT] " + $msg)
    if ($Mode -eq 'Publish' -and $state) {
        try {
            $state.status = 'FAILED'; $state.error = $msg; $state.finished = (Get-Date).ToString('o')
            Save-State $state
        } catch { }
    }
    Write-Host "排查日志: $logFile" -ForegroundColor Yellow
    Write-Host '回滚指引: -Mode Rollback -Yes（前端）；后端失败时蓝绿脚本自带回滚，见 logs/ 与 docs/deployment.md' -ForegroundColor Yellow
    exit 1
}
