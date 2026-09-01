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
$jarPath = Join-Path $distRoot 'car-physics-improved.jar'
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
        throw "Refusing to modify a path outside DevV2: $fullCandidate"
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

Write-Host "Compiling $($sources.Count) Car Physics Improved V2 sources..."
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
    'pzmod/carphysicsimproved/Main.class',
    'pzmod/carphysicsimproved/CarPhysicsImprovedMod.class',
    'pzmod/carphysicsimproved/Patch_CarController$Update.class',
    'pzmod/carphysicsimproved/Patch_WorldSimulation.class',
    'dev/carphysicsimproved/v2/physics/VehicleDynamics.class'
)
foreach ($entry in $requiredEntries) {
    if ($entries -cnotcontains $entry) {
        throw "Built V2 JAR is missing: $entry"
    }
}
if ($entries | Where-Object { $_ -cmatch '^zombie/' } | Select-Object -First 1) {
    throw 'Built V2 JAR contains a forbidden zombie.* class replacement.'
}

$hash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash
Write-Host "V2 core JAR: $jarPath"
Write-Host "SHA-256: $hash"
