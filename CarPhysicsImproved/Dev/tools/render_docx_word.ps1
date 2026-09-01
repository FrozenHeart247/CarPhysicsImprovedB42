[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $InputPath,
    [Parameter(Mandatory)] [string] $OutputPdf
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$inputFull = [System.IO.Path]::GetFullPath($InputPath)
$outputFull = [System.IO.Path]::GetFullPath($OutputPdf)
if (-not (Test-Path -LiteralPath $inputFull -PathType Leaf)) {
    throw "DOCX is missing: $inputFull"
}
$outputDirectory = Split-Path -Parent $outputFull
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

$word = $null
$document = $null
try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $document = $word.Documents.Open($inputFull, $false, $true)
    $document.ExportAsFixedFormat($outputFull, 17)
}
finally {
    if ($null -ne $document) {
        $document.Close($false)
        [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($document)
    }
    if ($null -ne $word) {
        $word.Quit()
        [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($word)
    }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}

if (-not (Test-Path -LiteralPath $outputFull -PathType Leaf)) {
    throw "Word did not create the PDF: $outputFull"
}
Write-Host "Rendered PDF: $outputFull"
