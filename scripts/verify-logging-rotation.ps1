<#
.SYNOPSIS
    日志轮转与 UTF-8 输出编码验证（第 5 项改造的落地校验）

.DESCRIPTION
    校验 NSSM 层与应用层的日志配置是否真正生效：
      1. 服务运行状态
      2. NSSM 轮转参数（AppRotateFiles/AppRotateOnline/AppRotateBytes）与 JVM 输出编码开关
      3. /health 五字段（version/buildTime/gitShort）
      4. out.log / err.log 体积与轮转产物
      5. 最新日志文件的编码是否为合法 UTF-8
    默认只读，不改任何配置。

    加 -Restart 时会先重启服务让参数生效（需管理员会话），再执行上述校验。

.PARAMETER ServiceName
    NSSM 服务名，默认 ExpertSystemBackend

.PARAMETER Port
    后端端口，默认 8080

.PARAMETER Restart
    先重启服务（需管理员），用于让 NSSM 参数与 -D 编码开关立即生效

.PARAMETER WaitSeconds
    重启后等待 /health 恢复的最长秒数，默认 90

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File D:\jshz-PMO\scripts\verify-logging-rotation.ps1 -Restart

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File D:\jshz-PMO\scripts\verify-logging-rotation.ps1
#>
[CmdletBinding()]
param(
    [string]$ServiceName = 'ExpertSystemBackend',
    [int]$Port = 8080,
    [switch]$Restart,
    [int]$WaitSeconds = 90
)

$ErrorActionPreference = 'Stop'
$script:FailCount = 0
$script:WarnCount = 0

function Write-Ok   ([string]$m) { Write-Host "[OK]   $m" -ForegroundColor Green }
function Write-Warn2([string]$m) { Write-Host "[WARN] $m" -ForegroundColor Yellow; $script:WarnCount++ }
function Write-Bad  ([string]$m) { Write-Host "[FAIL] $m" -ForegroundColor Red; $script:FailCount++ }
function Write-Step ([string]$m) { Write-Host ""; Write-Host "== $m ==" -ForegroundColor Cyan }

