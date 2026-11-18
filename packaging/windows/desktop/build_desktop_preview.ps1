#Requires -Version 5.1
param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$version = '1.0.4314.35'
$sha256 = '90778DEB1B77E36292828B4B5833C9255C27533F40E0DDD55891AE718944EDC4'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$out = (New-Item -ItemType Directory -Force -Path $OutputDirectory).FullName
$stage = Join-Path $out ("package-" + [Guid]::NewGuid().ToString('N'))
[void](New-Item -ItemType Directory -Path $stage)
try {
    $package = Join-Path $stage 'webview2.nupkg'
    Invoke-WebRequest -UseBasicParsing "https://api.nuget.org/v3-flatcontainer/microsoft.web.webview2/$version/microsoft.web.webview2.$version.nupkg" -OutFile $package
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $package).Hash -ne $sha256) { throw 'Unexpected WebView2 SDK bytes' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($package)
    try {
        foreach ($name in 'lib/net462/Microsoft.Web.WebView2.Core.dll','lib/net462/Microsoft.Web.WebView2.WinForms.dll','runtimes/win-x64/native/WebView2Loader.dll') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry($name), (Join-Path $stage ([IO.Path]::GetFileName($name))))
        }
    } finally { $zip.Dispose() }
    $csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
    & $csc /nologo /codepage:65001 /target:winexe /platform:x64 "/win32icon:$root/nous.ico" "/out:$stage/NOUS Desktop Preview.exe" /r:System.Drawing.dll /r:System.Windows.Forms.dll "/r:$stage/Microsoft.Web.WebView2.Core.dll" "/r:$stage/Microsoft.Web.WebView2.WinForms.dll" (Join-Path $PSScriptRoot 'NousDesktop.cs')
    if ($LASTEXITCODE -ne 0) { throw 'Desktop preview build failed' }
    Get-ChildItem -LiteralPath $stage -File | Where-Object Extension -In '.exe','.dll' | Copy-Item -Destination $out -Force
} finally {
    Remove-Item -LiteralPath $stage -Recurse -Force
}
Write-Host "NOUS desktop preview: $out"
