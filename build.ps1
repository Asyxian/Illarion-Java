<#
This file is part of the Illarion project.

Illarion is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Illarion is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
#>
# Convenience wrapper for Windows; JAVA_HOME must point to JDK 25.
$ErrorActionPreference = 'Stop'
$previousGradleHome = $env:GRADLE_USER_HOME
$buildArguments = $args

if ($buildArguments.Count -eq 0) {
    $buildArguments = @('build')
}

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
