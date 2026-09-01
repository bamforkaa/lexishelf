# LexiShelf v0.1.0

LexiShelf is a local-first Android vocabulary app with manual editing, optional offline dictionary
packs, handwriting input, Writing Practice and JSON backup/restore.

## Requirements

- Android 6.0 (API 23) or newer
- Free storage for the base APK and only the dictionary/handwriting models you choose

## Install

1. Download `LexiShelf-v0.1.0.apk` from this release.
2. Allow installation from the browser or file app used to open the APK.
3. Launch LexiShelf.
4. Open **Settings → Dictionary Data** and download only the packs needed for your languages.

## Optional download verification

Compare the downloaded APK's SHA-256 with `SHA256SUMS.txt` to verify file integrity. Android does
not automatically read a checksum file placed beside the APK. The signing certificate fingerprint
below identifies the signer of the official LexiShelf APK and can be inspected with Android SDK
`apksigner` or another trusted APK inspection tool.

Production signing certificate SHA-256:
`10:51:D4:C5:8B:DC:08:AE:CC:1A:FF:17:A9:57:3E:0B:8B:F7:DB:06:10:51:AF:3B:2E:10:5E:F3:DE:A7:B4:BA`.

The exact release APK byte size and SHA-256 are recorded in this release's `SHA256SUMS.txt`. This
avoids publishing a checksum for a pre-tag build.

## Optional dictionary packs

No dictionary database is bundled in the APK. Open **Settings → Dictionary Data** and download only
the packs needed for your languages. The app verifies HTTPS origin, archive checksum, manifest,
payload checksum and schema before atomic activation. Installed packs work offline and can be deleted
without deleting vocabulary.

JMdict is not a public v1 asset. Kaikki data is split into independent language packs; there is no
aggregate download.

## Known limitations

- Dictionary coverage is incomplete and does not guarantee a result for every word or expression.
- Handwriting language models are separate on-demand ML Kit downloads.
- There is no account, sync, backend, SRS or persistent Writing Practice history.
- Android app deletion can remove local vocabulary; export JSON first.

## Privacy and licenses

- Application source: [MIT License](https://github.com/Bamfor/lexishelf/blob/v0.1.0/LICENSE)
- Privacy: [PRIVACY.md](https://github.com/Bamfor/lexishelf/blob/v0.1.0/PRIVACY.md)
- Third-party notices: [NOTICE.md](https://github.com/Bamfor/lexishelf/blob/v0.1.0/NOTICE.md)
- Exact dictionary artifacts: [artifact audit](https://github.com/Bamfor/lexishelf/blob/v0.1.0/docs/public-dictionary-artifacts.md)

Dictionary content is supplied under third-party licenses and without a guarantee of accuracy or
fitness for a particular purpose.
