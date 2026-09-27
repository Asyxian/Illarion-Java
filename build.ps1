# Convenience wrapper for Windows; JAVA_HOME must point to JDK 25.
$ErrorActionPreference = 'Stop'
$previousGradleHome = $env:GRADLE_USER_HOME
$buildArguments = $args
if ($buildArguments.Count -eq 0) { $buildArguments = @('build') }
$buildExitCode = 1
Push-Location $PSScriptRoot
try {
    if (-not $env:GRADLE_USER_HOME) {
        $env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle/gradle-user-home'
    }
    & "$PSScriptRoot/gradlew.bat" @buildArguments
    $buildExitCode = $LASTEXITCODE
} finally {
    $env:GRADLE_USER_HOME = $previousGradleHome
    Pop-Location
}
exit $buildExitCode
