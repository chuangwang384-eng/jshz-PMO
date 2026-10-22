<#
.SYNOPSIS
  jshz-PMO 前端回滚脚本（Nginx 生产模式）
.DESCRIPTION
  流程: 定位最新备份 dist_backup_* -> 当前 dist 存档为 dist_broken_<时间戳>
        -> 恢复备份到 frontend/dist -> nginx -s reload -> 输出摘要
  用法:
    powershell -ExecutionPolicy Bypass -File D:\jshz-PMO\scripts\rollback-frontend.ps1
#>
$ErrorActionPreference = 'Stop'

$ProjectRoot = 'D:\jshz-PMO'
$FrontendDir = Join-Path $ProjectRoot 'frontend'
$DistDir     = Join-Path $FrontendDir 'dist'
$BackupRoot  = Join-Path $ProjectRoot 'backup\frontend'
$NginxExe    = 'D:\nginx\nginx.exe'

# ---------- [1/5] 定位最新备份 ----------
Write-Host '== [1/5] 定位最新备份 ==' -ForegroundColor Cyan
if (-not (Test-Path $BackupRoot)) {
    throw "备份目录不存在: $BackupRoot，无法回滚"
}
$LatestBackup = Get-ChildItem -Path $BackupRoot -Directory -Filter 'dist_backup_*' |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $LatestBackup) {
    throw "未找到任何 dist_backup_* 备份目录，无法回滚"
}
Write-Host "使用备份: $($LatestBackup.FullName)"

# ---------- [2/5] 当前 dist 存档为 dist_broken_ ----------
Write-Host '== [2/5] 备份当前（异常）dist ==' -ForegroundColor Cyan
$BrokenDir = $null
if (Test-Path $DistDir) {
    $stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
    $BrokenDir = Join-Path $BackupRoot "dist_broken_$stamp"
    New-Item -ItemType Directory -Path $BrokenDir -Force | Out-Null
    Copy-Item -Path (Join-Path $DistDir '*') -Destination $BrokenDir -Recurse -Force
    Write-Host "当前 dist 已存档: $BrokenDir"
} else {
    Write-Host '当前无 dist 目录，跳过 broken 存档' -ForegroundColor Yellow
}

# ---------- [3/5] 恢复备份到 dist ----------
Write-Host '== [3/5] 恢复备份到 dist ==' -ForegroundColor Cyan
if (Test-Path $DistDir) {
    Remove-Item -Path (Join-Path $DistDir '*') -Recurse -Force
} else {
    New-Item -ItemType Directory -Path $DistDir -Force | Out-Null
}
Copy-Item -Path (Join-Path $LatestBackup.FullName '*') -Destination $DistDir -Recurse -Force
Write-Host '备份已恢复'

# ---------- [4/5] Nginx reload ----------
Write-Host '== [4/5] nginx -s reload ==' -ForegroundColor Cyan
if (Test-Path $NginxExe) {
    & $NginxExe -s reload
    if ($LASTEXITCODE -ne 0) {
        Write-Host "警告: nginx -s reload 退出码 $LASTEXITCODE，请检查 Nginx 状态" -ForegroundColor Yellow
        Write-Host "  手动执行: D:\nginx\nginx.exe -t   校验配置"
        Write-Host "  手动执行: D:\nginx\nginx.exe -s reload   重新加载"
    } else {
        Write-Host 'nginx 已重新加载' -ForegroundColor Green
    }
} else {
    Write-Host "警告: 未找到 $NginxExe，跳过 reload；请确认 Nginx 已安装" -ForegroundColor Yellow
}

# ---------- [5/5] 回滚结果摘要 ----------
Write-Host ''
Write-Host '========== 回滚结果摘要 ==========' -ForegroundColor Cyan
Write-Host ("回滚时间   : {0:yyyy-MM-dd HH:mm:ss}" -f (Get-Date))
Write-Host "恢复来源   : $($LatestBackup.FullName)"
if ($BrokenDir) { Write-Host "异常版本存档: $BrokenDir" }
Write-Host '验证建议   : 登录 -> 专家列表 -> 照片 -> 导出'
