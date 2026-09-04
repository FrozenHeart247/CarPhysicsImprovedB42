[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string] $GameDirectory,
    [Parameter(Mandatory = $true)] [string] $JdkDirectory,
    [string] $OutputDirectory = (Join-Path ([System.IO.Path]::GetTempPath()) ('CPI-tests-' + [guid]::NewGuid().ToString('N')))
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$buildScript = Join-Path $projectRoot 'build.ps1'
$mainSourceRoot = Join-Path $projectRoot 'main\java'
$testSourceRoot = Join-Path $projectRoot 'test\java'
$outputRoot = [System.IO.Path]::GetFullPath($OutputDirectory)
$testClasses = Join-Path $outputRoot 'test-classes'
$javac = Join-Path $JdkDirectory 'javac.exe'
$jarTool = Join-Path $JdkDirectory 'jar.exe'
$java = Join-Path $JdkDirectory 'java.exe'
$patchTestManifest = Join-Path $projectRoot 'test\wheel-patch-agent.mf'
$patchTestAgent = Join-Path $outputRoot 'wheel-patch-test-agent.jar'
$gameRoot = [System.IO.Path]::GetFullPath($GameDirectory)
$gameJar = Join-Path $gameRoot 'projectzomboid.jar'
$zombieBuddyJar = Join-Path $gameRoot 'ZombieBuddy.jar'
$gameJava = Join-Path $gameRoot 'jre64\bin\java.exe'
$mediaRoot = Split-Path -Parent $projectRoot
$clientLua = Join-Path $mediaRoot 'lua\client\CarPhysicsImprovedV1\CPI_V1_Client.lua'
$tireDebugLua = Join-Path $mediaRoot 'lua\client\CarPhysicsImprovedV1\CPI_V1_TireDebug.lua'
$apiLua = Join-Path $mediaRoot 'lua\shared\CarPhysicsImprovedV1\API.lua'

foreach ($required in @($buildScript, $mainSourceRoot, $testSourceRoot, $javac, $java,
        $gameJar, $zombieBuddyJar, $gameJava, $clientLua, $tireDebugLua, $apiLua,
        $jarTool, $patchTestManifest)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required test input is missing: $required"
    }
}

if (Test-Path -LiteralPath $outputRoot) {
    throw "OutputDirectory must be a new directory; nothing was deleted: $outputRoot"
}
New-Item -ItemType Directory -Path $outputRoot | Out-Null
& $buildScript -GameDirectory $gameRoot -JdkDirectory $JdkDirectory -OutputDirectory (Join-Path $outputRoot 'production')

$fullRoot = $outputRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
$fullTests = [System.IO.Path]::GetFullPath($testClasses)
if (-not $fullTests.StartsWith($fullRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to write outside the test output: $fullTests"
}
New-Item -ItemType Directory -Path $testClasses | Out-Null

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

& $java '-cp' $testClasses 'dev.carphysicsimproved.v1.physics.LegacyZeroPowerTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyZeroPowerTest failed with exit code $LASTEXITCODE"
}

& $java '-cp' $testClasses 'dev.carphysicsimproved.v1.physics.LegacyPoweredRegressionTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyPoweredRegressionTest failed with exit code $LASTEXITCODE"
}

& $java '-cp' $testClasses 'dev.carphysicsimproved.v1.physics.LegacySlideDynamicsTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacySlideDynamicsTest failed with exit code $LASTEXITCODE"
}

& $java '-cp' $testClasses 'dev.carphysicsimproved.v1.runtime.LegacyDriftControlTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyDriftControlTest failed with exit code $LASTEXITCODE"
}

& $java '-cp' $testClasses 'dev.carphysicsimproved.v1.physics.LegacyAxleDriftTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyAxleDriftTest failed with exit code $LASTEXITCODE"
}

& $java '-cp' $testClasses 'dev.carphysicsimproved.v1.physics.LegacyKeyDriftTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyKeyDriftTest failed with exit code $LASTEXITCODE"
}

& $java '-cp' $testClasses 'dev.carphysicsimproved.v1.physics.LegacyTerrainDynamicsTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyTerrainDynamicsTest failed with exit code $LASTEXITCODE"
}

