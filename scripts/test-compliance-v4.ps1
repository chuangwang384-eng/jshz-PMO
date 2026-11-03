param(
  [string]$MysqlExe = 'C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe'
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if ($env:V4_ACCEPTANCE_DB -ne 'acceptance_v4' -or -not $env:V4_DB_PASSWORD -or -not $env:V4_TEST_PASSWORD) {
  throw 'Set V4_ACCEPTANCE_DB=acceptance_v4, V4_DB_PASSWORD and V4_TEST_PASSWORD for disposable fixtures.'
}
# Hard-coded loopback endpoint; verify actual DB and datadir before destructive fixture tests.
$previousMysqlPassword = $env:MYSQL_PWD
try {
  $env:MYSQL_PWD = $env:V4_DB_PASSWORD
  $dbArgs = @('--no-defaults','--host=127.0.0.1','--port=13316','--user=root','--batch','--raw','--skip-column-names','acceptance_v4')
  $identity = 'SELECT CONCAT(DATABASE(),CHAR(9),@@port,CHAR(9),@@datadir);' | & $MysqlExe @dbArgs
  if ($LASTEXITCODE -ne 0 -or $identity -notmatch '^acceptance_v4\s+13316\s+' -or $identity -notmatch '\.build-compliance-v4-acceptance') {
    throw 'Refusing non-isolated DB identity/datadir.'
  }
  # Both additive migrations are deliberately executed twice to validate re-runs.
  foreach ($round in 1..2) {
    foreach ($name in @('20261103_add_compliance_v4_tables.sql','20261103_compliance_v4_user_mode.sql')) {
      Get-Content -LiteralPath (Join-Path $repoRoot "database/migration/$name") -Raw -Encoding UTF8 | & $MysqlExe @dbArgs | Out-Null
      if ($LASTEXITCODE -ne 0) { throw "Migration failed: $name" }
    }
  }
  $tests = 'ComplianceV4AcceptanceIT,ComplianceV4EditingRegressionTest,ProjectAccessRegressionTest,ProjectStorageRegressionTest,SupplierDeletionRegressionTest,ComplianceAssignmentRegressionTest,ComplianceReminderRegressionTest'
  & mvn -q -f (Join-Path $repoRoot 'backend/pom.xml') "-Dtest=$tests" test
  if ($LASTEXITCODE -ne 0) { throw 'Acceptance tests failed.' }
} finally { $env:MYSQL_PWD = $previousMysqlPassword }
