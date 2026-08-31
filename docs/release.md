# Public v0.1.0 release readiness

Reviewed: 2026-09-01

Current decision: **B — ready after the listed manual blockers**. Source licensing, the permanent
production identity, signed build, local fresh/update QA and exact release staging are complete.
GitHub publication and the clean-device flow against the resulting public URLs have not occurred,
so this record does not claim the stricter A verdict.

## Fixed product invariants

| Item | Value |
| --- | --- |
| application ID | `com.example.localvocabulary` |
| version | `0.1.0` / versionCode 1 |
| Room | schema v6 with reviewed migrations |
| backup | schema v5 with legacy decoders |
| Kaikki public data | generated schema v3 |
| base APK | no dictionary pack, raw dataset or ML Kit language model |
| dictionary packs | optional GitHub Release assets installed into app-private storage |
| JMdict | local/development integration only; no public asset/catalog entry |

## Catalog and download channel

The APK knows one stable endpoint:

```text
https://github.com/Bamfor/lexishelf/releases/latest/download/dictionary-catalog-v1.json
```

Individual URLs exist only in [the generated catalog](../distribution/dictionary-catalog-v1.json).
The release generator audits exact `.dictpack` archives and emits 16 entries: Korean Basic,
CC-CEDICT, the pinned PanLex snapshot, 12 language-specific Kaikki packs and English morphology.
JMdict, unknown providers, old Kaikki schemas and aggregate packs fail generation or Android parsing.

The download lifecycle is:

```text
catalog/cache → selected HTTPS asset → app-private staging → archive SHA-256
→ catalog-to-manifest identity → existing manifest/payload/schema validation
→ atomic activation → staging cleanup
```

Only one pack is installed at a time. Settings displays download and installed sizes, progress,
validation, installation, success/failure and retry/cancel actions. Failure or cancellation removes
the partial archive and does not move the active pointer. Delete removes only the selected pack;
Room vocabulary and JSON backups are untouched.

The last-known valid catalog is cached. Network/catalog failure leaves app launch, manual CRUD,
backup, practice and installed local dictionaries operational.

### Security boundary

- cleartext app traffic is disabled;
- catalog entries require versioned HTTPS assets under `Bamfor/lexishelf`;
- redirects are followed manually and every hop must remain on approved GitHub asset hosts;
- catalog archive SHA-256 detects corruption/tampering relative to received metadata;
- the existing pack validator independently verifies payload size/SHA, archive shape, schema and
  SQLite integrity or complete GZip CRC stream before activation.

The catalog is not Ed25519-signed in v1. HTTPS plus catalog-declared archive hashes and the pack's
independent payload validation are the accepted minimum, while signed catalog metadata remains
future hardening. APK production signing is mandatory and separate.

## GitHub Release assets

Target release `v0.1.0`:

- `LexiShelf-v0.1.0.apk` (production signed);
- `dictionary-catalog-v1.json`;
- Korean Basic, CC-CEDICT and pinned PanLex `.dictpack` files;
- 12 `kaikki.<language>-en-...dictpack` files;
- `kaikki.en-morphology-...dictpack`;
- `SHA256SUMS.txt` covering the exact published files;
- `THIRD_PARTY_NOTICES.md` and release notes.

There is no JMdict asset. APK/AAB/dictpack/DB binaries remain outside normal Git history. After
signing and `apksigner` verification, stage assets under ignored `build/` with:

```powershell
python -X utf8 -m tools.prepare_github_release `
  --signed-apk app\build\outputs\apk\release\app-release.apk `
  --apksigner <ANDROID_SDK_PATH>\build-tools\36.0.0\apksigner.bat
```

The tool first requires `apksigner verify --verbose --print-certs` to succeed, regenerates the
reviewed catalog from all 16 exact local archives, validates every catalog size/hash and refuses
output outside `<PROJECT_ROOT>/build`. Upload the staged directory to GitHub Release; do not commit it.

The verified local staging result contains 21 files. `SHA256SUMS.txt` covers the other 20 files in
deterministic filename order; an independent pass recomputes every hash. Its APK entry, rather than
a pre-commit value embedded in source documentation, is the authoritative release APK size/hash
record.
There are exactly 16 `.dictpack` files, zero JMdict files, zero unsigned APKs and one signed APK.

This workstation has no GitHub CLI, and an unauthenticated HTTPS check on 2026-09-01 returned 404
for the repository API, v0.1.0 release API and stable catalog URL. That means the repository/release
is not yet publicly reachable. After intentionally making the repository public, installing and
authenticating `gh`, protecting and backup-testing the signing key, use this reviewed sequence from
the release commit:

