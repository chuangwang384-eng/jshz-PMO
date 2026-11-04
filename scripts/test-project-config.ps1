param([string]$MysqlExe='C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe')
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$buildRoot=Join-Path $repoRoot '.build-project-config-acceptance'
if($env:PC_ACCEPTANCE_DB -ne 'pc_acceptance' -or -not $env:PC_DB_PASSWORD){throw 'Set PC_ACCEPTANCE_DB=pc_acceptance and PC_DB_PASSWORD for the disposable instance.'}
$previousMysqlPassword=$env:MYSQL_PWD
$previousEncoding=$OutputEncoding
try {
  $OutputEncoding=[Text.UTF8Encoding]::new($false)
  $env:MYSQL_PWD=$env:PC_DB_PASSWORD
  $dbArgs=@('--no-defaults','--host=127.0.0.1','--port=13317','--user=root','--batch','--raw','--skip-column-names','pc_acceptance')
  $identity='SELECT CONCAT(DATABASE(),CHAR(9),@@port,CHAR(9),@@datadir);' | & $MysqlExe @dbArgs
  if($LASTEXITCODE -ne 0 -or $identity -notmatch '^pc_acceptance\s+13317\s+' -or $identity -notmatch '\.build-project-config-acceptance') {throw 'Refusing non-isolated database identity/datadir.'}
  Get-Content (Join-Path $repoRoot 'database/testing/project_config_baseline.sql') -Raw -Encoding UTF8 | & $MysqlExe @dbArgs | Out-Null
  if($LASTEXITCODE -ne 0){throw 'Isolated fixture schema bootstrap failed.'}
  foreach($round in 1..2){
    foreach($name in @('20261104_project_config_snapshots.sql','20261104_project_config_finalize.sql')){
      Get-Content (Join-Path $repoRoot "database/migration/$name") -Raw -Encoding UTF8 | & $MysqlExe @dbArgs | Out-Null
      if($LASTEXITCODE -ne 0){throw "Migration failed: $name"}
    }
  }
  New-Item -ItemType Directory -Force "$buildRoot/backend" | Out-Null
  Copy-Item "$repoRoot/backend/pom.xml" "$buildRoot/backend/pom.xml" -Force
  Copy-Item "$repoRoot/version.txt" "$buildRoot/version.txt" -Force
  Copy-Item "$repoRoot/backend/src" "$buildRoot/backend/" -Recurse -Force
  Push-Location "$buildRoot/backend"
  try {
    & mvn -q '-Dtest=ProjectConfigurationAcceptanceIT,ProjectAccessRegressionTest,ProjectStorageRegressionTest,SupplierDeletionRegressionTest' test
    if($LASTEXITCODE -ne 0){throw 'Project configuration regression failed.'}
  } finally {Pop-Location}
} finally {$env:MYSQL_PWD=$previousMysqlPassword;$OutputEncoding=$previousEncoding}
