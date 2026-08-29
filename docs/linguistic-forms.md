# Bounded linguistic form display

## Why raw Kaikki forms are not a UI list

The reviewed `enwiktionary-2026-08-05` extraction stores entry-level form objects with a `form`
string, `tags`, and sometimes `source`, `raw_tags`, `roman`, `links`, or table bookkeeping. Senses
can separately contain `form_of`/`alt_of` links. The current reverse index uses only an entry's
explicit tagged form array → that entry's headword; it does not reinterpret ambiguous alternative
or derivational sense links. The array is
not ordered as a learner curriculum. One entry can have hundreds of table cells: the measured
maximum was 465 for German, 569 for Hindi, and 1,897 for Turkish. Vietnamese and Thai arrays are
mostly CJK/Hán-Nôm spelling, romanization, classifiers, or alternatives rather than inflection.

The converter therefore has two independent policies:

- `DisplayFormPolicy` chooses a few high-confidence principal forms and learner-facing labels.
- `MorphologyLookupPolicy` indexes a broader set of attested inflections for exact reverse lookup.

Morphology eligibility also considers lemma-level source metadata. For the audited English
auxiliary index, a lemma is excluded only if every sense is explicitly non-lexical or
non-canonical; one lexical sense is enough to preserve the entry. This prevents abbreviation/form
entries from masquerading as useful lemmas without reducing morphology to a verb-only feature.

Neither policy uses source-order `take(N)` as semantic selection. The global display safety cap is
8, applied only after semantic selection and deduplication. Zero selected forms is valid.

## Display rules

| Language | Nouns | Verbs | Adjectives |
| --- | --- | --- | --- |
| German | plural; genitive singular | present 3sg; past; past participle | comparative; superlative |
| Dutch | plural | past; past participle | comparative; superlative |
| Swedish | indefinite plural; definite singular/plural | past; supine; past participle | comparative; superlative |
| Portuguese | plural | present 1sg; preterite 3sg; past participle; gerund | feminine/plural; comparative/superlative |
| Polish/Czech/Ukrainian | nominative plural; genitive singular | conservative 3sg present/past and imperative | comparative; superlative |
| Hindi | direct plural; oblique singular/plural | none until principal-part semantics are reviewed | none |
| Turkish | plain plural | none: reviewed conjugation person/number tags conflict with surface text | none |
| Indonesian | plural | active; passive | comparative; superlative |
| Vietnamese/Thai | none | none | none |

Unknown or ambiguous raw tags are excluded from display. The same normalized form and label is
deduplicated; the lemma itself is excluded. The runtime receives only the selected form text and
label and renders the section only when non-empty. Forms stay transient dictionary metadata and
are not copied into Room or JSON backup.

The Turkish reverse index still uses conservative source-attested form→lemma relations. Only the
learner-facing verb labels are suppressed; the app does not reinterpret visibly inconsistent raw
person/number tags or guess corrected labels from suffixes.

Measured schema-v3 cardinalities and real examples are recorded in
[Kaikki dataset](kaikki-dataset.md) after each full artifact regeneration.
