[CmdletBinding()]
param(
    [switch]$AcceptAndroidLicenses,
    [switch]$UseGradleMirror,
    [switch]$DownloadSdkArchives
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.tools'
$toolchain = Get-Content (Join-Path $PSScriptRoot 'toolchain.json') -Raw | ConvertFrom-Json
New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null

function Get-VerifiedArchive {
    param($Specification, [string]$Url)
    $archive = Join-Path $toolRoot $Specification.archive
    if (Test-Path -LiteralPath $archive) {
        $hash = (Get-FileHash -LiteralPath $archive -Algorithm $Specification.algorithm).Hash
        if ($hash -eq $Specification.checksum) { return $archive }
    }
    $partial = $archive + '.partial'
    Write-Host "Downloading $Url"
    & curl.exe --fail --location --retry 3 --connect-timeout 30 --max-time 1800 $Url --output $partial
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
    $hash = (Get-FileHash -LiteralPath $partial -Algorithm $Specification.algorithm).Hash
    if ($hash -ne $Specification.checksum) { throw "Checksum mismatch: $partial" }
    Move-Item -LiteralPath $partial -Destination $archive -Force
    return $archive
}

$jdkArchive = Get-VerifiedArchive $toolchain.jdk $toolchain.jdk.url
$gradleUrl = $toolchain.gradle.url
if ($UseGradleMirror) { $gradleUrl = $toolchain.gradle.mirrorUrl }
$gradleArchive = Get-VerifiedArchive $toolchain.gradle $gradleUrl
$sdkArchive = Get-VerifiedArchive $toolchain.commandLineTools $toolchain.commandLineTools.url

$jdkDirectory = Join-Path $toolRoot $toolchain.jdk.directory
if (!(Test-Path (Join-Path $jdkDirectory 'bin\java.exe'))) {
    & tar.exe -xf $jdkArchive -C $toolRoot
    if ($LASTEXITCODE -ne 0) { throw 'JDK extraction failed.' }
}
$gradleDirectory = Join-Path $toolRoot ('gradle-' + $toolchain.gradle.version)
if (!(Test-Path (Join-Path $gradleDirectory 'bin\gradle.bat'))) {
    & tar.exe -xf $gradleArchive -C $toolRoot
    if ($LASTEXITCODE -ne 0) { throw 'Gradle extraction failed.' }
}
$sdkDirectory = Join-Path $toolRoot 'android-sdk'
$commandLineDirectory = Join-Path $sdkDirectory ('cmdline-tools\' + $toolchain.commandLineTools.version)
if (!(Test-Path (Join-Path $commandLineDirectory 'bin\sdkmanager.bat'))) {
    New-Item -ItemType Directory -Force -Path $commandLineDirectory | Out-Null
    & tar.exe -xf $sdkArchive -C $commandLineDirectory --strip-components 1
    if ($LASTEXITCODE -ne 0) { throw 'Android command-line tools extraction failed.' }
}

. (Join-Path $PSScriptRoot 'env.ps1')
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME,$env:GRADLE_USER_HOME | Out-Null
$sdkmanager = Join-Path $commandLineDirectory 'bin\sdkmanager.bat'
if ($AcceptAndroidLicenses) {
    Write-Host 'Accepting Android SDK licenses as requested by -AcceptAndroidLicenses.'
    1..100 | ForEach-Object { 'y' } | & $sdkmanager "--sdk_root=$sdkDirectory" --licenses
} else {
    & $sdkmanager "--sdk_root=$sdkDirectory" --licenses
}
if ($LASTEXITCODE -ne 0) { throw 'Android license setup failed.' }
if ($DownloadSdkArchives) {
    foreach ($specification in $toolchain.sdkArchives) {
        $archive = Get-VerifiedArchive $specification $specification.url
        $destination = Join-Path $sdkDirectory $specification.directory
        New-Item -ItemType Directory -Force -Path $destination | Out-Null
        & tar.exe -xf $archive -C $destination --strip-components 1
        if ($LASTEXITCODE -ne 0) { throw "SDK extraction failed: $destination" }
    }
} else {
    & $sdkmanager "--sdk_root=$sdkDirectory" @($toolchain.sdkPackages)
    if ($LASTEXITCODE -ne 0) { throw 'Android SDK package installation failed.' }
}
foreach ($requiredFile in @('platforms\android-35\android.jar', 'build-tools\35.0.0\aapt2.exe', 'platform-tools\adb.exe')) {
    if (!(Test-Path (Join-Path $sdkDirectory $requiredFile))) { throw "SDK component missing: $requiredFile" }
}

$sdkProperty = 'sdk.dir=' + $sdkDirectory.Replace('\', '/').Replace(':', '\:')
[IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), "$sdkProperty`n", [Text.UTF8Encoding]::new($false))

# Generate the official wrapper without resolving Android plugins in the bootstrap build.
if (!(Test-Path (Join-Path $projectRoot 'gradle\wrapper\gradle-wrapper.jar'))) {
    $bootstrap = Join-Path $toolRoot 'wrapper-bootstrap'
    New-Item -ItemType Directory -Force -Path $bootstrap | Out-Null
    [IO.File]::WriteAllText((Join-Path $bootstrap 'settings.gradle.kts'), 'rootProject.name = "wrapper-bootstrap"')
    & (Join-Path $gradleDirectory 'bin\gradle.bat') -p $bootstrap wrapper `
        --gradle-version $toolchain.gradle.version --distribution-type bin --gradle-distribution-url $gradleUrl `
        --gradle-distribution-sha256-sum $toolchain.gradle.checksum --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Gradle wrapper generation failed.' }
    Copy-Item -LiteralPath (Join-Path $bootstrap 'gradlew'),(Join-Path $bootstrap 'gradlew.bat') -Destination $projectRoot
    New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot 'gradle\wrapper') | Out-Null
    Copy-Item -Path (Join-Path $bootstrap 'gradle\wrapper\*') -Destination (Join-Path $projectRoot 'gradle\wrapper')
}

Write-Host 'Setup complete. Load scripts/env.ps1, then run gradlew.bat assembleDebug lintDebug.'

