#requires -Version 5.1
<#
.SYNOPSIS
  后端发布统一入口（提权执行）：校验候选包版本 -> 注入凭据 -> 调用 deploy-backend.ps1 蓝绿发布。

.DESCRIPTION
  为什么需要这个包装脚本：
    1. 凭据一致性护栏：deploy-backend.ps1 阶段 1 会比对会话 MYSQL_PASSWORD / JWT_SECRET 与 NSSM
       服务环境，不一致直接中止。这里从本机 start-backend.bat（gitignore 明文源）读取并注入会话，
       保证与 NSSM 同值；不打印任何密码。
    2. 版本单一来源护栏：候选包的 build-info.version 必须等于 version.txt 的 revision。
       若有人用裸 mvn 构建（revision 落为 0.0.0-UNSET），此处直接拒绝发布。
    3. 日志统一 UTF-8：本脚本把控制台输出编码切到 UTF-8(无 BOM)，经 cmd 重定向落盘的日志即为
       UTF-8；每次发布独立日志文件，便于回溯。

  候选包默认为 builds\ 下最新的 expert-system.jar（构建产物统一存放约定）。

.PARAMETER JarPath
  候选包绝对路径；缺省取 builds\ 下最新的 expert-system.jar。

.PARAMETER Version
  发布版本号；缺省读 version.txt（须与候选包 build-info.version 一致）。

.PARAMETER DryRun
  仅预检与打印阶段动作，不改配置、不动服务、不启动进程。

.PARAMETER KeepVersions
  归档保留数量，默认 5。

.EXAMPLE
  .\scripts\release-backend.ps1 -DryRun
  .\scripts\release-backend.ps1
#>
[CmdletBinding()]
param(
    [string]$JarPath = '',
    [string]$Version = '',
    [switch]$DryRun,
    [int]$KeepVersions = 5
)

$ErrorActionPreference = 'Stop'
$root = 'D:\jshz-PMO'
. (Join-Path $PSScriptRoot 'logging.ps1')
. (Join-Path $PSScriptRoot 'build-paths.ps1')
Enable-Utf8Console

$logsDir = Join-Path $root 'logs'
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$logFile = Join-Path $logsDir "deploy-backend.$stamp.log"
$deployScript = Join-Path $PSScriptRoot 'deploy-backend.ps1'
$startBat = Join-Path $root 'start-backend.bat'
$serviceName = 'ExpertSystemBackend'

function Write-Both([string]$msg, [string]$color = 'Gray') {
    Write-Host $msg -ForegroundColor $color
    Add-Utf8Line -Path $logFile -Line $msg
}

Write-Host '============================================================' -ForegroundColor Cyan
Write-Host ' jshz-PMO 后端发布（蓝绿不停机）' -ForegroundColor Cyan
if ($DryRun) { Write-Host ' DRYRUN：仅预检与打印，不产生任何变更' -ForegroundColor Yellow }
Write-Host '============================================================' -ForegroundColor Cyan
Write-Both "日志文件: $logFile"

if (-not (Test-Path -LiteralPath $deployScript)) { throw "缺少发布脚本: $deployScript" }
if (-not (Test-Path -LiteralPath $startBat)) { throw "缺少凭据来源: $startBat" }

# ---------- 候选包定位 ----------
if (-not $JarPath) {
    # 只在 builds\ 的「当前构建目录」中找，绝不递归到 builds\archive（历史快照不可作为候选包）
    $jar = Get-ChildItem -Path (Get-BuildsRoot) -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne 'archive' } |
        ForEach-Object { Get-ChildItem -Path $_.FullName -Filter 'expert-system.jar' -File -ErrorAction SilentlyContinue } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar) { throw "builds\ 下未找到 expert-system.jar，请先执行 scripts\build-all.ps1 或用 -JarPath 指定" }
    $JarPath = $jar.FullName
}
$JarPath = (Resolve-Path -LiteralPath $JarPath).Path
Write-Both "候选包: $JarPath"

