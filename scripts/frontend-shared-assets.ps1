# Sourced by deployment, rollback and retention scripts. All mutations hold the frontend lock.
$FrontendAssetGraceDays = 7
$FrontendAssetManifest = '.asset-manifest.json'

function Enter-FrontendDeploymentLock([string]$FrontendDir) {
    $path = Join-Path $FrontendDir '.deployment.lock'
    try {
        return [IO.File]::Open($path, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch {
        throw "Cannot acquire frontend deployment lock ($path): $($_.Exception.Message)"
    }
}

function Get-ReleaseAssetNames([string]$ReleaseDir) {
    $manifest = Join-Path $ReleaseDir $FrontendAssetManifest
    if (Test-Path -LiteralPath $manifest -PathType Leaf) {
        $record = Get-Content -LiteralPath $manifest -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($record.schema -ne 1 -or !$record.names.Count) { throw "Invalid asset manifest: $manifest" }
        foreach ($name in $record.names) {
            if ($name -notmatch '^[a-zA-Z0-9][a-zA-Z0-9._-]*$') { throw "Invalid asset name in $manifest" }
            $name
        }
        return
    }
    $assets = Join-Path $ReleaseDir 'assets'
    if (Get-ChildItem -LiteralPath $assets -Force -Directory) { throw "Expected flat Vite assets: $assets" }
    Get-ChildItem -LiteralPath $assets -Force -File | ForEach-Object {
        if ($_.Name -notmatch '^[a-zA-Z0-9][a-zA-Z0-9._-]*$' -or ($_.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Unsupported asset: $($_.FullName)"
        }
        $_.Name
    }
}

function Read-FrontendAssetLeases([string]$Store) {
    $leases = @{}
    $file = Join-Path $Store 'leases.json'
    if (Test-Path -LiteralPath $file -PathType Leaf) {
        $data = Get-Content -LiteralPath $file -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($data.schema -ne 1 -or $null -eq $data.assets) { throw "Invalid asset leases: $file" }
        foreach ($item in $data.assets.PSObject.Properties) {
            if ($item.Name -notmatch '^[a-zA-Z0-9][a-zA-Z0-9._-]*$') { throw "Invalid asset name in $file" }
            $leases[$item.Name] = ([datetimeoffset]$item.Value).UtcDateTime
        }
    }
    return $leases
}

function Save-FrontendAssetLeases([string]$Store, [hashtable]$Leases) {
    $file = Join-Path $Store 'leases.json'
    $temporary = Join-Path $Store ("leases-" + [guid]::NewGuid().ToString('N') + '.tmp')
    $backup = "$temporary.previous"
    try {
        $values = @{}
        foreach ($name in $Leases.Keys) { $values[$name] = $Leases[$name].ToString('o') }
        @{ schema = 1; assets = $values } | ConvertTo-Json -Depth 4 -Compress |
            Set-Content -LiteralPath $temporary -Encoding UTF8
        if (Test-Path -LiteralPath $file) { [IO.File]::Replace($temporary, $file, $backup) }
        else { [IO.File]::Move($temporary, $file) }
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
        if (Test-Path -LiteralPath $backup) { Remove-Item -LiteralPath $backup }
    }
}

function Add-SharedFrontendAsset([string]$Source, [string]$Destination) {
    if ((Get-Item -LiteralPath $Source).Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Asset source is a reparse point: $Source"
    }
    if (Test-Path -LiteralPath $Destination) {
        if ((Get-FileHash -LiteralPath $Source).Hash -ne (Get-FileHash -LiteralPath $Destination).Hash) {
            throw "Immutable asset name collision: $(Split-Path $Destination -Leaf)"
        }
        return
    }
    # A hardlink publishes the entire immutable file atomically on the same NTFS volume.
    New-Item -ItemType HardLink -Path $Destination -Target $Source -ErrorAction Stop | Out-Null
}

function Copy-FrontendReleaseEntry([string]$Source, [string]$Destination) {
    New-Item -ItemType Directory -Path (Join-Path $Destination 'assets') -Force | Out-Null
    Get-ChildItem -LiteralPath $Source -Force | Where-Object Name -notin @('assets', $FrontendAssetManifest) |
        ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $Destination -Recurse }
}

function Add-FrontendAssetLinks([string]$Store, [string]$ReleaseDir, [string[]]$Names) {
    foreach ($name in $Names) {
        $link = Join-Path (Join-Path $ReleaseDir 'assets') $name
        if (!(Test-Path -LiteralPath $link)) {
            New-Item -ItemType HardLink -Path $link -Target (Join-Path (Join-Path $Store 'files') $name) -ErrorAction Stop | Out-Null
        }
    }
}

function New-SharedFrontendRelease([string]$FrontendDir, [string]$Stage, [string]$Previous, [string]$Target, [string]$Rollback) {
    $store = Join-Path $FrontendDir 'shared-assets'
    $files = Join-Path $store 'files'
    New-Item -ItemType Directory -Path $files -Force | Out-Null
    $leases = Read-FrontendAssetLeases $store
    $now = [datetime]::UtcNow
    $native = @(Get-ReleaseAssetNames $Stage)
    $previousNative = @(Get-ReleaseAssetNames $Previous)
    if (!$native.Count -or !$previousNative.Count) { throw 'Missing build or previous release assets' }
    foreach ($name in $native) {
        Add-SharedFrontendAsset (Join-Path (Join-Path $Stage 'assets') $name) (Join-Path $files $name)
        $leases[$name] = $now
    }
    $previousManaged = Test-Path -LiteralPath (Join-Path $Previous $FrontendAssetManifest) -PathType Leaf
    foreach ($name in $previousNative) {
        $shared = Join-Path $files $name
        if (!$previousManaged -or !(Test-Path -LiteralPath $shared -PathType Leaf)) {
            Add-SharedFrontendAsset (Join-Path (Join-Path $Previous 'assets') $name) $shared
        }
        $leases[$name] = $now
    }
    Save-FrontendAssetLeases $store $leases
    $cutoff = $now.AddDays(-$FrontendAssetGraceDays)
    $compatible = @($leases.Keys | Where-Object { $leases[$_] -ge $cutoff })
    Copy-FrontendReleaseEntry $Stage $Target
    Copy-FrontendReleaseEntry $Previous $Rollback
    foreach ($release in @(@{ path = $Target; names = $native }, @{ path = $Rollback; names = $previousNative })) {
        Add-FrontendAssetLinks $store $release.path $compatible
        @{ schema = 1; names = $release.names } | ConvertTo-Json -Depth 3 -Compress |
            Set-Content -LiteralPath (Join-Path $release.path $FrontendAssetManifest) -Encoding UTF8
    }
}

function Update-FrontendRollbackAssets([string]$FrontendDir, [string]$Current, [string]$Rollback) {
    if (!(Test-Path -LiteralPath (Join-Path $Rollback $FrontendAssetManifest))) { return }
    $store = Join-Path $FrontendDir 'shared-assets'
    $leases = Read-FrontendAssetLeases $store
    $now = [datetime]::UtcNow
    foreach ($release in @($Current, $Rollback)) {
        $managed = Test-Path -LiteralPath (Join-Path $release $FrontendAssetManifest) -PathType Leaf
        foreach ($name in Get-ReleaseAssetNames $release) {
            $shared = Join-Path (Join-Path $store 'files') $name
            if (!$managed -or !(Test-Path -LiteralPath $shared -PathType Leaf)) {
                Add-SharedFrontendAsset (Join-Path (Join-Path $release 'assets') $name) $shared
            }
            $leases[$name] = $now
        }
    }
    Save-FrontendAssetLeases $store $leases
    $cutoff = $now.AddDays(-$FrontendAssetGraceDays)
    Add-FrontendAssetLinks $store $Rollback @($leases.Keys | Where-Object { $leases[$_] -ge $cutoff })
}

function Remove-ExpiredFrontendAssets([string]$FrontendDir, [object[]]$Releases, [switch]$DryRun) {
    $store = Join-Path $FrontendDir 'shared-assets'
    $files = Join-Path $store 'files'
    if (!(Test-Path -LiteralPath (Join-Path $store 'leases.json') -PathType Leaf)) { return }
    $leases = Read-FrontendAssetLeases $store
    $cutoff = [datetime]::UtcNow.AddDays(-$FrontendAssetGraceDays)
    $protected = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $managed = @($Releases | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName $FrontendAssetManifest) -PathType Leaf })
    if (!$managed.Count) { return }
    foreach ($release in $managed) {
        foreach ($name in Get-ReleaseAssetNames $release.FullName) { [void]$protected.Add($name) }
    }
    $expired = @($leases.Keys | Where-Object { $leases[$_] -lt $cutoff -and !$protected.Contains($_) })
    if (!$expired.Count) { return }
    if ($DryRun) { Write-Host "[DRYRUN] shared assets eligible for expiration: $($expired.Count)"; return }
    foreach ($name in $expired) {
        foreach ($release in $managed) {
            $link = Join-Path (Join-Path $release.FullName 'assets') $name
            if (Test-Path -LiteralPath $link -PathType Leaf) { Remove-Item -LiteralPath $link -ErrorAction Stop }
        }
        $shared = Join-Path $files $name
        if (Test-Path -LiteralPath $shared -PathType Leaf) { Remove-Item -LiteralPath $shared -ErrorAction Stop }
        $leases.Remove($name)
    }
    Save-FrontendAssetLeases $store $leases
    Write-Host "[OK] shared assets expired: $($expired.Count)"
}
