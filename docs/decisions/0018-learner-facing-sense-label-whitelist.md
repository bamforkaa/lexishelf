# ADR 0018: Whitelist sense-level learner labels and keep them transient

- Status: Accepted
- Date: 2026-08-30

## Context

The pinned Kaikki source contains normalized tags, raw free-text labels, topics, source categories,
and form metadata. Blindly exposing them would leak parser bookkeeping and inflection mechanics.
Flattening labels to an entry would also be wrong: countability, transitivity, and register vary by
sense. Task 18 measured 2,380,828 production-language senses and found 196,778 senses (8.2651%)
with at least one high-precision learner label. English measured 414,843/1,779,278 (23.3152%).

## Decision

- Introduce provider-neutral `DictionarySenseLabel` and category/type enums.
- Accept only an exact, reviewed whitelist from Kaikki normalized `sense.tags`.
- Keep register, temporal, grammar, and region categories distinct and order them deterministically.
- Preserve labels on each source sense and each synthesis contribution; never spread a supporting
  source's label to the primary source.
- Display at most three labels plus a count in suggestions, while accessibility receives all labels.
- Ignore unknown tags. Audit tools, rather than production UI logging, expose version drift.
- Add only an optional compact sense payload field to Kaikki schema v3. No DDL or manifest schema
  change is required; old v3 payloads decode with an empty list.
- Keep labels transient. Explicit import does not copy them into vocabulary, Word Detail, Room, or
  backup.
- Defer named geographic and domain ontologies and JMdict-specific mapping.

## Consequences

Learners see concise, source-attested usage and grammar information without raw tag noise. Existing
dictionary search/ranking, morphology eligibility, form display, import, and user-data ownership
remain unchanged. A future persistence proposal must separately justify Room v7/backup v6, edit
semantics, and provider licensing; this ADR does not pre-authorize it.
