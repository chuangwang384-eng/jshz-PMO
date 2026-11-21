#requires -Version 5.1
#requires -RunAsAdministrator
<#
.SYNOPSIS
  后端凭据切换脚本（管理员执行）：把 start-backend.bat 中的新凭据写入 NSSM 服务并重启后端。

.DESCRIPTION
  配套 2026-10-26 凭据轮换流程使用。数据库密码已通过 MySQL 双密码机制
  （RETAIN CURRENT PASSWORD）完成第一階段轮换：新密码已生效、旧密码保留过渡。
  本脚本完成第二阶段：
    1) 从本地 start-backend.bat 解析 MYSQL_PASSWORD / JWT_SECRET（真实值不入库、不写入本脚本）
    2) 写入 NSSM 服务 ExpertSystemBackend 的 AppEnvironmentExtra（注册表 MultiString）
    3) 重启服务（中断约 3~10 秒，用户需重新登录）
    4) 健康检查：8080/health 与经 Nginx /api/health 应 200
  旧密码弃用属第三阶段（可选）：验证通过后用 -DiscardOldPassword 执行
    ALTER USER 'root'@'localhost' DISCARD OLD PASSWORD
  执行成功后旧密码彻底失效，过渡期结束。

.PARAMETER DiscardOldPassword
  重启并验证通过后，弃用旧密码（结束双密码过渡期）。建议在单独一次运行中执行。

.PARAMETER DryRun
  仅打印将执行的动作，不写注册表、不重启服务。

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File D:\jshz-PMO\scripts\rotate-backend-credentials.ps1            # 第一阶段：切环境变量并重启
  powershell -ExecutionPolicy Bypass -File D:\jshz-PMO\scripts\rotate-backend-credentials.ps1 -DiscardOldPassword   # 第三阶段：弃用旧密码
