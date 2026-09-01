[CmdletBinding()]
param(
    [string] $GameDirectory = 'F:\SteamLibrary\steamapps\common\ProjectZomboid',
    [string] $JdkDirectory = 'D:\JavaSDK\bin'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$sourceRoot = Join-Path $projectRoot 'src\main\java'
$buildRoot = Join-Path $projectRoot 'build'
$classesRoot = Join-Path $buildRoot 'classes'
$distRoot = Join-Path $buildRoot 'dist'
$jarPath = Join-Path $distRoot 'roadcraft-dynamics.jar'
$javac = Join-Path $JdkDirectory 'javac.exe'
$jarTool = Join-Path $JdkDirectory 'jar.exe'
$zombieBuddyJar = Join-Path ([System.IO.Path]::GetFullPath($GameDirectory)) 'ZombieBuddy.jar'

function Assert-ChildPath {
    param(
        [Parameter(Mandatory)] [string] $Root,
        [Parameter(Mandatory)] [string] $Candidate
    )

    $fullRoot = [System.IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
    $fullCandidate = [System.IO.Path]::GetFullPath($Candidate)
    $prefix = $fullRoot + [System.IO.Path]::DirectorySeparatorChar
    if (-not $fullCandidate.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify a path outside the project: $fullCandidate"
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
    Sort-Object FullName |
    ForEach-Object FullName)
if ($sources.Count -eq 0) {
    throw "Java sources are missing below: $sourceRoot"
}

Write-Host "Compiling $($sources.Count) ZombieBuddy runtime sources with JDK 24..."
& $javac '--release' '24' '-Xlint:all,-output-file-clash' '-Werror' `
    '-cp' $zombieBuddyJar '-d' $classesRoot @sources
if ($LASTEXITCODE -ne 0) {
    throw "Runtime compilation failed with exit code $LASTEXITCODE"
}

& $jarTool '--create' '--file' $jarPath '-C' $classesRoot '.'
if ($LASTEXITCODE -ne 0) {
    throw "JAR creation failed with exit code $LASTEXITCODE"
}

$entries = @(& $jarTool 'tf' $jarPath)
$requiredEntries = @(
    'pzmod/roadcraft/Main.class',
    'pzmod/roadcraft/RoadcraftMod.class',
    'pzmod/roadcraft/Patch_CarController$Update.class',
    'pzmod/roadcraft/Patch_CarController$UpdateTrailer.class',
    'pzmod/roadcraft/Patch_BaseVehicle$AutoStart.class',
    'pzmod/roadcraft/Patch_BaseVehicle$HitPlant.class',
    'pzmod/roadcraft/Patch_BaseVehicle$HitPedestrian.class',
    'pzmod/roadcraft/Patch_BaseVehicle$HitCorpse.class',
    'pzmod/roadcraft/Patch_WorldSimulation.class',
    'zombie/roadcraft/runtime/RoadcraftHooks.class'
)
foreach ($requiredEntry in $requiredEntries) {
    if ($entries -cnotcontains $requiredEntry) {
        throw "Built JAR is missing: $requiredEntry"
    }
}
if ($entries | Where-Object { $_ -cmatch '^zombie/core/physics/' } | Select-Object -First 1) {
    throw 'Built JAR contains a forbidden full game-class replacement.'
}

$hash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash
Write-Host "ZombieBuddy JAR: $jarPath"
Write-Host "SHA-256: $hash"
