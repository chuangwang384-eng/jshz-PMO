#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$NginxPrefix = 'D:\nginx',
    [string]$ServiceName = 'ExpertSystemNginx',
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$prefix = (Resolve-Path -LiteralPath $NginxPrefix).Path
$exe = Join-Path $prefix 'nginx.exe'
$pidFile = Join-Path $prefix 'logs\nginx.pid'
if ($DryRun) {
    Write-Output 'PLAN: validate config; SYSTEM one-shot task sends graceful reload; require unchanged master PID and Running service; never restart Nginx.'
    return
}
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Graceful reload requires an elevated PowerShell to run the one-shot task as the existing LocalSystem service identity.'
}
$service = Get-CimInstance Win32_Service -Filter "Name='$ServiceName'"
if (-not $service -or $service.State -ne 'Running' -or $service.StartName -notin @('LocalSystem','NT AUTHORITY\SYSTEM')) {
    throw 'Expected running LocalSystem Nginx service; refusing to guess another service identity.'
}
$masterBefore = (Get-Content -LiteralPath $pidFile -Raw).Trim()
$oldWorkers = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$masterBefore AND Name='nginx.exe'" | Select-Object -ExpandProperty ProcessId)
# nginx -t writes its success lines to stderr; capture explicitly (PS 5.1 turns redirected native
# stderr into ErrorRecords which would throw under ErrorActionPreference=Stop with a confusing
# message). Success is judged by $LASTEXITCODE, matching deploy-backend Step6's pattern.
$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$nginxTestOutput = & $exe -p ($prefix.Replace('\','/') + '/') -t 2>&1
$ErrorActionPreference = $prevEap
if ($LASTEXITCODE -ne 0) { throw ('Nginx configuration validation failed: ' + (@($nginxTestOutput | ForEach-Object { [string]$_ }) -join ' | ')) }
$name = 'JshzGracefulReload-' + [guid]::NewGuid().ToString('N')
# Use the native Task Scheduler COM API: the CIM ScheduledTasks provider on
# this host denies SYSTEM registration even with a verified elevated token.
$scheduler = New-Object -ComObject Schedule.Service
$scheduler.Connect()
$folder = $scheduler.GetFolder('\')
$definition = $scheduler.NewTask(0)
$definition.Principal.UserId = 'SYSTEM'
$definition.Principal.LogonType = 5
$definition.Principal.RunLevel = 1
$definition.Settings.ExecutionTimeLimit = 'PT1M'
$action = $definition.Actions.Create(0)
$action.Path = $exe
$action.Arguments = '-p "' + $prefix.Replace('\','/') + '/" -s reload'
$action.WorkingDirectory = $prefix
$registered = $false
try {
    $task = $folder.RegisterTaskDefinition($name, $definition, 6, 'SYSTEM', $null, 5, $null)
    $registered = $true
    $running = $task.Run($null)
    $complete = $false
    for ($attempt = 0; $attempt -lt 80; $attempt++) {
        Start-Sleep -Milliseconds 250
        $task = $folder.GetTask($name)
        if ($task.LastRunTime.Year -gt 2000 -and $task.State -ne 4 -and $task.LastTaskResult -eq 0) {
            $complete = $true
            break
        }
    }
    if (-not $complete) { throw "Nginx signal task failed or timed out; result=$($task.LastTaskResult)" }
    $newWorker = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$masterBefore AND Name='nginx.exe'")
        if (@($children | Where-Object { $_.ProcessId -notin $oldWorkers }).Count -gt 0) { $newWorker = $true; break }
        Start-Sleep -Milliseconds 250
    }
    if (-not $newWorker) { throw 'No new worker observed; preserve backends and inspect Nginx error log.' }
    $drainUntil = (Get-Date).AddSeconds(300)
    do {
        $remaining = @($oldWorkers | Where-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue })
        if ($remaining.Count -eq 0) { break }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $drainUntil)
    if ($remaining.Count -gt 0) { throw 'Old Nginx workers still draining; preserve both backends and stop release.' }
    if ((Get-Content -LiteralPath $pidFile -Raw).Trim() -ne $masterBefore) { throw 'Nginx master PID changed unexpectedly.' }
    if ((Get-Service -Name $ServiceName).Status -ne 'Running') { throw 'Nginx service is no longer Running.' }
    Write-Output "Graceful reload signal accepted; master PID unchanged: $masterBefore. Caller must verify upstream and continuous HTTP probes."
} finally {
    if ($registered) { $folder.DeleteTask($name, 0) }
}
