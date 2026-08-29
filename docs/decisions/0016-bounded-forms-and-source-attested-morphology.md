# ADR-0016: Separate bounded form display from source-attested morphology lookup

## Status

Accepted.

## Context

Kaikki `forms` mixes useful principal forms, complete paradigms, romanization, spelling variants,
and table metadata. Source order is not pedagogical order, while high-cardinality Turkish and
Slavic entries make raw display unusable. Exact dictionary lookup also cannot resolve a valid
surface such as German `Häuser` or English `went` to a lemma.

## Decision

- Use a conservative build-time display policy with semantic language/POS rules and a final cap of
  8. Zero forms is valid.
- Use a separate, broader build-time morphology policy containing only explicit source-attested
  inflections.
- Bump generated Kaikki indexes and manifests to schema v3 and add an indexed
  `morphology_forms` table.
- Add a compact English morphology-only pack, not a full English dictionary pack.
- Keep normal provider lookup exact-first. Resolve at most five lemmas and re-query registered
  providers through `exactLookup` once; never recurse.
- Keep direct and lemma-derived candidates distinct in synthesis and visibly label derived rows.
- Do not replace the editor's surface headword when a derived row is imported.
- Keep forms and resolution context transient; do not change vocabulary Room v6 or backup v5.

## Consequences

Search recall improves only where the reviewed dataset explicitly supports it. Some languages and
ambiguous paradigms intentionally return no morphology. New Kaikki releases require policy
coverage measurement and all schema-v3 packs must be regenerated together. This is preferable to
silent guessed lemmas, unreadable form tables, or persistent provider metadata that the user did
not author.

