# Kaikki representative form selection

## Why selection happens during conversion

Kaikki `forms` is not a ready-made learner list. In the pinned 2026-08-05 English Wiktionary
extraction, one entry can mix principal parts, a complete conjugation or declension table,
romanization, alternative spelling, classifiers, derivations, template markers, and malformed
generated rows. The compact v2 index retained only the first 24 cleaned rows, so it no longer had
enough source data for a correct runtime decision. Index schema v3 therefore performs semantic
selection while the converter can still see the complete raw entry.

Reproduce the source-level report without changing a database:

```powershell
python -X utf8 -m tools.report_kaikki_forms `
  --root D:\lang-Database `
  --output build\reports\kaikki-form-selection.json
```

The report reads `<root>/kaikki/source/filtered/<language>.jsonl.gz`. It includes only entries the
converter can index (headword plus at least one English gloss). Raw count is NFC/trim/whitespace
normalized, exact `(text, tags)` duplicates removed, and internal template/`-` rows removed.
Selected averages and percentiles use the same entries that have at least one raw form.

## Observed structure

- A form object has `form` and usually a `tags` array. Some rows also have `source`, `roman`, or
  `links`; these are not silently promoted into a grammatical meaning.
- Multiple tags on one form are normal. German `gegangen` is `participle + past`; Polish case
  forms carry case and number; Hindi forms combine gender, number, case/aspect.
- The same surface text can occur with different grammatical tags, and exact `(text, tags)` rows
  can also repeat.
- `romanization`, `alternative`, `canonical`, `CJK`, `Hán-Nôm`, `classifier`, `abstract-noun`,
  `diminutive`, `error-*`, `table-tags`, `inflection-template`, and `class` are not treated as
  inflection slots.
- Vietnamese raw forms are mostly CJK/spelling variants. Thai is mostly romanization,
  classifiers, and derived abstract nouns. Returning no inflection is intentional for both.
- Turkish generated tables are extremely large and the reviewed extraction contains shifted
  person labels in table rows (`geldim` can be tagged third-person). Only the compact principal
  present marker is accepted for verbs in this release.

Representative source observations included nouns, verbs and adjectives such as German
`Haus/gehen/gut`, Polish `dom/dobry`, Dutch `gaan`, Portuguese `ir`, Swedish `hus/gå/god`, Hindi
`करना`, Turkish `gitmek`, Indonesian `ajar/baik/rumah`, plus the maximum-cardinality entries in
the report.

## Policy

`tools/kaikki_form_selection.py` owns normalization and an immutable registry of language/POS
policies. UI and ViewModel code contain no language switch.

| Language | Reviewed representative slots |
| --- | --- |
| de | noun plural/genitive singular; verb present 3sg/past/past participle; adjective/adverb comparative/superlative |
| nl | noun plural; verb present 3sg/past/past participle; adjective/adverb comparative/superlative |
| sv | noun plural/definite singular/definite plural; verb present/preterite/supine; adjective/adverb comparative/superlative |
| pt | noun plural; verb present 1sg/preterite 1sg/past participle; conservative adjective gender/plural/comparison |
| pl, cs, uk | noun nominative plural/genitive singular/genitive plural; explicit standalone aspect counterpart; present 1sg/3sg and past masculine singular; adjective/adverb comparison |
| hi | noun direct/oblique principal number forms; verb stem and masculine/feminine habitual/perfective; conservative adjective gender/number forms |
| tr | noun plural/definite accusative/dative/locative; verb compact present 3sg only |
| id | noun plural; verb explicit active/passive; adjective/adverb comparative/superlative |
| vi, th | no reviewed inflection slot; return zero |

The fallback for a future unreviewed language is precision-first: only simple noun plural,
past participle, or adjective/adverb comparison can match. It is not “take N”. Within a semantic
slot, fewer unexplained tags win; shared words and orthographic similarity are deterministic
tie-breakers for fragmented multiword tables. Source order and alphabetical order are never the
primary policy.

The selector removes the lemma, malformed/internal/noisy rows, and duplicate `(text, tags)`.
When one surface realizes multiple selected slots, the UI uses one row with joined labels so the
grammatical meanings are not lost. Semantic selection runs first; a global cap of **8** is only a
last defense. Actual selected max is 5, so the cap did not truncate this measured batch.

