[CmdletBinding()]
param(
    [string] $GameDirectory = 'F:\SteamLibrary\steamapps\common\ProjectZomboid',
    [string] $JdkDirectory = 'D:\JavaSDK\bin'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$sourceRoot = Join-Path $projectRoot 'src\main\java'
$classesRoot = Join-Path $projectRoot 'build\classes'
$distRoot = Join-Path $projectRoot 'build\dist'
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
        throw "Refusing to modify a path outside DevV1: $fullCandidate"
    }
}

foreach ($required in @($sourceRoot, $javac, $jarTool, $zombieBuddyJar)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required build input is missing: $required"
    }
}

foreach ($target in @($classesRoot, $distRoot)) {
    Assert-ChildPath -Root $projectRoot -Candidate $target
    if (Test-Path -LiteralPath $target) {
        Remove-Item -LiteralPath $target -Recurse -Force
    }
    New-Item -ItemType Directory -Path $target -Force | Out-Null
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
$requiredEntries = @(
    'pzmod/carphysicsimproved/v1/Main.class',
    'pzmod/carphysicsimproved/v1/CarPhysicsImprovedV1Mod.class',
    'pzmod/carphysicsimproved/v1/Patch_CarController$Update.class',
    'pzmod/carphysicsimproved/v1/Patch_WorldSimulation.class',
    'pzmod/carphysicsimproved/v1/Patch_BaseVehicle.class',
    'pzmod/carphysicsimproved/v1/Patch_BaseVehicleImpulses$Plant.class',
    'pzmod/carphysicsimproved/v1/Patch_ItemContainer.class',
    'pzmod/carphysicsimproved/v1/Patch_FBORenderChunkManager.class',
    'dev/carphysicsimproved/v1/physics/LegacyPhysics.class',
    'dev/carphysicsimproved/v1/physics/LegacySlideDynamics.class',
    'dev/carphysicsimproved/v1/physics/LegacyTerrainDynamics.class',
    'dev/carphysicsimproved/v1/runtime/LegacyTireTrackRenderer.class'
)
foreach ($entry in $requiredEntries) {
    if ($entries -cnotcontains $entry) {
        throw "Built V1 JAR is missing: $entry"
    }
}
if ($entries | Where-Object { $_ -cmatch '^zombie/' } | Select-Object -First 1) {
    throw 'Built V1 JAR contains a forbidden zombie.* class replacement.'
}

$hash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash
Write-Host "V1 core JAR: $jarPath"
Write-Host "SHA-256: $hash"
