param(
    [Parameter(Mandatory = $true)]
    [string]$SampleRoot,
    [Parameter(Mandatory = $true)]
    [string]$RuntimeRoot
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$sample = (Resolve-Path -LiteralPath $SampleRoot).Path
$runtime = [System.IO.Path]::GetFullPath($RuntimeRoot)
if (Test-Path -LiteralPath $runtime) {
    throw "Validation runtime already exists: $runtime"
}
New-Item -ItemType Directory -Path $runtime | Out-Null
$fixture = Join-Path $runtime 'architecture-samples'
New-Item -ItemType Directory -Path $fixture | Out-Null
Get-ChildItem -LiteralPath $sample -Force |
    Where-Object { $_.Name -notin @('.git', 'docs', 'prompt-package', '.docpilot') } |
    Copy-Item -Destination $fixture -Recurse

function Invoke-Gradle([string[]]$Arguments) {
    & (Join-Path $repoRoot 'gradlew.bat') @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed: $($Arguments -join ' ')"
    }
}

function Get-ArtifactState([string]$Root, [string[]]$Directories) {
    $state = @{}
    foreach ($directory in $Directories) {
        $path = Join-Path $Root $directory
        if (-not (Test-Path -LiteralPath $path)) { continue }
        Get-ChildItem -LiteralPath $path -Recurse -File | ForEach-Object {
            $relative = $_.FullName.Substring($Root.Length + 1).Replace('\', '/')
            $state[$relative] = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLower()
        }
    }
    return $state
}

Push-Location $repoRoot
try {
    Invoke-Gradle @(':run', "--args=analyze $fixture")
    $analysisFirst = Get-ArtifactState $fixture @('docs', 'prompt-package')
    Invoke-Gradle @(':run', "--args=analyze $fixture")
    $analysisSecond = Get-ArtifactState $fixture @('docs', 'prompt-package')
    $analysisDelta = @(
        Compare-Object ($analysisFirst.GetEnumerator() | ForEach-Object { "$($_.Key)|$($_.Value)" }) `
            ($analysisSecond.GetEnumerator() | ForEach-Object { "$($_.Key)|$($_.Value)" })
    ).Count

    Invoke-Gradle @(':docpilot-cli:run', "--args=generate specification --project $fixture --output $fixture")
    $specificationFirst = Get-ArtifactState $fixture @('docs/specification', 'docs/architecture')
    Invoke-Gradle @(':docpilot-cli:run', "--args=generate specification --project $fixture --output $fixture")
    $specificationSecond = Get-ArtifactState $fixture @('docs/specification', 'docs/architecture')
    $specificationDelta = @(
        Compare-Object ($specificationFirst.GetEnumerator() | ForEach-Object { "$($_.Key)|$($_.Value)" }) `
            ($specificationSecond.GetEnumerator() | ForEach-Object { "$($_.Key)|$($_.Value)" })
    ).Count

    Invoke-Gradle @(':docpilot-cli:test', '--tests', '*ReconcileCommandE2eTest')

    $report = [ordered]@{
        formatVersion = 1
        sampleRoot = $sample
        fixture = $fixture
        analysisArtifacts = $analysisSecond.Count
        analysisDeterminismDelta = $analysisDelta
        specificationArtifacts = $specificationSecond.Count
        specificationDeterminismDelta = $specificationDelta
        reconciliationCliE2e = 'PASS'
        decision = if ($analysisDelta -eq 0 -and $specificationDelta -eq 0) { 'PASS' } else { 'FAIL' }
    }
    $reportPath = Join-Path $runtime 'product-validation-result.json'
    $report | ConvertTo-Json | Set-Content -LiteralPath $reportPath -Encoding utf8
    Write-Output "Product Validation Result: $reportPath"
    if ($report.decision -ne 'PASS') { exit 2 }
} finally {
    Pop-Location
}
