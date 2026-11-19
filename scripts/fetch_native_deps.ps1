# ShelfPulse AI - Native Dependencies Fetcher (PowerShell)
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$TargetDir = Join-Path $ProjectRoot "app\src\main\cpp\third_party"

if (!(Test-Path $TargetDir)) {
    New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
}

Set-Location $TargetDir

Write-Host "=== [1/3] Fetching ncnn-android-vulkan (20241226) ==="
$NcnnVersion = "20241226"
$NcnnUrl = "https://github.com/Tencent/ncnn/releases/download/$NcnnVersion/ncnn-$NcnnVersion-android-vulkan.zip"
if (!(Test-Path "ncnn-android-vulkan")) {
    Write-Host "Downloading $NcnnUrl..."
    Invoke-WebRequest -Uri $NcnnUrl -OutFile "ncnn.zip"
    Expand-Archive -Path "ncnn.zip" -DestinationPath "."
    Rename-Item "ncnn-$NcnnVersion-android-vulkan" "ncnn-android-vulkan"
    Remove-Item "ncnn.zip" -Force
} else {
    Write-Host "NCNN already present. Skipping."
}

Write-Host "=== [2/3] Fetching opencv-mobile (4.10.0) ==="
$OpencvVersion = "v29"
$OpencvUrl = "https://github.com/nihui/opencv-mobile/releases/download/$OpencvVersion/opencv-mobile-4.10.0-android.zip"
if (!(Test-Path "opencv-mobile-android")) {
    Write-Host "Downloading $OpencvUrl..."
    Invoke-WebRequest -Uri $OpencvUrl -OutFile "opencv.zip"
    Expand-Archive -Path "opencv.zip" -DestinationPath "."
    Rename-Item "opencv-mobile-4.10.0-android" "opencv-mobile-android"
    Remove-Item "opencv.zip" -Force
} else {
    Write-Host "opencv-mobile already present. Skipping."
}

Write-Host "=== [3/3] Fetching Eigen (3.4.0) ==="
$EigenVersion = "3.4.0"
$EigenUrl = "https://gitlab.com/libeigen/eigen/-/archive/$EigenVersion/eigen-$EigenVersion.zip"
if (!(Test-Path "eigen3")) {
    Write-Host "Downloading $EigenUrl..."
    Invoke-WebRequest -Uri $EigenUrl -OutFile "eigen.zip"
    Expand-Archive -Path "eigen.zip" -DestinationPath "."
    Rename-Item "eigen-$EigenVersion" "eigen3"
    Remove-Item "eigen.zip" -Force
} else {
    Write-Host "Eigen already present. Skipping."
}

Write-Host "=== All native dependencies ready ==="
