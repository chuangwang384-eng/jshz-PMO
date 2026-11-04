param([string]$MysqlExe='C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe')
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$buildRoot=Join-Path $repoRoot '.build-dashboard-acceptance'
if($env:DASHBOARD_ACCEPTANCE_DB -ne 'dashboard_acceptance' -or -not $env:DASHBOARD_DB_PASSWORD){throw 'Set DASHBOARD_ACCEPTANCE_DB=dashboard_acceptance and DASHBOARD_DB_PASSWORD for the disposable loopback instance.'}
$previousPassword=$env:MYSQL_PWD
$previousEncoding=$OutputEncoding
try {
  $OutputEncoding=[Text.UTF8Encoding]::new($false)
  $env:MYSQL_PWD=$env:DASHBOARD_DB_PASSWORD
  $dbArgs=@('--no-defaults','--host=127.0.0.1','--port=13319','--user=root','--batch','--raw','--skip-column-names')
  $identity='SELECT CONCAT(@@port,CHAR(9),@@datadir);' | & $MysqlExe @dbArgs
  if($LASTEXITCODE -ne 0 -or $identity -notmatch '^13319\s+' -or $identity -notmatch '\.build-dashboard-acceptance') {throw 'Refusing non-isolated MySQL identity/datadir.'}
  'CREATE DATABASE IF NOT EXISTS dashboard_acceptance CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;' | & $MysqlExe @dbArgs | Out-Null
  if($LASTEXITCODE -ne 0){throw 'Disposable DB setup failed.'}
  Get-Content (Join-Path $repoRoot 'database/testing/dashboard_baseline.sql') -Raw -Encoding UTF8 | & $MysqlExe @dbArgs dashboard_acceptance | Out-Null
  if($LASTEXITCODE -ne 0){throw 'Fixture schema bootstrap failed.'}
  New-Item -ItemType Directory -Force "$buildRoot/backend" | Out-Null
  Copy-Item -LiteralPath "$repoRoot/backend/pom.xml" -Destination "$buildRoot/backend/pom.xml" -Force
  Copy-Item -LiteralPath "$repoRoot/version.txt" -Destination "$buildRoot/version.txt" -Force
  Copy-Item -LiteralPath "$repoRoot/backend/src" -Destination "$buildRoot/backend" -Recurse -Force
  Push-Location "$buildRoot/backend"
  try {
    & mvn -q '-Dtest=DashboardRulesTest,DashboardReadIntegrationTest,DashboardResubmitIntegrationTest,ProjectAccessRegressionTest' package
    if($LASTEXITCODE -ne 0){throw 'Dashboard backend verification failed.'}
  } finally {Pop-Location}
  Push-Location (Join-Path $repoRoot 'frontend')
  try {
    & node --test tests/dashboard.test.mjs tests/dashboard-search.test.mjs
    if($LASTEXITCODE -ne 0){throw 'Dashboard frontend state tests failed.'}
    & npm run build -- --outDir "$buildRoot/frontend-script"
    if($LASTEXITCODE -ne 0){throw 'Isolated frontend build failed.'}
  } finally {Pop-Location}
} finally {$env:MYSQL_PWD=$previousPassword;$OutputEncoding=$previousEncoding}
