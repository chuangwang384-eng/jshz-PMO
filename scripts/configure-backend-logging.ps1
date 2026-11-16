#requires -Version 5.1
<#
.SYNOPSIS
  后端日志治理：NSSM 捕获日志轮转 + JVM 标准输出 UTF-8 编码（幂等）。

.DESCRIPTION
  背景（2026-11-06 验收发现）：
    1. logs\expert-backend.out.log 无轮转，单文件已增长到 11.9MB。
    2. 该文件由 NSSM 捕获 JVM 标准输出生成，编码跟随系统默认代码页（GBK），
       与 UTF-8 工具链不兼容。

  本脚本做两件事，均为幂等操作，可重复执行：
    a) 设置 NSSM 轮转：AppRotateFiles=1、AppRotateOnline=1、AppRotateBytes=10MB。
       （NSSM 轮转在服务启动时读取参数，下次重启后生效）
    b) 在 NSSM AppParameters 的 -jar 之前插入 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8，
       使 JVM 直接以 UTF-8 写出，NSSM 捕获到的即为 UTF-8 文本。

  安全约束：
    - 只改 AppParameters 与 AppRotate* 键，不读不写 AppEnvironmentExtra（凭据所在），不打印任何密钥。
    - 需管理员权限（写 HKLM）。非管理员调用直接失败，不静默跳过。
    - AppParameters 改动会保留全部原有参数，仅前置编码开关。

.PARAMETER ServiceName
  NSSM 服务名，默认 ExpertSystemBackend。

.PARAMETER NssmPath
  nssm.exe 路径，默认 D:\nssm\nssm.exe。

.PARAMETER RotationBytes
  单文件轮转阈值，默认 10485760（10MB）。

.PARAMETER Quiet
  只输出变更摘要，减少噪音（供发布脚本调用）。
#>
[CmdletBinding()]
param(
    [string]$ServiceName = 'ExpertSystemBackend',
    [string]$NssmPath = 'D:\nssm\nssm.exe',
    [int]$RotationBytes = 10485760,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'logging.ps1')
Enable-Utf8Console

function Write-Line([string]$msg, [string]$color = 'Gray') {
    if (-not $Quiet) { Write-Host $msg -ForegroundColor $color }
}

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "配置 NSSM 参数需要管理员权限：$ServiceName"
}

$paramsKey = "HKLM:\SYSTEM\CurrentControlSet\Services\$ServiceName\Parameters"
if (-not (Test-Path -LiteralPath $paramsKey)) {
    throw "未找到 NSSM 参数键: $paramsKey（服务名是否正确？）"
}

# ---------- a) 轮转参数 ----------
if (Test-Path -LiteralPath $NssmPath) {
    & $NssmPath set $ServiceName AppRotateFiles 1 | Out-Null
    & $NssmPath set $ServiceName AppRotateOnline 1 | Out-Null
    & $NssmPath set $ServiceName AppRotateBytes $RotationBytes | Out-Null
    Write-Line "  [OK] NSSM 轮转已启用：${RotationBytes} 字节/文件，在线轮转" Green
} else {
    Write-Line "  [WARN] 未找到 nssm.exe（$NssmPath），跳过轮转设置" Yellow
}

# ---------- b) JVM 输出编码 ----------
$currentParams = (Get-ItemProperty -Path $paramsKey).AppParameters
if (-not $currentParams) { throw 'AppParameters 为空，拒绝修改' }

$encodingFlags = '-Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8'
if ($currentParams -match 'sun\.stdout\.encoding') {
    Write-Line '  [OK] JVM 输出编码已为 UTF-8（无需重复设置）' Green
} else {
    # 仅在第一个 -jar 之前插入，保持原有参数顺序与内容不变
    $jarIndex = $currentParams.IndexOf('-jar')
    if ($jarIndex -lt 0) { throw "AppParameters 未包含 -jar，拒绝修改: $currentParams" }
    $newParams = $currentParams.Substring(0, $jarIndex) + $encodingFlags + ' ' + $currentParams.Substring($jarIndex)
    Set-ItemProperty -Path $paramsKey -Name AppParameters -Value $newParams
    Write-Line "  [OK] 已插入 JVM 输出编码开关（下次服务启动生效）" Green
    Write-Line "       修改前: $currentParams" DarkGray
    Write-Line "       修改后: $newParams" DarkGray
}

# ---------- 校验 ----------
$verify = (Get-ItemProperty -Path $paramsKey).AppParameters
if ($verify -notmatch 'sun\.stdout\.encoding=UTF-8') { throw '校验失败：AppParameters 未包含编码开关' }
$rotateFiles = (Get-ItemProperty -Path $paramsKey).AppRotateFiles
$rotateBytes = (Get-ItemProperty -Path $paramsKey).AppRotateBytes
Write-Line "  [OK] 校验通过：AppRotateFiles=$rotateFiles AppRotateBytes=$rotateBytes" Green
Write-Line '  说明：轮转与编码参数在服务下次重启后生效（发布流程阶段 9 会重启服务）' DarkGray