function Test-Elevated {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    $pr = New-Object Security.Principal.WindowsPrincipal($id)
    return $pr.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

# 直接用 WebClient 并置空代理：本机有 HTTPS_PROXY 时 Invoke-WebRequest 会把 127.0.0.1 也送去代理
function Get-HealthJson([int]$p) {
    $wc = New-Object System.Net.WebClient
    try {
        $wc.Proxy = $null
        $raw = $wc.DownloadString("http://127.0.0.1:$p/health")
        return ($raw | ConvertFrom-Json)
    } finally {
        $wc.Dispose()
    }
}

# 日志正被 NSSM 持续写入，必须用 FileShare.ReadWrite 打开，否则报"文件正由另一进程使用"
function Read-FileShared([string]$Path) {
    $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    $ms = New-Object System.IO.MemoryStream
    try {
        $fs.CopyTo($ms)
        return $ms.ToArray()
    } finally {
        $ms.Dispose()
        $fs.Dispose()
    }
}

function Test-Utf8File([string]$Path) {
    $bytes = Read-FileShared $Path
    if ($bytes.Length -eq 0) {
        return [pscustomobject]@{ Empty = $true; HasBom = $false; HasNonAscii = $false; IsUtf8 = $true }
    }
    $hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
    $hasNonAscii = $false
    foreach ($b in $bytes) { if ($b -gt 0x7F) { $hasNonAscii = $true; break } }
    $strict = New-Object System.Text.UTF8Encoding($false, $true)
    try { [void]$strict.GetString($bytes); $isUtf8 = $true } catch { $isUtf8 = $false }
    return [pscustomobject]@{ Empty = $false; HasBom = $hasBom; HasNonAscii = $hasNonAscii; IsUtf8 = $isUtf8 }
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " 日志轮转与 UTF-8 编码验证  service=$ServiceName  port=$Port" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# ---------- 0. 可选：重启让参数生效 ----------
if ($Restart) {
    Write-Step '0. 重启服务（使 NSSM 参数与 -D 编码开关生效）'
    if (-not (Test-Elevated)) {
        Write-Bad '当前会话不是管理员，无法重启服务。请右键以管理员身份打开 PowerShell 后重试，或去掉 -Restart 只做只读校验。'
        exit 2
    }
    try {
        Restart-Service -Name $ServiceName -Force -ErrorAction Stop
        Write-Ok "已下发重启命令：$ServiceName"
    } catch {
        Write-Bad "重启失败：$($_.Exception.Message)"
        exit 2
    }

    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    $recovered = $false
    while ((Get-Date) -lt $deadline) {
        try {
            $h = Get-HealthJson $Port
            if ($h.data -and $h.data.status -eq 'UP') { $recovered = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 800
    }
    if ($recovered) {
        Write-Ok "/health 已恢复（status=UP）"
    } else {
        Write-Bad "等待 ${WaitSeconds}s 后 /health 仍未恢复，请检查 logs\expert-backend.err.log"
    }
}

# ---------- 1. 服务状态 ----------
Write-Step '1. 服务状态'
$svc = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($null -eq $svc) {
    Write-Bad "服务不存在：$ServiceName"
} elseif ($svc.Status -eq 'Running') {
    Write-Ok "服务运行中：$ServiceName"
} else {
    Write-Bad "服务状态异常：$($svc.Status)"
}

# ---------- 2. NSSM 参数 ----------
Write-Step '2. NSSM 轮转参数与 JVM 输出编码开关'
$paramsKey = "SYSTEM\CurrentControlSet\Services\$ServiceName\Parameters"
try {
    $rk = [Microsoft.Win32.Registry]::LocalMachine.OpenSubKey($paramsKey)

    $rotateFiles  = $rk.GetValue('AppRotateFiles')
    $rotateOnline = $rk.GetValue('AppRotateOnline')
    $rotateBytes  = $rk.GetValue('AppRotateBytes')
    $appParams    = [string]$rk.GetValue('AppParameters')
    $appStdout    = [string]$rk.GetValue('AppStdout')
    $appStderr    = [string]$rk.GetValue('AppStderr')
    $rk.Close()

    if ($rotateFiles -eq 1) { Write-Ok 'AppRotateFiles=1（轮转已启用）' } else { Write-Bad "AppRotateFiles=$rotateFiles（应为 1）" }
    if ($rotateOnline -eq 1) { Write-Ok 'AppRotateOnline=1（在线轮转，无需重启进程）' } else { Write-Warn2 "AppRotateOnline=$rotateOnline（为 0 时轮转需重启进程才发生）" }
    if ($rotateBytes -eq 10485760) { Write-Ok 'AppRotateBytes=10485760（10MB/文件）' } else { Write-Warn2 "AppRotateBytes=$rotateBytes（脚本期望 10485760）" }

    if ([string]::IsNullOrWhiteSpace($appStdout)) {
        Write-Warn2 'AppStdout 未配置，NSSM 不写 stdout 重定向文件，轮转参数无实际作用对象'
    } else {
        Write-Ok "AppStdout=$appStdout"
    }
    if ([string]::IsNullOrWhiteSpace($appStderr)) {
        Write-Warn2 'AppStderr 未配置'
    } else {
        Write-Ok "AppStderr=$appStderr"
    }

    if ($appParams -match '-Dsun\.stdout\.encoding=UTF-8') { Write-Ok 'AppParameters 含 -Dsun.stdout.encoding=UTF-8' } else { Write-Bad 'AppParameters 缺少 -Dsun.stdout.encoding=UTF-8（重定向文件里的中文会变成 ?）' }
    if ($appParams -match '-Dsun\.stderr\.encoding=UTF-8') { Write-Ok 'AppParameters 含 -Dsun.stderr.encoding=UTF-8' } else { Write-Bad 'AppParameters 缺少 -Dsun.stderr.encoding=UTF-8' }
    if ($appParams -match '-jar\s') { Write-Ok "启动命令：$appParams" } else { Write-Bad "AppParameters 未包含 -jar：$appParams" }
} catch {
    Write-Bad "读取注册表失败：$($_.Exception.Message)"
}

# ---------- 3. /health ----------
Write-Step '3. /health 五字段'
try {
    $h = Get-HealthJson $Port
    $d = $h.data
    if ($null -eq $d) {
        Write-Bad '/health 返回结构中缺少 data'
    } else {
        if ($d.status -eq 'UP') { Write-Ok "status=$($d.status)" } else { Write-Bad "status=$($d.status)" }
        foreach ($f in 'version', 'buildTime', 'gitShort') {
            $v = $d.$f
            if ([string]::IsNullOrWhiteSpace([string]$v)) {
                Write-Bad "$f 缺失（当前运行包可能早于 build-info 改造，需发布含新 HealthController 的包）"
            }
            elseif ([string]$v -eq 'unknown') {
                Write-Warn2 "$f=unknown（占位符未替换，请检查构建是否传了 -Drevision/-DgitShort）"
            }
            else { Write-Ok "$f=$v" }
        }
    }
} catch {
    Write-Bad "请求 /health 失败：$($_.Exception.Message)"
}

# ---------- 4. 日志文件与轮转产物 ----------
Write-Step '4. 日志文件与轮转产物'
$logDir = 'D:\jshz-PMO\logs'
$targets = @(
    @{ Name = 'expert-backend.out.log'; Desc = 'NSSM stdout 重定向' },
    @{ Name = 'expert-backend.err.log'; Desc = 'NSSM stderr 重定向' },
    @{ Name = 'expert-app.log';         Desc = 'logback 应用日志（需含 logback-spring.xml 的包）' }
)
foreach ($t in $targets) {
    $p = Join-Path $logDir $t.Name
    if (Test-Path $p) {
        $f = Get-Item $p
        Write-Ok ("{0} = {1} MB（{2}）最后写入 {3}" -f $t.Name, [math]::Round($f.Length / 1MB, 2), $t.Desc, $f.LastWriteTime)
    } else {
        if ($t.Name -eq 'expert-app.log') {
            Write-Warn2 "$($t.Name) 不存在：当前运行包早于 logback-spring.xml 改造，应用层日志需下次发布后才有"
        } else {
            Write-Bad "$($t.Name) 不存在（$($t.Desc)）"
        }
    }
}

# NSSM 的轮转命名：把 UTC 时间戳插在扩展名「之前」，形如 expert-backend.out-20261106T083210.791.log
# （08:32:10 UTC = 16:32:10 北京时间）。不要用 'x.log.*' 去匹配，那个模式既匹配不到也容易误伤基名。
$rotated = Get-ChildItem $logDir -File -ErrorAction SilentlyContinue |
           Where-Object { $_.Name -like 'expert-backend.out-*.log' } |
           Sort-Object LastWriteTime -Descending
if ($rotated) {
    Write-Ok "发现 $($rotated.Count) 个 out.log 轮转产物，最近一个：$($rotated[0].Name)（$([math]::Round($rotated[0].Length / 1MB, 2)) MB，UTC 时间戳需 +8h 换算）"
} else {
    Write-Warn2 '尚未出现 out.log 轮转产物：要么重启后新日志还没到 10MB，要么轮转未生效'
}

$rotatedErr = Get-ChildItem $logDir -File -ErrorAction SilentlyContinue |
              Where-Object { $_.Name -like 'expert-backend.err-*.log' }
if ($rotatedErr) { Write-Ok "发现 $($rotatedErr.Count) 个 err.log 轮转产物" }
else { Write-Warn2 '尚未出现 err.log 轮转产物' }

# ---------- 5. 编码判定 ----------
Write-Step '5. 日志编码判定'
foreach ($name in 'expert-backend.out.log', 'expert-backend.err.log', 'expert-app.log') {
    $p = Join-Path $logDir $name
    if (-not (Test-Path $p)) { continue }
    $r = Test-Utf8File $p
    if ($r.Empty) { Write-Warn2 "$name 为空文件，跳过编码判定"; continue }
    if ($r.HasBom) { Write-Warn2 "$name 带 UTF-8 BOM（日志文件不需要 BOM）" }
    if (-not $r.HasNonAscii) {
        Write-Warn2 "$name 全为 ASCII，无法区分 UTF-8 与 GBK（需等出现中文日志后再判定）"
    } elseif ($r.IsUtf8) {
        Write-Ok "$name 含中文且为合法 UTF-8"
    } else {
        Write-Bad "$name 含中文但不是合法 UTF-8（仍是 GBK，检查 -Dsun.stdout.encoding 是否随重启生效）"
    }
}

# ---------- 汇总 ----------
Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
if ($script:FailCount -eq 0) {
    Write-Host " 校验通过（$script:WarnCount 项提示）" -ForegroundColor Green
} else {
    Write-Host " 校验未通过：$script:FailCount 项失败，$script:WarnCount 项提示" -ForegroundColor Red
}
Write-Host "============================================================" -ForegroundColor Cyan
exit $(if ($script:FailCount -eq 0) { 0 } else { 1 })