```powershell
git tag -a v0.1.0 -m "LexiShelf v0.1.0"
git push origin master
git push origin v0.1.0
$assets = Get-ChildItem .\build\release-assets\v0.1.0 -File |
  Sort-Object Name |
  ForEach-Object FullName
gh release create v0.1.0 $assets `
  --repo Bamfor/lexishelf `
  --verify-tag `
  --draft `
  --title "LexiShelf v0.1.0" `
  --notes-file .\build\release-assets\v0.1.0\RELEASE_NOTES.md
gh release view v0.1.0 --repo Bamfor/lexishelf
```

Inspect the draft's exact 21-file inventory and hashes before the separate, deliberate public step:

```powershell
gh release edit v0.1.0 --repo Bamfor/lexishelf --draft=false
```

Do not create the tag or run the public step merely to test credentials.

## Production signing

Gradle accepts an untracked `signing.properties` or the four `LEXISHELF_*` signing environment
variables. The public command is:

```powershell
.\gradlew.bat assembleRelease -PrequireProductionSigning=true
```

It fails when signing configuration is incomplete. Details, key continuity, backup and verification
are in [signing.md](signing.md). The private key and passwords remain outside Git. The permanent
certificate SHA-256 is
`10:51:D4:C5:8B:DC:08:AE:CC:1A:FF:17:A9:57:3E:0B:8B:F7:DB:06:10:51:AF:3B:2E:10:5E:F3:DE:A7:B4:BA`.

## Public provider and artifact decision

The complete provider matrix, exact source/release/checksum, ShareAlike/transformation decision and
per-pack download/installed size are generated in
[public-dictionary-artifacts.md](public-dictionary-artifacts.md). All public entries are explicitly
`PUBLIC_DISTRIBUTION_ALLOWED`. The decision is artifact-specific and must be repeated for updates.

## Privacy and notices

- [PRIVACY.md](../PRIVACY.md) covers local data, GitHub requests, ML Kit SDK disclosure, explicit
  NAVER browser actions, deletion and the absence of accounts/backend/app analytics.
- [NOTICE.md](../NOTICE.md) separates dictionary data, software dependencies and LexiShelf source.
- Settings displays provider attribution/license links and prioritizes an installed pack's exact
  dataset version.
- Each `.dictpack` manifest retains source URL/version/build tool, license and attribution; the
  catalog adds exact source artifact/checksum and conversion notice.

## Environment observed for this release pass

- Android Studio installed;
- Android SDK platform 37 and build-tools/apksigner installed;
- Android Studio JBR available;
- Gradle Wrapper present and used;
- two AVD definitions and one running emulator detected;
- shell `JAVA_HOME` and `PATH` were initially invalid/missing, so verification commands set the
  bundled JBR and SDK tools per process without changing the OS or shell profile.

## Verification evidence

The final local verification pass used the Gradle Wrapper and completed:

| Check | Result |
| --- | --- |
| Python dictionary/converter/release tests | 59 passed |
| `testDebugUnitTest` | 236 passed |
| `connectedDebugAndroidTest` | 138 tests: 137 passed, 1 deliberate external-pack skip, 0 failed |
| `lintDebug` / `lintRelease` | 0 errors, 6 dependency-update warnings in each variant |
| `assembleDebug` / `assembleDebugAndroidTest` / `assembleRelease` | successful |
| public catalog/release staging audit | exact 16-pack catalog; 21 assets; all 20 checksum entries verified |

The final connected run used a freshly launched dedicated `Medium_Phone_Test` AVD and completed in
4 minutes 6 seconds. The one skip requires an externally staged real pack and is deliberate; pack
archive/installation behavior is covered by the remaining repository/instrumentation tests and the
separate Manual AVD release flow.

Two independent pre-commit production builds used the same permanent certificate. Their SHA-256
values were `8993ceb08fd2dc3a7bf177c363526053b2d72a82fdb8e871812f7d61ceaa1354` and
`5f4ec02e1906f8daa952b2f001d4995ee2c31145c2da20756f95daad4283f3c7`. Different APK hashes are
expected build outputs; `apksigner` reported the exact certificate fingerprint above for both. The
distributable candidate is rebuilt from the release commit so its embedded Git revision matches the
tag target; its exact size and hash are generated into staged `SHA256SUMS.txt`. That candidate must
verify with v1, v2 and v3 signatures, one RSA 4096-bit signer and no `.dictpack`, database or named
dictionary dataset payload.

Fresh-install QA used a wiped `Medium_Phone_Manual` AVD, separate from the instrumentation AVD. The
signed APK installed, cold-launched, showed the no-pack state, created `releaseword` with a sense,
created standalone `releasetag` and `releasebook`, changed the default language to `ja`, opened the
backup screen and restarted normally. Installing the independently rebuilt APK with `adb install -r`
succeeded. `firstInstallTime` remained `2026-08-31 16:19:04` and `lastUpdateTime` changed from that
value to `2026-08-31 16:32:38`; the vocabulary, tag, wordbook and DataStore language setting were
still visible after a cold restart. The initial install intentionally had zero packs, so the
zero-pack state was preserved. v0.1.0 implements no API credential store, making credential
migration not applicable. Actual installed-pack update preservation remains covered by Android
integration tests and must be exercised again in the published-URL flow.

The final merged manifest declares `INTERNET` plus transitive network-state, wake-lock, boot and
foreground-service permissions used by AndroidX/ML Kit background work. It requests no contacts,
location, camera, microphone or broad storage permission. Version metadata is `0.1.0` / 1,
minSdk 23 and targetSdk 37.

## Public repository sanitization

- current-tree occurrences of the developer username: 6 before sanitization, 0 after;
- current-tree occurrences of the developer dictionary-data path: 19 before, 0 after;
- remaining developer-specific absolute paths in source and public documentation: 0 (synthetic
  path fixtures remain in path-resolution tests);
- `local.properties`, signing properties, keystores, APK/AAB, dictionary packs and databases are
  ignored and not tracked;
- four previously tracked `__pycache__` bytecode files are removed by this release patch;
- tracked filenames and full Git object names contain no keystore, private-key or release-binary
  artifact;
- history review found harmless local paths in six commits, public example/config keywords, no
  potential credential and no confirmed credential;
- history rewrite is not required. Rewriting for harmless paths alone would add unnecessary clone
  and collaboration disruption.

## Public release blocker table

| Issue | Status | Blocker? | Evidence |
| --- | --- | --- | --- |
| MIT source license | PASS | no | root `LICENSE`, README and NOTICE separate source/data/software licenses |
| Permanent signing key | PASS locally | publication safeguard | external PKCS#12, pinned certificate, ignored credentials; two encrypted backups and one restore test remain an operator action |
| Signed APK | PASS | no | production gate, two builds, exact `apksigner` identity and archive audit |
| Fresh install | PASS | no | wiped Manual AVD workflow above |
| Update install | PASS | no | signer-matched `adb install -r`, timestamps and persisted Room/DataStore state above |
| Release assets | PASS | no | fail-closed helper produced 21 assets; 16 packs, 20 verified checksums, no JMdict/unsigned APK |
| GitHub publication | NOT RUN | yes | no `gh`/auth; unauthenticated repository and v0.1.0 API checks return 404 |
| Published catalog | BLOCKED by publication | yes | release URLs intentionally return no release until `v0.1.0` exists |
| Clean-device public URL QA | BLOCKED by publication | yes | must use the public catalog and assets, not local substitutes |
| Privacy | PASS | no | GitHub, ML Kit and explicit NAVER paths match app behavior and merged permissions |
| NOTICE | PASS | no | exact four public dataset families, software separation and JMdict exclusion |

## Rollback after publication

For a critical v0.1.0 problem, preserve the signing key, withdraw the GitHub Release while triaging,
and publish a new `v0.1.1` tag/release after correction. Prefer a new immutable version over silently
replacing the v0.1.0 APK or dictionary asset under an existing filename/checksum. Deleting or
withdrawing the release stops new downloads but does not remove APKs or packs users already have;
release notes should describe the affected hashes and safe next action. Never discard or casually
rotate the v0.1.0 signing identity, because existing sideload installs depend on it for updates.

## OPTIONAL after v0.1.0

- Ed25519-sign the catalog or release manifest and pin a public verification key in the app.
- Move large foreground downloads to a user-visible WorkManager/foreground-service flow if downloads
  must survive process death.
- Add privacy-scrubbed real screenshots.
- Evaluate R8/resource shrinking with a minified smoke test.

Automatic dictionary updates, account/sync/backend, a full Kaikki bundle, public JMdict, SRS,
persistent learning history and Google Play submission are outside v0.1.0.
