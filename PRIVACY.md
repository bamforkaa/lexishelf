# LexiShelf Privacy Notice

Last reviewed: 2026-08-31

LexiShelf is a local-first Android vocabulary app. It has no user account, project-operated backend,
cloud synchronization, advertising or app-owned analytics service. This notice describes the app
code in this repository and the third-party/network behavior it actually invokes.

## Data stored on the device

LexiShelf stores vocabulary, meanings, selected original definitions, examples, reading,
pronunciation, grammatical gender, notes, timestamps, tags, wordbooks, dictionary provenance and
language settings in app-private storage. Installed dictionary packs, activation metadata and a
cached public catalog are also local. The v0.1.0 domain model has no favorite or persistent review
metadata.

Writing Practice is session-only and does not persist attempt history. Dictionary packs and ML Kit
models are not part of the vocabulary JSON backup. Android platform backup/device transfer is
disabled; users must create a JSON backup before uninstalling if they want to preserve vocabulary.

## Handwriting

The app passes in-memory stroke data to Google ML Kit Digital Ink Recognition. Recognition is
performed on the device after the selected language model is available. Raw ink and recognition
candidates are temporary UI state and are not stored in Room or included in JSON backups.

The ML Kit SDK can contact Google to download/delete language models and for SDK/model compatibility
behavior. Google's current Android disclosure says ML Kit SDKs collect device and application
information, installation/device identifiers, performance metrics, API configuration, input/output
size, feature version, event types and error codes for diagnostics and usage analytics; Digital Ink
Recognition additionally collects configured languages. Google states that the listed data is
encrypted in transit with HTTPS and is not transferred to third parties.

- Digital Ink Recognition: https://developers.google.com/ml-kit/vision/digital-ink-recognition/android
- ML Kit Android data disclosure: https://developers.google.com/ml-kit/android-data-disclosure
- ML Kit terms: https://developers.google.com/ml-kit/terms

LexiShelf does not add its own analytics around handwriting recognition and does not upload the ink
or recognition text to a project-operated server.

## Network connections

### GitHub catalog and dictionary packs

Opening Settings can fetch the public dictionary catalog from GitHub. A pack download happens only
after the user selects it. Requests can disclose ordinary network metadata to GitHub and its release
asset infrastructure, including IP address, request time, app/network headers and the requested
catalog or pack URL. The request does not include vocabulary, notes, tags or handwriting.

The app requires HTTPS, restricts catalog and pack URLs to the LexiShelf GitHub repository and known
GitHub asset hosts, verifies archive SHA-256, then reuses strict manifest, payload checksum, schema
and archive validation before atomic activation. Catalog failure does not block vocabulary or
installed packs.

### External NAVER Dictionary action

When the user explicitly taps a supported NAVER Dictionary reference, LexiShelf opens the system
browser with the entered headword in the search URL. The browser and NAVER then receive that query
and normal web request metadata under their own privacy policies. LexiShelf does not fetch, scrape,
prefetch, cache or import the NAVER page.

### No vocabulary upload

The app does not transmit the local vocabulary database, backups, meanings, examples, notes, tags or
wordbooks to GitHub, Google, NAVER or a LexiShelf-operated service.

## Permissions and files

`INTERNET` supports catalog/pack downloads and ML Kit SDK/model behavior. Transitive AndroidX/ML Kit
components can add network-state, wake-lock, boot and foreground-service declarations used for
background model work. LexiShelf does not request contacts, location, camera, microphone or broad
storage access. Backup/import and local pack import use Android's Storage Access Framework for a
file the user explicitly selects.

## Deletion and export

Deleting a dictionary pack removes only that app-private dictionary dataset and does not delete
user vocabulary or backups. Clearing app data or uninstalling can delete the Room database,
settings, installed packs and catalog cache. Use the in-app JSON export before doing so.

## Distribution scope

This release is distributed as a GitHub APK, not through Google Play. No Google Play Data Safety
form has been submitted. Any future store submission must reassess the final app, resolved SDK
versions and store disclosure requirements at that time.
