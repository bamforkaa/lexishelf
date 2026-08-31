# Future Google Play Data Safety notes

LexiShelf v0.1.0 is planned as a GitHub APK. No Google Play Data Safety form has been submitted.
This file is a future-form worksheet, not a declaration that Play distribution exists.

The app's own code has no account, backend, advertising or analytics service and does not upload
vocabulary. Vocabulary, notes, tags, wordbooks, backup data and handwriting ink remain local under
the behavior documented in [PRIVACY.md](../PRIVACY.md).

The integrated `com.google.mlkit:digital-ink-recognition:19.0.0` SDK must be assessed separately.
Google's current disclosure lists device information, app information, identifiers, performance
metrics, API configuration, input/output size, feature versions, event types and error codes for
diagnostics/usage analytics, plus configured languages for Digital Ink Recognition. Google says the
listed data is encrypted in transit and not transferred to third parties.

Before any future Play submission:

1. Re-read Google's disclosure for the exact resolved ML Kit version.
2. Inspect the final merged release manifest and dependency graph.
3. Distinguish app-developer collection from SDK collection and Play's definition of “sharing.”
4. Include GitHub pack delivery only if the submitted build still uses it.
5. Recheck retention, deletion and optional-processing answers against the actual build.
6. Have the final form reviewed; do not copy this worksheet as a completed submission.

Official reference: https://developers.google.com/ml-kit/android-data-disclosure
