#requires -Version 5.1
[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][string]$StageDirectory,
  [Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9._-]+$')][string]$ReleaseId,
  [Parameter(Mandatory=$true)][string]$ExpectedJarSha256,
  [Parameter(Mandatory=$true)][string]$ExpectedIndexSha256,
  [switch]$Apply
)
$ErrorActionPreference='Stop'
$OutputEncoding=New-Object Text.UTF8Encoding($false)
try{[Console]::OutputEncoding=$OutputEncoding}catch{}
$root='D:\jshz-PMO'
$stage=(Resolve-Path -LiteralPath $StageDirectory).Path
if(-not $stage.StartsWith('D:\jshz-PMO\.build-project-config-production-',[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected private stage directory'}
$jar=Join-Path $stage 'backend\target\expert-system.jar'
$dist=Join-Path $stage 'frontend-dist'
$conf='D:\nginx\conf\conf.d\jshz-pmo.conf'
. (Join-Path $PSScriptRoot 'deployment-http.ps1')
. (Join-Path $PSScriptRoot 'deployment-artifact.ps1')
if((Get-FileHash $jar).Hash -ne $ExpectedJarSha256 -or (Get-FileHash (Join-Path $dist 'index.html')).Hash -ne $ExpectedIndexSha256){throw 'Approved candidate hashes differ'}
if(-not(Test-ExecutableBootJar -Path $jar)){throw 'Candidate is not executable'}
$classpath=(Join-Path $stage 'backend\target\classes')+';'+[IO.File]::ReadAllText((Join-Path $stage 'backend\classpath.txt')).Trim()+';'+(Join-Path $stage 'verifier')
if(-not(Test-Path (Join-Path $stage 'verifier\ProjectConfigReleaseVerifier.class'))){throw 'Missing compiled read-only verifier'}
if(-not $Apply){Write-Output 'PLAN VERIFIED: freeze project writes only; backup/verify; additive schema; explicitly exclude deleted orphan project 3/type 5; transaction backfill; uniqueness; unchanged business hashes; graceful backend blue/green and frontend root; authenticated read checks; reopen project writes. No production mutation.';return}
$principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Requires Windows Administrator; no production mutation performed'}
$state=[ordered]@{releaseId=$ReleaseId;status='RUNNING';phase='preflight';started=(Get-Date).ToString('o');migrationStarted=$false;writeGate=$false;excludedProjectIds=@(3)}
function Save-State{[IO.File]::WriteAllText((Join-Path $stage 'release-state.json'),($state|ConvertTo-Json -Depth 8),[Text.Encoding]::UTF8)}
function Load-ServiceEnvironment{
  $service=Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Services\ExpertSystemBackend\Parameters'
  foreach($line in $service.AppEnvironmentExtra){if($line -match '^([^=]+)=(.*)$'){[Environment]::SetEnvironmentVariable($matches[1],$matches[2],'Process')}}
  if(-not $env:MYSQL_PASSWORD -or -not $env:JWT_SECRET){throw 'Required service environment absent'}
  $env:MYSQL_PWD=$env:MYSQL_PASSWORD
  $env:PC_MIGRATION_URL='jdbc:mysql://127.0.0.1:3306/expert_system?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
  $env:PC_MIGRATION_DATABASE='expert_system';$env:PC_MIGRATION_USER='root';$env:PC_MIGRATION_PASSWORD=$env:MYSQL_PASSWORD
}
function Invoke-SqlFile([string]$path){
  Get-Content -LiteralPath $path -Raw -Encoding UTF8 | & 'C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe' --no-defaults --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --batch --raw expert_system >> (Join-Path $stage 'migration.log')
  if($LASTEXITCODE -ne 0){throw ('SQL failed: '+[IO.Path]::GetFileName($path))}
}
function Invoke-JsonJava([string]$main,[string[]]$arguments,[string]$file){
  $output=& java '-Dfile.encoding=UTF-8' '-Dsun.stdout.encoding=UTF-8' -cp $classpath $main @arguments
  if($LASTEXITCODE -ne 0){throw ('Java verifier/migration failed: '+$main)}
  $lines=@($output|Where-Object {$_.StartsWith('{')})
  if(-not $lines.Count){throw 'No structured Java result'}
  [IO.File]::WriteAllText((Join-Path $stage $file),$lines[-1],[Text.Encoding]::UTF8)
  return ($lines[-1]|ConvertFrom-Json)
}
function Assert-Report($report){
  if(@($report.anomalies).Count -or @($report.optionalFallbackDifferences).Count -or @($report.flowOrderDifferences).Count){throw 'Unapproved data anomaly or legacy-rule difference'}
  if(@($report.excludedDeletedOrphans).Count -ne 1 -or $report.excludedDeletedOrphans[0].projectId -ne 3 -or $report.excludedDeletedOrphans[0].typeId -ne 5){throw 'Deleted orphan exclusion differs from explicit approval'}
}
function Load-SmokeEnvironment{
  Add-Type -AssemblyName System.Security
  $protected=[IO.File]::ReadAllBytes((Join-Path $stage 'smoke-password.dpapi'))
  $env:PC_SMOKE_PASSWORD=[Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect($protected,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))
  $env:PC_SMOKE_USER='测试';$env:PLAYWRIGHT_MODULE=[IO.File]::ReadAllText((Join-Path $stage 'playwright-module.txt')).Trim()
}
function Assert-Continuity{
  if($probe -and $probe.HasExited){throw 'Continuity process exited unexpectedly'}
  $sample=Get-Content (Join-Path $stage 'continuity.json') -Raw|ConvertFrom-Json
  if(([DateTime]::UtcNow-[DateTime]::Parse($sample.updated).ToUniversalTime()).TotalSeconds -gt 20){throw 'Continuity evidence is stale'}
  if($sample.pooled.failures -or $sample.fresh.failures){throw 'Continuity probe reports failed GET/session requests'}
}
function Get-AnonymousProjectWriteStatus{
  $request=[Net.HttpWebRequest]::Create('http://127.0.0.1/api/project')
  $request.Method='POST';$request.ProtocolVersion=[Version]'1.0';$request.KeepAlive=$false
  $request.ContentType='application/json';$request.Timeout=10000
  $bytes=[Text.Encoding]::UTF8.GetBytes('{}');$request.ContentLength=$bytes.Length
  $stream=$request.GetRequestStream();try{$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
  $response=$null
  try{$response=$request.GetResponse();return [int]$response.StatusCode}
  catch [Net.WebException]{if($_.Exception.Response){$response=$_.Exception.Response;return [int]$response.StatusCode};throw}
  finally{if($response){$response.Dispose()}}
}
function Verify-Backup([string]$path){
  $files=@(Get-ChildItem -LiteralPath $path -File|Where-Object {$_.Extension -in @('.sql','.zip')})
  if(-not @($files|Where-Object Extension -eq '.sql').Count){throw 'No database backup'}
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  foreach($file in $files){
    $expected=([IO.File]::ReadAllText($file.FullName+'.sha256').Trim() -split '\s+')[0]
    if((Get-FileHash $file.FullName).Hash -ne $expected){throw 'Backup hash mismatch'}
    if($file.Extension -eq '.zip'){$zip=[IO.Compression.ZipFile]::OpenRead($file.FullName);try{foreach($entry in $zip.Entries){if($entry.Name){$stream=$entry.Open();try{$stream.CopyTo([IO.Stream]::Null)}finally{$stream.Dispose()}}}}finally{$zip.Dispose()}}
  }
}
$original=[IO.File]::ReadAllText($conf)
$master=[int]([IO.File]::ReadAllText('D:\nginx\logs\nginx.pid').Trim())
$oldRoot=[regex]::Match($original,'(?m)^\s*root\s+([^;]+);').Groups[1].Value.Trim().Trim('"')
$probe=$null
Save-State
try{
  Load-ServiceEnvironment
  $initialSecret=$env:JWT_SECRET;$initialDbPassword=$env:MYSQL_PASSWORD
  if((Get-DeploymentHttpResponse -Url 'http://127.0.0.1/api/health').StatusCode -ne 200){throw 'Production is not healthy'}
  $oldJar=Join-Path $stage 'pre-release-backend.jar'
  Copy-Item -LiteralPath 'D:\jshz-PMO\backend\target\expert-system.jar' -Destination $oldJar
  if(-not(Test-ExecutableBootJar -Path $oldJar)){throw 'Current rollback baseline is not executable'}
  [IO.File]::WriteAllText((Join-Path $stage 'nginx-before.conf'),$original,[Text.UTF8Encoding]::new($false))
  $state.oldRoot=$oldRoot;$state.masterBefore=$master;$state.rollbackJar=$oldJar;Save-State
  Load-SmokeEnvironment
  $stop=Join-Path $stage 'probe.stop';if(Test-Path $stop){Remove-Item -LiteralPath $stop}
  if(Test-Path (Join-Path $stage 'continuity.json')){throw 'Continuity evidence exists; use a fresh approved stage'}
  $probe=Start-Process node.exe -ArgumentList @((Join-Path $PSScriptRoot 'project-config-continuity.mjs'),$stage) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $stage 'probe.stdout.log') -RedirectStandardError (Join-Path $stage 'probe.stderr.log')
  Remove-Item Env:PC_SMOKE_PASSWORD
  $fresh=$false
  for($i=0;$i -lt 40;$i++){Start-Sleep -Milliseconds 500;try{$sample=Get-Content (Join-Path $stage 'continuity.json') -Raw|ConvertFrom-Json;if($sample.pooled.requests -ge 6 -and $sample.fresh.requests -ge 6){Assert-Continuity;$fresh=$true;break}}catch{}}
  if(-not $fresh){throw 'No successful authenticated continuity baseline; no production mutation attempted'}
  $gate=@'
    # PC_V3_WRITE_GATE_BEGIN
    location ~ ^/api/project(?:/|$) {
        default_type application/json;
        if ($request_method !~ "^(GET|HEAD|OPTIONS)$") {
            return 503 '{"code":503,"message":"项目升级中，操作暂不可用，请稍后重试","data":null}';
        }
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_connect_timeout 60s;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
        client_max_body_size 20m;
    }
    # PC_V3_WRITE_GATE_END

'@
  if($original.Contains('PC_V3_WRITE_GATE_BEGIN')){throw 'A project maintenance gate is already installed'}
  $location=[regex]::Match($original,'(?m)^\s*location /api/ \{')
  if(-not $location.Success){throw 'Expected API proxy location absent'}
  $state.phase='freeze-project-writes';Save-State
  [IO.File]::WriteAllText($conf,$original.Insert($location.Index,$gate),[Text.UTF8Encoding]::new($false))
  & (Join-Path $PSScriptRoot 'invoke-nginx-graceful.ps1')
  $state.writeGate=$true;Save-State
  if((Get-AnonymousProjectWriteStatus) -ne 503){throw 'Project write gate did not reject unauthenticated POST before authentication'}
  Assert-Continuity
  $state.phase='final-backup';Save-State
  $backupStart=Get-Date
  & powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File (Join-Path $PSScriptRoot 'backup.ps1') -Mode Backup -Keep 1000 *> (Join-Path $stage 'final-backup.log')
  $backup=Get-ChildItem 'D:\jshz-PMO\backup' -Directory -Filter 'backup_*'|Where-Object {$_.CreationTime -ge $backupStart.AddSeconds(-2)}|Sort-Object Name -Descending|Select-Object -First 1
  if(-not $backup){throw 'No fresh final backup created'}
  Verify-Backup $backup.FullName
  [IO.Compression.ZipFile]::CreateFromDirectory('D:\jshz-PMO\项目资料库',(Join-Path $backup.FullName 'project-files.zip'),[IO.Compression.CompressionLevel]::Optimal,$false)
  (Get-FileHash (Join-Path $backup.FullName 'project-files.zip')).Hash|Set-Content (Join-Path $backup.FullName 'project-files.zip.sha256') -Encoding ASCII
  Verify-Backup $backup.FullName
  $state.backup=$backup.FullName;Save-State
  $baseline=Invoke-JsonJava 'ProjectConfigReleaseVerifier' @('--baseline') 'business-before.json'
  $state.phase='schema-expansion';$state.migrationStarted=$true;Save-State
  Invoke-SqlFile (Join-Path $root 'database\migration\20261104_project_config_snapshots.sql')
  $preflight=Invoke-JsonJava 'com.jshz.expert.tools.ProjectConfigurationMigrationMain' @('--preflight','--allow-deleted-orphans') 'production-preflight.json'
  Assert-Report $preflight
  $state.phase='transaction-backfill';Save-State
  $env:PC_MIGRATION_APPROVED_DATABASE='expert_system'
  $migration=Invoke-JsonJava 'com.jshz.expert.tools.ProjectConfigurationMigrationMain' @('--apply','--allow-deleted-orphans') 'production-migration.json'
  Assert-Report $migration
  Invoke-SqlFile (Join-Path $root 'database\migration\20261104_project_config_finalize.sql')
  $verified=Invoke-JsonJava 'ProjectConfigReleaseVerifier' @('--excluded=3') 'business-after.json'
  # Semantic per-table comparison: rows and sha256 must match exactly. Never compare serialized
  # JSON strings — Java map key order differs between verifier runs and is not a data change.
  foreach($table in $baseline.business.PSObject.Properties){
    $afterTable=$verified.business.PSObject.Properties[$table.Name]
    if(-not $afterTable){throw ('Business table missing after migration: '+$table.Name)}
    if($table.Value.rows -ne $afterTable.Value.rows -or $table.Value.sha256 -ne $afterTable.Value.sha256){throw 'Original business records changed during migration'}
  }
  if(@($baseline.business.PSObject.Properties).Count -ne @($verified.business.PSObject.Properties).Count){throw 'Business table set changed during migration'}
  if($baseline.typeNamesHash -ne $verified.typeNamesHash){throw 'Type names changed during migration'}
  if($verified.verifiedSnapshots -ne $baseline.projects-1){throw 'Snapshot coverage differs from approved exclusion'}
  Assert-Continuity
  $state.phase='backend-blue-green';Save-State
  & powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File (Join-Path $PSScriptRoot 'deploy-backend.ps1') -JarPath $jar -RollbackJarPath $oldJar -Version $ReleaseId -KeepVersions 1000 -GracefulSwitch *> (Join-Path $stage 'backend-deployment.log')
  if($LASTEXITCODE -ne 0){throw 'Backend blue/green did not complete; project writes remain frozen'}
  if((Get-FileHash 'D:\jshz-PMO\backend\target\expert-system.jar').Hash -ne $ExpectedJarSha256){throw 'Formal executable differs from candidate'}
  $state.phase='frontend-switch';Save-State
  & (Join-Path $PSScriptRoot 'deploy-frontend-graceful.ps1') -StagedDist $dist -ReleaseId $ReleaseId -Apply
  $state.phase='authenticated-read-check';Save-State
  Load-SmokeEnvironment;$env:PC_EXPECT_V3='true'
  & node.exe (Join-Path $PSScriptRoot 'project-config-production-check.mjs') 'http://127.0.0.1' $stage $dist $oldRoot
  if($LASTEXITCODE -ne 0){throw 'Authenticated read-only checks failed; writes remain frozen'}
  Remove-Item Env:PC_SMOKE_PASSWORD
  Assert-Continuity
  Load-ServiceEnvironment
  if($env:JWT_SECRET -ne $initialSecret -or $env:MYSQL_PASSWORD -ne $initialDbPassword){throw 'Service credentials changed unexpectedly'}
  $state.phase='reopen-project-writes';Save-State
  $current=[IO.File]::ReadAllText($conf)
  $next=[regex]::Replace($current,'(?ms)^\s*# PC_V3_WRITE_GATE_BEGIN.*?# PC_V3_WRITE_GATE_END\s*\r?\n','')
  if($next -eq $current){throw 'Cannot locate owned write gate for removal'}
  [IO.File]::WriteAllText($conf,$next,[Text.UTF8Encoding]::new($false))
  & (Join-Path $PSScriptRoot 'invoke-nginx-graceful.ps1')
  $state.writeGate=$false
  if((Get-AnonymousProjectWriteStatus) -ne 401){throw 'Write route has not returned to normal authentication'}
  Assert-Continuity
  $state.masterAfter=[int]([IO.File]::ReadAllText('D:\nginx\logs\nginx.pid').Trim())
  if($state.masterAfter -ne $master){throw 'Nginx master changed'}
  $state.status='SUCCESS';$state.phase='complete';$state.completed=(Get-Date).ToString('o');Save-State
}catch{
  $state.status='FAILED';$state.error=$_.Exception.Message;$state.failed=(Get-Date).ToString('o');Save-State
  if(-not $state.migrationStarted){[IO.File]::WriteAllText($conf,$original,[Text.UTF8Encoding]::new($false));try{& (Join-Path $PSScriptRoot 'invoke-nginx-graceful.ps1')}catch{}}
  throw
}finally{
  if($probe){New-Item -ItemType File -Path (Join-Path $stage 'probe.stop') -Force|Out-Null;if(-not $probe.WaitForExit(10000)){Stop-Process -Id $probe.Id -ErrorAction SilentlyContinue}}
  if($state.status -eq 'SUCCESS'){
    $finalProbe=Get-Content (Join-Path $stage 'continuity.json') -Raw|ConvertFrom-Json
    if($finalProbe.pooled.failures -or $finalProbe.fresh.failures -or -not $finalProbe.end){$state.status='DEPLOYED_PROBE_FAILED';Save-State;throw 'Published, but final continuity evidence failed'}
  }
  Remove-Item Env:PC_SMOKE_PASSWORD -ErrorAction SilentlyContinue
}
