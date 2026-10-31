#requires -Version 5.1
<#
.SYNOPSIS
  R-1 后端蓝绿不停机发布脚本（jshz-PMO 专家库系统）

.DESCRIPTION
  按 docs/2026-10-24-发布运维体系与UI偏移修复-design.md §4.1 实现 11 阶段蓝绿发布：
    1 预检 -> 2 影子启动8081 -> 3 健康+业务探测 -> 4 备份conf -> 5 改proxy_pass ->
    6 nginx -t -> 7 Restart-Service ExpertSystemNginx -> 8 蓝验证 -> 9 稳态化NSSM ->
    10 切回8080 -> 11 清理+归档
  Nginx 切流一律 Restart-Service ExpertSystemNginx（NSSM 约束，禁止 nginx -s reload）。
  阶段9 先停 NSSM 服务再替换 target/*.jar（释放进程锁，历史踩坑：不先停会替换失败）。
  支持 -DryRun（仅打印动作与检查点，不改配置/不动服务/不启动进程）与 -KeepVersions（默认 5）。

.PARAMETER JarPath
  新版本 jar 绝对路径；缺省自动取 backend\target 下最新 *.jar。

.PARAMETER Version
  发布版本号（用于归档目录命名）；缺省从项目根 version.txt 读取（revision 键）。

.PARAMETER DryRun
  仅打印各阶段将执行的动作与检查点，不实际修改配置、不动服务、不启动进程。

.PARAMETER KeepVersions
  版本保留数量，默认 5；超出按时间清理最旧归档目录（backend/releases/ 下）。

.EXAMPLE
  .\scripts\deploy-backend.ps1 -JarPath D:\jshz-PMO\backend\target\expert-system.jar
  .\scripts\deploy-backend.ps1 -DryRun
#>
[CmdletBinding()]
param(
    [string]$JarPath = '',
    [string]$Version = '',
    [switch]$DryRun,
    [int]$KeepVersions = 5
)

# ==================== 常量区（按环境调整） ====================
$BackendDir      = 'D:\jshz-PMO\backend'
$TargetJarName   = 'expert-system.jar'   # NSSM AppParameters 固定的正式 jar 名（替换方式保持此名）
$ReleasesDir     = 'D:\jshz-PMO\backend\releases'
$DeployDoc       = 'D:\jshz-PMO\docs\deployment.md'
$NginxConfFile   = 'D:\nginx\conf\conf.d\jshz-pmo.conf'
$NginxConfBackup = 'D:\nginx\conf\conf.d\backup'
$NginxExe        = 'D:\nginx\nginx.exe'
$NginxSvc        = 'ExpertSystemNginx'
$BackendSvc      = 'ExpertSystemBackend'
# 数据库密码由环境变量注入（与 start-backend.bat / NSSM AppEnvironmentExtra 保持一致），
# 禁止把真实密码写入本文件或任何入库文件。发布前须在会话中 setx/手动设置后再运行。
$MysqlPassword   = $env:MYSQL_PASSWORD
$GreenPort       = 8080                        # 生产（绿）
$BluePort        = 8081                        # 影子（蓝）
$ShadowRetries   = 10                          # 影子健康检查重试次数
$ShadowDelaySec  = 6                           # 影子健康检查重试间隔（秒）
$HealthOkRetries = 10                          # 稳态化后健康检查重试次数
$HealthOkDelay   = 3                           # 稳态化后健康检查重试间隔（秒）

# ==================== 工具函数 ====================
function Write-Step([string]$msg) { Write-Host "`n==== [阶段] $msg ====" -ForegroundColor Cyan }
function Write-Info([string]$msg) { Write-Host "   - $msg" }
function Write-Ok([string]$msg)   { Write-Host "   [OK] $msg" -ForegroundColor Green }
function Write-Fail([string]$msg) { Write-Host "   [FAIL] $msg" -ForegroundColor Red }

# DryRun 感知的动作执行：仅打印描述，不真正执行副作用
function Invoke-Action([string]$desc, [scriptblock]$action) {
    if ($DryRun) {
        Write-Host "   [DRYRUN][动作] $desc" -ForegroundColor Yellow
        return
    }
    Write-Host "   [执行] $desc" -ForegroundColor DarkGray
    & $action
}

# DryRun 感知的检查执行：仅打印检查点描述，不真正发请求/判定
function Invoke-Check([string]$desc, [scriptblock]$check) {
    if ($DryRun) {
        Write-Host "   [DRYRUN][检查] $desc" -ForegroundColor Yellow
        return $true
    }
    try {
        & $check
        Write-Ok $desc
        return $true
    } catch {
        Write-Fail "$desc -> $_"
        return $false
    }
}

function Test-HttpOk([string]$url, [int]$timeoutSec = 10) {
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec $timeoutSec
        return ($r.StatusCode -eq 200)
    } catch { return $false }
}

function Test-HttpOkOr401([string]$url, [int]$timeoutSec = 10) {
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec $timeoutSec
        return ($r.StatusCode -eq 200 -or $r.StatusCode -eq 401)
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        return ($code -eq 200 -or $code -eq 401)
    }
}
function Get-HttpBody([string]$url, [int]$timeoutSec = 10) {
    try { return (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec $timeoutSec).Content } catch { return '' }
}

function Wait-Healthy([string]$url, [int]$retries, [int]$delaySec, [string]$name) {
    for ($i = 1; $i -le $retries; $i++) {
        if (Test-HttpOk $url) {
            Write-Ok "$name 健康（$url）"
            return $true
        }
        Write-Info "$name 未就绪（${i}/${retries}），${delaySec}s 后重试..."
        Start-Sleep -Seconds $delaySec
    }
    return $false
}

# ==================== 阶段 1: 预检 ====================
function Step1-Preflight {
    Write-Step '1/11 预检（新 jar 存在 + 绿服务健康）'
    if (-not $JarPath) {
        $jar = Get-ChildItem (Join-Path $BackendDir 'target') -Filter '*.jar' -File |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if (-not $jar) { throw '未找到新 jar，请用 -JarPath 显式指定' }
        $script:NewJar = $jar.FullName
    } else {
        if (-not (Test-Path $JarPath)) { throw "新 jar 不存在: $JarPath" }
        $script:NewJar = $JarPath
    }
    Write-Info "新 jar: $script:NewJar"

    if (-not $MysqlPassword) {
        throw '未设置 MYSQL_PASSWORD 环境变量：影子启动(8081)与生产同库依赖该变量，请先注入后再发布'
    }
    if (-not $env:JWT_SECRET) {
        throw '未设置 JWT_SECRET 环境变量：新版 application.yml 要求注入 JWT 签名密钥（与 NSSM AppEnvironmentExtra 一致），否则影子启动会失败'
    }

    # 凭据一致性预检（长期免重登机制核心护栏）：
    # 会话注入的 MYSQL_PASSWORD / JWT_SECRET 必须与 NSSM 服务环境完全一致。
    # JWT_SECRET 属长期固定凭据——发布/重启不更换，已登录用户的 token 跨发布保持有效（免全员重登）；
    # 密钥变更只能作为独立的轮换操作（rotate-backend-credentials.ps1）并在非发布时段执行。
    # 本检查直接执行（不随 DryRun 跳过），注册表读取为只读操作。
    $nssmParamsKey = 'HKLM:\SYSTEM\CurrentControlSet\Services\ExpertSystemBackend\Parameters'
    $svcEnv = (Get-ItemProperty -Path $nssmParamsKey -ErrorAction SilentlyContinue).AppEnvironmentExtra
    if ($svcEnv) {
        $envJoined = $svcEnv -join "`n"
        $nssmDb  = [regex]::Match($envJoined, '(?m)^MYSQL_PASSWORD=(.+)$').Groups[1].Value.Trim()
        $nssmJwt = [regex]::Match($envJoined, '(?m)^JWT_SECRET=(.+)$').Groups[1].Value.Trim()
        if ($nssmDb -and ($nssmDb -ne $MysqlPassword)) {
            throw 'MYSQL_PASSWORD 与 NSSM 服务环境不一致：请以 start-backend.bat（=NSSM）当前值为准注入会话后再发布，防止影子实例连接异常'
        }
        if ($nssmJwt -and ($nssmJwt -ne $env:JWT_SECRET)) {
            throw 'JWT_SECRET 与 NSSM 服务环境不一致：JWT 密钥为长期固定凭据（保证发布/重启免全员重登），禁止在发布时更换；确需轮换请单独执行 rotate-backend-credentials.ps1 并提前知会全员重新登录'
        }
        Write-Ok '凭据一致性: 会话环境与 NSSM 服务一致（JWT 密钥未变更，发布后用户无需重新登录）'
    } else {
        Write-Info '未读取到 NSSM AppEnvironmentExtra，跳过一致性比对（首次部署场景）'
    }

    if ($Version) { $script:NewVersion = $Version }
    else {
        # jar 已固定名为 expert-system.jar（无版本号），版本号从唯一真源 version.txt 读取
        $verFile = 'D:\jshz-PMO\version.txt'
        if (Test-Path $verFile) {
            $m = [regex]::Match((Get-Content $verFile -Raw -Encoding UTF8), '(?m)^revision\s*=\s*(.+)\s*$')
            if ($m.Success) { $script:NewVersion = $m.Groups[1].Value.Trim() }
            else { $script:NewVersion = (Get-Item $script:NewJar).LastWriteTime.ToString('yyyyMMddHHmm') }
        } else {
            $script:NewVersion = (Get-Item $script:NewJar).LastWriteTime.ToString('yyyyMMddHHmm')
        }
    }
    Write-Info "发布版本: $script:NewVersion"

    $ok = Invoke-Check "GET http://127.0.0.1:${GreenPort}/health 应 200（绿在线）" {
        if (-not (Test-HttpOk "http://127.0.0.1:${GreenPort}/health")) { throw '绿服务不健康，中止发布' }
    }
    if (-not $ok) { throw '预检失败：绿服务不可用，不发布' }
}

# ==================== 阶段 2: 影子启动 ====================
function Step2-StartShadow {
    Write-Step '2/11 影子启动（新 jar @8081）'
    Invoke-Action "启动影子进程: java -jar $script:NewJar --server.port=${BluePort}（工作目录 $BackendDir，数据库密码经环境变量继承）" {
        $psi = Start-Process -FilePath 'java' -ArgumentList @('-jar', $script:NewJar, "--server.port=${BluePort}") `
            -WorkingDirectory $BackendDir -PassThru -WindowStyle Hidden
        $script:ShadowPid = $psi.Id
        Write-Ok "影子进程 PID=$($psi.Id)"
    }
    if ($DryRun -and -not $script:ShadowPid) { $script:ShadowPid = 0 }
}

# ==================== 阶段 3: 影子健康 + 业务探测 ====================
function Step3-ShadowHealth {
    Write-Step '3/11 影子健康 + 业务探测'
    $ok = Invoke-Check "影子健康检查: 循环 GET http://127.0.0.1:${BluePort}/health（${ShadowRetries} 次 x ${ShadowDelaySec}s）" {
        if (-not (Wait-Healthy "http://127.0.0.1:${BluePort}/health" $ShadowRetries $ShadowDelaySec '影子')) { throw '影子健康检查超时' }
    }
    if (-not $ok) { throw '影子健康检查失败：中止并清理影子进程' }

    $ok2 = Invoke-Check "业务探测: GET http://127.0.0.1:${BluePort}/api/expert/list 应 200/401（无 token 401 视为在线）" {
        if (-not (Test-HttpOkOr401 "http://127.0.0.1:${BluePort}/api/expert/list")) { throw '业务探测失败' }
    }
    if (-not $ok2) {
        Invoke-Action "kill 影子进程 PID=$script:ShadowPid（绿不受影响）" {
            if ($script:ShadowPid -and $script:ShadowPid -gt 0) { Stop-Process -Id $script:ShadowPid -Force -ErrorAction SilentlyContinue }
        }
        throw '业务探测失败：中止并清理影子进程，绿不受影响'
    }
}

# ==================== 阶段 4: 备份 Nginx 配置 ====================
function Step4-BackupConf {
    Write-Step '4/11 备份 Nginx 配置'
    $stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
    $script:ConfBackup = Join-Path $NginxConfBackup "jshz-pmo.conf.$stamp"
    Invoke-Action "备份 $NginxConfFile -> $script:ConfBackup" {
        if (-not (Test-Path $NginxConfBackup)) { New-Item -ItemType Directory -Path $NginxConfBackup -Force | Out-Null }
        Copy-Item -Path $NginxConfFile -Destination $script:ConfBackup -Force
        if (-not (Test-Path $script:ConfBackup)) { throw '配置备份失败' }
    }
}

# ==================== 阶段 5: 切流 8080 -> 8081 ====================
function Step5-SwitchToBlue {
    Write-Step '5/11 切流: proxy_pass 8080 -> 8081（/api/ 与 /api/health 两处同步）'
    Invoke-Action '替换 conf 中 proxy_pass 为 8081' {
        $before = [IO.File]::ReadAllText($NginxConfFile)
        $after = $before -replace '(?m)^(\s*)proxy_pass\s+http://127\.0\.0\.1:8080(?=/|;)', "`$1proxy_pass http://127.0.0.1:${BluePort}"
        if ($after -eq $before) { throw '未找到 proxy_pass 8080 可替换，配置可能已变更' }
        [IO.File]::WriteAllText($NginxConfFile, $after)
    }
}

# ==================== 阶段 6: nginx -t 校验 ====================
function Step6-TestNginx {
    Write-Step '6/11 nginx -t 配置校验'
    $ok = Invoke-Check "$NginxExe -t 应通过" {
        $raw = & cmd /v:on /c ""$NginxExe" -t -p "D:\nginx\" 2>&1 & echo EXITCODE=!ERRORLEVEL!"
        $out = $raw | Where-Object { $_ -notlike 'EXITCODE=*' }
        $codeLine = $raw | Where-Object { $_ -like 'EXITCODE=*' } | Select-Object -Last 1
        $exitCode = if ($codeLine) { [int](($codeLine -replace '^EXITCODE=','').Trim()) } else { -1 }
        if ($exitCode -ne 0) { throw "nginx -t 失败: $out (exit=$exitCode)" }
    }
    if (-not $ok) {
        Invoke-Action '回滚配置：恢复步骤4备份（不重启 Nginx，绿继续）' {
            Copy-Item -Path $script:ConfBackup -Destination $NginxConfFile -Force
        }
        throw 'nginx -t 校验失败，已回滚配置，未重启 Nginx，绿继续服务'
    }
}

# ==================== 阶段 7: 重启 Nginx ====================
function Step7-RestartNginx {
    Write-Step '7/11 重启 Nginx 服务（NSSM 约束: Restart-Service，禁止 nginx -s reload）'
    $ok = Invoke-Check "Restart-Service $NginxSvc 成功" {
        Restart-Service -Name $NginxSvc -Force
        Start-Sleep -Seconds 3
        if ((Get-Service -Name $NginxSvc).Status -ne 'Running') { throw "服务未运行: $((Get-Service -Name $NginxSvc).Status)" }
    }
    if (-not $ok) {
        Invoke-Action '回滚：恢复备份配置 + 重启 Nginx（绿继续）' {
            Copy-Item -Path $script:ConfBackup -Destination $NginxConfFile -Force
            Restart-Service -Name $NginxSvc -Force -ErrorAction SilentlyContinue
        }
        throw 'Nginx 重启失败，已回滚配置'
    }
}

# ==================== 阶段 8: 蓝验证 ====================
function Step8-VerifyBlue {
    Write-Step '8/11 蓝验证（经 Nginx 请求 /api 由 8081 响应）'
    $ok = Invoke-Check '经 Nginx GET http://127.0.0.1/api/health 应 200，且响应体与 8081 直连一致' {
        if (-not (Test-HttpOk 'http://127.0.0.1/api/health')) { throw '经 Nginx 请求失败' }
        $viaNginx = Get-HttpBody 'http://127.0.0.1/api/health' | ConvertFrom-Json
        $directBlue = Get-HttpBody "http://127.0.0.1:${BluePort}/health" | ConvertFrom-Json
        if (($viaNginx.code -ne $directBlue.code) -or ($viaNginx.data.status -ne $directBlue.data.status)) { throw '经 Nginx 响应与 8081 不一致（code/status），可能未切流到蓝' }
    }
    if (-not $ok) {
        Invoke-Action '回滚：Nginx 切回 8080 + 恢复配置 + 重启服务' {
            $txt = [IO.File]::ReadAllText($NginxConfFile) -replace "(?m)^(\s*)proxy_pass\s+http://127\.0\.0\.1:${BluePort}(?=/|;)", "`$1proxy_pass http://127.0.0.1:${GreenPort}"
            [IO.File]::WriteAllText($NginxConfFile, $txt)
            Restart-Service -Name $NginxSvc -Force -ErrorAction SilentlyContinue
        }
        throw '蓝验证失败，已回滚到 8080'
    }
}

# ==================== 阶段 9: 稳态化 NSSM ====================
function Step9-StabilizeNssm {
    Write-Step '9/11 稳态化：停 NSSM -> 替换 jar -> 启 NSSM -> 8080 健康'
    $targetJar = Join-Path (Join-Path $BackendDir 'target') $TargetJarName

    # 9a 先停服务，释放 target/*.jar 进程锁（历史踩坑：不先停会导致替换失败）
    Invoke-Action "9a 停止 NSSM 服务 $BackendSvc（释放 $targetJar 进程锁）" {
        Stop-Service -Name $BackendSvc -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 3
        if ((Get-Service -Name $BackendSvc).Status -ne 'Stopped') { throw "服务停止失败: $((Get-Service -Name $BackendSvc).Status)" }
    }

    # 9b 归档当前旧 jar 到 backend/releases/<版本>-<时间戳>/
    $stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
    $relDir = Join-Path $ReleasesDir "$script:NewVersion-$stamp"
    $script:OldJar = Join-Path $relDir "previous-$TargetJarName"
    Invoke-Action "9b 归档当前旧 jar -> $script:OldJar" {
        New-Item -ItemType Directory -Path $relDir -Force | Out-Null
        if (Test-Path $targetJar) { Copy-Item -Path $targetJar -Destination $script:OldJar -Force }
    }

    # 9c 替换 jar（NSSM AppParameters 固定指向 $TargetJarName，替换方式最稳）
    Invoke-Action "9c 复制新 jar -> $targetJar" {
        $srcFull = (Get-Item $script:NewJar -ErrorAction Stop).FullName
        $dstFull = (Get-Item $targetJar -ErrorAction SilentlyContinue).FullName
        if ($dstFull -and $srcFull -eq $dstFull) {
            Write-Host "[跳过] 新 jar 已在目标位置（源=目标），无需复制"
        } else {
            Copy-Item -Path $script:NewJar -Destination $targetJar -Force
        }
    }

    # 9d 启动 NSSM 服务（8080 新版本）
    Invoke-Action "9d 启动 NSSM 服务 $BackendSvc（8080 新版本）" {
        Start-Service -Name $BackendSvc -ErrorAction Stop
        Start-Sleep -Seconds 3
    }

    # 9e 健康检查
    $ok = Invoke-Check "9e 健康检查: 8080/health = UP（新版本）" {
        if (-not (Wait-Healthy "http://127.0.0.1:${GreenPort}/health" $HealthOkRetries $HealthOkDelay '8080 新版本')) { throw '8080 新版本健康检查失败' }
    }
    if (-not $ok) {
        Invoke-Action '回滚：从 releases 恢复旧 jar -> 重启 NSSM' {
            Stop-Service -Name $BackendSvc -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 2
            if ($script:OldJar -and (Test-Path $script:OldJar)) { Copy-Item -Path $script:OldJar -Destination $targetJar -Force }
            Start-Service -Name $BackendSvc -ErrorAction SilentlyContinue
        }
        throw '稳态化失败，已回滚旧 jar 并重启 NSSM'
    }
}

# ==================== 阶段 10: 切回 8080 ====================
function Step10-SwitchBack {
    Write-Step '10/11 切回 8080（正式端口）'
    Invoke-Action '替换 conf 中 proxy_pass 8081 -> 8080' {
        $txt = [IO.File]::ReadAllText($NginxConfFile)
        $new = $txt -replace "(?m)^(\s*)proxy_pass\s+http://127\.0\.0\.1:${BluePort}(?=/|;)", "`$1proxy_pass http://127.0.0.1:${GreenPort}"
        if ($new -eq $txt) { throw '未找到 proxy_pass 8081 可回切' }
        [IO.File]::WriteAllText($NginxConfFile, $new)
    }

    $okT = Invoke-Check 'nginx -t 校验' {
        $raw = & cmd /v:on /c ""$NginxExe" -t -p "D:\nginx\" 2>&1 & echo EXITCODE=!ERRORLEVEL!"
        $out = $raw | Where-Object { $_ -notlike 'EXITCODE=*' }
        $codeLine = $raw | Where-Object { $_ -like 'EXITCODE=*' } | Select-Object -Last 1
        $exitCode = if ($codeLine) { [int](($codeLine -replace '^EXITCODE=','').Trim()) } else { -1 }
        if ($exitCode -ne 0) { throw "nginx -t 失败: $out (exit=$exitCode)" }
    }
    if (-not $okT) {
        Invoke-Action '回滚：Nginx 指回 8081（蓝仍可服务）' {
            $txt = [IO.File]::ReadAllText($NginxConfFile) -replace "(?m)^(\s*)proxy_pass\s+http://127\.0\.0\.1:${GreenPort}(?=/|;)", "`$1proxy_pass http://127.0.0.1:${BluePort}"
            [IO.File]::WriteAllText($NginxConfFile, $txt)
            Restart-Service -Name $NginxSvc -Force -ErrorAction SilentlyContinue
        }
        throw '切回 8080 配置校验失败，已回滚 Nginx 指向 8081'
    }

    $okR = Invoke-Check "Restart-Service $NginxSvc" {
        Restart-Service -Name $NginxSvc -Force
        Start-Sleep -Seconds 3
        if ((Get-Service -Name $NginxSvc).Status -ne 'Running') { throw 'Nginx 服务未运行' }
    }
    if (-not $okR) {
        Invoke-Action '回滚：Nginx 指回 8081（蓝仍可服务）' {
            $txt = [IO.File]::ReadAllText($NginxConfFile) -replace "(?m)^(\s*)proxy_pass\s+http://127\.0\.0\.1:${GreenPort}(?=/|;)", "`$1proxy_pass http://127.0.0.1:${BluePort}"
            [IO.File]::WriteAllText($NginxConfFile, $txt)
            Restart-Service -Name $NginxSvc -Force -ErrorAction SilentlyContinue
        }
        throw 'Nginx 重启失败，已回滚 Nginx 指向 8081'
    }

    $okV = Invoke-Check '验证：经 Nginx /api 走 8080 新版本' {
        if (-not (Test-HttpOk 'http://127.0.0.1/api/health')) { throw '经 Nginx 请求失败' }
        $viaNginx = Get-HttpBody 'http://127.0.0.1/api/health' | ConvertFrom-Json
        $directGreen = Get-HttpBody "http://127.0.0.1:${GreenPort}/health" | ConvertFrom-Json
        if (($viaNginx.code -ne $directGreen.code) -or ($viaNginx.data.status -ne $directGreen.data.status)) { throw '经 Nginx 响应与 8080 不一致（code/status）' }
    }
    if (-not $okV) {
        Invoke-Action '回滚：Nginx 指向 8081（蓝继续服务）' {
            $txt = [IO.File]::ReadAllText($NginxConfFile) -replace "(?m)^(\s*)proxy_pass\s+http://127\.0\.0\.1:${GreenPort}(?=/|;)", "`$1proxy_pass http://127.0.0.1:${BluePort}"
            [IO.File]::WriteAllText($NginxConfFile, $txt)
            Restart-Service -Name $NginxSvc -Force -ErrorAction SilentlyContinue
        }
        throw '验证失败，已回滚 Nginx 指向 8081，蓝继续服务'
    }
}

# ==================== 阶段 11: 清理 + 归档 ====================
function Step11-Cleanup {
    Write-Step '11/11 清理 + 归档'
    if ($script:ShadowPid -and $script:ShadowPid -gt 0) {
        Invoke-Action "kill 8081 影子进程 PID=$script:ShadowPid" {
            Stop-Process -Id $script:ShadowPid -Force -ErrorAction SilentlyContinue
        }
    } else { Write-Info '无影子进程需清理' }

    Invoke-Action "版本保留: 保留最近 ${KeepVersions} 个归档版本，超出删除最旧" {
        $dirs = @(Get-ChildItem -Path $ReleasesDir -Directory | Sort-Object LastWriteTime -Descending)
        if ($dirs.Count -gt $KeepVersions) {
            $toRemove = $dirs | Select-Object -Skip $KeepVersions
            foreach ($d in $toRemove) { Remove-Item -Path $d.FullName -Recurse -Force -ErrorAction SilentlyContinue }
            Write-Ok "已清理 $($toRemove.Count) 个旧版本目录"
        } else { Write-Ok "当前 $($dirs.Count) 个版本目录，未超上限" }
    }

    Invoke-Action '记录发布到 docs/deployment.md' {
        $line = "- $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') R-1 发布 $script:NewVersion 成功（jar: $([IO.Path]::GetFileName($script:NewJar))）"
        Add-Content -Path $DeployDoc -Value "`n## R-1 发布记录`n$line" -Encoding UTF8
    }
}

# ==================== 主流程 ====================
Write-Host '========================================' -ForegroundColor Cyan
Write-Host ' R-1 后端蓝绿不停机发布（jshz-PMO）' -ForegroundColor Cyan
if ($DryRun) { Write-Host ' DRYRUN 模式：仅打印动作/检查点，不产生任何变更' -ForegroundColor Yellow }
Write-Host '========================================' -ForegroundColor Cyan
Write-Info "KeepVersions = $KeepVersions"

try {
    Step1-Preflight
    Step2-StartShadow
    Step3-ShadowHealth
    Step4-BackupConf
    Step5-SwitchToBlue
    Step6-TestNginx
    Step7-RestartNginx
    Step8-VerifyBlue
    Step9-StabilizeNssm
    Step10-SwitchBack
    Step11-Cleanup
    Write-Host "`n======== R-1 发布完成（新版本 $script:NewVersion 正式服务） ========" -ForegroundColor Green
    if ($DryRun) { Write-Host 'DRYRUN 结束：未执行任何配置修改/服务操作/进程启动' -ForegroundColor Yellow }
    exit 0
} catch {
    Write-Fail "发布中止: $_"
    Write-Info '回滚指引：见 docs/2026-10-24-发布运维体系与UI偏移修复-design.md §4.1.4（后端回滚预案）'
    if ($DryRun) { Write-Host 'DRYRUN 中止：未产生任何副作用' -ForegroundColor Yellow }
    exit 1
}