# ---------- 版本单一来源校验 ----------
$expectedVersion = if ($Version) { $Version } else { Get-ProjectRevision }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
try {
    # 注意打包后路径：Spring Boot repackage 把 META-INF 放到 jar 根（META-INF/build-info.properties），
    # 未打包时在 BOOT-INF/classes/META-INF 下，两种都要认。
    $entry = $zip.Entries | Where-Object { $_.FullName -like '*META-INF/build-info.properties' } | Select-Object -First 1
    if (-not $entry) { throw "候选包缺少 build-info.properties：$JarPath（请用 scripts\build-all.ps1 构建）" }
    $reader = New-Object System.IO.StreamReader($entry.Open())
    $biText = $reader.ReadToEnd()
    $reader.Dispose()
} finally { $zip.Dispose() }

$biVersion = ([regex]::Match($biText, '(?m)^build\.version=(.+)$')).Groups[1].Value.Trim()
$biGit = ([regex]::Match($biText, '(?m)^build\.gitShort=(.+)$')).Groups[1].Value.Trim()
$biTime = ([regex]::Match($biText, '(?m)^build\.time=(.+)$')).Groups[1].Value.Trim()
if ($biVersion -ne $expectedVersion) {
    throw "版本不一致：候选包 build.version=$biVersion，期望=$expectedVersion（version.txt / -Version）。请用 scripts\build-all.ps1 重新构建。"
}
Write-Both "[OK] 版本校验: version=$biVersion gitShort=$biGit buildTime=$biTime" Green

# ---------- 提权校验 ----------
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
$elevated = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $elevated -and -not $DryRun) {
    throw '必须以管理员身份运行（蓝绿发布需停止/启动 NSSM 服务并修改 Nginx 配置）'
}

# ---------- 凭据注入（不打印值） ----------
$batText = Get-Content -LiteralPath $startBat -Raw -Encoding Default
function Get-BatValue([string]$name) {
    $m = [regex]::Match($batText, "(?im)^[ \t]*set[ \t]+$name=(.*)$")
    if (-not $m.Success) { throw "start-backend.bat 缺少 $name" }
    $v = $m.Groups[1].Value.Trim()
    $quote = [char]34
    if ($v.Length -ge 2 -and $v[0] -eq $quote -and $v[$v.Length - 1] -eq $quote) { $v = $v.Substring(1, $v.Length - 2) }
    if ([string]::IsNullOrWhiteSpace($v)) { throw "start-backend.bat 中 $name 为空" }
    return $v
}
$env:MYSQL_PASSWORD = Get-BatValue 'MYSQL_PASSWORD'
$env:JWT_SECRET = Get-BatValue 'JWT_SECRET'
Write-Both '[OK] 凭据已从 start-backend.bat 注入会话（值不打印）' Green

# ---------- 调用发布脚本 ----------
$argLine = 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "' + $deployScript + '"' `
    + ' -JarPath "' + $JarPath + '"' `
    + ' -Version "' + $expectedVersion + '"' `
    + ' -GracefulSwitch -KeepVersions ' + $KeepVersions
if ($DryRun) { $argLine += ' -DryRun' }

Write-Both '[1/1] 调用 deploy-backend.ps1 ...'
# Start-Process -RedirectStandardOutput 在本机因重复 HTTPS_PROXY 键失败，改用 cmd 重定向；
# cmd 继承已注入的环境变量，且子进程自身已把输出编码切到 UTF-8，落盘即为 UTF-8。
& cmd.exe /c ($argLine + ' >> "' + $logFile + '" 2>&1')
$exitCode = $LASTEXITCODE

Write-Host ''
Write-Host '==================== 发布输出（末 60 行） ====================' -ForegroundColor Cyan
if (Test-Path -LiteralPath $logFile) { Get-Content -LiteralPath $logFile -Tail 60 }
Write-Host ''
Write-Both ("发布脚本退出码 = " + $exitCode) ($(if ($exitCode -eq 0) { 'Green' } else { 'Red' }))
exit $exitCode
