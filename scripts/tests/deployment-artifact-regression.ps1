param(
  [string]$KnownGood='D:\jshz-PMO\backend\target\expert-system.jar',
  [Parameter(Mandatory=$true)][string]$NonExecutableJar
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '../deployment-artifact.ps1')
if(-not (Test-ExecutableBootJar -Path $KnownGood)){throw 'Known-good boot package rejected'}
if(Test-ExecutableBootJar -Path $NonExecutableJar){throw 'Non-executable archive accepted as rollback'}
if(Test-ExecutableBootJar -Path (Join-Path $PSScriptRoot 'missing.jar')){throw 'Missing file accepted'}
Write-Output 'PASS: executable Boot package accepted; missing/non-executable JAR rejected.'