#>
[CmdletBinding()]
param(
    [switch]$DiscardOldPassword,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
# 仓库根单一真源（方案 C 二期，2026-11-19 收敛；JSHZ_ROOT 可覆盖）
. (Join-Path $PSScriptRoot 'common-paths.ps1')
$BatFile   = Join-Path $root 'start-backend.bat'
$BackendSvc   = 'ExpertSystemBackend'
$NginxSvc     = 'ExpertSystemNginx'
$NssmParamsKey = 'HKLM:\SYSTEM\CurrentControlSet\Services\ExpertSystemBackend\Parameters'
$MysqlExe  = 'C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe'

function Write-Ok([string]$m) { Write-Host "   [OK] $m" -ForegroundColor Green }
function Write-Fail([string]$m) { Write-Host "   [FAIL] $m" -ForegroundColor Red }

# ---------- 1) 从 start-backend.bat 解析新凭据 ----------
Write-Host '== [1/4] 解析 start-backend.bat 凭据 ==' -ForegroundColor Cyan
if (-not (Test-Path $BatFile)) { throw "未找到 $BatFile" }
$content = Get-Content $BatFile -Raw
$DbPwd  = [regex]::Match($content, '(?m)^\s*set\s+MYSQL_PASSWORD=(.+?)\s*$').Groups[1].Value.Trim()
$JwtSec = [regex]::Match($content, '(?m)^\s*set\s+JWT_SECRET=(.+?)\s*$').Groups[1].Value.Trim()
if (-not $DbPwd)  { throw 'start-backend.bat 中未找到 MYSQL_PASSWORD' }
if (-not $JwtSec) { throw 'start-backend.bat 中未找到 JWT_SECRET' }
if ($JwtSec.Length -lt 32) { throw 'JWT_SECRET 长度不足 32（HS256 要求），请检查 start-backend.bat' }
Write-Ok "MYSQL_PASSWORD 已读取（长度 $($DbPwd.Length)，值不回显）"
Write-Ok "JWT_SECRET 已读取（长度 $($JwtSec.Length)，值不回显）"

# ---------- 2) 写入 NSSM 服务环境变量 ----------
Write-Host '== [2/4] 写入 NSSM AppEnvironmentExtra ==' -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "   [DRYRUN] 将写入 $NssmParamsKey -> AppEnvironmentExtra = @('MYSQL_PASSWORD=***','JWT_SECRET=***')" -ForegroundColor Yellow
} else {
    Set-ItemProperty -Path $NssmParamsKey -Name AppEnvironmentExtra -Type MultiString `
        -Value @("MYSQL_PASSWORD=$DbPwd", "JWT_SECRET=$JwtSec")
    $now = (Get-ItemProperty -Path $NssmParamsKey).AppEnvironmentExtra -join ';'
    if ($now -notmatch 'MYSQL_PASSWORD=' -or $now -notmatch 'JWT_SECRET=') { throw '注册表写入校验失败' }
    Write-Ok 'AppEnvironmentExtra 已更新（含 MYSQL_PASSWORD + JWT_SECRET）'
}

# ---------- 3) 重启后端服务 ----------
Write-Host '== [3/4] 重启后端服务 ==' -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "   [DRYRUN] 将 Restart-Service $BackendSvc（中断约 3~10 秒）" -ForegroundColor Yellow
} else {
    Restart-Service -Name $BackendSvc -Force
    Start-Sleep -Seconds 5
    $ready = $false
    for ($i = 1; $i -le 15; $i++) {
        try {
            $r = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/health' -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -eq 200) { $ready = $true; break }
        } catch { Start-Sleep -Seconds 3 }
    }
    if (-not $ready) { throw '后端 8080/health 重启后未恢复，请立即检查 logs\expert-backend.err.log；回滚可参考 deployment.md' }
    Write-Ok "8080/health = 200（重试 ${i} 次）"
}

# ---------- 4) 冒烟验证 ----------
Write-Host '== [4/4] 冒烟验证 ==' -ForegroundColor Cyan
if (-not $DryRun) {
    try {
        $api = Invoke-WebRequest -Uri 'http://127.0.0.1/api/health' -UseBasicParsing -TimeoutSec 10
        if ($api.StatusCode -ne 200) { throw "经 Nginx /api/health 返回 $($api.StatusCode)" }
        Write-Ok '经 Nginx /api/health = 200'
    } catch {
        # Nginx 未运行时降级为直连提示
        Write-Fail "经 Nginx /api/health 验证失败: $_（请确认 $NginxSvc 状态）"
    }
    try {
        Invoke-WebRequest -Uri 'http://127.0.0.1/api/expert/list' -UseBasicParsing -TimeoutSec 10 | Out-Null
        Write-Ok '/api/expert/list = 200'
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq 401) { Write-Ok '/api/expert/list = 401（未登录拦截正常）' }
        else { Write-Fail "/api/expert/list 异常: $code" }
    }
    if ($DiscardOldPassword) {
        Write-Host '   - 弃用旧密码（ALTER USER ... DISCARD OLD PASSWORD）...'
        & $MysqlExe -h127.0.0.1 -P3306 -uroot -p$DbPwd -e "ALTER USER 'root'@'localhost' DISCARD OLD PASSWORD;" 2>$null
        if ($LASTEXITCODE -eq 0) { Write-Ok '旧密码已弃用，双密码过渡期结束' }
        else { Write-Fail '弃用旧密码失败（可用新密码手动重试）' }
    }
}

Write-Host ''
if ($DryRun) {
    Write-Host 'DRYRUN 结束：未产生任何变更' -ForegroundColor Yellow
} else {
    Write-Host '== 凭据切换完成 ==' -ForegroundColor Green
    Write-Host ' - 全体用户需重新登录（JWT 由后端签发，服务重启后登录态失效属预期）'
    if (-not $DiscardOldPassword) {
        Write-Host ' - 旧数据库密码仍在过渡期（RETAIN CURRENT PASSWORD）；验证系统正常后，再次以 -DiscardOldPassword 运行本脚本弃用旧密码'
    }
    Write-Host ' - 若下次后端发布（deploy-backend.ps1），请先在会话中注入 MYSQL_PASSWORD 与 JWT_SECRET（与 start-backend.bat 一致）'
}
