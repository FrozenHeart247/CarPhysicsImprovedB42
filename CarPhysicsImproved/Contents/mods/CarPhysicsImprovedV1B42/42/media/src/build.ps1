[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string] $GameDirectory,
    [Parameter(Mandatory = $true)] [string] $JdkDirectory,
    [string] $OutputDirectory = (Join-Path ([System.IO.Path]::GetTempPath()) ('CPI-build-' + [guid]::NewGuid().ToString('N')))
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$sourceRoot = Join-Path $projectRoot 'main\java'
$outputRoot = [System.IO.Path]::GetFullPath($OutputDirectory)
$classesRoot = Join-Path $outputRoot 'classes'
$distRoot = Join-Path $outputRoot 'dist'
$jarPath = Join-Path $distRoot 'car-physics-improved-v1.jar'
$javac = Join-Path $JdkDirectory 'javac.exe'
$jarTool = Join-Path $JdkDirectory 'jar.exe'
$zombieBuddyJar = Join-Path ([System.IO.Path]::GetFullPath($GameDirectory)) 'ZombieBuddy.jar'

function Assert-ChildPath {
    param([string] $Root, [string] $Candidate)
    $fullRoot = [System.IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
    $fullCandidate = [System.IO.Path]::GetFullPath($Candidate)
    if (-not $fullCandidate.StartsWith(
        $fullRoot + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to write outside the build output: $fullCandidate"
    }
}

foreach ($required in @($sourceRoot, $javac, $jarTool, $zombieBuddyJar)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required build input is missing: $required"
    }
}

if (Test-Path -LiteralPath $outputRoot) {
    throw "OutputDirectory must be a new directory; nothing was deleted: $outputRoot"
}
New-Item -ItemType Directory -Path $outputRoot | Out-Null
foreach ($target in @($classesRoot, $distRoot)) {
    Assert-ChildPath -Root $outputRoot -Candidate $target
    New-Item -ItemType Directory -Path $target | Out-Null
}

$sources = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' -File |
    Sort-Object FullName | ForEach-Object FullName)
if ($sources.Count -eq 0) {
    throw "Java sources are missing below: $sourceRoot"
}

Write-Host "Compiling $($sources.Count) Car Physics Improved V1 sources..."
& $javac '--release' '24' '-Xlint:all,-output-file-clash' '-Werror' `
    '-cp' $zombieBuddyJar '-d' $classesRoot @sources
if ($LASTEXITCODE -ne 0) {
    throw "Compilation failed with exit code $LASTEXITCODE"
}

& $jarTool '--create' '--file' $jarPath '-C' $classesRoot '.'
if ($LASTEXITCODE -ne 0) {
    throw "JAR creation failed with exit code $LASTEXITCODE"
}

$entries = @(& $jarTool 'tf' $jarPath)
if ($LASTEXITCODE -ne 0) {
    throw "JAR inspection failed with exit code $LASTEXITCODE"
}
$requiredEntries = @(
    'pzmod/carphysicsimproved/v1/Main.class',
    'pzmod/carphysicsimproved/v1/CarPhysicsImprovedV1Mod.class',
    'pzmod/carphysicsimproved/v1/Patch_CarController$Update.class',
    'pzmod/carphysicsimproved/v1/Patch_WorldSimulation.class',
    'pzmod/carphysicsimproved/v1/Patch_BaseVehicle.class',
    'pzmod/carphysicsimproved/v1/Patch_BaseVehicleWheelGrip.class',
    'pzmod/carphysicsimproved/v1/Patch_BaseVehicleImpulses$Plant.class',
    'pzmod/carphysicsimproved/v1/Patch_BaseVehicleImpulses$ProneCharacter.class',
    'pzmod/carphysicsimproved/v1/Patch_BaseVehicleImpulses$ObstacleSlowdown.class',
    'pzmod/carphysicsimproved/v1/Patch_ItemContainer.class',
    'pzmod/carphysicsimproved/v1/Patch_FBORenderChunkManager.class',
    'pzmod/carphysicsimproved/v1/Patch_Temperature.class',
    'pzmod/carphysicsimproved/v1/Patch_ClothingWetness.class',
    'pzmod/carphysicsimproved/v1/Patch_WorldSimulationVisual.class',
    'dev/carphysicsimproved/v1/physics/LegacyPhysics.class',
    'dev/carphysicsimproved/v1/physics/LegacySlideDynamics.class',
    'dev/carphysicsimproved/v1/physics/LegacyAxleDrift.class',
    'dev/carphysicsimproved/v1/physics/LegacyKeyDrift.class',
    'dev/carphysicsimproved/v1/runtime/LegacyAxleDriftHooks.class',
    'dev/carphysicsimproved/v1/physics/LegacyTerrainDynamics.class',
    'dev/carphysicsimproved/v1/physics/LegacyTireCondition.class',
    'dev/carphysicsimproved/v1/physics/LegacyCabinExposure.class',
    'dev/carphysicsimproved/v1/physics/LegacyDriverTraits.class',
    'dev/carphysicsimproved/v1/runtime/ProneImpulseLimiter.class',
    'dev/carphysicsimproved/v1/runtime/LegacyTireTrackRenderer.class',
    'dev/carphysicsimproved/v1/runtime/LegacyCabinExposureHooks.class'
)
foreach ($entry in $requiredEntries) {
    if ($entries -cnotcontains $entry) {
        throw "Built V1 JAR is missing: $entry"
    }
}
if ($entries | Where-Object { $_ -cmatch '^zombie/' } | Select-Object -First 1) {
    throw 'Built V1 JAR contains a forbidden zombie.* class replacement.'
}
if ($entries | Where-Object { $_ -cmatch '(Test|Fixture|Agent).*\.class$' } | Select-Object -First 1) {
    throw 'Built V1 JAR contains a test-only class.'
}

$hash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash
Write-Host "V1 core JAR: $jarPath"
Write-Host "SHA-256: $hash"
Write-Host 'Build only: the shipped JAR and game installation were not modified.'
