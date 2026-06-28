param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

if (-not (Test-Path $Path)) {
    Write-Error "Trace file not found: $Path"
    exit 1
}

$packetRegex = [regex]'\[PACKET\]\s+bytes=(\d+)\s+hex=([0-9A-F]*)\s+vehicles=(\d+)\s+level=([A-Z_]+)'
$stateRegex = [regex]'\[STATE\]\s+level=([A-Z_]+)\s+vehicles=(\d+)'

$packetCount = 0
$stateCount = 0
$vehiclePacketCount = 0
$malformedPacketCount = 0
$levelCounts = @{}
$interestingPackets = New-Object System.Collections.Generic.List[string]
$latestStates = New-Object System.Collections.Generic.List[string]

Get-Content $Path | ForEach-Object {
    $line = $_

    $packetMatch = $packetRegex.Match($line)
    if ($packetMatch.Success) {
        $packetCount++
        $declaredBytes = [int]$packetMatch.Groups[1].Value
        $hexValue = $packetMatch.Groups[2].Value
        $vehicles = [int]$packetMatch.Groups[3].Value
        $level = $packetMatch.Groups[4].Value
        $actualBytes = [int]($hexValue.Length / 2)

        if ($actualBytes -ne $declaredBytes) {
            $malformedPacketCount++
        }
        if ($vehicles -gt 0) {
            $vehiclePacketCount++
            $interestingPackets.Add($line) | Out-Null
        }
        if (-not $levelCounts.ContainsKey($level)) {
            $levelCounts[$level] = 0
        }
        $levelCounts[$level]++
        return
    }

    $stateMatch = $stateRegex.Match($line)
    if ($stateMatch.Success) {
        $stateCount++
        $latestStates.Add($line) | Out-Null
        if ($latestStates.Count -gt 5) {
            $latestStates.RemoveAt(0)
        }
    }
}

Write-Output "Radar trace summary"
Write-Output "Path: $Path"
Write-Output "Packets: $packetCount"
Write-Output "State transitions: $stateCount"
Write-Output "Packets with vehicles: $vehiclePacketCount"
Write-Output "Malformed packet lines: $malformedPacketCount"
Write-Output ""
Write-Output "Alert levels:"
if ($levelCounts.Count -eq 0) {
    Write-Output "  none"
} else {
    $levelCounts.GetEnumerator() | Sort-Object Name | ForEach-Object {
        Write-Output ("  {0}: {1}" -f $_.Key, $_.Value)
    }
}

Write-Output ""
Write-Output "Recent states:"
if ($latestStates.Count -eq 0) {
    Write-Output "  none"
} else {
    $latestStates | ForEach-Object { Write-Output ("  " + $_) }
}

Write-Output ""
Write-Output "Interesting packets:"
if ($interestingPackets.Count -eq 0) {
    Write-Output "  none"
} else {
    $interestingPackets | Select-Object -First 10 | ForEach-Object { Write-Output ("  " + $_) }
}
