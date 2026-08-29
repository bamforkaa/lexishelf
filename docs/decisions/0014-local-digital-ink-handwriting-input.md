# ADR 0014: Local digital ink handwriting input

- Status: Accepted
- Date: 2026-08-28

## Context

Word Editor needs an input alternative for users who cannot easily type a character. A camera/OCR
or cloud recognition path would transmit or rasterize content unnecessarily and conflict with the
local-first product boundary. The selected recognizer also has to follow the editor's canonical
BCP 47 identity and must not bypass existing headword change rules.

## Decision

Use Google ML Kit Digital Ink Recognition `19.0.0` with on-demand language models.

- Capture ordered vector strokes as x/y/timestamp points; do not capture a screenshot.
- Let Ink exist before model selection. Resolve the user-selected canonical BCP 47 language through
  the SDK model identifier API and reuse the same Ink when switching models.
- Download only the user-requested language model through `RemoteModelManager`; do not bundle
  models in the APK or prefetch the language catalog.
- Keep SDK and model operations behind domain contracts and use fakes in automated tests.
- Trigger recognition 350ms after stroke end, cancel stale work, and require an explicit candidate
  tap.
- Append a selected candidate because the current editor does not retain a cursor selection. Never
  implicitly replace existing headword text.
- Route the resulting text through the existing `HeadwordChanged` action.
- Reuse one callback-based handwriting surface from Word Editor and vocabulary search. Search
  candidates replace the query and continue through the existing repository/filter flow.
- Use a fixed-height dialog with the Canvas above a separately scrolling result panel so state
  changes cannot recenter the physical writing surface.
- Persist only the five recent recognition language tags in DataStore; do not infer a language from
  raw Ink or change the vocabulary entry language automatically.
- Keep strokes, candidates and model state transient. Room remains v6 and backup remains v5.
- Treat writing practice, scoring, stroke-order evaluation, history and SRS as separate future work.

## Consequences

The app adds `INTERNET` permission for user-initiated model downloads, while actual recognition is
on-device after installation. The official terms also permit SDK model/update contacts and describe
performance/utilization metrics, so documentation must not claim the SDK is entirely network-silent.
An unsupported or offline/missing-model state affects only the modal handwriting surface; keyboard
entry and local vocabulary persistence remain available.
