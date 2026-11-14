#Requires -Version 5.1
<#
.SYNOPSIS
Collects bounded, read-only Android diagnostics. No root, install, settings writes or log clearing.
.DESCRIPTION
Requires an already authorized ADB device. Creates a new local output directory; never overwrites it.
Dumps may contain device/app identifiers. Review before sharing. Does not enumerate MediaCodecList
or start Perfetto; those require separate app instrumentation / explicit trace capture.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_.:-]+$')]
    [string]$Serial,
    [string]$AdbPath = 'adb',
    [string]$OutputDirectory = (Join-Path (Get-Location) ('android-diagnostics-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff')))
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$adb = (Get-Command $AdbPath -CommandType Application -ErrorAction Stop).Source
if (Test-Path -LiteralPath $OutputDirectory) {
    throw 'Output directory already exists. Choose a new directory to avoid overwriting evidence.'
}
$null = New-Item -ItemType Directory -Path $OutputDirectory
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$utf8 = New-Object System.Text.UTF8Encoding($false)
$results = New-Object System.Collections.Generic.List[object]

function Save-AdbRead {
    param([string]$Name, [string[]]$AdbArguments)
    # All remote commands below are fixed literals. Serial is validated and passed only to adb.
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $adb
    $psi.Arguments = '-s ' + $Serial + ' ' + ($AdbArguments -join ' ')
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    $started = [DateTime]::UtcNow
    try {
        if (-not $process.Start()) { throw 'Could not start adb.' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $timedOut = -not $process.WaitForExit(20000)
        if ($timedOut) { $process.Kill() }
        $process.WaitForExit()
        [System.IO.File]::WriteAllText((Join-Path $OutputDirectory ($Name + '.txt')), $stdout.GetAwaiter().GetResult(), $utf8)
        [System.IO.File]::WriteAllText((Join-Path $OutputDirectory ($Name + '.stderr.txt')), $stderr.GetAwaiter().GetResult(), $utf8)
        $results.Add([pscustomobject]@{
            name = $Name; command = ('adb -s <serial> ' + ($AdbArguments -join ' '))
            startedUtc = $started.ToString('o'); exitCode = $process.ExitCode; timedOut = $timedOut
        })
        if ($timedOut -or $process.ExitCode -ne 0) { Write-Warning "$Name failed or timed out; see stderr and manifest." }
    }
    finally { $process.Dispose() }
}

try {
    Save-AdbRead 'device-state' @('get-state')
    if (($results[0].exitCode -ne 0) -or $results[0].timedOut -or
        ([System.IO.File]::ReadAllText((Join-Path $OutputDirectory 'device-state.txt')).Trim() -ne 'device')) {
        throw 'Device unavailable or unauthorized. Authorize manually; no automatic retries.'
    }
    foreach ($property in @('ro.product.manufacturer', 'ro.product.model', 'ro.product.device',
        'ro.soc.manufacturer', 'ro.soc.model', 'ro.build.version.release', 'ro.build.version.sdk',
        'ro.build.version.security_patch', 'ro.build.fingerprint')) {
        Save-AdbRead $property @('shell', 'getprop', $property)
    }
    Save-AdbRead 'services' @('shell', 'dumpsys', '-l')
    foreach ($service in @('media.codec', 'media.player', 'media.metrics', 'display', 'thermalservice', 'battery')) {
        Save-AdbRead $service @('shell', 'dumpsys', '-t', '10', $service)
    }
    Save-AdbRead 'surface-layers' @('shell', 'dumpsys', '-t', '10', 'SurfaceFlinger', '--list')
    Save-AdbRead 'trace-categories' @('shell', 'atrace', '--list_categories')
    Save-AdbRead 'perfetto-help' @('shell', 'perfetto', '--help')
}
finally {
    $manifest = [pscustomobject]@{
        collectedUtc = [DateTime]::UtcNow.ToString('o')
        scope = 'Read-only snapshots; no codec benchmark, MediaCodecList probe or Perfetto recording.'
        results = @($results.ToArray())
    }
    [System.IO.File]::WriteAllText((Join-Path $OutputDirectory 'manifest.json'), ($manifest | ConvertTo-Json -Depth 5), $utf8)
}
Write-Output $OutputDirectory
