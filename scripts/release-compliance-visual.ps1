#requires -Version 5.1
# 20261105 compliance visual editor release executor (1.0.25-compliance-visual-20261105).
# Sequence: backup -> continuity probe -> metadata guard (4 tables absent) -> additive migration
# -> backend blue/green (COM graceful) -> frontend root switch -> authenticated read checks.
# Rollback: previous-expert-system.jar + frontend rollback metadata; migration tables are
# additive and retained on rollback (per release assessment section 5).
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$StageDirectory,[Parameter(Mandatory=$true)][string]$ExpectedJarSha256,[Parameter(Mandatory=$true)][string]$ExpectedIndexSha256,[switch]$Apply,[switch]$Hotfix,[ValidatePattern('^\d+\.\d+\.\d+$')][string]$HotfixVersion='1.0.26')
$ErrorActionPreference='Stop'
$root='D:\jshz-PMO'
$stage=(Resolve-Path -LiteralPath $StageDirectory).Path
if(-not $stage.StartsWith($root+'\.build-compliance-visual-production-',[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected stage directory'}
$expectedVersion=if($Hotfix){$HotfixVersion}else{'1.0.25'}
$release=if($Hotfix){"$HotfixVersion-compliance-visual-hotfix-20261105"}else{'1.0.25-compliance-visual-20261105'}
$jar=Join-Path $stage 'backend\target\expert-system.jar'
$dist=Join-Path $stage 'frontend-dist'
$rollback=Join-Path $stage 'previous-expert-system.jar'
$migrationSql=Join-Path $root 'database\migration\20261105_compliance_template_workspace.sql'
$newTables=@('compliance_template_workspace','compliance_template_workspace_request','compliance_template_extraction','compliance_template_review')
. (Join-Path $PSScriptRoot 'deployment-artifact.ps1')
foreach($path in @($jar,$rollback)){if(-not(Test-ExecutableBootJar -Path $path)){throw 'Candidate or rollback is not an executable Boot archive'}}
if((Get-FileHash $jar).Hash -ne $ExpectedJarSha256 -or (Get-FileHash (Join-Path $dist 'index.html')).Hash -ne $ExpectedIndexSha256){throw 'Staged hashes differ'}
foreach($line in (Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Services\ExpertSystemBackend\Parameters').AppEnvironmentExtra){
 if($line -match '^([^=]+)=(.*)$'){[Environment]::SetEnvironmentVariable($matches[1],$matches[2],'Process')}
}
if(-not $env:MYSQL_PASSWORD -or -not $env:JWT_SECRET){throw 'Missing existing service credentials'}
$env:MYSQL_PWD=$env:MYSQL_PASSWORD
$mysqlExe='C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe'
function Invoke-MetadataGuard{
  foreach($t in $newTables){
    $exists=& $mysqlExe --no-defaults --host=127.0.0.1 --port=3306 --user=root --batch --raw -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='expert_system' AND table_name='$t'"
    if($LASTEXITCODE -ne 0){throw 'Metadata query failed'}
    if([int]$exists -ne 0){throw ('Metadata guard: table already exists: '+$t+'; inspect legacy structure before migrating')}
  }
}
if(-not $Apply){
  & (Join-Path $PSScriptRoot 'deploy-backend.ps1') -JarPath $jar -RollbackJarPath $rollback -Version $release -GracefulSwitch -KeepVersions 1000 -DryRun
  & (Join-Path $PSScriptRoot 'deploy-frontend-graceful.ps1') -StagedDist $dist -ReleaseId $release
  if($Hotfix){Write-Output 'Plan validated: backup, metadata verification, backend and frontend switch, smoke checks. No migration.';return}
  Write-Output ('Plan validated: backup; probe; metadata guard (4 tables must be absent); additive migration; verify tables empty; backend blue/green; frontend root switch; authenticated read checks; continuity evidence recorded strictly. No production writes.')
  return
}
$principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Windows Administrator required; no production write performed'}
$state=[ordered]@{release=$release;status='RUNNING';phase='backup';started=(Get-Date).ToString('o');migration=$false}
function Save-State{[IO.File]::WriteAllText((Join-Path $stage 'release-state.json'),($state|ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))}
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
 if($Hotfix){
   foreach($table in $newTables){
     & $mysqlExe --no-defaults --host=127.0.0.1 --port=3306 --user=root --batch --raw -N -e "SELECT 1 FROM expert_system.$table LIMIT 1" | Out-Null
     if($LASTEXITCODE -ne 0){throw ('Required table unavailable: '+$table)}
   }
 }else{
 $state.phase='migration';$state.migration=$true;Save-State
 Invoke-MetadataGuard
 Get-Content $migrationSql -Raw -Encoding UTF8 | & $mysqlExe --no-defaults --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --raw expert_system *> (Join-Path $stage 'migration.log')
 if($LASTEXITCODE -ne 0){throw 'Compliance workspace migration failed'}
 foreach($t in $newTables){
   $n=& $mysqlExe --no-defaults --host=127.0.0.1 --port=3306 --user=root --batch --raw -N -e "SELECT COUNT(*) FROM expert_system.$t"
   if($LASTEXITCODE -ne 0){throw ('Post-migration verification failed for '+$t)}
   Write-Output ('table '+$t+' rows='+$n)
 }
 }
 $state.phase='backend';Save-State
 & (Join-Path $PSScriptRoot 'deploy-backend.ps1') -JarPath $jar -RollbackJarPath $rollback -Version $release -GracefulSwitch -KeepVersions 1000 *> (Join-Path $stage 'backend-release.log')
 if($LASTEXITCODE -ne 0){throw 'Backend deployment failed; frontend switch blocked'}
 $state.phase='frontend';Save-State
 & (Join-Path $PSScriptRoot 'deploy-frontend-graceful.ps1') -StagedDist $dist -ReleaseId $release -Apply *> (Join-Path $stage 'frontend-release.log')
 $state.phase='verification';Save-State
 & node.exe (Join-Path $PSScriptRoot 'dashboard-release-verify.mjs') $stage *> (Join-Path $stage 'production-verification.log')
 if($LASTEXITCODE -ne 0){throw 'Authenticated verification failed; preserve evidence and inspect release'}
 $health=Invoke-WebRequest -Uri 'http://127.0.0.1:8080/health' -UseBasicParsing -TimeoutSec 10
 if($health.Content -notmatch [regex]::Escape($expectedVersion)){throw "Health endpoint does not report $expectedVersion"}
 Start-Sleep -Seconds 5
 [IO.File]::WriteAllText((Join-Path $stage 'probe.stop'),'stop')
 $probe.WaitForExit(10000) | Out-Null
 $continuity=Get-Content (Join-Path $stage 'continuity.json') -Raw | ConvertFrom-Json
 $state.continuity=$continuity
 if($continuity.pooled.failures -or $continuity.fresh.failures){
   $state.status='DEPLOYED_CONTINUITY_FAILED';$state.phase='verified';$state.completed=(Get-Date).ToString('o');Save-State
   throw 'Deployed, but continuity failures recorded; strict gate failed. Inspect continuity.json before further releases.'
 }
 $state.status='COMPLETE';$state.phase='verified';$state.completed=(Get-Date).ToString('o');Save-State
} catch {
 if($state.status -ne 'DEPLOYED_CONTINUITY_FAILED'){$state.status='FAILED'};$state.error=$_.Exception.Message;Save-State
 if($probe -and -not $probe.HasExited){[IO.File]::WriteAllText((Join-Path $stage 'probe.stop'),'stop')}
 throw
}
