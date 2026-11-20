param(
 [Parameter(Mandatory=$true)][string]$SetupPath,
 [Parameter(Mandatory=$true)][string]$MsiPath,
 [string]$CacheDir = (Join-Path $env:TEMP 'nous-setup-verifier')
)
$ErrorActionPreference='Stop'
$setup=(Resolve-Path -LiteralPath $SetupPath).Path
# Read resources only. Never execute either installer layer or any MSI actions.
try {$assembly=[Reflection.Assembly]::ReflectionOnlyLoadFrom($setup)}
catch [BadImageFormatException] {$assembly=$null}
$work=$null
try {
 if($null -eq $assembly) {
  New-Item -ItemType Directory -Path $CacheDir -Force | Out-Null
  $archive=Join-Path $CacheDir 'innounp-2.71.1.zip'
  $extractorHash='f2f037fdbc63de31248efae9ccb294398d160dc0cbad5f36d14c2f159e17bbf5'
  if(!(Test-Path -LiteralPath $archive)) {
   $partial=Join-Path $CacheDir ([Guid]::NewGuid().ToString('N')+'.partial')
   try {
    [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -UseBasicParsing -Uri 'https://raw.githubusercontent.com/jrathlev/InnoUnpacker-Windows-GUI/6fb49264aacf512a093e7b4fc6fb3dd266dad31a/innounp-2/bin/innounp-2.zip' -OutFile $partial
    if((Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash -ne $extractorHash){throw 'Setup extractor download failed SHA-256 verification'}
    Move-Item -LiteralPath $partial -Destination $archive
   } finally {if(Test-Path -LiteralPath $partial){Remove-Item -LiteralPath $partial -Force}}
  }
  if((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $extractorHash){throw 'Cached setup extractor failed SHA-256 verification'}
  $work=Join-Path $CacheDir ('verify-'+[Guid]::NewGuid().ToString('N'))
  Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $work 'unpacker')
  $output=Join-Path $work 'payload'
  & (Join-Path $work 'unpacker\innounp.exe') -x -q -y "-d$output" $setup '{tmp}\nous-install-engine.exe' | Out-Null
  if($LASTEXITCODE -ne 0){throw 'Inno Setup engine extraction failed'}
  $engine=Join-Path $output '{tmp}\nous-install-engine.exe'
  $assembly=[Reflection.Assembly]::ReflectionOnlyLoad([IO.File]::ReadAllBytes($engine))
 }
 $stream=$assembly.GetManifestResourceStream('Payload.msi')
 if($null -eq $stream){throw 'Setup engine has no embedded Payload.msi'}
 $hasher=[Security.Cryptography.SHA256]::Create()
 try {
  $embedded=([BitConverter]::ToString($hasher.ComputeHash($stream))).Replace('-','').ToLowerInvariant()
 } finally {$stream.Dispose();$hasher.Dispose()}
} finally {
 if($work -and (Test-Path -LiteralPath $work)){Remove-Item -LiteralPath $work -Recurse -Force}
}
$expected=(Get-FileHash -LiteralPath $MsiPath -Algorithm SHA256).Hash.ToLowerInvariant()
if($embedded -cne $expected){throw 'Setup embedded MSI differs from the exact qualified internal MSI'}
$installer=New-Object -ComObject WindowsInstaller.Installer
$database=$installer.OpenDatabase((Resolve-Path -LiteralPath $MsiPath).Path,0)
function Read-MsiProperty([string]$Name) {
 $view=$database.OpenView("SELECT ``Value`` FROM ``Property`` WHERE ``Property``='$Name'")
 try{$null=$view.Execute();$record=$view.Fetch();if($null -eq $record){throw "Missing MSI property $Name"};return $record.StringData(1)}finally{$null=$view.Close()}
}
# ProductCode ordering is a setup hint; MSI ordering/replacement comes from this table.
$upgradeCode=Read-MsiProperty 'UpgradeCode'
if((Read-MsiProperty 'VIBESHINE_TRANSACTIONAL_REPLACEMENT') -ne '1'){throw 'MSI lacks transactional replacement contract'}
$view=$database.OpenView('SELECT `UpgradeCode`,`VersionMin`,`VersionMax`,`Attributes`,`ActionProperty` FROM `Upgrade`')
$replacement=$false
try {
 $null=$view.Execute()
 while($row=$view.Fetch()) {
  if($row.StringData(1) -eq $upgradeCode -and $row.StringData(2) -eq '0' -and
     $row.StringData(3) -eq '' -and ([int]$row.IntegerData(4) -band 256) -ne 0 -and
     ([int]$row.IntegerData(4) -band 2) -eq 0 -and $row.StringData(5) -eq 'WIX_UPGRADE_DETECTED'){$replacement=$true}
 }
} finally {$null=$view.Close()}
if(!$replacement){throw 'MSI Upgrade table does not include same-version replacements'}
$sequence=@{}
$view=$database.OpenView('SELECT `Action`,`Sequence` FROM `InstallExecuteSequence`')
try{$null=$view.Execute();while($row=$view.Fetch()){$sequence[$row.StringData(1)]=[int]$row.IntegerData(2)}}finally{$null=$view.Close()}
if(!$sequence.ContainsKey('RemoveExistingProducts') -or
   $sequence['RemoveExistingProducts'] -le $sequence['InstallInitialize'] -or
   $sequence['RemoveExistingProducts'] -ge $sequence['InstallFinalize']){throw 'MSI replacement must run within the rollback transaction'}
[pscustomobject]@{
 verified=$true;sha256=$embedded;upgrade_table_verified=$true
 setup_file_version=[Diagnostics.FileVersionInfo]::GetVersionInfo((Resolve-Path -LiteralPath $SetupPath).Path).FileVersion
 msi_product_version=Read-MsiProperty 'ProductVersion'
 msi_product_code=Read-MsiProperty 'ProductCode'
 msi_upgrade_code=Read-MsiProperty 'UpgradeCode'
} | ConvertTo-Json -Compress
