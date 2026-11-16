# Read-only localhost deployment probes. HTTP/1.0 avoids .NET Framework 4.x
# response framing failures observed with the local Tomcat HTTP/1.1 endpoint.
function Get-DeploymentHttpResponse {
    param([Parameter(Mandatory=$true)][string]$Url, [int]$TimeoutSec=10)
    $uri=[Uri]$Url
    if(-not $uri.IsLoopback -or $uri.Scheme -ne 'http'){throw 'Deployment probe requires a loopback HTTP URL.'}
    $request=[Net.HttpWebRequest]::Create($uri)
    $request.Method='GET'
    $request.ProtocolVersion=[Version]'1.0'
    $request.KeepAlive=$false
    $request.Timeout=$TimeoutSec*1000
    $request.ReadWriteTimeout=$TimeoutSec*1000
    $response=$null
    try {
        try {$response=$request.GetResponse()}
        catch [Net.WebException] {if(-not $_.Exception.Response){throw};$response=$_.Exception.Response}
        $stream=New-Object IO.MemoryStream
        try {$response.GetResponseStream().CopyTo($stream);$bytes=$stream.ToArray()}
        finally {$stream.Dispose()}
        return [pscustomobject]@{StatusCode=[int]$response.StatusCode;Content=[Text.Encoding]::UTF8.GetString($bytes);ContentBytes=$bytes}
    } finally {if($response){$response.Close()}}
}
