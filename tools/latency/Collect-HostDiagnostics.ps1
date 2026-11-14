#Requires -Version 5.1
# Read-only inventory. No configuration, driver, service or power-plan changes.
[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path $PWD ('host-diagnostics-' + (Get-Date -Format 'yyyyMMdd-HHmmss')))
)
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $OutputDirectory) {
    throw "Output directory already exists: $OutputDirectory"
}
$null = New-Item -ItemType Directory -Path $OutputDirectory
$report = [ordered]@{
    schema_version = 1
    collected_utc = [DateTime]::UtcNow.ToString('o')
    note = 'Inventory only; not a latency benchmark or NVENC capability probe.'
}
foreach ($entry in @(
    @{ Name = 'windows'; Class = 'Win32_OperatingSystem'; Fields = @('Caption', 'Version', 'BuildNumber') },
    @{ Name = 'gpu'; Class = 'Win32_VideoController'; Fields = @('Name', 'DriverVersion', 'CurrentHorizontalResolution', 'CurrentVerticalResolution', 'CurrentRefreshRate') },
    @{ Name = 'cpu'; Class = 'Win32_Processor'; Fields = @('Name', 'NumberOfCores', 'NumberOfLogicalProcessors') }
)) {
    try {
        $report[$entry.Name] = @(Get-CimInstance -ClassName $entry.Class | Select-Object -Property $entry.Fields)
    } catch {
        $report[$entry.Name + '_error'] = $_.Exception.Message
    }
}
$smi = Get-Command nvidia-smi.exe -ErrorAction SilentlyContinue
if ($smi) {
    $output = & $smi.Source '--query-gpu=name,driver_version,memory.total,pci.device_id' '--format=csv,noheader' 2>&1
    $report['nvidia_smi_exit_code'] = $LASTEXITCODE
    $report['nvidia_smi'] = @($output | ForEach-Object { $_.ToString() })
} else {
    $report['nvidia_smi_error'] = 'nvidia-smi.exe not found'
}
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'host.json') -Encoding UTF8
Write-Output "Saved inventory: $OutputDirectory"
