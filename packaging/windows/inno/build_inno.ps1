#Requires -Version 5.1
param(
    [Parameter(Mandatory=$true)][string]$EnginePath,
    [Parameter(Mandatory=$true)][string]$MsiPath,
    [Parameter(Mandatory=$true)][string]$FileVersion,
    [string]$Compiler = $env:INNO_SETUP_COMPILER
)
$ErrorActionPreference = 'Stop'
if (!$Compiler) { $Compiler = Join-Path ${env:ProgramFiles(x86)} 'Inno Setup 6\ISCC.exe' }
if (!(Test-Path -LiteralPath $Compiler)) { throw 'Inno Setup 6.7+ required. Set INNO_SETUP_COMPILER to ISCC.exe.' }
$EnginePath = (Resolve-Path -LiteralPath $EnginePath).Path
$MsiPath = (Resolve-Path -LiteralPath $MsiPath).Path
$directory = Split-Path $EnginePath
$engine = Join-Path $directory 'nous-install-engine.exe'
if ($EnginePath -ieq $engine) { throw 'EnginePath must be the output setup name, not nous-install-engine.exe' }
Copy-Item -LiteralPath $EnginePath -Destination $engine -Force
$assembly = [Reflection.Assembly]::LoadFile($engine)
$payload = $assembly.GetManifestResourceStream('Payload.msi')
if (!$payload) { throw 'Installer engine has no embedded MSI' }
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $embeddedHash = ([BitConverter]::ToString($sha.ComputeHash($payload))).Replace('-', '')
    if ($embeddedHash -ne (Get-FileHash $MsiPath -Algorithm SHA256).Hash) { throw 'Embedded MSI differs from selected MSI' }
} finally { $payload.Dispose(); $sha.Dispose() }
$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.OpenDatabase($MsiPath, 0)
function Read-Property([string]$Name) {
    $view = $database.OpenView("SELECT ``Value`` FROM ``Property`` WHERE ``Property``='$Name'")
    try { $null=$view.Execute(); $row=$view.Fetch(); if ($row) { return $row.StringData(1) }; return '' } finally { $null=$view.Close() }
}
$gamepad = Read-Property 'VhfGamepadDriverBundled'
$usbip = Read-Property 'USBIP_TRANSPORT_BUNDLED'
$thumbprint = Read-Property 'LOCAL_GAMEPAD_CERTIFICATE_THUMBPRINT'
if ($thumbprint -eq 'none') { $thumbprint = '' }
if ($thumbprint -and $thumbprint -notmatch '^[0-9A-Fa-f]{40}$') { throw 'Invalid bundled gamepad certificate thumbprint' }
$args = @(
    '/Qp', "/DEnginePath=$engine", "/DArtifactDir=$directory",
    "/DOutputBase=$([IO.Path]::GetFileNameWithoutExtension($EnginePath))",
    "/DProductVersion=$(Read-Property 'ProductVersion')", "/DFileVersion=$FileVersion",
    "/DEngineSha256=$((Get-FileHash $engine -Algorithm SHA256).Hash)",
    "/DGamepadBundled=$(if($gamepad -eq '1'){'1'}else{'0'})",
    "/DUsbIpBundled=$(if($usbip -eq '1'){'1'}else{'0'})",
    "/DLocalGamepadCertificate=$(if($thumbprint){'1'}else{'0'})",
    "/DGamepadThumbprint=$thumbprint", (Join-Path $PSScriptRoot 'NOUS.iss')
)
& $Compiler @args
if ($LASTEXITCODE -ne 0) { throw "Inno Setup compiler failed: $LASTEXITCODE" }
Write-Host "[inno] Single-file setup built: $EnginePath"
