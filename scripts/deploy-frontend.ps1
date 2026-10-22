<#
.SYNOPSIS
  jshz-PMO 前端发布脚本（Nginx 生产模式）
.DESCRIPTION
  流程: 备份现有 dist -> npm run build -> 校验产物 -> nginx -s reload -> 输出摘要
  前置条件:
    1. Node.js 已安装，D:\jshz-PMO\frontend 下可执行 npm run build
    2. Nginx 已安装到 D:\nginx 并已加载 nginx-jshz-pmo.conf 站点配置
    3. 后端 8080 由 NSSM 服务 ExpertSystemBackend 托管（无需本脚本处理）
  用法:
    powershell -ExecutionPolicy Bypass -File D:\jshz-PMO\scripts\deploy-frontend.ps1
#>
$ErrorActionPreference = 'Stop'

$ProjectRoot = 'D:\jshz-PMO'
$FrontendDir = Join-Path $ProjectRoot 'frontend'
$DistDir     = Join-Path $FrontendDir 'dist'
$BackupRoot  = Join-Path $ProjectRoot 'backup\frontend'
$NginxExe    = 'D:\nginx\nginx.exe'

# ---------- [1/5] 备份现有 dist（旧版存档，供 rollback 使用） ----------
Write-Host '== [1/5] 备份现有 dist ==' -ForegroundColor Cyan
$BackupDir = $null
if (Test-Path $DistDir) {
    $stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
    $BackupDir = Join-Path $BackupRoot "dist_backup_$stamp"
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    Copy-Item -Path (Join-Path $DistDir '*') -Destination $BackupDir -Recurse -Force
    Write-Host "已备份到: $BackupDir"
} else {
    Write-Host '当前无 dist 目录，跳过备份' -ForegroundColor Yellow
}

# ---------- [2/5] 前端构建（vite 默认输出到 frontend/dist） ----------
Write-Host '== [2/5] npm run build ==' -ForegroundColor Cyan
Push-Location $FrontendDir
try {
    & npm run build
    if ($LASTEXITCODE -ne 0) {
        throw "npm run build 失败，退出码: $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

# ---------- [3/5] 校验构建产物 ----------
Write-Host '== [3/5] 校验构建产物 ==' -ForegroundColor Cyan
$IndexFile = Join-Path $DistDir 'index.html'
if (-not (Test-Path $IndexFile)) {
    throw "构建产物缺失: $IndexFile，发布中止（旧版已备份，可执行回滚脚本）"
}
Write-Host "dist 产物就绪: $DistDir"

# ---------- [4/5] Nginx reload ----------
Write-Host '== [4/5] nginx -s reload ==' -ForegroundColor Cyan
if (Test-Path $NginxExe) {
    & $NginxExe -s reload
    if ($LASTEXITCODE -ne 0) {
        Write-Host "警告: nginx -s reload 退出码 $LASTEXITCODE，请检查 Nginx 状态" -ForegroundColor Yellow
        Write-Host '排查建议:'
        Write-Host "  1) D:\nginx\nginx.exe -t   校验配置语法"
        Write-Host "  2) 若 Nginx 未启动: D:\nginx\nginx.exe   启动"
        Write-Host "  3) 再次: D:\nginx\nginx.exe -s reload   重新加载"
    } else {
        Write-Host 'nginx 已重新加载配置与静态资源' -ForegroundColor Green
    }
} else {
    Write-Host "警告: 未找到 $NginxExe，跳过 reload；请确认 Nginx 已安装" -ForegroundColor Yellow
}

# ---------- [5/5] 部署结果摘要 ----------
Write-Host ''
Write-Host '========== 部署结果摘要 ==========' -ForegroundColor Cyan
Write-Host ("构建时间   : {0:yyyy-MM-dd HH:mm:ss}" -f (Get-Date))
if ($BackupDir) { Write-Host "备份位置   : $BackupDir" }
Write-Host "部署位置   : $DistDir"
Write-Host '访问地址   : http://<本机IP>/  (80 端口；动态 IP 用 ipconfig 确认)'
Write-Host '验证建议   : 登录 -> 专家列表 -> 照片 -> 导出'
