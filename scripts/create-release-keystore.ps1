<#
.SYNOPSIS
    Creates the BabyLog release keystore, outside the repository and outside Syncthing.

.DESCRIPTION
    Phase 16 of the BabyLog plan. The release key IS the app's identity on the phone: Android only
    installs a build as an update when its signing certificate matches the one already there. Lose the
    keystore and every installed copy has to be uninstalled before it can move forward.

    The password is typed here, at a prompt, and handed to keytool through an environment variable
    (-storepass:env) so it never appears in a command line, a shell history or a terminal log. It is then
    written to keystore.properties, which Gradle reads and which is never committed -- the copy in your
    password manager is the one that matters.

    Refuses to overwrite an existing keystore. Regenerating the key would orphan both phones.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\create-release-keystore.ps1 -DryRun

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\create-release-keystore.ps1
#>
[CmdletBinding()]
param(
    [string] $KeystoreDir = (Join-Path $env:USERPROFILE 'keystores'),
    [string] $Alias = 'babylog',
    [string] $DName = 'CN=BabyLog, OU=Family, O=BabyLog, C=TT',
    [int]    $ValidityDays = 10000,
    [switch] $DryRun
)

$ErrorActionPreference = 'Stop'

$keystorePath    = Join-Path $KeystoreDir 'babylog-release.jks'
$propsPath       = Join-Path $KeystoreDir 'keystore.properties'
$fingerprintPath = Join-Path $KeystoreDir 'babylog-release.fingerprints.txt'

# keytool ships with the JDK. This machine has only Android Studio's JBR, so check that first, then
# JAVA_HOME, then whatever is on PATH. Never assume the binary is on PATH.
$candidates = New-Object System.Collections.Generic.List[string]
$candidates.Add('C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe')
if ($env:JAVA_HOME) { $candidates.Add((Join-Path $env:JAVA_HOME 'bin\keytool.exe')) }

$keytool = $null
foreach ($candidate in $candidates) {
    if ($candidate -and (Test-Path -LiteralPath $candidate)) { $keytool = $candidate; break }
}
if (-not $keytool) {
    $found = Get-Command keytool.exe -ErrorAction SilentlyContinue
    if ($found) { $keytool = $found.Source }
}
if (-not $keytool) {
    throw 'keytool.exe not found. Set JAVA_HOME to a JDK, or install Android Studio (its bundled JBR has one).'
}

Write-Host ''
Write-Host 'BabyLog release keystore (Phase 16)'
Write-Host "  keytool  : $keytool"
Write-Host "  keystore : $keystorePath"
Write-Host "  props    : $propsPath"
Write-Host "  alias    : $Alias"
Write-Host "  validity : $ValidityDays days"
Write-Host ''

if (Test-Path -LiteralPath $keystorePath) {
    throw "A keystore already exists at $keystorePath. This script will not overwrite it: a new key would orphan every installed copy of BabyLog. Back the existing one up instead -- see docs/release-signing.md."
}

if ($DryRun) {
    Write-Host '[DryRun] Nothing was created. Re-run without -DryRun to make the key.'
    return
}

New-Item -ItemType Directory -Path $KeystoreDir -Force | Out-Null

$secure1 = Read-Host -AsSecureString -Prompt 'Keystore password (not echoed)'
$secure2 = Read-Host -AsSecureString -Prompt 'Repeat the password'

$bstr1 = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure1)
$bstr2 = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure2)
try {
    $pw1 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr1)
    $pw2 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr2)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr1)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr2)
}

if ($pw1 -ne $pw2) {
    throw 'The two passwords did not match. Nothing was created.'
}
if ($pw1.Length -lt 12) {
    throw 'Use at least 12 characters. This password is the whole of the app''s update identity.'
}
# Gradle loads .properties as ISO-8859-1, so a non-ASCII password would come back mangled on the build.
if ($pw1 -match '[^\x20-\x7E]') {
    throw 'The password must be printable ASCII. keystore.properties is read as ISO-8859-1 by Gradle.'
}

try {
    # The password travels in the child process environment, never in argv: keytool reads the
    # variable named after -storepass:env.
    $env:BABYLOG_KS_PASS = $pw1

    & $keytool -genkeypair -v `
        -keystore $keystorePath `
        -storetype PKCS12 `
        -alias $Alias `
        -keyalg RSA `
        -keysize 4096 `
        -validity $ValidityDays `
        -dname $DName `
        -storepass:env BABYLOG_KS_PASS

    if ($LASTEXITCODE -ne 0) {
        throw "keytool failed with exit code $LASTEXITCODE. No keystore was created."
    }

    $listing = & $keytool -list -v -keystore $keystorePath -alias $Alias -storepass:env BABYLOG_KS_PASS
    if ($LASTEXITCODE -ne 0 -or -not $listing) {
        throw 'The keystore was created but could not be read back. Check it by hand before using it.'
    }
} finally {
    Remove-Item Env:BABYLOG_KS_PASS -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host 'Certificate fingerprints:'
$listing | Select-String -Pattern 'SHA1:|SHA256:' | ForEach-Object { Write-Host ('  ' + $_.Line.Trim()) }
Set-Content -Path $fingerprintPath -Value $listing -Encoding ASCII

$storeFile = $keystorePath.Replace('\', '/')
$props = @(
    '# BabyLog release signing - generated by scripts/create-release-keystore.ps1 (Phase 16).',
    '# NEVER commit this file. The keystore it points at IS the app identity: see docs/release-signing.md.',
    "storeFile=$storeFile",
    "storePassword=$pw1",
    "keyAlias=$Alias",
    "keyPassword=$pw1"
)
Set-Content -Path $propsPath -Value $props -Encoding ASCII

$pw1 = $null
$pw2 = $null

Write-Host ''
Write-Host "Keystore written : $keystorePath"
Write-Host "Fingerprints     : $fingerprintPath"
Write-Host "Gradle reads     : $propsPath"
Write-Host ''
Write-Host 'Do these three things next:'
Write-Host '  1. Put the password in your password manager, and copy the .jks to a backup that is'
Write-Host '     NOT in Documents\Syncthing. Nobody can recover it for you.'
Write-Host '  2. Register the SHA-1 above in the Firebase console:'
Write-Host '     Project settings -> Your apps -> app.babylog -> Add fingerprint.'
Write-Host '  3. Build the release APK: ./gradlew :app:assembleRelease'
