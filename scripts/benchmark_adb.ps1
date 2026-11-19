# ShelfPulse AI - Dynamic Device Benchmark & Verification Runner (PowerShell)
param(
    [string]$ApkPath = ""
)
$ErrorActionPreference = "Continue"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$DocsImgDir = Join-Path $ProjectRoot "docs\assets\screenshots"

if (!(Test-Path $DocsImgDir)) {
    New-Item -ItemType Directory -Path $DocsImgDir -Force | Out-Null
}

Write-Host "=== [1/5] Detecting Connected ADB Device ==="
$AdbOutput = (adb devices) | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of devices" }

if (-not $AdbOutput) {
    Write-Error "No authorized Android device detected via ADB."
    exit 1
}

$Devices = @($AdbOutput | ForEach-Object { ($_ -split "\s+")[0] })
$TargetSerial = $Devices[0]
Write-Host "Selected target device: [SERIAL PROTECTED] (Detected $($Devices.Count) active device(s))"

$DeviceModel = (adb -s $TargetSerial shell getprop ro.product.model).Trim()
$DeviceSdk = (adb -s $TargetSerial shell getprop ro.build.version.release).Trim()
$DeviceAbi = (adb -s $TargetSerial shell getprop ro.product.cpu.abi).Trim()
$DevicePlatform = (adb -s $TargetSerial shell getprop ro.board.platform).Trim()

Write-Host "Device Specifications:"
Write-Host "  - Model: $DeviceModel"
Write-Host "  - Android Version: $DeviceSdk"
Write-Host "  - ABI: $DeviceAbi"
Write-Host "  - Platform: $DevicePlatform"

if ($ApkPath -and (Test-Path $ApkPath)) {
    Write-Host "=== [2/5] Installing APK: $ApkPath ==="
    adb -s $TargetSerial install -r -d $ApkPath
}

$PackageName = "com.shelfpulse.ai"
$ActivityName = "$PackageName/.ui.MainActivity"

Write-Host "=== [3/5] Launching App & Verifying JNI Engine ==="
adb -s $TargetSerial logcat -c
adb -s $TargetSerial shell am start -n $ActivityName

Start-Sleep -Seconds 3
Write-Host "Recent Logcat Output:"
adb -s $TargetSerial logcat -d -s ShelfPulseNative ShelfPulseAI AndroidRuntime:E | Select-Object -First 40

Write-Host "=== [4/5] Capturing Authentic Screenshots ==="
$ScreenPath = Join-Path $DocsImgDir "device_screen.png"
adb -s $TargetSerial exec-out screencap -p > $ScreenPath
Write-Host "Saved screenshot to: $ScreenPath"

Write-Host "=== [5/5] Verification Complete ==="
