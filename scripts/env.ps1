# Dot-source this script in each development terminal: . .\scripts\env.ps1
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolchain = Get-Content (Join-Path $PSScriptRoot 'toolchain.json') -Raw | ConvertFrom-Json
$env:JAVA_HOME = Join-Path $projectRoot ('.tools\' + $toolchain.jdk.directory)
$env:ANDROID_HOME = Join-Path $projectRoot '.tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-user'
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-user-home'
$projectToolPaths = @(
    (Join-Path $env:JAVA_HOME 'bin'),
    (Join-Path $env:ANDROID_HOME 'platform-tools'),
    (Join-Path $env:ANDROID_HOME ('cmdline-tools\' + $toolchain.commandLineTools.version + '\bin')),
    (Join-Path $projectRoot ('.tools\gradle-' + $toolchain.gradle.version + '\bin'))
)
$env:Path = (($projectToolPaths + ($env:Path -split ';') | Select-Object -Unique) -join ';')

