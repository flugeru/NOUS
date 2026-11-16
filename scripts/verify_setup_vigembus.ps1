param([string]$SetupPath = "$PSScriptRoot\..\build\cpack_artifacts\setup.exe")
$ErrorActionPreference = 'Stop'
# Read resources only. Never execute setup or install drivers on the test host.
$assembly = [Reflection.Assembly]::LoadFile((Resolve-Path -LiteralPath $SetupPath).Path)
$stream = $assembly.GetManifestResourceStream('ViGEmBus.exe')
if ($null -eq $stream) { throw 'Embedded ViGEmBus missing' }
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $hash = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
    if ($hash -ne '89220a7865076b342892f98865f3499fb7c4cfd673159e89d352c360fd014c6a') { throw 'Embedded ViGEmBus SHA-256 mismatch' }
} finally {
    $stream.Dispose()
    $sha.Dispose()
}
$source = Get-Content "$PSScriptRoot\..\packaging\windows\bootstrapper\VibeshineInstaller.cs" -Raw
if ([regex]::Matches($source, 'var driverExitCode = InstallBundledViGEmBus\(logPath\);').Count -ne 2) { throw 'GUI/CLI driver install hooks missing' }
if (-not $source.Contains('"/quiet /norestart"')) { throw 'Silent driver install missing' }
Write-Output 'PASS: official ViGEmBus embedded; GUI/CLI hooks and silent arguments present. Driver installation not exercised.'
