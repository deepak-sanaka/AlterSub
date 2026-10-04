param([string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk", [string]$JdkRoot = 'C:\Program Files\Java\jdk-21')
$ErrorActionPreference = 'Stop'
$buildRoot = Join-Path $PSScriptRoot 'build'
$buildTools = Join-Path $SdkRoot 'build-tools\35.0.0'
$androidJar = Join-Path $SdkRoot 'platforms\android-34\android.jar'
New-Item -ItemType Directory -Force -Path $buildRoot, (Join-Path $buildRoot 'classes'), (Join-Path $buildRoot 'dex') | Out-Null
$sources = @(Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& (Join-Path $JdkRoot 'bin\javac.exe') --release 8 -classpath $androidJar -d (Join-Path $buildRoot 'classes') $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
$classes = @(Get-ChildItem (Join-Path $buildRoot 'classes') -Recurse -Filter '*.class' | ForEach-Object FullName)
& (Join-Path $buildTools 'd8.bat') --min-api 28 --lib $androidJar --output (Join-Path $buildRoot 'dex') $classes
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
& (Join-Path $buildTools 'aapt.exe') package -f -M (Join-Path $PSScriptRoot 'AndroidManifest.xml') -S (Join-Path $PSScriptRoot 'res') -I $androidJar -F (Join-Path $buildRoot 'unsigned.apk')
if ($LASTEXITCODE -ne 0) { throw 'aapt package failed' }
Push-Location (Join-Path $buildRoot 'dex')
try { & (Join-Path $buildTools 'aapt.exe') add (Join-Path $buildRoot 'unsigned.apk') classes.dex; if ($LASTEXITCODE -ne 0) { throw 'aapt add failed' } } finally { Pop-Location }
& (Join-Path $buildTools 'zipalign.exe') -f 4 (Join-Path $buildRoot 'unsigned.apk') (Join-Path $buildRoot 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
& (Join-Path $buildTools 'apksigner.bat') sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out (Join-Path $buildRoot 'probe.apk') (Join-Path $buildRoot 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }
& (Join-Path $buildTools 'apksigner.bat') verify (Join-Path $buildRoot 'probe.apk')
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
Get-Item (Join-Path $buildRoot 'probe.apk') | Select-Object FullName, Length
