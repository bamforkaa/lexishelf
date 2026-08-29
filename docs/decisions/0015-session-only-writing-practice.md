# ADR 0015: Session-only writing practice over saved vocabulary

- Status: Accepted
- Date: 2026-08-29

## Context

The app can already store user-owned multilingual vocabulary and recognize local vector Ink. A
first writing exercise should reuse those boundaries without prematurely defining mastery, review
history, stroke quality, morphology, or a new persistence schema.

## Decision

- Use the saved `VocabularyEntry.headword` as the only accepted answer.
- Compare NFC-normalized, trimmed, collapsed-whitespace text with case preserved. Do not accept
  synonyms, alternate forms, edit distance, fuzzy matches, or inferred lemmas.
- Prefer meaning, then reading, pronunciation, POS/gender, and example hints. Hide normalized hints
  equal to the headword and examples containing the normalized headword.
- Query compact scope-filtered practice rows through the existing `VocabularyRepository`; do not
  load every aggregate or add a parallel repository.
- Snapshot and shuffle eligible entry IDs once per 10/20/all session.
- Reuse `HandwritingInputViewModel`, its stable Canvas, language resolver, model download, 350ms
  recognition debounce, and stale-result cancellation. A candidate tap only fills the answer;
  explicit submit separates recognition error from knowledge judgment.
- Always offer keyboard input when handwriting is unsupported, missing, or unavailable offline.
- Count only the first submitted attempt. Retry clears transient input for the same question and
  never rewrites the first-attempt result.
- Keep the session, answers, Ink and summary in memory. Do not write Room, DataStore or backup.
- Do not evaluate stroke order, glyph shape or handwriting quality.

## Consequences

Room remains v6 and backup remains v5. Process death ends an active session, which is intentional
for this MVP. Intentional duplicate entries remain separate questions, and identical hints can be
ambiguous. Persistent review history, SRS, alternate forms and morphology require separate product
and migration decisions.
