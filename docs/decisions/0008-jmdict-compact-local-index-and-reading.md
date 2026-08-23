# ADR-0008: JMdict compact local index and first-class reading

- Status: Accepted
- Date: 2026-08-23
- Related: ADR-0003 and ADR-0005

## Context

JMdict is a daily XML lexical database with multiple writings/readings, restrictions and ordered
senses. Runtime parsing of the 63 MB XML is unsuitable for inline Android lookup. The existing
vocabulary could display a transient reading but could not persist one, and sense provenance is
not a correct owner for an entry-level reading.

## Decision

- Use provider ID `jmdict`, `LOCAL_DATASET`, and only `ja → en` translation.
- Build a separate exact-key read-only SQLite with minified UTF-8 provider-private payloads. Never
  put dictionary rows in user Room.
- Preserve writing/reading and sense restrictions and filter them at the provider mapping boundary.
- Retain official expanded POS labels as editable strings; do not invent a lossy taxonomy.
- Provide sense-level explicit Use instead of importing a large entry wholesale.
- Add provider-neutral reading to Room schema v4 and backup schema v3.
- Add `entry_dictionary_provenance`, keyed by entry and field, for reading. Existing sense
  provenance continues to own meaning/POS/examples.
- Keep alternative spellings transient until a user-owned alternate-form model is justified.

## Consequences

Exact kanji/kana lookup is offline and deterministic, while provider updates cannot overwrite
saved data. Room v3 data migrates with an empty reading and old backup v1/v2 remains importable.
The 113.73 MB DB adds 27.87 MB to the debug APK and materially increases first-copy cost; Task 9 should move it to a
versioned downloadable pack without changing provider or user-data boundaries.
