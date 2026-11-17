<#
.SYNOPSIS
Ollama service health watchdog (2026-11-17).
Probes the real inference path with a 60s budget; restarts ExpertSystemOllama when it hangs
(process alive but unresponsive - NSSM only restarts on process exit, so a GPU/model-load hang
never recovers by itself). Registered as scheduled task OllamaHealthWatch (every 5 min, SYSTEM).
Consumed by: schtasks OllamaHealthWatch -> this script. Do not duplicate restart logic elsewhere.
#>
$ErrorActionPreference = 'Continue'
$service = 'ExpertSystemOllama'
$base = 'http://127.0.0.1:11434'
$log = 'D:\jshz-PMO\logs\ollama-watch.log'
$model = $env:OLLAMA_MODEL
if (-not $model) { $model = 'qwen2.5:3b' }

function Write-Watch([string]$level, [string]$msg) {
    $line = "{0} [{1}] {2}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $level, $msg
    Add-Content -Path $log -Value $line -Encoding UTF8
}

# keep log bounded: retain last 300 lines
if (Test-Path $log) {
    $tail = Get-Content $log -Tail 300
    Set-Content -Path $log -Value $tail -Encoding UTF8
}

# skip if another watch instance is mid-restart (lock file, stale after 10 min)
$lock = 'D:\jshz-PMO\temp\ollama-watch.lock'
if (Test-Path $lock) {
    $age = (Get-Date) - (Get-Item $lock).LastWriteTime
    if ($age.TotalMinutes -lt 10) { Write-Watch 'SKIP' 'another instance is handling a restart'; exit 0 }
    Remove-Item $lock -Force -ErrorAction SilentlyContinue
}

function Test-Inference {
    # real inference probe: model load + 1 token generate, 60s budget (= backend hang threshold)
    try {
        $body = @{ model = $model; prompt = 'hi'; stream = $false; options = @{ num_predict = 1 } } | ConvertTo-Json -Depth 3
        $r = Invoke-WebRequest -Uri "$base/api/generate" -Method Post -Body $body -ContentType 'application/json' -TimeoutSec 60 -UseBasicParsing
        return ($r.StatusCode -eq 200)
    } catch { return $false }
}

$svc = Get-Service -Name $service -ErrorAction SilentlyContinue
if ($svc -and $svc.Status -ne 'Running') {
    Write-Watch 'WARN' "service not running ($($svc.Status)), starting"
    Set-Content -Path $lock -Value (Get-Date) -Encoding UTF8
    Start-Service -Name $service -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 20
}

if (Test-Inference) { exit 0 }

Write-Watch 'ERROR' "inference probe failed/timed out (60s), restarting $service"
Set-Content -Path $lock -Value (Get-Date) -Encoding UTF8
try {
    Restart-Service -Name $service -Force
} catch {
    Write-Watch 'ERROR' "Restart-Service failed: $($_.Exception.Message)"
}
# wait up to 180s for API to come back (2026-11-17 measured full recovery up to ~114s incl. model reload)
$ok = $false
for ($i = 0; $i -lt 12; $i++) {
    Start-Sleep -Seconds 10
    try {
        $null = Invoke-WebRequest -Uri "$base/api/tags" -Method Get -TimeoutSec 10 -UseBasicParsing
        $ok = $true; break
    } catch { }
}
if ($ok) {
    Write-Watch 'INFO' 'service back up after restart'
    # warm-up so the first real request does not pay model-load latency; non-fatal
    try {
        $body = @{ model = $model; prompt = 'hi'; stream = $false; options = @{ num_predict = 1 } } | ConvertTo-Json -Depth 3
        $null = Invoke-WebRequest -Uri "$base/api/generate" -Method Post -Body $body -ContentType 'application/json' -TimeoutSec 120 -UseBasicParsing
        Write-Watch 'INFO' 'warm-up generate ok'
    } catch { Write-Watch 'WARN' "warm-up failed: $($_.Exception.Message)" }
} else {
    Write-Watch 'ERROR' 'service still unresponsive 90s after restart, manual attention needed'
}
Remove-Item $lock -Force -ErrorAction SilentlyContinue
