[CmdletBinding()]
param(
    [string] $Version = '0.4.4-dev',
    [string] $GameDirectory = 'F:\SteamLibrary\steamapps\common\ProjectZomboid',
    [string] $JdkDirectory = 'D:\JavaSDK\bin',
    [switch] $SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$workshopRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $projectRoot))
$modRoot = Join-Path $workshopRoot 'Contents\mods\RoadcraftDynamicsB42'
$versionRoot = Join-Path $modRoot '42'
$manualRoot = Join-Path $modRoot 'MANUAL_INSTALLATION'
$buildRoot = Join-Path $projectRoot 'build'
$sourceJar = Join-Path $buildRoot 'dist\roadcraft-dynamics.jar'
$targetJavaRoot = Join-Path $versionRoot 'media\java'
$targetJar = Join-Path $targetJavaRoot 'roadcraft-dynamics.jar'
$sourceLicense = Join-Path $projectRoot 'LICENSE'
$targetLicense = Join-Path $modRoot 'LICENSE.txt'
$stageParent = Join-Path $buildRoot 'package-stage'
$releaseRoot = Join-Path $buildRoot 'release'

if ($Version -notmatch '^[0-9A-Za-z][0-9A-Za-z._-]*$') {
    throw "Version contains unsafe filename characters: $Version"
}

$packageName = "RoadcraftDynamicsB42-$Version-workshop"
$stageRoot = Join-Path $stageParent $packageName
$zipPath = Join-Path $releaseRoot ($packageName + '.zip')

function Assert-ChildPath {
    param(
        [Parameter(Mandatory)] [string] $Root,
        [Parameter(Mandatory)] [string] $Candidate
    )

    $fullRoot = [System.IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
    $fullCandidate = [System.IO.Path]::GetFullPath($Candidate)
    $prefix = $fullRoot + [System.IO.Path]::DirectorySeparatorChar
    if (-not $fullCandidate.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify a path outside '$fullRoot': $fullCandidate"
    }
}

$workshopDescriptor = Join-Path $workshopRoot 'workshop.txt'
$modInfo = Join-Path $versionRoot 'mod.info'
foreach ($required in @($workshopDescriptor, $modInfo, $sourceLicense)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required Workshop input is missing: $required"
    }
}

if (-not $SkipBuild) {
    & (Join-Path $projectRoot 'build.ps1') `
        -GameDirectory $GameDirectory -JdkDirectory $JdkDirectory
}
if (-not (Test-Path -LiteralPath $sourceJar -PathType Leaf)) {
    throw "Required ZombieBuddy JAR is missing: $sourceJar"
}

$modVersionLine = "modversion=$Version"
$modInfoLines = @(Get-Content -LiteralPath $modInfo)
if ($modInfoLines -cnotcontains $modVersionLine) {
    throw "mod.info version does not match package version '$Version': $modInfo"
}
if ($modInfoLines -cnotcontains 'require=\ZombieBuddy' -or
    $modInfoLines -cnotcontains 'javaJarFile=media/java/roadcraft-dynamics.jar' -or
    $modInfoLines -cnotcontains 'javaPkgName=pzmod.roadcraft') {
    throw "mod.info does not declare the expected ZombieBuddy package: $modInfo"
}

foreach ($candidate in @($targetJavaRoot, $targetJar, $targetLicense, $manualRoot)) {
    Assert-ChildPath -Root $workshopRoot -Candidate $candidate
}
New-Item -ItemType Directory -Path $targetJavaRoot -Force | Out-Null
Copy-Item -LiteralPath $sourceJar -Destination $targetJar -Force
Copy-Item -LiteralPath $sourceLicense -Destination $targetLicense -Force
if (Test-Path -LiteralPath $manualRoot) {
    Remove-Item -LiteralPath $manualRoot -Recurse -Force
}

$previewPath = Join-Path $workshopRoot 'preview.png'
if (-not (Test-Path -LiteralPath $previewPath -PathType Leaf)) {
    Write-Warning 'preview.png is missing. Project Zomboid requires a 256x256 PNG no larger than 1000 KB before Workshop upload.'
}

foreach ($candidate in @($stageRoot, $zipPath)) {
    Assert-ChildPath -Root $projectRoot -Candidate $candidate
}
if (Test-Path -LiteralPath $stageRoot) {
    Remove-Item -LiteralPath $stageRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $stageRoot -Force | Out-Null
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null

try {
    Copy-Item -LiteralPath $workshopDescriptor -Destination $stageRoot
    Copy-Item -LiteralPath (Join-Path $workshopRoot 'Contents') -Destination $stageRoot -Recurse
    if (Test-Path -LiteralPath $previewPath -PathType Leaf) {
        Copy-Item -LiteralPath $previewPath -Destination $stageRoot
    }

    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }
    Compress-Archive -LiteralPath $stageRoot -DestinationPath $zipPath -CompressionLevel Optimal

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
    try {
        $entries = @($archive.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
        $prefix = $packageName + '/'
        $requiredEntries = @(
            $prefix + 'workshop.txt'
            $prefix + 'Contents/mods/RoadcraftDynamicsB42/mod.info'
            $prefix + 'Contents/mods/RoadcraftDynamicsB42/42/mod.info'
            $prefix + 'Contents/mods/RoadcraftDynamicsB42/42/media/java/roadcraft-dynamics.jar'
            $prefix + 'Contents/mods/RoadcraftDynamicsB42/LICENSE.txt'
        )
        foreach ($requiredEntry in $requiredEntries) {
            if ($entries -cnotcontains $requiredEntry) {
                throw "Workshop archive is missing: $requiredEntry"
            }
        }
        foreach ($entry in $entries) {
            if ($entry -cmatch '(^|/)MANUAL_INSTALLATION(/|$)' -or
                $entry -cmatch '(^|/)Dev(/|$)' -or
                $entry -cmatch '(^|/)zombie/core/physics/CarController[^/]*\.class$') {
                throw "Retired loose-install or developer content entered the archive: $entry"
            }
        }
    }
    finally {
        $archive.Dispose()
    }

    $hash = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash
    Write-Host "Workshop folder synchronized: $workshopRoot"
    Write-Host "ZombieBuddy JAR: $targetJar"
    Write-Host "Workshop archive: $zipPath"
    Write-Host "SHA-256: $hash"
}
finally {
    if (Test-Path -LiteralPath $stageRoot) {
        Assert-ChildPath -Root $stageParent -Candidate $stageRoot
        Remove-Item -LiteralPath $stageRoot -Recurse -Force
    }
}
