param([switch]$DashboardLayout)
# One-process isolated MySQL + service + real Edge acceptance. All credentials are synthetic,
# generated here, passed only through child-process environments, and never written to disk.
$ErrorActionPreference = 'Stop'
$oldOutputEncoding = $OutputEncoding
$oldDashboardLayout = $env:VITE_DASHBOARD_LAYOUT_V2
$OutputEncoding = New-Object Text.UTF8Encoding($false)
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $PSScriptRoot 'build-paths.ps1')
# 沙箱统一收敛到 builds\（不再在仓库根生成 .build-*）；目录名保留以维持 datadir 隔离护栏正则
$sandbox = Join-Path (Get-BuildsRoot) '.build-compliance-v4-acceptance'
$revision = Get-ProjectRevision
$mysqlExe = 'C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe'
$mysqldExe = 'C:\Program Files\MySQL\MySQL Server 26.7\bin\mysqld.exe'
$baseDir = 'C:/Program Files/MySQL/MySQL Server 26.7'
$port = 13316
if ($DashboardLayout -and (Get-NetTCPConnection -State Listen -LocalPort 18086,5173 -ErrorAction SilentlyContinue)) {
    throw 'Dashboard acceptance ports 18086 and 5173 must be free'
}
# Remove only stale processes whose full command line proves they belong to
# this ignored acceptance workspace. Never touch the shared MySQL service.
$stale = Get-CimInstance Win32_Process | Where-Object {
    ($_.Name -eq 'mysqld.exe' -and $_.CommandLine -and $_.CommandLine.Contains($sandbox)) -or
    ($_.Name -eq 'java.exe' -and $_.CommandLine -and $_.CommandLine.Contains((Join-Path $sandbox 'candidate-')))
}
foreach ($p in $stale) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 2
$listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
if ($listener) {
    $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if (-not $owner.CommandLine -or -not $owner.CommandLine.Contains($sandbox)) { throw '13316 is owned outside the isolated acceptance workspace' }
    Stop-Process -Id $owner.ProcessId -Force
    Start-Sleep -Seconds 2
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dataDir = Join-Path $sandbox "mysql-data-e2e-$stamp"
$config = Join-Path $sandbox "mysql-e2e-$stamp.ini"
$initFile = Join-Path $sandbox "mysql-init-$stamp.sql"
$mysqlLog = Join-Path $sandbox "mysql-e2e-$stamp.log"
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rootBytes = New-Object byte[] 36; $rng.GetBytes($rootBytes)
$testBytes = New-Object byte[] 24; $rng.GetBytes($testBytes)
$jwtBytes = New-Object byte[] 48; $rng.GetBytes($jwtBytes)
$rng.Dispose()
$rootPassword = [Convert]::ToBase64String($rootBytes).TrimEnd('=').Replace('+','A').Replace('/','B')
$testPassword = [Convert]::ToBase64String($testBytes).TrimEnd('=').Replace('+','C').Replace('/','D')
$jwtSecret = [Convert]::ToBase64String($jwtBytes)
$candidateProc = $null; $viteProc = $null; $dbProc = $null
$initSql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword'; CREATE DATABASE acceptance_v4 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
[IO.File]::WriteAllText($initFile, $initSql, [Text.Encoding]::ASCII)
try {
    & $mysqldExe --initialize-insecure "--basedir=$baseDir" "--datadir=$($dataDir -replace '\\','/')" --console | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Isolated MySQL initialization failed' }
    @('[mysqld]', "basedir=$baseDir", "datadir=$($dataDir -replace '\\','/')", "port=$port", 'bind-address=127.0.0.1', 'mysqlx=0', "log-error=$($mysqlLog -replace '\\','/')", "init-file=$($initFile -replace '\\','/')") | Set-Content -LiteralPath $config -Encoding ascii
    $mysqlArgs = "--defaults-file=$config"
    $dbProc = Start-Process -FilePath $mysqldExe -ArgumentList $mysqlArgs -WindowStyle Hidden -PassThru
    $deadline = (Get-Date).AddSeconds(60)
    do { Start-Sleep -Seconds 1; $dbListener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue } while (-not $dbListener -and (Get-Date) -lt $deadline)
    Remove-Item -LiteralPath $initFile -Force -ErrorAction SilentlyContinue
    if (-not $dbListener) { Get-Content $mysqlLog -Tail 20; throw 'Isolated MySQL did not become ready' }
    $env:V4_ACCEPTANCE_DB = 'acceptance_v4'; $env:V4_DB_PASSWORD = $rootPassword; $env:V4_TEST_PASSWORD = $testPassword; $env:MYSQL_PWD = $rootPassword
    Get-Content -LiteralPath (Join-Path $sandbox 'schema-only.sql') -Raw -Encoding UTF8 | & $mysqlExe --no-defaults --host=127.0.0.1 --port=$port --user=root acceptance_v4
    if ($LASTEXITCODE -ne 0) { throw 'Import of schema-only fixture failed' }
    if ($DashboardLayout) {
        foreach ($name in @('20261105_user_dashboard_layout.sql', '20261106_dashboard_layout_v2.sql', '20261106_dashboard_layout_v2.sql', '20261106_add_user_token_version.sql', '20261106_add_knowledge_parse_status.sql')) {
            Get-Content -LiteralPath (Join-Path $repo "database/migration/$name") -Raw -Encoding UTF8 | & $mysqlExe --no-defaults --host=127.0.0.1 --port=$port --user=root acceptance_v4
            if ($LASTEXITCODE -ne 0) { throw "Dashboard acceptance migration failed: $name" }
        }
    }
    & (Join-Path $PSScriptRoot 'test-compliance-v4.ps1') -MigrationsOnly:$DashboardLayout
    if (-not $DashboardLayout -and $LASTEXITCODE -ne 0) { throw 'Real MySQL acceptance suite failed' }
    $candidate = Join-Path $sandbox "candidate-$stamp"
    $candidateBackend = Join-Path $candidate 'backend'
    New-Item -ItemType Directory -Path $candidateBackend | Out-Null
    Copy-Item -LiteralPath (Join-Path $repo 'backend/pom.xml') -Destination $candidateBackend
    Copy-Item -LiteralPath (Join-Path $repo 'backend/src') -Destination $candidateBackend -Recurse
    Copy-Item -LiteralPath (Join-Path $repo 'version.txt') -Destination $candidate
    # Acceptance test methods intentionally mutate shared fixture tables.
    # Re-run only the deterministic seed before the browser uses those accounts.
    & mvn -q -f (Join-Path $candidateBackend 'pom.xml') '-Dtest=ComplianceV4AcceptanceIT#browserFixture' "-Drevision=$revision" test
    if ($LASTEXITCODE -ne 0) { throw 'Browser fixture reseed failed' }

    & mvn -q -f (Join-Path $candidateBackend 'pom.xml') -DskipTests "-Drevision=$revision" package
    if ($LASTEXITCODE -ne 0) { throw 'Isolated candidate package failed' }
    $jar = Join-Path $candidateBackend 'target/expert-system.jar'
    $appLog = Join-Path $sandbox "candidate-$stamp.log"
    $psi = [Diagnostics.ProcessStartInfo]::new('java')
    $psi.Arguments = "-jar `"$jar`" --server.port=18086 --server.address=127.0.0.1 --expert.compliance.due-enabled=false --expert.compliance.v4-reminder-enabled=false --expert.wecom.sync-cron-enabled=false"
    $fixtureFiles=Join-Path $sandbox "files-$stamp"
    foreach($key in @('photo.storage-dir','photo.temp-dir','knowledge.storage-dir','knowledge.preview-dir','customer.storage-dir','supplier.storage-dir','project.storage-dir')) {
        $fixturePath=Join-Path $fixtureFiles $key
        $psi.Arguments += " --expert.$key=`"$fixturePath`""
    }
    $childEnv=$psi.EnvironmentVariables
    $childEnv['MYSQL_HOST']='127.0.0.1'; $childEnv['MYSQL_PORT']="$port"; $childEnv['MYSQL_DB']='acceptance_v4'; $childEnv['MYSQL_USER']='root'; $childEnv['MYSQL_PASSWORD']=$rootPassword
    $childEnv['COMPLIANCE_V4_ENABLED']='true'; $childEnv['COMPLIANCE_V4_REMINDER_ENABLED']='false'; $childEnv['V4_TEST_PASSWORD']=$testPassword; $childEnv['JWT_SECRET']=$jwtSecret
    $childEnv['TESSERACT_CMD']='C:\Program Files\Tesseract-OCR\tesseract.exe'; $childEnv['TESSDATA_DIR']="$env:LOCALAPPDATA\Tesseract-OCR\tessdata"
    $candidateStdout = Join-Path $sandbox "candidate-$stamp.stdout.log"; $candidateStderr = Join-Path $sandbox "candidate-$stamp.stderr.log"
    $psi.RedirectStandardOutput=$true; $psi.RedirectStandardError=$true; $psi.UseShellExecute=$false; $psi.WindowStyle='Hidden'
    $candidateProc=[Diagnostics.Process]::Start($psi)
    $candidateProc.BeginOutputReadLine(); $candidateProc.BeginErrorReadLine()
    $candidateUntil=(Get-Date).AddSeconds(90); $healthy=$false
    do { Start-Sleep 2; try { $probe=Invoke-WebRequest 'http://127.0.0.1:18086/api/health' -UseBasicParsing; $healthy=$probe.StatusCode -eq 200 } catch { if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) { $healthy=$true } } } while (-not $healthy -and (Get-Date)-lt $candidateUntil)
    if (-not $healthy) { throw 'Isolated candidate backend health check failed' }
    $env:VITE_API_PROXY_TARGET='http://127.0.0.1:18086'
    $nodeExe=(Get-Command node.exe).Source
    $viteEntry=Join-Path $repo 'frontend/node_modules/vite/bin/vite.js'
    $viteStdout=Join-Path $sandbox "vite-$stamp.stdout.log"; $viteStderr=Join-Path $sandbox "vite-$stamp.stderr.log"
    $viteArgs=@("`"$viteEntry`"",'--host','127.0.0.1','--port','5173','--strictPort')
    if ($DashboardLayout) {
        $dist = Join-Path $candidate 'frontend-dist'
        $env:VITE_DASHBOARD_LAYOUT_V2='true'
        & node $viteEntry build --outDir $dist --config (Join-Path $repo 'frontend/vite.config.js') (Join-Path $repo 'frontend')
        if ($LASTEXITCODE -ne 0) { throw 'Dashboard candidate build failed' }
        $viteArgs=@("`"$viteEntry`"",'preview','--outDir',"`"$dist`"",'--host','127.0.0.1','--port','5173','--strictPort')
    }
    $viteProc=Start-Process -FilePath $nodeExe -ArgumentList $viteArgs -WorkingDirectory (Join-Path $repo 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput $viteStdout -RedirectStandardError $viteStderr
    $viteUntil=(Get-Date).AddSeconds(45); $viteReady=$false
    do { Start-Sleep 1; try { $viteReady=(Invoke-WebRequest 'http://localhost:5173/login' -UseBasicParsing).StatusCode -eq 200 } catch {} } while (-not $viteReady -and (Get-Date)-lt $viteUntil)
    if (-not $viteReady) { Get-Content $viteStderr -Tail 30 -ErrorAction SilentlyContinue; Get-Content $viteStdout -Tail 30 -ErrorAction SilentlyContinue; throw 'Vite candidate did not become ready' }
    $playwrightRoot=Join-Path $sandbox 'playwright'
    if (-not (Test-Path (Join-Path $playwrightRoot 'node_modules/playwright/index.mjs'))) { & npm install --prefix $playwrightRoot playwright; if ($LASTEXITCODE -ne 0) { throw 'Playwright install failed' } }
    $env:LIVE_V4_ACCEPTANCE='acceptance_v4'; $env:PLAYWRIGHT_MODULE=([Uri](Join-Path $playwrightRoot 'node_modules/playwright/index.mjs')).AbsoluteUri; $env:V4_SAMPLE_PDF='E:\王创\2026-11-4\北京融和医学发展基金会合规要求.pdf'
    if ($env:V4_DATABASE_ONLY -eq 'true') { 'PASS: database-only acceptance'; return }
    Push-Location (Join-Path $repo 'frontend')
    try { $browserScript=if($DashboardLayout){'tests/dashboard-layout-live.mjs'}elseif($env:V4_VISUAL_EDITOR -eq 'true'){'tests/compliance-visual-live.mjs'}else{'tests/compliance-v4-live.mjs'}; & node $browserScript; if ($LASTEXITCODE -ne 0) { throw 'Real Edge browser acceptance failed' } } finally { Pop-Location }
    'PASS: isolated MySQL migrations/acceptance and real Edge -> Vite -> Spring -> MySQL browser flow.'
} finally {
    Remove-Item -LiteralPath $initFile -Force -ErrorAction SilentlyContinue
    $env:MYSQL_PWD=$null; $env:V4_DB_PASSWORD=$null; $env:V4_TEST_PASSWORD=$null; $env:JWT_SECRET=$null
    $OutputEncoding = $oldOutputEncoding
    $env:VITE_DASHBOARD_LAYOUT_V2 = $oldDashboardLayout
    if ($viteProc -and -not $viteProc.HasExited) { Stop-Process -Id $viteProc.Id -Force }
    if ($candidateProc -and -not $candidateProc.HasExited) { Stop-Process -Id $candidateProc.Id -Force }
    # Kill the listener and any MySQL child process for this exact config only.
    $owned = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'mysqld.exe' -and $_.CommandLine -and $_.CommandLine.Contains($config) }
    foreach ($p in $owned) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }
    $env:MYSQL_HOST=$null; $env:MYSQL_PORT=$null; $env:MYSQL_DB=$null; $env:MYSQL_USER=$null; $env:MYSQL_PASSWORD=$null; $env:COMPLIANCE_V4_ENABLED=$null; $env:COMPLIANCE_V4_REMINDER_ENABLED=$null; $env:VITE_API_PROXY_TARGET=$null; $env:PLAYWRIGHT_MODULE=$null; $env:LIVE_V4_ACCEPTANCE=$null; $env:V4_SAMPLE_PDF=$null
}



