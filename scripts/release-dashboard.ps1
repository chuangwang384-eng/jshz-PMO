#requires -Version 5.1
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$StageDirectory,[Parameter(Mandatory=$true)][string]$ExpectedJarSha256,[Parameter(Mandatory=$true)][string]$ExpectedIndexSha256,[switch]$Apply)
$ErrorActionPreference='Stop'
$root='D:\jshz-PMO'
$stage=(Resolve-Path -LiteralPath $StageDirectory).Path
if(-not $stage.StartsWith($root+'\.build-dashboard-production-',[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected dashboard stage directory'}
$release='1.0.24-dashboard-20261104'
if($Apply -and (Test-Path -LiteralPath (Join-Path $stage 'release-state.json'))){throw 'Release already attempted. Inspect release-state.json and final-state.json; do not repeat production switches.'}
$jar=Join-Path $stage 'backend\target\expert-system.jar'
$dist=Join-Path $stage 'frontend-dist'
$rollback=Join-Path $stage 'previous-expert-system.jar'
. (Join-Path $PSScriptRoot 'deployment-artifact.ps1')
foreach($path in @($jar,$rollback)){if(-not(Test-ExecutableBootJar -Path $path)){throw 'Candidate or rollback is not an executable Boot archive'}}
if((Get-FileHash $jar).Hash -ne $ExpectedJarSha256 -or (Get-FileHash (Join-Path $dist 'index.html')).Hash -ne $ExpectedIndexSha256){throw 'Staged hashes differ'}
foreach($line in (Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Services\ExpertSystemBackend\Parameters').AppEnvironmentExtra){
 if($line -match '^([^=]+)=(.*)$'){[Environment]::SetEnvironmentVariable($matches[1],$matches[2],'Process')}
}
if(-not $env:MYSQL_PASSWORD -or -not $env:JWT_SECRET){throw 'Missing existing service credentials'}
if(-not $Apply){
 & (Join-Path $PSScriptRoot 'deploy-backend.ps1') -JarPath $jar -RollbackJarPath $rollback -Version $release -GracefulSwitch -KeepVersions 1000 -DryRun
 & (Join-Path $PSScriptRoot 'deploy-frontend-graceful.ps1') -StagedDist $dist -ReleaseId $release
 Write-Output 'Validated plan only. No production writes.'
 return
}
$principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Windows Administrator required; no production write performed'}
$state=[ordered]@{release=$release;status='RUNNING';phase='backup';started=(Get-Date).ToString('o');migration=$false}
function Save-State{[IO.File]::WriteAllText((Join-Path $stage 'release-state.json'),($state|ConvertTo-Json -Depth 6),[Text.Encoding]::UTF8)}
Save-State
try {
 Add-Type -AssemblyName System.Security
 $protected=[IO.File]::ReadAllBytes('D:\jshz-PMO\.build-project-config-production-20261104\smoke-password.dpapi')
 $env:PC_SMOKE_PASSWORD=[Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect($protected,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))
 $env:PC_SMOKE_USER='测试'
 $started=Get-Date
 & (Join-Path $PSScriptRoot 'backup.ps1') -Mode Backup -Keep 1000 *> (Join-Path $stage 'backup.log')
 $backup=Get-ChildItem (Join-Path $root 'backup') -Directory -Filter 'backup_*' | Where-Object CreationTime -ge $started.AddSeconds(-2) | Sort-Object Name -Descending | Select-Object -First 1
 if(-not $backup){throw 'Fresh backup absent'}
 & (Join-Path $PSScriptRoot 'backup.ps1') -Mode Verify -From $backup.FullName *> (Join-Path $stage 'backup-verify.log')
 $state.backup=$backup.FullName;Save-State
 $probe=Start-Process node.exe -ArgumentList @((Join-Path $PSScriptRoot 'dashboard-release-probe.mjs'),$stage) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $stage 'probe.log') -RedirectStandardError (Join-Path $stage 'probe.err')
 Start-Sleep -Seconds 3
 if($probe.HasExited){throw 'Continuity probe failed before release'}
 $state.phase='backend';Save-State
 & (Join-Path $PSScriptRoot 'deploy-backend.ps1') -JarPath $jar -RollbackJarPath $rollback -Version $release -GracefulSwitch -KeepVersions 1000 *> (Join-Path $stage 'backend-release.log')
 $state.phase='frontend';Save-State
 & (Join-Path $PSScriptRoot 'deploy-frontend-graceful.ps1') -StagedDist $dist -ReleaseId $release -Apply *> (Join-Path $stage 'frontend-release.log')
 $state.phase='verification';Save-State
 & node.exe (Join-Path $PSScriptRoot 'dashboard-release-verify.mjs') $stage *> (Join-Path $stage 'production-verification.log')
 if($LASTEXITCODE -ne 0){throw 'Authenticated verification failed; preserve evidence and inspect release'}
 Start-Sleep -Seconds 5
 [IO.File]::WriteAllText((Join-Path $stage 'probe.stop'),'stop')
 $probe.WaitForExit(10000) | Out-Null
 $continuity=Get-Content (Join-Path $stage 'continuity.json') -Raw | ConvertFrom-Json
 if($continuity.pooled.failures -or $continuity.fresh.failures){throw 'Continuity failure recorded; inspect raw evidence'}
 $state.continuity=$continuity;$state.status='COMPLETE';$state.phase='verified';$state.completed=(Get-Date).ToString('o');Save-State
} catch {
 $state.status='FAILED';$state.error=$_.Exception.Message;Save-State
 if($probe -and -not $probe.HasExited){[IO.File]::WriteAllText((Join-Path $stage 'probe.stop'),'stop')}
 throw
}
