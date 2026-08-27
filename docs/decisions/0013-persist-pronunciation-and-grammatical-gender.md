# ADR 0013: Persist pronunciation and grammatical gender, defer forms

- Status: Accepted
- Date: 2026-08-27

## Context

Task 12 exposed Kaikki pronunciation, gender, and inflection data only as transient suggestion
metadata. Task 14 required a data-driven decision before expanding user-owned vocabulary. The
production twelve-language SQLite indexes contain 1,793,612 indexed entries. IPA coverage is
40% or higher in eight languages, and gender coverage is 40% or higher in five languages. Forms
are useful but vary from none to 1,857 available forms per entry and use language-specific tags.

## Decision

Persist ordered pronunciation items and optional sense-level grammatical gender.

- Pronunciation is a separate 1:N model; it does not reuse the `reading` field.
- Each pronunciation has a stable backup ID, notation, value, optional BCP 47 language tag, and
  generic dictionary provenance.
- Gender is normalized to a provider-independent category with a raw fallback and reuses the
  existing sense provenance field set.
- Kaikki forms stay transient. General usage labels, audio, etymology, and lexical relation graphs
  are not persisted.
- Room moves from v5 to v6 with an explicit migration. Backup moves from v4 to v5 while retaining
  v1-v4 import.

## Consequences

The editor and detail UI can distinguish reading, pronunciation, POS, and gender. Provider-derived
values obey the same explicit-import, toggle cleanup, edit preservation, licensing, and backup
rules as existing meanings/examples. The list query does not join or render forms. A later forms
feature must define a per-language selection policy and bounded storage before changing schema.

