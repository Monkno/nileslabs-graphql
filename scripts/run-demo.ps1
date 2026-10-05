param([switch]$Quick, [switch]$OnlyOverload)
if ($Quick -and $OnlyOverload) { throw 'Choose Quick or OnlyOverload' }
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
Push-Location $repoPath
try {
    $wrapper = if ($IsWindows -or $env:OS -eq 'Windows_NT') { Join-Path $repoPath 'mvnw.cmd' } else { Join-Path $repoPath 'mvnw' }
    New-Item -ItemType Directory -Force artifacts/demo | Out-Null
    function Invoke-DemoRun([string]$Name, [string[]]$Parameters, [bool]$ExpectFailure = $false) {
        Write-Host "Running $Name against the local fixture..."
        $logPath = Join-Path $repoPath "artifacts/demo/$Name.log"
        $resultPath = Join-Path $repoPath "target/gatling/$Name-$([guid]::NewGuid().ToString('N'))"
        & $wrapper --batch-mode gatling:test "-Dgatling.resultsFolder=$resultPath" @Parameters *> $logPath
        $code = $LASTEXITCODE
        $log = Get-Content -LiteralPath $logPath -Raw
        if ($log -match 'ResponseProcessor crashed|Failed to build request|Failed to build the scenario') { throw "$Name has an internal error, not a valid test result. See $logPath" }
        $newReports = @(Get-ChildItem -LiteralPath $resultPath -Directory -ErrorAction SilentlyContinue)
        if ($newReports.Count -ne 1) { throw "$Name did not produce exactly one new Gatling report. See $logPath" }
        $html = Get-Content -LiteralPath (Join-Path $newReports[0].FullName 'index.html') -Raw
        $row = [regex]::Match($html, '(?s)<tr id="ROOT".*?</tr>').Value
        $cells = @{}
        [regex]::Matches($row, '<td class="value[^"\r\n]* col-(\d+)">([^<]+)</td>') | ForEach-Object { $cells[[int]$_.Groups[1].Value] = [double]::Parse($_.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture) }
        if (-not $cells.ContainsKey(2) -or $cells[2] -lt 3) { throw "$Name report has no complete request evidence" }
        if ($ExpectFailure) {
            if ($code -eq 0 -or $cells[4] -lt 1 -or $log -notmatch 'Global: count of failed events is 0.0 : false') { throw "$Name did not demonstrate a failed Gatling assertion. See $logPath" }
            $reason = switch ($Name) {
                'negative-graphql-error' { 'GraphQL error: Deliberate fixture GraphQL failure' }
                'congested-spike' { 'GraphQL error: Deliberate fixture GraphQL failure' }
                'negative-null-data' { 'jsonPath\(\$\.data\..*found (nothing|0)' }
                'negative-invalid-json' { 'Jackson failed to parse into a valid AST' }
                'negative-http-error' { 'status\.find\.is\(200\), found 500' }
                default { throw "No expected failure reason configured for $Name" }
            }
            if ($log -notmatch $reason -or $log -notmatch 'Fixture: [1-9]\d* HTTP requests') { throw "$Name failed for an unexpected reason. See $logPath" }
        } elseif ($code -ne 0) { throw "$Name failed. See $logPath" }
        [pscustomobject]@{ name = $Name; exitCode = $code; expectedFailure = $ExpectFailure; total = $cells[2]; ok = $cells[3]; ko = $cells[4]; requestsPerSecond = $cells[6]; p95Ms = $cells[10]; p99Ms = $cells[11]; report = $newReports[0].FullName } |
            ConvertTo-Json | Set-Content -LiteralPath "artifacts/demo/$Name.json"
        Write-Host "$Name verified. Report: $($newReports[0].FullName)"
    }
    $profiles = if ($OnlyOverload) { @() } elseif ($Quick) { @('smoke') } else { @('smoke', 'load', 'spike', 'stress', 'soak') }
    foreach ($profile in $profiles) {
        Invoke-DemoRun $profile @("-Dprofile=$profile", '-Dtarget=fixture', '-DstressApproved=true')
    }
    if (-not $OnlyOverload) {
        Invoke-DemoRun 'native-features' @('-Dgatling.simulationClass=performance.NativeFeaturesSimulation', '-Dtarget=fixture')
        foreach ($mode in @('graphql-error', 'null-data', 'invalid-json', 'http-error')) {
            Invoke-DemoRun "negative-$mode" @('-Dprofile=smoke', '-Dtarget=fixture', "-DfixtureMode=$mode") $true
        }
    }
    if (-not $Quick) {
        Invoke-DemoRun 'congested-load' @('-Dprofile=load', '-Dtarget=fixture', '-DfixtureMode=congested')
        Invoke-DemoRun 'congested-spike' @('-Dprofile=spike', '-Dtarget=fixture', '-DfixtureMode=congested') $true
    }
    Write-Host 'Demo completed without Gatling Cloud credits. Evidence: artifacts/demo/ and target/gatling/.'
} finally { Pop-Location }

exit 0