## Full-source before/after measurement

| language | entries with raw forms | raw avg | raw p95 | raw max | selected avg | selected p95 | selected max | selected 0 | selected 1–3 | selected 4–8 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| de | 102,854 | 33.1443 | 171 | 449 | 1.1482 | 3 | 3 | 25,344 | 77,510 | 0 |
| hi | 38,852 | 14.3598 | 14 | 335 | 0.7277 | 2 | 5 | 23,763 | 13,439 | 1,650 |
| pl | 96,769 | 13.1725 | 32 | 176 | 1.1859 | 3 | 3 | 37,559 | 59,210 | 0 |
| nl | 64,706 | 6.8190 | 25 | 79 | 1.0907 | 3 | 3 | 12,538 | 52,168 | 0 |
| pt | 73,596 | 8.7706 | 80 | 236 | 1.0544 | 3 | 3 | 11,720 | 61,876 | 0 |
| tr | 21,374 | 147.8379 | 929 | 1,857 | 2.6434 | 4 | 4 | 4,107 | 5,116 | 12,151 |
| cs | 47,585 | 19.0304 | 34 | 78 | 1.5073 | 4 | 4 | 18,737 | 26,419 | 2,429 |
| sv | 48,132 | 9.6576 | 24 | 108 | 1.9877 | 3 | 3 | 7,292 | 40,840 | 0 |
| uk | 59,422 | 12.7279 | 36 | 108 | 0.8266 | 3 | 4 | 38,997 | 18,144 | 2,281 |
| vi | 11,476 | 2.0782 | 6 | 19 | 0 | 0 | 0 | 11,476 | 0 | 0 |
| th | 20,606 | 1.5023 | 3 | 11 | 0 | 0 | 0 | 20,606 | 0 | 0 |
| id | 30,025 | 1.5077 | 3 | 33 | 0.9847 | 2 | 2 | 4,295 | 25,730 | 0 |

No selected entry has 9 or more forms.

## Manual raw-to-selected checks

| language | lemma / POS | raw | selected |
| --- | --- | ---: | --- |
| de | `groß` / adjective | 449 | `größer` comparative; `am größten` superlative |
| hi | `आना-जाना` / verb | 335 | stem plus masculine/feminine habitual and perfective: 5 |
| pl | `kupować` / verb | 176 | `kupić`; `kupuje`; `kupowałem` |
| nl | `uitscheiden` / verb | 79 | `uitscheidt`; `scheidde uit`; `uitgescheiden` |
| pt | `chamar ao pão, pão e ao queijo, queijo` / verb | 236 | `chamo ao pão`; `chamei ao pão`; `chamado ao pão` |
| tr | `bilmek` / verb | **1,857** | `bilir` present 3sg |
| cs | `rozepnout` / verb | 78 | aspect counterpart plus present 1sg/3sg and past masculine: 4 |
| sv | `tjuv` / noun | 108 | 0; rows are spelling variants, not reviewed inflections |
| uk | `користуватися` / verb | 108 | explicit perfective counterpart plus present 1sg/3sg and past masculine: 4 |
| vi | `giữ` / verb | 19 | 0; CJK variants |
| th | `สัตว์` / noun | 11 | 0; romanization/spelling variants |
| id | `ajar` / verb | 33 | `mengajar` active; `diajar` passive |

## Runtime and persistence boundary

Index schema v3 stores only selected `{form, concise label}` plus the cleaned raw count. The generic
provider maps these to transient `DictionaryInflection`; the suggestion row renders the section
only when selected forms exist. `DictionaryEntryDraftMapper` still does not copy `INFLECTION`, so
forms never reach Room or JSON backup. Room remains v6 and backup remains v5.

The runtime accepts old Kaikki schema v2 packs for meanings, pronunciation, gender and examples,
but hides their raw-order first-24 forms. Rebuilding/installing a v3 pack enables representative
forms without making an old pack unusable. A future full conjugation/declension feature would need
a separate paradigm model and is explicitly outside this policy.
