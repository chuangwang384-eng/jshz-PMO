#requires -Version 5.1
# 20261111 rembg 证件照抠图运行时安装 v2（Owner 2026-11-11 需求：简历照片自动抠透明背景）
# 用法（管理员 PowerShell）：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-photomatte-20261111.ps1
# 内容 v2（离线，秒级）：E 盘 venv 已装好 rembg 全套 → robocopy site-packages 增量到受保护 venv
#       （两 venv 同源同版本，覆盖幂等）→ 拷 photomatte.py + u2net.onnx（SHA-256 校验）→ 推理自测。
# 回滚：删 photomatte.py、models\models\u2net、site-packages 内 rembg/pooch/pymatting/scipy/tqdm 等新增包目录。
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
$src = 'E:\AI助手开发\pp-ocrv5'
$dst = 'C:\Program Files\JSHZ\pp-ocrv5'
$log = 'D:\jshz-PMO\logs\install-photomatte-20261111.log'
$py = Join-Path $dst 'runtime\Scripts\python.exe'

function Write-Both([string]$msg, [string]$color = 'Gray') {
    Write-Host $msg -ForegroundColor $color
    Add-Content -LiteralPath $log -Value $msg -Encoding UTF8
}

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator) -and -not $DryRun) {
    throw '必须以管理员身份运行（写入 Program Files）'
}
if (-not (Test-Path -LiteralPath (Join-Path $src 'runtime\Lib\site-packages\rembg'))) {
    throw "E 盘 venv 缺少 rembg：请先在 E 盘完成安装"
}
Write-Both ('=== photomatte 运行时安装开始(v2 离线拷贝) ' + (Get-Date).ToString('s') + ' ===') 'Cyan'
if ($DryRun) { Write-Both '[DRY-RUN] site-packages 增量 robocopy + 脚本/模型拷贝 + 自测' Cyan; exit 0 }

# ---------- 1/3 site-packages 增量拷贝 ----------
Write-Both '[1/3] site-packages 增量 robocopy（同源同版本，覆盖幂等）...' Cyan
robocopy (Join-Path $src 'runtime\Lib\site-packages') (Join-Path $dst 'runtime\Lib\site-packages') /E /NFL /NDL /NJH /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy site-packages 失败，退出码 $LASTEXITCODE" }
Write-Both "  robocopy 退出码 $LASTEXITCODE（0-7 为成功）" DarkGray

# ---------- 2/3 脚本与模型 ----------
Write-Both '[2/3] 拷贝 photomatte.py 与 u2net.onnx ...' Cyan
Copy-Item -LiteralPath (Join-Path $src 'photomatte.py') -Destination (Join-Path $dst 'photomatte.py') -Force
$modelDstDir = Join-Path $dst 'models\models\u2net'
New-Item -ItemType Directory -Path $modelDstDir -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $src 'models\models\u2net\u2net.onnx') -Destination (Join-Path $modelDstDir 'u2net.onnx') -Force
$h1 = (Get-FileHash -LiteralPath (Join-Path $src 'models\models\u2net\u2net.onnx') -Algorithm SHA256).Hash
$h2 = (Get-FileHash -LiteralPath (Join-Path $modelDstDir 'u2net.onnx') -Algorithm SHA256).Hash
if ($h1 -ne $h2) { throw 'u2net.onnx 哈希不一致' }
Write-Both ('  u2net.onnx SHA256 一致（' + $h1.Substring(0, 12) + '...）') DarkGray

# ---------- 3/3 受保护目录推理自测 ----------
Write-Both '[3/3] 受保护目录推理自测（python -I photomatte.py）...' Cyan
$testPng = 'D:\jshz-PMO\temp\matte-test.png'
$testOut = 'D:\jshz-PMO\temp\matte-out-protected.png'
& $py -I (Join-Path $dst 'photomatte.py') $testPng $testOut *>> $log
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $testOut)) { throw '受保护目录推理自测失败' }
$size = (Get-Item -LiteralPath $testOut).Length
if ($size -lt 1000) { throw "推理输出异常（$size 字节）" }
$chk = & $py -c "import rembg, sys; print('rembg-ok')"
if ($chk -ne 'rembg-ok') { throw 'rembg 导入校验失败' }
Write-Both "  自测输出 $size 字节；rembg 导入 OK" DarkGray

Write-Both ('PHOTOMATTE_INSTALL_OK ' + (Get-Date).ToString('s')) Green
Write-Both '  下一步：Java PhotoMatteService 接入 → 真实简历验证 → 发布。' Yellow
exit 0
