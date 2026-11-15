param(
    [string]$BuildDir = (Join-Path $PSScriptRoot '..\build')
)

$ErrorActionPreference = 'Stop'
$installer = Join-Path $BuildDir 'cpack_artifacts\NOUSSetup.exe'
if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) {
    throw "NOUS installer missing: $installer. Build the custom installer first."
}

$process = Start-Process -FilePath (Resolve-Path -LiteralPath $installer).Path -Wait -PassThru
if ($process.ExitCode -notin @(0, 1641, 3010)) {
    throw "NOUS installer failed with exit code $($process.ExitCode)."
}
