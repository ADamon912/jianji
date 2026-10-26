$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'env.ps1')
$required = @(
    (Join-Path $env:JAVA_HOME 'bin\java.exe'),
    (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'),
    (Join-Path $env:ANDROID_HOME 'platforms\android-35\android.jar'),
    (Join-Path $env:ANDROID_HOME 'build-tools\35.0.0\aapt2.exe'),
    (Join-Path $projectRoot 'gradle\wrapper\gradle-wrapper.jar'),
    (Join-Path $projectRoot 'local.properties')
)
$missing = @($required | Where-Object { !(Test-Path -LiteralPath $_) })
if ($missing.Count -gt 0) { throw ('Missing components. Run setup-android.ps1 first: ' + ($missing -join ', ')) }
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
& java -version
if ($LASTEXITCODE -ne 0) { throw 'Java failed.' }
& (Join-Path $projectRoot 'gradlew.bat') --version
if ($LASTEXITCODE -ne 0) { throw 'Gradle failed.' }
& adb version
if ($LASTEXITCODE -ne 0) { throw 'ADB failed.' }
Write-Host 'Toolchain check passed. Device availability is checked separately with adb devices -l.'

