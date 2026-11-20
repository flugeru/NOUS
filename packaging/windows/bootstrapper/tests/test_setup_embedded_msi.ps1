param(
 [Parameter(Mandatory=$true)][string]$SetupPath,
 [Parameter(Mandatory=$true)][string]$MsiPath,
 [string]$CacheDir = (Join-Path $env:TEMP 'nous-setup-verifier')
)
$ErrorActionPreference='Stop'
$verify=Join-Path $PSScriptRoot '..\..\..\..\scripts\verify_setup_embedded_msi.ps1'
$work=Join-Path $env:TEMP ('nous-setup-test-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
function Invoke-Verifier([string]$Payload,[string]$Cache) {
 $arguments=@('-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',"`"$verify`"",'-SetupPath',"`"$((Resolve-Path $SetupPath).Path)`"",'-MsiPath',"`"$Payload`"",'-CacheDir',"`"$Cache`"")
 $process=Start-Process powershell.exe -ArgumentList $arguments -Wait -PassThru -NoNewWindow -RedirectStandardOutput "$work\stdout.txt" -RedirectStandardError "$work\stderr.txt"
 return @{code=$process.ExitCode;output=(Get-Content "$work\stdout.txt" -Raw);error=(Get-Content "$work\stderr.txt" -Raw)}
}
try {
 $result=Invoke-Verifier (Resolve-Path $MsiPath).Path ([IO.Path]::GetFullPath($CacheDir))
 if($result.code -ne 0){throw $result.error}
 $proof=$result.output | ConvertFrom-Json
 if(!$proof.verified -or !$proof.upgrade_table_verified -or $proof.sha256 -ne (Get-FileHash -LiteralPath $MsiPath).Hash){throw 'Qualified MSI proof failed'}
 $wrong=Join-Path $work 'wrong.msi'
 [IO.File]::WriteAllText($wrong,'Not the qualified MSI')
 $result=Invoke-Verifier $wrong ([IO.Path]::GetFullPath($CacheDir))
 if($result.code -eq 0 -or $result.error -notlike '*differs from the exact qualified internal MSI*'){throw "Wrong MSI rejection failed: $($result.error)"}
 $badCache=Join-Path $work 'bad-cache'
 New-Item -ItemType Directory -Path $badCache | Out-Null
 [IO.File]::WriteAllText((Join-Path $badCache 'innounp-2.71.1.zip'),'Corrupt cached extractor')
 $result=Invoke-Verifier (Resolve-Path $MsiPath).Path $badCache
 if($result.code -eq 0 -or $result.error -notlike '*Cached setup extractor failed SHA-256 verification*'){throw "Corrupt cache rejection failed; test requires Inno Setup: $($result.error)"}
} finally {Remove-Item -LiteralPath $work -Recurse -Force}
Write-Host '3 setup verification cases passed: qualified payload, wrong payload, corrupt extractor. No installer executed.'
