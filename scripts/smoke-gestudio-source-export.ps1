[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$GestudioRepository
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$platformRoot = Split-Path -Parent $PSScriptRoot
$gestudioRoot = [IO.Path]::GetFullPath($GestudioRepository)
$gestudioPom = Join-Path $gestudioRoot 'backend\pom.xml'
$platformPom = Join-Path $platformRoot 'backend\pom.xml'
$targetRoot = [IO.Path]::GetFullPath((Join-Path $platformRoot 'backend\target'))
$artifactRoot = [IO.Path]::GetFullPath((Join-Path $targetRoot 'gestudio-source-export-smoke'))
$currentOutput = Join-Path $artifactRoot 'current'
$previousOutput = Join-Path $artifactRoot 'previous'
$reportPath = Join-Path $targetRoot 'gestudio-source-export-smoke-report.json'

if (-not (Test-Path -LiteralPath $gestudioPom -PathType Leaf)) {
    throw "Gestudio backend pom not found"
}
if (-not (Test-Path -LiteralPath $platformPom -PathType Leaf)) {
    throw "Jere Platform backend pom not found"
}
if (-not $artifactRoot.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar)) {
    throw "Smoke artifact directory must remain under backend target"
}

function New-RuntimeSecret {
    $bytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

function Invoke-MavenGate {
    param(
        [string]$Repository,
        [string[]]$Arguments,
        [string]$Label
    )
    Push-Location $Repository
    try {
        & mvn @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$Label failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

$previousEnvironment = @{
    CurrentSecret = $env:GESTUDIO_SOURCE_EXPORT_SMOKE_SECRET
    PreviousSecret = $env:GESTUDIO_SOURCE_EXPORT_SMOKE_PREVIOUS_SECRET
    CurrentOutput = $env:GESTUDIO_SOURCE_EXPORT_SMOKE_OUTPUT
    PreviousOutput = $env:GESTUDIO_SOURCE_EXPORT_SMOKE_PREVIOUS_OUTPUT
}
$oldSecret = New-RuntimeSecret
$newSecret = New-RuntimeSecret
$status = 'FAIL'
$failure = $null

try {
    if (Test-Path -LiteralPath $artifactRoot) {
        Remove-Item -LiteralPath $artifactRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Path $currentOutput -Force | Out-Null
    New-Item -ItemType Directory -Path $previousOutput -Force | Out-Null

    # Produce an immutable old-secret snapshot before the receiver is available.
    $env:GESTUDIO_SOURCE_EXPORT_SMOKE_SECRET = $oldSecret
    $env:GESTUDIO_SOURCE_EXPORT_SMOKE_OUTPUT = $previousOutput
    Invoke-MavenGate -Repository $gestudioRoot -Label 'Gestudio previous-secret emitter' -Arguments @(
        '-B', '-f', $gestudioPom,
        '-Dtest=StudentSourceExportPostgreSqlTest', 'test'
    )

    # Rotate the emitter current secret and create a new checkpoint.
    $env:GESTUDIO_SOURCE_EXPORT_SMOKE_SECRET = $newSecret
    $env:GESTUDIO_SOURCE_EXPORT_SMOKE_OUTPUT = $currentOutput
    Invoke-MavenGate -Repository $gestudioRoot -Label 'Gestudio current-secret emitter' -Arguments @(
        '-B', '-f', $gestudioPom,
        '-Dtest=StudentSourceExportPostgreSqlTest', 'test'
    )

    # Start the receiver with new=current and old=previous, then consume exact bytes.
    $env:GESTUDIO_SOURCE_EXPORT_SMOKE_PREVIOUS_SECRET = $oldSecret
    $env:GESTUDIO_SOURCE_EXPORT_SMOKE_PREVIOUS_OUTPUT = $previousOutput
    Invoke-MavenGate -Repository $platformRoot -Label 'Jere Platform receiver' -Arguments @(
        '-B', '-f', $platformPom, '-pl', 'platform-api', '-am',
        '-Dit.test=PartyReferenceIntegrationIT',
        '-Dfailsafe.failIfNoSpecifiedTests=false', 'verify'
    )
    $status = 'PASS'
}
catch {
    $failure = $_.Exception.Message
    throw
}
finally {
    $report = [ordered]@{
        generatedAt = [DateTimeOffset]::UtcNow.ToString('O')
        result = $status
        checks = [ordered]@{
            gestudioSnapshotAndReplay = $status
            exactByteSignature = $status
            receiverReconciliationAndImport = $status
            receiverReplayWithoutDuplicates = $status
            conflictTenantAndSignatureNegatives = $status
            auditOutboxAndMetrics = $status
            currentAndPreviousRotationWindow = $status
            unavailableReceiverThenRetry = $status
        }
        failure = $failure
        note = 'Synthetic data only; payloads, signatures and secrets are omitted.'
    }
    $report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $reportPath -Encoding utf8

    $env:GESTUDIO_SOURCE_EXPORT_SMOKE_SECRET = $previousEnvironment.CurrentSecret
    $env:GESTUDIO_SOURCE_EXPORT_SMOKE_PREVIOUS_SECRET = $previousEnvironment.PreviousSecret
    $env:GESTUDIO_SOURCE_EXPORT_SMOKE_OUTPUT = $previousEnvironment.CurrentOutput
    $env:GESTUDIO_SOURCE_EXPORT_SMOKE_PREVIOUS_OUTPUT = $previousEnvironment.PreviousOutput
    if (Test-Path -LiteralPath $artifactRoot) {
        Remove-Item -LiteralPath $artifactRoot -Recurse -Force
    }
}

Write-Host "Gestudio -> Jere Platform smoke: $status"
Write-Host "Sanitized report: $reportPath"
