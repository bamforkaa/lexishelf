<h1 align="center">LexiShelf</h1>

<p align="right">
  <a href="README.md">한국어</a> · <strong>English</strong>
</p>

<p align="center">
  A local-first multilingual vocabulary notebook and offline dictionary tool for Android
</p>

<p align="center">
  <a href="https://github.com/bamforkaa/lexishelf/releases">Releases</a> ·
  <a href="#installation">Installation</a> ·
  <a href="#features">Features</a> ·
  <a href="#dictionary-packs">Dictionary data</a> ·
  <a href="PRIVACY.md">Privacy</a> ·
  <a href="#licenses-and-data-sources">Licenses</a>
</p>

<p align="center">
  <img alt="Android 6.0+" src="https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&amp;logoColor=white">
  <a href="https://github.com/bamforkaa/lexishelf/releases"><img alt="Latest release" src="https://img.shields.io/badge/release-v0.1.0-4f46e5"></a>
  <img alt="Local first" src="https://img.shields.io/badge/storage-local--first-334155">
  <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/license-MIT-0f766e"></a>
</p>

## Overview

LexiShelf is a personal Android vocabulary notebook for organizing words and expressions and using
them entirely on the device. You can edit multiple senses, examples, notes, tags, wordbooks,
readings and pronunciation. Reviewed dictionary packs can optionally assist entry creation, while
expressions not found in a dictionary can always be entered manually.

User vocabulary is stored locally with Room. LexiShelf has no account system, project-operated
backend, cloud sync, advertising or app analytics. Saved vocabulary and installed local dictionary
packs remain available offline.

This project began as a vocabulary app I wanted to build and use myself. The source code and
installable APK are published to document its development and to let others with similar needs try
the app or learn from its implementation.

As a personal project, it does not come with a guaranteed update schedule or long-term support.

## Features

- Store words and multi-word expressions with language tags
- Edit multiple senses, parts of speech, examples, readings, pronunciation, grammatical gender,
  notes and favorite status
- Manage independent tags and wordbooks, with local search and filtering
- Review and edit selected local dictionary results before saving
- Find English lemmas from forms such as `is → be` and `went → go`
- On-device handwriting recognition with optional ML Kit language models
- Session-only Writing Practice
- Export versioned JSON backups and restore them with validation and an explicit conflict policy
- Use the complete manual vocabulary workflow without installing dictionary data

Words and phrases use the same `VocabularyEntry` model. Idioms, phrasal verbs, collocations and
other phrases can be organized with the existing tag system instead of separate entity types.

## Screenshots

Screenshots will be added after real screens have been reviewed to ensure that they contain no
personal vocabulary or identifiable emulator data. The project does not publish fake mockups or
placeholder screenshots.

## Installation

Once public `v0.1.0` is published:

