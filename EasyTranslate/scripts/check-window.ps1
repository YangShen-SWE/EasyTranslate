param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Preview
)

$ErrorActionPreference = 'Stop'
if (-not $JavaHome -or -not (Test-Path -LiteralPath "$JavaHome/bin/java.exe")) {
    throw '请通过 -JavaHome 指定 JDK 25 目录，或先设置 JAVA_HOME。'
}

$previousJavaHome = $env:JAVA_HOME
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $env:JAVA_HOME = $JavaHome
    & ./mvnw.cmd -q test-compile dependency:build-classpath '-Dmdep.outputFile=target/ui-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw '界面检查编译失败。' }
    $uiDependencies = (Get-Content 'target/ui-classpath.txt' -Raw).Trim()
    $launchArguments = @(
        '--enable-native-access=javafx.graphics,com.sun.jna',
        '--module-path', $uiDependencies,
        '--add-modules', 'javafx.controls,javafx.fxml,com.sun.jna,com.sun.jna.platform',
        '-cp', 'target/classes;target/test-classes',
        'com.easytranslate.view.FloatingWindowSmokeTest'
    )
    if ($Preview) { $launchArguments += '--preview' }
    & "$JavaHome/bin/java.exe" @launchArguments
    if ($LASTEXITCODE -ne 0) { throw '界面检查未通过。' }
}
finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
}
