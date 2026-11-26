#requires -Version 5.1
# 20261111 PP-OCRv5 生产运行时安装（交接文档 §后续执行顺序 第2步）
# 用法（管理员 PowerShell）：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-ppocr-runtime-20261111.ps1
# 内容：E:\AI助手开发\pp-ocrv5 → C:\Program Files\JSHZ\pp-ocrv5（解引用 junction、排除 cache），
#       重写 runtime/pyvenv.cfg home 指向 Program Files 内实体 Python，校验模型 SHA-256、无重解析点。
# 回滚：删除 C:\Program Files\JSHZ\pp-ocrv5 即可（源目录 E 盘保留不动；未安装时 LocalTableOcr 回退 Tesseract）。
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
$src = 'E:\AI助手开发\pp-ocrv5'
$dst = 'C:\Program Files\JSHZ\pp-ocrv5'
$log = 'D:\jshz-PMO\logs\install-ppocr-runtime-20261111.log'
$pyRealName = 'cpython-3.12.16-windows-x86_64-none'   # 实体 Python（另一个同名目录是 junction）

function Write-Both([string]$msg, [string]$color = 'Gray') {
    Write-Host $msg -ForegroundColor $color
    Add-Content -LiteralPath $log -Value $msg -Encoding UTF8
}

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator) -and -not $DryRun) {
    throw '必须以管理员身份运行（写入 Program Files）'
}
if (-not (Test-Path -LiteralPath "$src\runtime\Scripts\python.exe")) { throw "源运行时缺失：$src" }
Write-Both ('=== PP-OCRv5 运行时安装开始 ' + (Get-Date).ToString('s') + ' ===') 'Cyan'

if ($DryRun) { Write-Both '[DRY-RUN] robocopy + pyvenv.cfg 重写 + 校验' Cyan; exit 0 }

# ---------- 1/4 主体拷贝（/XJ 排除 junction；排除 cache） ----------
Write-Both '[1/4] 拷贝主体（解引用 junction，排除 cache）...' Cyan
New-Item -ItemType Directory -Path (Split-Path $dst) -Force | Out-Null
robocopy $src $dst /E /XJ /XD cache /NFL /NDL /NJH /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy 主体失败，退出码 $LASTEXITCODE" }
Write-Both "  robocopy 主体退出码 $LASTEXITCODE（0-7 为成功）" DarkGray

# ---------- 2/4 实体 Python 物理拷贝（源 junction 指向实体目录） ----------
Write-Both "[2/4] 物理拷贝实体 Python（$pyRealName）..." Cyan
robocopy (Join-Path $src "python\$pyRealName") (Join-Path $dst "python\$pyRealName") /E /NFL /NDL /NJH /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy Python 失败，退出码 $LASTEXITCODE" }
Write-Both "  robocopy Python 退出码 $LASTEXITCODE" DarkGray

# ---------- 3/4 重写 pyvenv.cfg home ----------
Write-Both '[3/4] 重写 runtime/pyvenv.cfg home ...' Cyan
$cfg = Join-Path $dst 'runtime\pyvenv.cfg'
$lines = Get-Content -LiteralPath $cfg -Encoding UTF8
$new = foreach ($l in $lines) {
    if ($l -like 'home =*') { "home = $dst\python\$pyRealName" } else { $l }
}
Set-Content -LiteralPath $cfg -Value $new -Encoding UTF8
Write-Both ('  pyvenv.cfg home -> ' + "$dst\python\$pyRealName") DarkGray

# ---------- 4/4 校验：哈希 / 重解析点 / 关键文件 ----------
Write-Both '[4/4] 校验 ...' Cyan
$models = @('ch_PP-OCRv5_det_server.onnx', 'ch_PP-OCRv5_rec_server.onnx')
foreach ($m in $models) {
    $h1 = (Get-FileHash -LiteralPath "$src\models\$m" -Algorithm SHA256).Hash
    $h2 = (Get-FileHash -LiteralPath "$dst\models\$m" -Algorithm SHA256).Hash
    if ($h1 -ne $h2) { throw "模型哈希不一致：$m" }
    Write-Both "  $m SHA256 一致（$($h1.Substring(0,12))...）" DarkGray
}
$rp = Get-ChildItem -LiteralPath $dst -Recurse -Force -Attributes ReparsePoint -ErrorAction SilentlyContinue
if ($rp) { throw ("目标内存在重解析点：" + ($rp.FullName -join '; ')) }
foreach ($f in @('runtime\Scripts\python.exe', 'compliance-ppocr-table.py', "python\$pyRealName\python.exe")) {
    if (-not (Test-Path -LiteralPath (Join-Path $dst $f))) { throw "关键文件缺失：$f" }
}
Write-Both '  重解析点=0；关键文件齐全' DarkGray

Write-Both ('PPPOCR_INSTALL_OK ' + (Get-Date).ToString('s')) Green
Write-Both '  下一步：python -I 推理验证 → 隔离后端解析验证 → 1.0.38.3 蓝绿发布。' Yellow
exit 0
