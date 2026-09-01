[CmdletBinding()]
param(
    [string] $GameDirectory = 'F:\SteamLibrary\steamapps\common\ProjectZomboid',
    [string] $JdkDirectory = 'D:\JavaSDK\bin'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$buildScript = Join-Path $projectRoot 'build.ps1'
$mainSourceRoot = Join-Path $projectRoot 'src\main\java'
$testSourceRoot = Join-Path $projectRoot 'src\test\java'
$testClasses = Join-Path $projectRoot 'build\test-classes'
$javac = Join-Path $JdkDirectory 'javac.exe'
$java = Join-Path $JdkDirectory 'java.exe'
$gameRoot = [System.IO.Path]::GetFullPath($GameDirectory)
$gameJar = Join-Path $gameRoot 'projectzomboid.jar'
$zombieBuddyJar = Join-Path $gameRoot 'ZombieBuddy.jar'
$gameJava = Join-Path $gameRoot 'jre64\bin\java.exe'

foreach ($required in @($buildScript, $mainSourceRoot, $testSourceRoot, $javac, $java, $gameJar, $zombieBuddyJar, $gameJava)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required test input is missing: $required"
    }
}

& $buildScript -GameDirectory $gameRoot -JdkDirectory $JdkDirectory

$fullRoot = $projectRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
$fullTests = [System.IO.Path]::GetFullPath($testClasses)
if (-not $fullTests.StartsWith($fullRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to modify a path outside DevV1: $fullTests"
}
if (Test-Path -LiteralPath $testClasses) {
    Remove-Item -LiteralPath $testClasses -Recurse -Force
}
New-Item -ItemType Directory -Path $testClasses -Force | Out-Null

# Compile production and test sources into an isolated test tree. This also
# catches missing ZombieBuddy annotations without loading any game class.
$mainSources = @(Get-ChildItem -LiteralPath $mainSourceRoot -Recurse -Filter '*.java' -File |
    Sort-Object FullName | ForEach-Object FullName)
$testSources = @(Get-ChildItem -LiteralPath $testSourceRoot -Recurse -Filter '*.java' -File |
    Sort-Object FullName | ForEach-Object FullName)
& $javac '--release' '24' '-Xlint:all,-output-file-clash' '-Werror' `
    '-cp' $zombieBuddyJar '-d' $testClasses @mainSources @testSources
if ($LASTEXITCODE -ne 0) {
    throw "Test compilation failed with exit code $LASTEXITCODE"
}

& $java '-cp' $testClasses 'dev.carphysicsimproved.v1.physics.LegacyPhysicsTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyPhysicsTest failed with exit code $LASTEXITCODE"
}

$runtimeClasspath = $testClasses + [System.IO.Path]::PathSeparator + $zombieBuddyJar + `
    [System.IO.Path]::PathSeparator + $gameJar
& $gameJava '-cp' $runtimeClasspath 'dev.carphysicsimproved.v1.runtime.RuntimeAbiSmokeTest'
if ($LASTEXITCODE -ne 0) {
    throw "RuntimeAbiSmokeTest failed with exit code $LASTEXITCODE"
}

Write-Host 'All V1 model tests and the installed-game ABI smoke test passed.'
