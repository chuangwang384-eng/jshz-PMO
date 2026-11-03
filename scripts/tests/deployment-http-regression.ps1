param([int]$Port=8080)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '../deployment-http.ps1')
$health=Get-DeploymentHttpResponse -Url "http://127.0.0.1:$Port/health" -TimeoutSec 5
if($health.StatusCode -ne 200 -or ($health.Content | ConvertFrom-Json).data.status -ne 'UP'){throw 'Health probe failed'}
$auth=Get-DeploymentHttpResponse -Url "http://127.0.0.1:$Port/api/compliance/v4/mode" -TimeoutSec 5
if($auth.StatusCode -ne 401){throw 'Anonymous auth probe did not return 401'}
$index=Get-DeploymentHttpResponse -Url 'http://127.0.0.1/' -TimeoutSec 5
if($index.StatusCode -ne 200 -or $index.ContentBytes.Length -lt 100){throw 'Frontend byte probe failed'}
Write-Output 'PASS: Windows PowerShell deployment HTTP health=200, anonymous=401, frontend bytes=200.'
