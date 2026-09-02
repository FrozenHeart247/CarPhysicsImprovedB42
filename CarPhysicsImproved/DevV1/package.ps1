[CmdletBinding()]
param(
    [string] $GameDirectory = 'F:\SteamLibrary\steamapps\common\ProjectZomboid',
    [string] $JdkDirectory = 'D:\JavaSDK\bin'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$devRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$workshopRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $devRoot))
$testScript = Join-Path $devRoot 'test.ps1'
$sourceJar = Join-Path $devRoot 'build\dist\car-physics-improved-v1.jar'
$liveMod = Join-Path $workshopRoot 'Contents\mods\CarPhysicsImprovedV1B42'
$liveJar = Join-Path $liveMod '42\media\java\car-physics-improved-v1.jar'
$stageRoot = Join-Path $devRoot 'build\package-stage'
$stageMod = Join-Path $stageRoot 'Contents\mods\CarPhysicsImprovedV1B42'
$releaseRoot = Join-Path $devRoot 'build\release'
$release = Join-Path $releaseRoot 'CarPhysicsImprovedV1B42-0.1.1-dev-workshop.zip'

function Assert-ChildPath {
    param([string] $Root, [string] $Candidate)
    $fullRoot = [System.IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
    $fullCandidate = [System.IO.Path]::GetFullPath($Candidate)
    if (-not $fullCandidate.StartsWith(
        $fullRoot + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify an unexpected path: $fullCandidate"
    }
}

& $testScript -GameDirectory $GameDirectory -JdkDirectory $JdkDirectory

Assert-ChildPath -Root $workshopRoot -Candidate $liveJar
New-Item -ItemType Directory -Path (Split-Path -Parent $liveJar) -Force | Out-Null
Copy-Item -LiteralPath $sourceJar -Destination $liveJar -Force
$sourceHash = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash
$liveHash = (Get-FileHash -LiteralPath $liveJar -Algorithm SHA256).Hash
if ($sourceHash -ne $liveHash) {
    throw 'Live V1 JAR hash differs from the build output.'
}

Assert-ChildPath -Root $devRoot -Candidate $stageRoot
if (Test-Path -LiteralPath $stageRoot) {
    Remove-Item -LiteralPath $stageRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $stageRoot -Force | Out-Null
Assert-ChildPath -Root $devRoot -Candidate $releaseRoot
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
Assert-ChildPath -Root $devRoot -Candidate $release
if (Test-Path -LiteralPath $release) {
    Remove-Item -LiteralPath $release -Force
}

New-Item -ItemType Directory -Path (Split-Path -Parent $stageMod) -Force | Out-Null
Copy-Item -LiteralPath $liveMod -Destination $stageMod -Recurse
Copy-Item -LiteralPath (Join-Path $devRoot 'workshop.txt') -Destination (Join-Path $stageRoot 'workshop.txt')
Compress-Archive -LiteralPath (Join-Path $stageRoot 'Contents'), (Join-Path $stageRoot 'workshop.txt') `
    -DestinationPath $release -CompressionLevel Optimal

$releaseHash = (Get-FileHash -LiteralPath $release -Algorithm SHA256).Hash
Write-Host "Live V1 JAR SHA-256: $liveHash"
Write-Host "Workshop package: $release"
Write-Host "Package SHA-256: $releaseHash"
