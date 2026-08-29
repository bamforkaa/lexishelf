# Source-attested morphology search

## Contract

Morphology search is exact-first and dataset-derived:

1. Providers run the normal query unchanged.
2. A `DictionaryMorphologyResolver` looks up the normalized surface in the installed Kaikki
   reverse index.
3. Every returned lemma is an actual Kaikki form-to-headword relation. There is no stemming,
   suffix stripping, edit distance, guessed irregular table, or AI.
4. Existing providers are re-queried with the lemma through `exactLookup` only.
5. Direct results remain first. Derived candidates are deterministic: the first eligible lemma is
   the primary analysis and remaining lemmas are visibly marked as alternate analyses.

The exact-only re-query cannot invoke morphology again, so resolution depth is one. Provider
failure is isolated and never blocks manual editing. When a derived row is imported, the existing
generic mapper imports the chosen meaning/provenance but the editor headword remains the user's
surface form. Refresh never mutates imported or user-authored fields.

## Reverse index filtering

Schema v3 adds `morphology_forms` to generated Kaikki SQLite artifacts. Its normalized key
preserves multiple lemmas for a homographic surface. Runtime prefers an exact original-case form,
then deterministic source entry/form order, and deduplicates duplicate source entries for the same
normalized lemma. It never chooses by an invented frequency score.

Normalization is NFC + trimmed/collapsed whitespace followed by case mapping. Most languages use
`Locale.ROOT`; Turkish uses its language-aware dotted/dotless-I pairs (`I ↔ ı`, `İ ↔ i`) in both
the Python converter and Android lookup. Stored source forms keep their original case, so exact
case can still rank a likely interpretation first.

The index excludes:

- blank, malformed, punctuation-only, overlong, reconstructed, and lemma-identical forms;
- romanization/transliteration and alternative/canonical spelling records;
- archaic, obsolete, dated, rare, dialectal, and nonstandard records when explicitly tagged;
- classifiers, roots, reduplication, technical table/template rows, and uncertain derivation;
- Vietnamese and Thai forms, because measured current metadata is not a reliable inflection
  relation;
- Turkish negative/potential/evidential/causative and other combinatorial derivations that caused
  pathological table expansion.

The English auxiliary additionally rejects a lemma only when **every** source sense explicitly
identifies it as either non-lexical (`abbreviation`, `acronym`, `initialism`, `letter`,
`punctuation`, `symbol`) or non-canonical (`alt-of`, `form-of`, `misspelling`, `nonstandard`,
`archaic`, `dated`, `dialectal`, `historical`, `obsolete`, `rare`). Mixed entries remain eligible.
This is a build-time source-metadata rule, not a runtime English-verb allowlist. Other languages
retain their reviewed Task 17 policy until their sense metadata receives the same source audit.

The remaining row must contain a reviewed grammatical marker and a supported inflecting POS.
This is broader than display selection, but precision still wins over nominal recall.

## English compact auxiliary index

The full English Wiktionary dictionary is not added as a provider pack. During the same streamed
raw scan, the converter writes only English `surface → lemma` rows to `en-morphology.db`. The pack
ID is `kaikki.en-morphology` and its active pair is `en → en / MONOLINGUAL_DEFINITION`; it is used
only by the resolver. A lemma such as `be` or `go` is then looked up in already registered English
source providers. This keeps the feature source-derived without shipping a giant duplicate English
dictionary.

## Ambiguity and bounded presentation

Semantic eligibility and deduplication happen before the runtime safety cap. The current cap is
five distinct lemmas. The resolver reads far enough to report whether more eligible analyses exist;
it does not truncate SQL rows before distinct-lemma filtering. Direct exact results remain first,
the first resolved lemma is labelled `기본형 분석`, and subsequent lemmas are labelled
`다른 형태 분석`. When the cap is reached, the UI states that only some analyses are shown.

The `2026-08-05` English source audit found the false `is → I` relation came from the title-cased
form `Is`, recorded as the plural of noun `I`; all four senses are abbreviations/alternate forms.
The valid `is → be` row is the third-person singular present form of lexical verb `be`. The old
converter looked only at entry POS and form tags, so both rows survived and lowercase key
normalization merged them. The all-sense eligibility rule removes the abbreviation lemma while
retaining legitimate ambiguity such as `axes → axe/axis`, `alumni → alum/alumna/alumnus`, and
`better → good/well`.

Measured English auxiliary changes:

| metric | before | after |
| --- | ---: | ---: |
| relation rows | 706,365 | 543,791 |
| unique surfaces | 666,519 | 521,889 |
| ambiguous surfaces | 3,058 (0.4588%) | 630 (0.1207%) |
| lemmas per ambiguous surface, average / p95 / max | 2.032 / 2 / 7 | 2.013 / 2 / 3 |
| case-variant ambiguous surfaces | 198 | 63 |
| DB bytes | 110,923,776 | 86,433,792 |

The regenerated build rejected 153,441 non-canonical and 9,126 non-lexical relation rows across
113,203 English entries. Seven additional `form only` pseudo-form rows were removed by the common
form filter. These categories are source-driven and deliberately do not remove ordinary proper
nouns or all nominal morphology.

## Cross-language case-collision audit

`case variants` counts normalized keys containing two or more differently-cased source forms.
`multiple normalized lemmas` is the subset that exposes multiple normalized lemma identities;
`case-only lemma variants` contains differently-cased lemma spellings sharing one normalized lemma.

| language | case variants | multiple normalized lemmas | case-only lemma variants |
| --- | ---: | ---: | ---: |
| de | 1,564 | 1,289 | 275 |
| hi | 0 | 0 | 0 |
| pl | 1,253 | 64 | 1,189 |
| nl | 165 | 16 | 149 |
| pt | 143 | 26 | 117 |
| tr | 17 | 0 | 17 |
| cs | 316 | 77 | 239 |
| sv | 199 | 0 | 199 |
| uk | 26 | 3 | 23 |
| vi | 0 | 0 | 0 |
| th | 0 | 0 | 0 |
| id | 43 | 0 | 43 |

German capitalization frequently distinguishes a noun from a verb or adjective. `Flossen` (noun
`Flosse`) versus `flossen` (verb `fließen`) and `Delegierten` (delegate) versus `delegierten`
(verb form) are real lexical ambiguity, not corrupt metadata. Turkish `aya/Aya` (palm/saint) and
`can/Can` (soul/given name) are likewise case-distinguished lexical pairs. Exact-case ranking
selects the matching spelling first. Case-only lemma variants remain deduplicated by normalized
lemma in the bounded fallback, avoiding duplicate cross-provider re-query; this is an explicit
precision tradeoff rather than treating the source rows as invalid.

## Persistence boundary

Surface/lemma context and selected forms are presentation/reference state. Room remains v6 and
backup remains v5. Persistent provenance is still the actual provider entry chosen by the user;
the transient resolver context is not misrepresented as a saved dictionary definition source.

See [ADR-0017](decisions/0017-morphology-lemma-eligibility-and-ambiguity-ranking.md) for the
eligibility/ranking decision and [Kaikki dataset](kaikki-dataset.md) for reproducible artifact
measurements.
