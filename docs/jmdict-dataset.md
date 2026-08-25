# JMdict local dataset

> Task 9부터 생성 DB는 APK asset이 아니라 `jmdict/generated/`에서 generic pack으로
> 만듭니다. 현재 root/install 절차는 [dictionary-packs.md](dictionary-packs.md)가 우선합니다.

## Official artifact and licence

Task 8 uses the English-only legacy XML `JMdict_e.gz` from the
[EDRDG JMdict project](https://www.edrdg.org/jmdict/j_jmdict.html). The selected artifact was
created 2026-08-23. The [official distribution index](https://ftp.edrdg.org/pub/Nihongo/00INDEX.html)
also publishes `_NG` files in parallel; this converter intentionally uses the still-published
legacy format whose DTD defines `re_restr`, `re_nokanji`, `stagk`, and `stagr`. NG support must
later be an explicit converter adapter.

| Property | Verified value |
| --- | --- |
| artifact / release | `JMdict_e.gz` / `2026-08-23` |
| compressed / XML bytes | 10,556,309 / 63,029,646 |
| SHA-256 | `11c3fb43a82ae775269e6832d117c4f52152f4d8cf49f44c16a0ed619aa98a6a` |
| entries / senses | 218,551 / 253,268 |
| exact lookup rows | 498,553 before duplicate-key elimination |
| generated SQLite | 113,729,536 bytes |
| conversion time | 13.25 seconds on the Task 8 Windows PC |
| debug APK increase | 27,874,032 bytes (116,588,051 → 144,462,083) |
| JMdict DB inside APK | 27,720,199 compressed bytes |

The [EDRDG licence statement](https://www.edrdg.org/edrdg/licence.html) licenses the Japanese
and English components under CC BY-SA 4.0. Attribution and ShareAlike apply. It specifically
requires an acknowledgement in an app-accessible About/Sources-style screen and a regular data
update procedure. Settings / Dictionary Sources supplies the acknowledgement and links. Other
JMdict gloss languages may have separate translator copyright and are not enabled.

The HTTPS FTP host presented a certificate host-name mismatch in this environment. Certificate
validation was not disabled. Obtain the artifact through the official project/index with a
correctly validating browser/client. Gradle and the app never download it.

## Build and install the index

Place `JMdict_e.gz` at `<dataset-root>/jmdict/source/JMdict_e.gz`, then run:

```powershell
python -X utf8 -m tools.build_jmdict_index
python -m tools.build_dictionary_packs --pack jmdict
```

Verify the converter's release, checksum and counts. Raw XML and generated DB are ignored by Git.
A clean clone still builds and manual vocabulary works, but JMdict suggestions report
`LocalDatasetUnavailable` until the generated pack is installed. Conversion uses a
temporary DB and atomic replacement, so malformed XML cannot replace an existing output.

## Index and mapping

The read-only SQLite contains metadata, minified UTF-8 provider-private entry payloads keyed by
stable `ent_seq`, and NFC/trimmed exact kanji/kana lookup keys. Payloads preserve writing/reading
order, `ke_inf`/`ke_pri`, `re_nokanji`/`re_restr`/`re_inf`/`re_pri`, ordered senses,
`stagk`/`stagr`, inherited POS, cross references, antonyms, fields, miscellaneous labels, sense
notes, loan sources, dialects, ordered glosses and gloss attributes. After the generic pack is
installed through Settings, the validated DB is opened read-only from
`noBackupFilesDir/dictionary-packs/jmdict/jmdict.ja-en/`; it is never imported into user Room or
JSON backup.

An experimental per-entry zlib payload produced a smaller 72,970,240-byte DB but occupied
58,783,843 bytes inside the APK because independently compressed blobs defeated APK-wide
deflate. The selected UTF-8 payload produces a larger 113,729,536-byte copied DB but only
27,720,199 APK bytes in the old bundled build. Task 9 removed this payload from the base APK and
distributes it as a separately installed pack.

Exact writing and reading matches are supported. No prefix, fuzzy, conjugation or morphological
normalization is performed. Homographs stay independent. A result whose exact matched source
element has an official priority marker sorts first; marker types are not assigned invented
scores. Ties use source order, match kind, element order and `ent_seq`.

Mapping policy:

- `re_restr` chooses only valid writing/reading combinations; `re_nokanji` remains kana-only.
- `stagk`/`stagr` filter inapplicable senses and remain visible as generic restriction sets.
- Only English glosses are emitted (`ja → en`), never Korean. Korean Basic Dictionary remains
  a distinct `ja → ko` provider.
- Official DTD-expanded POS labels are retained as editable strings. A broad enum was rejected
  because it loses transitivity, conjugation class and other source detail.
- All applicable senses are shown in order, but UI offers `Use this sense` per sense.
- Explicit Use copies only the chosen gloss/POS and primary reading. Alternative spellings and
  readings remain transient; the user's current headword is the persistent canonical writing.

Reading is a first-class vocabulary field in Room v4. Provider-neutral entry-field provenance
records its source independently from sense meaning/POS provenance. Backup v3 round-trips both.
Editing or clearing reading marks its provenance modified; refresh has no persistence path.

PC lookup measurement after the final payload choice: new connection plus first `食べる` lookup
8.364 ms; 100 warm lookups median 0.060 ms and p95 0.073 ms. `PRAGMA integrity_check` returned
`ok`. On `Medium_Phone_Test` AVD, a forced first asset copy took 531 ms and the following first
open plus `食べる` query took 19 ms. These are single-run diagnostic figures, not thresholds.

## Manual QA samples

All rows were read from the generated 2026-08-23 DB.

| Query | Expected entry / reading / ent_seq | Representative POS | First gloss |
| --- | --- | --- | --- |
| `食べる` | `食べる`, `たべる`, `1358280` | Ichidan; transitive | to eat |
| `たべる` | same entry by reading | Ichidan; transitive | to eat |
| `学校` | `学校` (`學校`), `がっこう`, `1206730` | noun | school |
| `もしもし` | kana-only entry (no kanji element), `1012550` | interjection | hello (e.g. on phone) |
| `見る` | `見る` (`観る`, `視る`), `みる`, `1259290` | Ichidan; transitive | to see |
| `こんにちは` | `今日は`, reading `こんにちは`, `1289400` | interjection | hello |
| `明日` | `あした`, `あす`, `みょうにち`, `1584660` | noun; adverb | tomorrow |
| `生` | seven independent entries; first `なま`, `1378450` | noun | raw |
| `開く` | independent `ひらく` and `あく` entries | Godan | to open |
| `今日` | `きょう`, `こんにち`, `こんち`, `1579110` | noun; adverb | today; "these days" is restricted to `こんにち` |
| `行く` | `行く`/`往く`, `いく`/`ゆく`, `1578850` | Iku/Yuku special | to go |
| `大人` | independent `おとな`, `たいじん`, `だいにん` entries | noun | adult |
| `会う` | `会う`/`逢う`/`遭う`, `あう`, `1198180` | Godan | to meet |
| `あう` | independent `会う` and `合う` reading matches | Godan | meet / merge |

Full-index restriction coverage: 2,752 entries with `re_restr`, 6,446 with `re_nokanji`,
466 with `stagk`, and 806 with `stagr`.

## Update, rollback and Task 9

For each official update: recheck licence/format, record checksum and sizes, run fixture/full
conversion, verify `PRAGMA integrity_check`, counts, restriction coverage and QA keys, compare
DB/pack/Android performance, then update release constants, manifest metadata, NOTICE and docs
together. Test old and new packs separately. Failed installation retains the active pack and a
successful update keeps one rollback version; saved user entries are never refreshed.
