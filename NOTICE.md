# Third-Party Data and Software Notices

This notice distinguishes the LexiShelf source code from third-party dictionary data and software.
It is a summary and does not replace the applicable license text.

## Dictionary data

### Korean Basic Dictionary

- Source: National Institute of Korean Language, Korean Basic Dictionary
- Exact dataset: official full JSON export dated 2026-08-19
- License: Creative Commons Attribution-ShareAlike 2.0 Korea
- Attribution: `한국어기초사전 - 국립국어원 제공, CC BY-SA 2.0 KR.`
- Modifications: text-only data is transformed and indexed for LexiShelf; image, audio, video,
  pronunciation media and other multimedia are excluded
- Source: https://krdict.korean.go.kr/download/downloadPopup
- Official copyright policy: https://krdict.korean.go.kr/kor/kboardPolicy/copyRightTermsInfo
- License: https://creativecommons.org/licenses/by-sa/2.0/kr/

### CC-CEDICT

- Source: CC-CEDICT / MDBG
- Exact dataset: `cedict_1_0_ts_utf-8_mdbg.txt.gz`, 2026-08-22T08:27:42Z
- License: Creative Commons Attribution-ShareAlike 4.0 International
- Attribution: `CC-CEDICT data from MDBG, CC BY-SA 4.0.`
- Modifications: the source GZip is repackaged in a validated `.dictpack`; the dictionary text is
  not rewritten
- Source: https://cc-cedict.org/editor/editor.php?handler=Download
- License: https://creativecommons.org/licenses/by-sa/4.0/

### PanLex

- Source: PanLex Database
- Exact dataset: checksum-pinned `panlex-20190901-csv.zip` snapshot dated 2019-09-01
- License decision: the embedded CC0 1.0 grant in that exact artifact only
- Attribution: citation to https://panlex.org/ is recommended
- Modifications: selected language varieties and direct Korean co-denotation relationships are
  transformed into a compact exact-lookup SQLite index
- Preserved artifact: https://web.archive.org/web/20240614064246id_/https://db.panlex.org/panlex-20190901-csv.zip
- License: https://creativecommons.org/publicdomain/zero/1.0/

This decision must not be generalized to another PanLex release or to PanLex as a whole.

### Kaikki / English Wiktionary / Wiktextract

- Source: English Wiktionary data extracted by Wiktextract and distributed through Kaikki.org
- Exact dataset: English Wiktionary dump `enwiktionary-2026-08-05`
- Reuse path: Creative Commons Attribution-ShareAlike 4.0
- Attribution is retained in each pack and imported field provenance
- Modifications: language-specific SQLite indexes and an English morphology index are generated;
  externally attributed quotations, audio/media and separately licensed material are excluded
- Source: https://kaikki.org/dictionary/rawdata.html
- Wiktionary copyright terms: https://en.wiktionary.org/wiki/Wiktionary:Copyrights
- License: https://creativecommons.org/licenses/by-sa/4.0/

No combined Kaikki pack is distributed. The public release contains 12 independent language packs
and one English morphology pack.

### JMdict

JMdict integration, converter and tests remain available for development/local use. No JMdict data
pack is distributed in the public v1 catalog or GitHub Release. The EDRDG license requires software
using the data to maintain a regular procedure for updating to recent dictionary versions; public
v1 does not assume that continuing obligation.

- EDRDG license: https://www.edrdg.org/edrdg/licence.html
- JMdict project: https://www.edrdg.org/jmdict/j_jmdict.html

### Exact pack metadata

Every public archive, dataset version, source artifact identifier and checksum, transformation,
license, ShareAlike flag, size and redistribution decision is listed in
[docs/public-dictionary-artifacts.md](docs/public-dictionary-artifacts.md) and the machine-readable
[distribution/dictionary-catalog-v1.json](distribution/dictionary-catalog-v1.json).

## Software dependencies

LexiShelf uses AndroidX, Jetpack Compose, Material 3, Room, Navigation, DataStore, Kotlin,
kotlinx.coroutines, kotlinx.serialization, Dagger/Hilt, OkHttp and Google ML Kit Digital Ink
Recognition. Open-source dependency licenses and ML Kit terms are separate from dictionary data
licenses. See [docs/third-party-software.md](docs/third-party-software.md) for the maintained summary
and verify the resolved release dependency graph for every release.

## LexiShelf source code

Copyright (c) 2026 letorrte. LexiShelf-authored source code is licensed under the MIT License; see
the repository root `LICENSE`.

That MIT License does not replace or alter the separate licenses for dictionary data or third-party
software listed above.
