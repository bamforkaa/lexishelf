# Linguistic metadata persistence policy

Task 14 measured the production Kaikki batch before changing the user-owned vocabulary schema.
The source of truth for the numbers below is the twelve generated SQLite indexes under
`<dictionaryDataDir>/kaikki/generated`. Reproduce the report with:

```powershell
python -X utf8 -m tools.report_kaikki_metadata --root D:\lang-Database
```

The report reads the databases in SQLite read-only mode. “Present” counts entries, not senses.
Average is per indexed entry; p95 is calculated among entries where the field is present. The
converter retains at most eight pronunciation strings, 24 representative forms, and two eligible
examples per sense. Available form/example counts are recorded before that display bound.

## Measured 2026-08-05 generated indexes

| Language | Entries | IPA present | Other text pronunciation | Gender present | Forms present | Eligible examples present |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| de | 369,967 | 83,522 (22.6%) | 1 (0.0%) | 206,828 (55.9%) | 102,854 (27.8%) | 8,110 (2.2%) |
| hi | 38,856 | 36,495 (93.9%) | 0 | 26,609 (68.5%) | 38,852 (100.0%) | 3,756 (9.7%) |
| pl | 197,832 | 197,552 (99.9%) | 0 | 115,854 (58.6%) | 96,769 (48.9%) | 6,582 (3.3%) |
| nl | 145,878 | 55,856 (38.3%) | 0 | 60,692 (41.6%) | 64,706 (44.4%) | 4,740 (3.2%) |
| pt | 445,173 | 77,428 (17.4%) | 0 | 104,832 (23.5%) | 73,596 (16.5%) | 3,144 (0.7%) |
| tr | 45,617 | 18,336 (40.2%) | 0 | 21 (0.0%) | 21,374 (46.9%) | 1,975 (4.3%) |
| cs | 72,027 | 71,056 (98.7%) | 0 | 43,700 (60.7%) | 47,585 (66.1%) | 4,548 (6.3%) |
| sv | 312,058 | 6,572 (2.1%) | 0 | 53,253 (17.1%) | 48,132 (15.4%) | 9,843 (3.2%) |
| uk | 59,433 | 58,482 (98.4%) | 0 | 8,905 (15.0%) | 59,422 (100.0%) | 2,917 (4.9%) |
| vi | 46,170 | 34,164 (74.0%) | 0 | 16 (0.0%) | 11,476 (24.9%) | 6,449 (14.0%) |
| th | 20,939 | 20,787 (99.3%) | 0 | 10 (0.0%) | 20,606 (98.4%) | 1,862 (8.9%) |
| id | 39,662 | 29,852 (75.3%) | 0 | 27 (0.1%) | 30,025 (75.7%) | 1,352 (3.4%) |

The only non-IPA textual pronunciation retained in this batch is German `Heap` with enPR `hēp`.
The current converter prefixes enPR explicitly; the reviewed batch has no `zh-pron` language.

| Language | IPA avg / p95 / max | Gender senses avg / p95 / max | Available forms avg / p95 / max | Eligible examples avg / p95 / max |
| --- | ---: | ---: | ---: | ---: |
| de | 0.29 / 3 / 8 | 0.77 / 2 / 26 | 9.22 / 171 / 449 | 0.042 / 5 / 29 |
| hi | 1.93 / 4 / 8 | 0.97 / 3 / 14 | 14.76 / 15 / 335 | 0.134 / 3 / 31 |
| pl | 1.10 / 2 / 8 | 0.73 / 2 / 103 | 6.44 / 32 / 176 | 0.077 / 7 / 88 |
| nl | 0.41 / 2 / 8 | 0.51 / 2 / 22 | 3.03 / 25 / 80 | 0.062 / 4 / 26 |
| pt | 0.75 / 8 / 8 | 0.28 / 2 / 20 | 1.45 / 80 / 236 | 0.014 / 6 / 35 |
| tr | 0.48 / 2 / 8 | 0.00 / 1 / 2 | 69.27 / 929 / 1,857 | 0.071 / 4 / 38 |
| cs | 1.00 / 1 / 4 | 0.72 / 2 / 9 | 12.59 / 34 / 78 | 0.098 / 3 / 32 |
| sv | 0.03 / 2 / 8 | 0.20 / 2 / 14 | 1.49 / 24 / 108 | 0.065 / 5 / 56 |
| uk | 1.01 / 1 / 8 | 0.18 / 2 / 37 | 13.14 / 37 / 109 | 0.083 / 4 / 67 |
| vi | 2.24 / 5 / 8 | 0.00 / 2 / 2 | 0.52 / 6 / 19 | 0.223 / 4 / 28 |
| th | 1.06 / 2 / 6 | 0.00 / 1 / 1 | 1.48 / 3 / 11 | 0.185 / 5 / 29 |
| id | 1.29 / 2 / 8 | 0.00 / 1 / 3 | 1.14 / 3 / 33 | 0.051 / 3 / 31 |

Form labels are present for almost every retained form-bearing entry, but their language-specific
shape and extreme cardinality make blind persistence unsuitable. General sense usage labels such
as formal/informal/archaic are not retained by index schema v2, so runtime coverage is zero and no
schema is inferred from raw Wiktionary internals.

## Decision matrix

| Field | Coverage and learner value | Storage complexity | Decision |
| --- | --- | --- | --- |
| IPA / classified textual pronunciation | High in 8 languages; useful independently of reading | Medium: ordered 1:N, stable ID, notation, language, provenance | **Persist now** |
| Grammatical gender | High in de/hi/pl/nl/cs and useful for noun learning | Low: optional normalized sense value plus raw fallback | **Persist now** |
| Examples | Lower coverage but already useful and licensed subset is implemented | Existing model | **Keep current persistence** |
| Forms / inflections | Often high coverage, but p95 929 and max 1,857 for Turkish | High; language-specific filtering and UI policy unresolved | **Transient only; defer persistence** |
| Usage / register labels | Not retained in the generated runtime index | Requires a reviewed whitelist and index change | **Defer** |
| Synonym / antonym graph | Outside the compact index and current UX | High | **Reject for Task 14** |
| Audio, etymology | Licensing/storage not reviewed for this workflow | High | **Reject for Task 14** |

## Semantic and ownership boundaries

- `reading` remains kana, pinyin, or transliteration. IPA is never written into `reading`.
- `VocabularyPronunciation` is ordered 1:N and stores stable ID, notation (`IPA`, `PHONETIC`,
  `ROMANIZATION`, `OTHER`), text, optional BCP 47 language tag, and item-level provenance.
- Grammatical gender belongs to a sense and is separate from POS. Known values normalize to
  masculine/feminine/neuter/common; combinations or dataset-specific values use `OTHER` with the
  original label.
- Provider results change the editor only after an explicit row tap. A second tap removes unchanged
  session-owned fields; user-edited values survive with provider ownership detached.
- Forms remain visible in suggestions but are not included in Room or JSON backup.
