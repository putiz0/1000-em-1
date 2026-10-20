# Script de build local do PlayBox Offline (máquina do usuário).
# Define JAVA_HOME/ANDROID_HOME apontando para o toolchain instalado em
# C:\Users\mikay\playbox-dev e repassa os argumentos ao Gradle wrapper.
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$root = "C:\Users\mikay\playbox-dev"

$env:JAVA_HOME = "$root\jdk"
$env:Path = "$root\jdk\bin;" + $env:Path
$env:ANDROID_HOME = "$root\android-sdk"
$env:ANDROID_SDK_ROOT = "$root\android-sdk"

if (-not $GradleArgs) { $GradleArgs = @("assembleDebug") }

& "$PSScriptRoot\gradlew.bat" @GradleArgs
exit $LASTEXITCODE
