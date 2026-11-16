function Test-ExecutableBootJar {
    param([Parameter(Mandatory=$true)][string]$Path)
    if(-not (Test-Path -LiteralPath $Path -PathType Leaf)){return $false}
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip=$null
    try {
        $zip=[IO.Compression.ZipFile]::OpenRead($Path)
        $manifest=$zip.GetEntry('META-INF/MANIFEST.MF')
        if(-not $manifest){return $false}
        $reader=New-Object IO.StreamReader($manifest.Open())
        try {$text=$reader.ReadToEnd()} finally {$reader.Dispose()}
        return ($text -match '(?m)^Main-Class:\s*org\.springframework\.boot\.loader\.' -and
            @($zip.Entries | Where-Object FullName -like 'BOOT-INF/lib/*.jar').Count -gt 0)
    } catch {return $false} finally {if($zip){$zip.Dispose()}}
}
