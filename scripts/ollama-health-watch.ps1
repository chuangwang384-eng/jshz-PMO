<#
.SYNOPSIS
Ollama service health watchdog (2026-11-17).
Probes the real inference path with a 60s budget; restarts ExpertSystemOllama when it hangs
(process alive but unresponsive - NSSM only restarts on process exit, so a GPU/model-load hang
never recovers by itself). Registered as scheduled task OllamaHealthWatch (every 5 min, SYSTEM).
Consumed by: schtasks OllamaHealthWatch -> this script. Do not duplicate restart logic elsewhere.
#>
$ErrorActionPreference = 'Continue'

# ===== 参数区（全部阈值集中于此，勿在函数体内散落魔法数字） =====
$repoRoot = Split-Path -Parent $PSScriptRoot            # 仓库根：scripts/ 的上一级
$service  = 'ExpertSystemOllama'
$base     = if ($env:OLLAMA_BASE_URL) { $env:OLLAMA_BASE_URL } else { 'http://127.0.0.1:11434' }
$model    = if ($env:OLLAMA_MODEL) { $env:OLLAMA_MODEL } else { 'qwen2.5:3b' }
$log      = Join-Path $repoRoot 'logs\ollama-watch.log' # 日志归 logs/（AGENTS.md §0.4）
$lock     = Join-Path $repoRoot 'temp\ollama-watch.lock' # 锁文件归 temp/
$logTailLines           = 300   # 日志只保留最后 N 行
$lockMaxAgeMin          = 10    # 锁文件过期分钟数（陈旧锁视为无效）
$startWaitSec           = 20    # Start-Service 后等待秒数
$probeTimeoutSec        = 60    # 正常推理探测预算（= 后端挂起判定阈值）
$recoverLoops           = 12    # 重启后恢复等待：12 轮 × 10s = 120s 上限
$recoverIntervalSec     = 10
$recoverProbeTimeoutSec = 10    # 恢复期轻量探测（/api/tags）超时
$warmupTimeoutSec       = 120   # 预热生成超时（2026-11-17 实测含模型重载恢复 ~114s）

function Write-Watch([string]$level, [string]$msg) {
    $line = "{0} [{1}] {2}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $level, $msg
    Add-Content -Path $log -Value $line -Encoding UTF8
}

# keep log bounded: retain last $logTailLines lines
if (Test-Path $log) {
    $tail = Get-Content $log -Tail $logTailLines
    Set-Content -Path $log -Value $tail -Encoding UTF8
}

# skip if another watch instance is mid-restart (lock file, stale after $lockMaxAgeMin min)
if (Test-Path $lock) {
    $age = (Get-Date) - (Get-Item $lock).LastWriteTime
    if ($age.TotalMinutes -lt $lockMaxAgeMin) { Write-Watch 'SKIP' 'another instance is handling a restart'; exit 0 }
    Remove-Item $lock -Force -ErrorAction SilentlyContinue
}

# real inference probe: model load + 1 token generate
function Invoke-Probe([int]$timeoutSec) {
    $body = @{ model = $model; prompt = 'hi'; stream = $false; options = @{ num_predict = 1 } } | ConvertTo-Json -Depth 3
    try {
        $r = Invoke-WebRequest -Uri "$base/api/generate" -Method Post -Body $body -ContentType 'application/json' -TimeoutSec $timeoutSec -UseBasicParsing
        return ($r.StatusCode -eq 200)
    } catch { return $false }
}

$svc = Get-Service -Name $service -ErrorAction SilentlyContinue
if ($svc -and $svc.Status -ne 'Running') {
    Write-Watch 'WARN' "service not running ($($svc.Status)), starting"
    Set-Content -Path $lock -Value (Get-Date) -Encoding UTF8
    Start-Service -Name $service -ErrorAction SilentlyContinue
    Start-Sleep -Seconds $startWaitSec
}

if (Invoke-Probe $probeTimeoutSec) { exit 0 }

Write-Watch 'ERROR' "inference probe failed/timed out (${probeTimeoutSec}s), restarting $service"
Set-Content -Path $lock -Value (Get-Date) -Encoding UTF8
try {
    Restart-Service -Name $service -Force
} catch {
    Write-Watch 'ERROR' "Restart-Service failed: $($_.Exception.Message)"
}
# wait up to $recoverLoops x $recoverIntervalSec = 120s for API to come back (measured full recovery up to ~114s incl. model reload)
$ok = $false
for ($i = 0; $i -lt $recoverLoops; $i++) {
    Start-Sleep -Seconds $recoverIntervalSec
    try {
        $null = Invoke-WebRequest -Uri "$base/api/tags" -Method Get -TimeoutSec $recoverProbeTimeoutSec -UseBasicParsing
        $ok = $true; break
    } catch { }
}
if ($ok) {
    Write-Watch 'INFO' 'service back up after restart'
    # warm-up so the first real request does not pay model-load latency; non-fatal
    if (Invoke-Probe $warmupTimeoutSec) {
        Write-Watch 'INFO' 'warm-up generate ok'
    } else {
        Write-Watch 'WARN' "warm-up generate failed (${warmupTimeoutSec}s budget), non-fatal"
    }
} else {
    Write-Watch 'ERROR' "service still unresponsive ${recoverLoops}x${recoverIntervalSec}=120s after restart, manual attention needed"
}
Remove-Item $lock -Force -ErrorAction SilentlyContinue
