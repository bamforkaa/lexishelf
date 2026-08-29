# ADR-0017: Source-metadata lemma eligibility and deterministic ambiguity ranking

- Status: Accepted
- Date: 2026-08-30

## Context

The schema-v3 Kaikki reverse index correctly preserved every tagged form-to-headword relation, but
that alone did not make every headword a useful lookup lemma. In the reviewed English source,
lowercase `is` resolved both to lexical verb `be` and to noun `I`: the latter entry's title-cased
`Is` form is the plural of four abbreviation/alternate-form senses. Case-insensitive exact-key
normalization made the low-value relation user-visible. Runtime also returned a source-ordered list
without distinguishing a primary analysis from alternatives.

## Decision

1. Build-time policy may reject an English morphology lemma only when every source sense is
   explicitly non-lexical or non-canonical. Mixed lexical entries remain eligible.
2. Technical pseudo-forms are removed before indexing. We do not add a verb-only allowlist, a
   hand-maintained irregular table, or a frequency corpus.
3. Runtime ranks an exact original-case form before case-folded matches, then preserves source
   entry/form order and distinct-lemma order.
4. The first eligible lemma is the primary analysis; further lemmas are alternates. Exact provider
   results remain ahead of all morphology-derived rows.
5. The five-lemma safety cap is applied after eligibility and distinct-lemma filtering. Truncation
   is explicit in the result/UI.
6. The source surface/lemma relation remains transient. Re-query is exact-only and one level deep;
   Room v6 and backup v5 are unchanged.

## Consequences

- `is → I` is removed while `is → be` remains.
- Genuine ambiguity such as `axes → axe/axis`, `alumni → alum/alumna/alumnus`, and Portuguese
  `fui → ir/ser` remains visible and deterministic.
- English precision improves without guessing user intent. Alternatives are not silently discarded.
- Other language policies remain unchanged until their sense metadata is independently audited.
- Regeneration reports rejection categories so future source releases can be reviewed rather than
  accepted by an unexplained row-count change.
