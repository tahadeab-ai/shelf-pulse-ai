# ShelfPulse AI - Model Fetcher (PowerShell)
param(
    [string]$Tag = "models-v1.0.0"
)
$ErrorActionPreference = "Continue"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ModelDir = Join-Path $ProjectRoot "app\src\main\assets\models"
$Repo = "tahadeab-ai/shelf-pulse-ai"

if (!(Test-Path $ModelDir)) {
    New-Item -ItemType Directory -Path $ModelDir -Force | Out-Null
}

$ParamFile = Join-Path $ModelDir "model.ncnn.param"
$BinFile = Join-Path $ModelDir "model.ncnn.bin"

if ((Test-Path $ParamFile) -and (Test-Path $BinFile)) {
    Write-Host "Models already exist at $ModelDir. Skipping download."
    exit 0
}

Write-Host "Attempting to fetch model from GitHub Release $Tag in $Repo..."
$DownloadSuccess = $false

if (Get-Command gh -ErrorAction SilentlyContinue) {
    try {
        gh release download $Tag -R $Repo -D $ModelDir --pattern "*.ncnn.*"
        if ((Test-Path $ParamFile) -and (Test-Path $BinFile)) {
            $DownloadSuccess = $true
            Write-Host "Successfully fetched models via gh release."
        }
    } catch {
        Write-Host "Could not fetch from gh release."
    }
}

if (-not $DownloadSuccess) {
    Write-Host "Falling back to baseline YOLO11n NCNN smoke-test model..."
    $FallbackUrl = "https://github.com/ultralytics/assets/releases/download/v8.3.0/yolo11n_ncnn_model.zip"
    $TempZip = Join-Path $ModelDir "fallback_yolo11n.zip"
    try {
        Invoke-WebRequest -Uri $FallbackUrl -OutFile $TempZip
        Expand-Archive -Path $TempZip -DestinationPath $ModelDir -Force
        Remove-Item $TempZip -Force

        $SubDir = Join-Path $ModelDir "yolo11n_ncnn_model"
        if (Test-Path $SubDir) {
            Move-Item (Join-Path $SubDir "model.ncnn.param") $ParamFile -Force
            Move-Item (Join-Path $SubDir "model.ncnn.bin") $BinFile -Force
            Remove-Item $SubDir -Recurse -Force
        }
        Write-Host "Baseline YOLO11n NCNN model installed to $ModelDir."
    } catch {
        Write-Host "Could not download fallback archive. Details: $_"
    }
}
