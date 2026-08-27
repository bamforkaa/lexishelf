# ADR-0014: Bound Kaikki forms with semantic conversion policies

- Status: Accepted
- Date: 2026-08-28

## Context

The production Kaikki languages contain 594,391 indexable entries with at least one cleaned raw
form. Cardinality is not UI-safe: German p95 is 171, Turkish p95 is 929 and Turkish maximum is
1,857. Raw arrays also mix inflection, romanization, spelling variants, derivation, classifiers,
template markers and malformed table rows. Index v2's source-ordered first 24 rows were bounded
but not a defensible learner policy.

Forms are provider reference data. Task 14 intentionally did not add them to user-owned Room v6
or backup v5, and this decision does not change that ownership boundary.

## Decision

Perform precision-first form selection in the Python conversion boundary, where the complete raw
entry and tags are available. Normalize NFC/text/tags, remove malformed/internal/duplicate/lemma
rows, and use reviewed language/POS semantic slots. Unknown languages use a conservative slot
whitelist; Vietnamese and Thai currently emit no inflection because their observed form arrays are
not inflection paradigms.

Selection is semantic before bounding. A global cap of eight is a pathological-data guard only;
full-source measurement produced selected average 0–2.64, p95 0–4 and maximum 5. Same surface with
multiple selected meanings is rendered once with combined labels.

Kaikki provider index schema advances from v2 to v3 to identify semantic-v1 payloads. Runtime keeps
v2 meanings and other metadata searchable but suppresses its source-order forms. V3 selected forms
map to the existing common transient `DictionaryInflection` model and generic suggestion UI.

## Consequences

- UI never receives hundreds of forms and contains no language-specific branching.
- Recall is deliberately lower than a conjugation/declension tool; uncertain rows are omitted.
- Some legitimate alternative principal parts can be missed because the compact model selects one
  representative per slot.
- Turkish verb support is intentionally minimal until source person-tag quality improves.
- New languages require raw-tag measurement and a reviewed policy before richer output.
- Full paradigms would require a separate normalized paradigm model, dedicated UI and licensing/
  persistence decision; they should not expand this compact suggestion model.

Detailed measurements and samples are in [linguistic-forms.md](../linguistic-forms.md).
