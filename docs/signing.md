# Production APK signing

Android accepts an update for the same application ID only when the new APK uses the same signing
identity (subject to Android's supported signing-key rotation mechanisms). Losing the LexiShelf
production key can therefore prevent normal updates for existing installs.

## Secret-free repository configuration

The repository contains no production key or password. `app/build.gradle.kts` reads either:

- environment variables `LEXISHELF_KEYSTORE_FILE`, `LEXISHELF_KEYSTORE_PASSWORD`,
  `LEXISHELF_KEY_ALIAS`, `LEXISHELF_KEY_PASSWORD`; or
- an untracked root `signing.properties` with `storeFile`, `storePassword`, `keyAlias`,
  `keyPassword`.

`config/signing.properties.example` documents the keys without real values. `signing.properties`,
`keystore.properties`, `*.jks`, `*.keystore`, `*.p12` and `*.pfx` are gitignored. Do not place signing secrets in
`local.properties`, Gradle files, source, screenshots, issue logs or CI output.

## Create and protect the release identity

The v0.1.0 identity policy is:

- purpose: sign every public sideload release for `io.github.bamfor.lexishelf`;
- alias: `lexishelf-release`;
- keystore type: PKCS#12 stored outside the Git repository;
- key: RSA 4096-bit with SHA-256 signatures;
- validity: 10,000 days;
- certificate common name: the repository author identity `letorrte`;
- Gradle credentials: the ignored root `signing.properties` on this release workstation.

These are identity-continuity choices, not disposable build settings. A later APK signed by a
different identity will not be a normal update for existing sideload installations. The earlier
`com.example.localvocabulary` application ID was a pre-release development identity and has no
public update-compatibility commitment.

Run the JDK `keytool` interactively so passwords are prompted instead of recorded in shell history:

```text
keytool -genkeypair -v -keystore <KEYSTORE_PATH> -alias <KEY_ALIAS> -keyalg RSA -keysize 4096 -validity 10000
```

Record the public certificate SHA-256 fingerprint after generation. Keep the primary keystore and
credential record in private local storage, plus at least two encrypted backups in separate
controlled locations, with at least one offline. Test restoring one backup before publishing.
Never copy the private key or credential record into Git, a release asset, issue, log or screenshot.

## Build a public candidate

Configure one of the two secret sources above, then require signing explicitly:

```powershell
$env:JAVA_HOME = '<ANDROID_STUDIO_INSTALL>\jbr'
.\gradlew.bat assembleRelease -PrequireProductionSigning=true
```

Without complete signing configuration, ordinary `assembleRelease` can still create an unsigned
archive for analysis. It is not a public APK. `-PrequireProductionSigning=true` makes the build fail
instead of silently producing that unsigned result.

## Verify before publishing

Use the `apksigner` from the installed Android SDK build-tools:

```text
<ANDROID_SDK_PATH>/build-tools/<VERSION>/apksigner verify --verbose --print-certs app-release.apk
```

Record the APK bytes, SHA-256 and certificate SHA-256 fingerprint in the private release record and
public release notes. A certificate fingerprint is public metadata; passwords and private-key
material are not.

The permanent LexiShelf production certificate SHA-256 is:

```text
10:51:D4:C5:8B:DC:08:AE:CC:1A:FF:17:A9:57:3E:0B:8B:F7:DB:06:10:51:AF:3B:2E:10:5E:F3:DE:A7:B4:BA
```

The release staging helper pins this value and refuses an otherwise valid APK from another signer.

The release staging helper also requires the verifier path and refuses an unsigned or invalid APK:

```powershell
python -X utf8 -m tools.prepare_github_release `
  --signed-apk app\build\outputs\apk\release\app-release.apk `
  --apksigner <ANDROID_SDK_PATH>\build-tools\<VERSION>\apksigner.bat
```

On a disposable device/AVD, test both a fresh install and an update over the last signed build:

```text
adb install LexiShelf-v0.1.0.apk
adb install -r LexiShelf-v0.1.0.apk
```

The update test must preserve Room data, DataStore settings and installed dictionary packs. Keep the
same production identity for every future `io.github.bamfor.lexishelf` APK unless a reviewed Android
key-rotation plan is deliberately adopted.
