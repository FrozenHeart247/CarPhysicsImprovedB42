[CmdletBinding()]
param(
    [string] $GameDirectory = 'F:\SteamLibrary\steamapps\common\ProjectZomboid',
    [string] $JdkDirectory = 'D:\JavaSDK\bin'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$buildScript = Join-Path $projectRoot 'build.ps1'
$classesRoot = Join-Path $projectRoot 'build\classes'
$builtJar = Join-Path $projectRoot 'build\dist\roadcraft-dynamics.jar'
$testClasses = Join-Path $projectRoot 'build\test-classes'
$mainSourceRoot = Join-Path $projectRoot 'src\main\java'
$testSourceRoot = Join-Path $projectRoot 'src\test\java'
$gameRoot = [System.IO.Path]::GetFullPath($GameDirectory)
$gameJar = Join-Path $gameRoot 'projectzomboid.jar'
$zombieBuddyJar = Join-Path $gameRoot 'ZombieBuddy.jar'
$javac = Join-Path $JdkDirectory 'javac.exe'
$gameJava = Join-Path $gameRoot 'jre64\bin\java.exe'

function Assert-ChildPath {
    param([string] $Root, [string] $Candidate)
    $fullRoot = [System.IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
    $fullCandidate = [System.IO.Path]::GetFullPath($Candidate)
    if (-not $fullCandidate.StartsWith(
        $fullRoot + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify a path outside the project: $fullCandidate"
    }
}

foreach ($required in @($buildScript, $javac, $gameJava, $gameJar, $zombieBuddyJar)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required test input is missing: $required"
    }
}

& $buildScript -GameDirectory $gameRoot -JdkDirectory $JdkDirectory

Assert-ChildPath -Root $projectRoot -Candidate $testClasses
if (Test-Path -LiteralPath $testClasses) {
    Remove-Item -LiteralPath $testClasses -Recurse -Force
}
New-Item -ItemType Directory -Path $testClasses -Force | Out-Null

$mainSources = @(Get-ChildItem -LiteralPath $mainSourceRoot -Recurse -Filter '*.java' -File |
    Sort-Object FullName |
    ForEach-Object FullName)
$testSources = @(Get-ChildItem -LiteralPath $testSourceRoot -Recurse -Filter '*.java' -File |
    Sort-Object FullName |
    ForEach-Object FullName)
if ($mainSources.Count -eq 0 -or $testSources.Count -eq 0) {
    throw "Java sources or tests are missing"
}

$compileClasspath = $zombieBuddyJar
Write-Host "Compiling $($mainSources.Count) runtime sources and $($testSources.Count) tests with JDK 24..."
& $javac '--release' '24' '-Xlint:all,-output-file-clash' '-Werror' `
    '-cp' $compileClasspath '-d' $testClasses @mainSources @testSources
if ($LASTEXITCODE -ne 0) {
    throw "Test compilation failed with exit code $LASTEXITCODE"
}

$runtimeClasspath = $testClasses + [System.IO.Path]::PathSeparator + $classesRoot +
    [System.IO.Path]::PathSeparator + $zombieBuddyJar +
    [System.IO.Path]::PathSeparator + $gameJar
$javaTests = @(
    'zombie.roadcraft.physics.RoadcraftCalibrationTest',
    'zombie.roadcraft.physics.DrivetrainModelTest',
    'zombie.roadcraft.physics.DrivetrainPerformanceSmokeTest',
    'zombie.roadcraft.runtime.RoadcraftBridgeContractTest',
    'zombie.roadcraft.runtime.PzAccessWheelVisualTest'
)
foreach ($testClass in $javaTests) {
    Write-Host "Running $testClass with the Project Zomboid Java runtime..."
    & $gameJava '-cp' $runtimeClasspath $testClass
    if ($LASTEXITCODE -ne 0) {
        throw "$testClass failed with exit code $LASTEXITCODE"
    }
}

Write-Host 'Checking ZombieBuddy annotations, JAR layout, and the B42.20.4 reflection adapter...'
& $gameJava '-cp' $runtimeClasspath `
    'zombie.roadcraft.runtime.ZombieBuddyPackageSmokeTest' $builtJar
if ($LASTEXITCODE -ne 0) {
    throw "ZombieBuddyPackageSmokeTest failed with exit code $LASTEXITCODE"
}

$translationRoot = Join-Path (Split-Path -Parent $projectRoot) `
    'Contents\mods\RoadcraftDynamicsB42\42\media\lua\shared\Translate'
$jsonFiles = @(Get-ChildItem -LiteralPath $translationRoot -Recurse -Filter '*.json' -File)
foreach ($jsonFile in $jsonFiles) {
    Get-Content -LiteralPath $jsonFile.FullName -Raw | ConvertFrom-Json | Out-Null
}
Write-Host "Parsed $($jsonFiles.Count) translation JSON files."
Write-Host 'All static tests passed. In-game SP/MP behavior is still unverified.'