& $java '-cp' $testClasses 'dev.carphysicsimproved.v1.physics.LegacyTireEffectsTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyTireEffectsTest failed with exit code $LASTEXITCODE"
}

& $java '-cp' $testClasses 'dev.carphysicsimproved.v1.physics.LegacyTireConditionTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyTireConditionTest failed with exit code $LASTEXITCODE"
}

& $java '-cp' $testClasses 'dev.carphysicsimproved.v1.physics.LegacyCabinExposureTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyCabinExposureTest failed with exit code $LASTEXITCODE"
}

& $java '-cp' $testClasses 'dev.carphysicsimproved.v1.physics.LegacyDriverTraitsTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyDriverTraitsTest failed with exit code $LASTEXITCODE"
}

& $java '-cp' $testClasses 'dev.carphysicsimproved.v1.runtime.LegacyTireTrackRendererTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyTireTrackRendererTest failed with exit code $LASTEXITCODE"
}

& $java '-cp' $testClasses 'dev.carphysicsimproved.v1.runtime.ProneImpulseLimiterTest'
if ($LASTEXITCODE -ne 0) {
    throw "ProneImpulseLimiterTest failed with exit code $LASTEXITCODE"
}

$runtimeClasspath = $testClasses + [System.IO.Path]::PathSeparator + $zombieBuddyJar + `
    [System.IO.Path]::PathSeparator + $gameJar
& $gameJava '-cp' $runtimeClasspath 'dev.carphysicsimproved.v1.runtime.RuntimeAbiSmokeTest'
if ($LASTEXITCODE -ne 0) {
    throw "RuntimeAbiSmokeTest failed with exit code $LASTEXITCODE"
}

& $gameJava '-cp' $runtimeClasspath 'dev.carphysicsimproved.v1.runtime.LegacyKeyDriftAdapterTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyKeyDriftAdapterTest failed with exit code $LASTEXITCODE"
}

& $gameJava '-cp' $runtimeClasspath 'dev.carphysicsimproved.v1.runtime.LegacyAxleDriftLifecycleTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyAxleDriftLifecycleTest failed with exit code $LASTEXITCODE"
}

# This agent only grants instrumentation inside the isolated test JVM. It is
# never packaged into the mod or installed as a game/Steam launch option.
& $jarTool '--create' '--file' $patchTestAgent '--manifest' $patchTestManifest `
    '-C' $testClasses 'dev/carphysicsimproved/v1/runtime/LegacyAxleDriftPatchTest.class'
if ($LASTEXITCODE -ne 0) {
    throw "Wheel patch test agent creation failed with exit code $LASTEXITCODE"
}
& $gameJava "-javaagent:$patchTestAgent" '-cp' $runtimeClasspath `
    'dev.carphysicsimproved.v1.runtime.LegacyAxleDriftPatchTest'
if ($LASTEXITCODE -ne 0) {
    throw "LegacyAxleDriftPatchTest failed with exit code $LASTEXITCODE"
}

& $gameJava '-cp' $runtimeClasspath 'dev.carphysicsimproved.v1.runtime.LuaSyntaxSmokeTest' `
    $clientLua $tireDebugLua $apiLua
if ($LASTEXITCODE -ne 0) {
    throw "LuaSyntaxSmokeTest failed with exit code $LASTEXITCODE"
}

$luaCommand = Get-Command 'lua' -ErrorAction SilentlyContinue
if ($luaCommand) {
    & $luaCommand.Source (Join-Path $projectRoot 'test\lua\drift_key_options_test.lua') $clientLua
    if ($LASTEXITCODE -ne 0) {
        throw "Drift-key Lua options test failed with exit code $LASTEXITCODE"
    }
} else {
    Write-Host 'Lua options mock skipped: standalone lua executable is unavailable (Kahlua syntax checked above).'
}

Write-Host 'All V1 drivetrain, driver-traits, drift-key, slide, terrain, tire-effects, cabin-weather, tire-renderer, Lua syntax, installed-game ABI and ZB wheel-patch integration tests passed.'