1. Download `LexiShelf-v0.1.0.apk` from
   [GitHub Releases](https://github.com/bamforkaa/lexishelf/releases).
2. Allow installation from the browser or file app that opened the APK.
3. Launch LexiShelf.
4. Open **Settings → Dictionary Data** and download only the packs needed for your languages.

The APK does not bundle dictionary databases or ML Kit language models. Regular users do not need
the converters, `local.properties`, raw databases or a development dictionary workspace.

The only official sideload build is `LexiShelf-v0.1.0.apk` attached to the GitHub `v0.1.0` release.
Unsigned builds from source and APKs re-signed by third parties are not official distribution APKs.

### Optional download verification

You can compare the APK's SHA-256 with `SHA256SUMS.txt` from the same release. The public signing
certificate fingerprint in the [APK verification guide](docs/apk-verification.md) identifies the
signer. Android does not automatically verify a checksum file placed beside an APK.

## Dictionary packs

| Data source | Best suited for | Direction | Optional download |
| --- | --- | --- | --- |
| Korean Basic Dictionary | Korean meanings within its coverage | 11 supported languages ↔ Korean | 67,967,805 bytes |
| PanLex pinned snapshot | Korean lexical fallback | 12 selected languages ↔ Korean | 72,462,030 bytes |
| CC-CEDICT | Chinese pinyin and detailed English glosses | Chinese → English | 3,971,412 bytes |
| Kaikki language packs | English fallback that may include part of speech, pronunciation, forms and examples | Per language → English | Separate pack per language |
| English Morphology | Form lookup such as `is → be` and `went → go` | English form → lemma | 29,322,956 bytes |

Kaikki packs are provided separately for `de`, `hi`, `pl`, `nl`, `pt`, `tr`, `cs`, `sv`, `uk`,
`vi`, `th` and `id`. LexiShelf does not offer a 600 MB aggregate bundle or install-everything
default. The app shows download and estimated installed sizes before installation.

Installing Korean Basic Dictionary or PanLex does not guarantee a Korean meaning for every word.
Dataset coverage is limited, and PanLex is a broad lexical fallback rather than a complete
dictionary.

The JMdict provider and converter remain available for local development, but no JMdict pack is
included in the public catalog or GitHub release because the project does not currently operate the
regular update process required by EDRDG.

Exact pack versions, source and payload checksums, installed sizes and artifact-specific release
decisions are listed in the [public artifact audit](docs/public-dictionary-artifacts.md) and the
[catalog JSON](distribution/dictionary-catalog-v1.json). Each data source has its own license.

## Privacy

- Vocabulary, meanings, examples, notes, tags and wordbooks are stored on the device.
- There is no account, project-operated backend or app analytics.
- Handwriting strokes and recognition candidates are session-only and are not stored in Room or
  backups.
- Network access may be used for catalog and pack downloads, ML Kit model or SDK behavior, and
  external NAVER Dictionary links opened by the user.
- LexiShelf does not upload user vocabulary.

See [PRIVACY.md](PRIVACY.md) for the full disclosure, including diagnostic or usage metadata that
the ML Kit SDK may collect and information that can be sent to GitHub or NAVER.

## Building from source

- **Required environment:** the latest stable Android Studio, Android SDK Platform 37 and compatible
  Build Tools. You can use the JDK bundled with Android Studio; Kotlin and Gradle do not need to be
  installed separately.
- **Android SDK setup:** open the cloned root directory in Android Studio and run Gradle sync.
  Android Studio normally creates the `local.properties` with the selected SDK path. If
  it is not created or the build reports `SDK location not found`, create it in the repository root
  and enter the actual SDK path.

```properties
sdk.dir=C\:\\Users\\<USER>\\AppData\\Local\\Android\\Sdk
```

- **Dictionary data location:** dictionary data is not required for a regular app build or test.
  `<repository>/.local/dictionary-data` is the default workspace used only when rebuilding packs for
  existing providers; adding an arbitrary dictionary file there does not make the app recognize it.
  Public `.dictpack` files can be installed from **Settings → Dictionary Data** in the built app.
- **Output:** `assembleDebug` creates a local debug key when needed and uses it to sign
  `app/build/outputs/apk/debug/app-debug.apk`. You do not need to create a key manually.

```powershell
git clone https://github.com/bamforkaa/lexishelf.git
cd lexishelf
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

If Android Studio is installed elsewhere, change `JAVA_HOME` to the `jbr` directory in that
installation. `gradlew.bat` is the repository's Gradle Wrapper: it downloads and runs the Gradle
version selected by the project, so a global Gradle installation is unnecessary. Use `./gradlew` on
macOS or Linux.

The generated debug APK has a different signing identity from the official production APK
published on GitHub Releases. See [development setup](docs/setup.md), the
[dictionary pack guide](docs/dictionary-packs.md) and [APK verification](docs/apk-verification.md).

## Background

LexiShelf was partly inspired by my experience using DevStory's
[VoCat - My Own Vocabulary](https://play.google.com/store/apps/details?id=kr.co.devstory.vocat)
several years ago. VoCat introduced me to a thoughtfully designed and convenient personal
vocabulary experience, and it was one of the things that motivated me to begin this project. I
respect the work its developer has put into building and maintaining the app over the years. That
experience encouraged me to create a separate tool around my own learning workflow and
local-first, offline design goals.

This acknowledgement refers only to my personal experience several years ago; it is not intended
as a comparison or assessment of VoCat's current features. LexiShelf is independently designed and
implemented and is not affiliated with or officially associated with VoCat or DevStory.

## Licenses and data sources

Application source code is available under the [MIT License](LICENSE). That license applies only to
source code authored for LexiShelf.

Dictionary content, transformed packs and third-party software retain their respective licenses and
are not relicensed under MIT. See [NOTICE.md](NOTICE.md), the
[dictionary source review](docs/dictionary-sources.md) and the
[artifact audit](docs/public-dictionary-artifacts.md). Dictionary accuracy or fitness for a
particular purpose is not guaranteed.

## Developer documentation

Detailed developer documentation is currently maintained primarily in Korean.

- [Architecture](docs/architecture.md)
- [Setup and environment](docs/setup.md)
- [Dictionary pack format and lifecycle](docs/dictionary-packs.md)
- [Dictionary sources and licenses](docs/dictionary-sources.md)
- [Backup format](docs/backup.md)
- [Public dictionary artifact audit](docs/public-dictionary-artifacts.md)
- [APK verification](docs/apk-verification.md)
- [Third-party software](docs/third-party-software.md)
