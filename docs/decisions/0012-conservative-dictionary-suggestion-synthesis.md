# ADR-0012: Conservative dictionary suggestion synthesis

## Status

Accepted — 2026-08-27

## Context

The editor can query several providers for one headword. Provider-grouped presentation repeats identical lexical candidates and makes provider names more prominent than user-facing meanings. Raw provider entries still carry distinct source identity, license policy, readings, POS, examples, and transient metadata, and Task 12.1 contribution ownership depends on that identity.

Current real providers commonly overlap by source language but return different result languages. Exact same-result-language overlap is expected as providers expand, so the boundary must be ready without fabricating current data or introducing semantic matching.

## Decision

- Preserve immutable provider result groups as the raw source of truth.
- Derive immutable result-language groups and candidates in a presentation/application synthesizer.
- Merge only exact lexical candidates after NFC, trim, and whitespace normalization. Preserve case.
- Keep homographs, same-provider multiple senses, conflicting POS, restrictions, and distinct headwords separate.
- Keep every source contribution; display all provider names compactly.
- Select one deterministic primary source using importable field richness followed by a centralized provider-role tie-break and stable source identity.
- Send only that raw primary entry through the existing `DictionaryEntryDraftMapper` and contribution ownership path.
- Build stable UI identity from semantic content and the sorted source identity set, never UI order.
- Group Korean before English before other result languages and base scroll behavior on synthesized selectable count.
- Do not persist synthesis state or supplementary provider metadata.

## Consequences

Raw response diagnostics, provider-specific licensing, and supporting attribution remain available. The UI can suppress only demonstrably identical duplicates without semantic heuristics. An added or removed supporting source changes the synthesized identity by design, while a refresh with the same source identities does not.

Because Room and backup store only the chosen imported provenance and user-owned data, no migration is needed: Room remains v5 and backup remains v4. If future product requirements demand persisted multi-source attribution, that must be a separate schema and migration decision.
