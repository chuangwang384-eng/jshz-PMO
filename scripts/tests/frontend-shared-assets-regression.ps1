#requires -Version 5.1
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../frontend-shared-assets.ps1')
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$fixtures = Join-Path $repo 'temp'
New-Item -ItemType Directory -Path $fixtures -Force | Out-Null
$fixture = Join-Path $fixtures ('frontend-shared-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixture | Out-Null

function Assert-True([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
}

function New-FixtureRelease([string]$Path, [string]$Index, [hashtable]$Assets) {
    $dir = Join-Path $Path 'assets'
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $Path 'index.html') -Value $Index -Encoding UTF8
    foreach ($name in $Assets.Keys) {
        Set-Content -LiteralPath (Join-Path $dir $name) -Value $Assets[$name] -Encoding UTF8
    }
}

try {
    $old = Join-Path $fixture 'legacy'
    $stage = Join-Path $fixture 'stage'
    $releases = Join-Path $fixture 'releases'
    $next = Join-Path $releases 'r2'
    $rollback = Join-Path $releases 'r2.rollback'
    $store = Join-Path $fixture 'shared-assets'
    New-FixtureRelease $old 'old index' @{ 'old-111.js' = 'old'; 'common-222.js' = 'same'; 'carry-333.js' = 'carry' }
    New-FixtureRelease $stage 'new index' @{ 'new-444.js' = 'new'; 'common-222.js' = 'same' }
    $lock = Enter-FrontendDeploymentLock $fixture
    try {
        $blocked = $false
        try { $other = Enter-FrontendDeploymentLock $fixture; $other.Dispose() } catch { $blocked = $true }
        Assert-True $blocked 'A concurrent deployment acquired the lock'
        New-SharedFrontendRelease $fixture $stage $old $next $rollback
    } finally { $lock.Dispose() }

    Assert-True ((Get-Content -LiteralPath "$next/index.html") -eq 'new index') 'Wrong release HTML'
    Assert-True ((Get-Content -LiteralPath "$rollback/index.html") -eq 'old index') 'Wrong rollback HTML'
    Assert-True ((Get-ReleaseAssetNames $next) -notcontains 'old-111.js') 'Manifest includes compatibility links'
    Assert-True ((Test-Path -LiteralPath "$next/assets/old-111.js") -and (Test-Path -LiteralPath "$rollback/assets/new-444.js")) 'Compatibility links missing'
    $links = (& fsutil.exe hardlink list "$store/files/common-222.js") -join ' '
    Assert-True ($links -match '\\releases\\r2\\assets\\common-222.js' -and $links -match '\\releases\\r2\.rollback\\assets\\common-222.js') 'Shared file is not linked from both snapshots'

    $stage3 = Join-Path $fixture 'stage3'
    $latest = Join-Path $releases 'r3'
    $latestRollback = Join-Path $releases 'r3.rollback'
    New-FixtureRelease $stage3 'third index' @{ 'third-555.js' = 'third' }
    New-SharedFrontendRelease $fixture $stage3 $next $latest $latestRollback
    $leases = Read-FrontendAssetLeases $store
    $leases['old-111.js'] = [datetime]::UtcNow.AddDays(-8)
    $leases['carry-333.js'] = [datetime]::UtcNow.AddDays(-8)
    $leases['new-444.js'] = [datetime]::UtcNow.AddDays(-8)
    Save-FrontendAssetLeases $store $leases

    $retained = @(Get-Item -LiteralPath $next,$latest,$latestRollback)
    Remove-ExpiredFrontendAssets $fixture $retained -DryRun
    Assert-True (Test-Path -LiteralPath "$store/files/carry-333.js") 'Dry run deleted an asset'
    Remove-ExpiredFrontendAssets $fixture $retained
    Assert-True (!(Test-Path -LiteralPath "$store/files/carry-333.js")) 'Expired compatibility asset retained'
    Assert-True (!(Test-Path -LiteralPath "$latest/assets/old-111.js")) 'Expired compatibility link retained'
    Assert-True (Test-Path -LiteralPath "$store/files/new-444.js") 'Manifest reference failed to protect asset'
    Assert-True (Test-Path -LiteralPath "$old/assets/carry-333.js") 'Legacy original was touched'
    Update-FrontendRollbackAssets $fixture $latest $next
    Assert-True (Test-Path -LiteralPath "$next/assets/third-555.js") 'Rollback cannot serve outgoing browser asset'

    $conflict = Join-Path $fixture 'conflict.js'
    Set-Content -LiteralPath $conflict -Value 'different' -Encoding UTF8
    $rejected = $false
    try { Add-SharedFrontendAsset $conflict "$store/files/common-222.js" } catch { $rejected = $_.Exception.Message -match 'collision' }
    Assert-True $rejected 'An immutable name collision was accepted'

    $metadata = Join-Path $fixture 'release-metadata'
    New-Item -ItemType Directory -Path $metadata | Out-Null
    $conf = Join-Path $fixture 'nginx.conf'
    Set-Content -LiteralPath $conf -Value ('root "' + $latest.Replace('\','/') + '";')
    Set-Content -LiteralPath "$metadata/r3.nginx-before.txt" -Value ('root "' + $next.Replace('\','/') + '";')
    Set-Content -LiteralPath "$metadata/r2.nginx-before.txt" -Value ('root "' + $old.Replace('\','/') + '";')
    $prune = Join-Path $PSScriptRoot '../prune-frontend-releases.ps1'
    $argsForPrune = @{
        FrontendDir = $fixture
        NginxConf = $conf
        KeepReleases = 2
        CleanupWorkers = if ($PSVersionTable.PSVersion.Major -ge 7) { 2 } else { 1 }
        KeepLegacyDist = $true
    }
    & $prune @argsForPrune -DryRun
    Assert-True (Test-Path -LiteralPath $rollback) 'Dry run removed a retired rollback'
    & $prune @argsForPrune
    Assert-True (!(Test-Path -LiteralPath $rollback)) 'Failed to remove a retired rollback'
    Assert-True ((Test-Path -LiteralPath $next) -and (Test-Path -LiteralPath $latestRollback)) 'Lost a rollback safety target'
    Write-Host 'PASS: shared links, deployment lock, legacy upgrade, rollback and seven-day collection'
} finally {
    $resolved = (Resolve-Path -LiteralPath $fixture).Path
    $parent = (Resolve-Path -LiteralPath $fixtures).Path
    if ((Split-Path -Parent $resolved) -ne $parent -or (Split-Path -Leaf $resolved) -notlike 'frontend-shared-test-*') {
        throw "Rejected fixture cleanup: $resolved"
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
