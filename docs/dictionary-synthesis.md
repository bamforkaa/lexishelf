# Dictionary suggestion synthesis

## Scope

The editor keeps each `DictionaryProvider` response as an immutable raw group. A presentation/application layer then derives result-language groups and visible candidates. It does not create a synthetic provider, write provider results to Room, or bypass `DictionaryEntryDraftMapper`.

The measurements below were made on 2026-08-27 against generated databases under the configured dictionary dataset root. Counts are selectable sense/candidate rows after each provider's existing exact lookup mapping, not estimates from documentation.

## Measured overlap

| Query | Raw provider observations | Reading/POS/example observations | Visible synthesis |
|---|---|---|---|
| `de: Wasser` | PanLex: 4 Korean relations (`물`, `광천수`, `수`, `수서`). Kaikki: 2 entries and 7 English senses (`Wasser` noun 5, `wasser` verb 2). | Kaikki noun has IPA `/ˈva.sər/`, neuter gender and 17 forms. Its `urine` sense retains the usage example `Wasser lassen`. | Korean 4, English 7; 11 rows. No cross-language merge. |
| `vi: ăn` | Korean Basic Dictionary: 11 Korean reverse rows, including `먹다` under distinct official senses. Kaikki: 1 entry and 14 English senses. PanLex does not declare Vietnamese. | KBD rows retain Korean POS and source sense identities. Kaikki retains verb POS, pronunciation/form metadata where present, and licensed usage examples. | Korean 11, English 14; 25 rows. Repeated KBD homograph/sense rows remain separate. |
| `ja: 食べる` | Korean Basic Dictionary: 4 Korean reverse rows (`먹고살다`, `먹다`, `벌어먹다`, `잡아먹다`). JMdict: 1 entry and 2 English senses. | JMdict retains reading `たべる`; both senses retain `Ichidan verb; transitive verb`. The second sense has three ordered glosses. | Korean 4, English 2; 6 rows. Kana reading remains a reading, not a pronunciation. |
| `zh-Hans: 你好` | CC-CEDICT: 1 English row, `hello; hi`. KBD declares source tag `zh`, not `zh-Hans`, so exact BCP 47 discovery does not select it. | CC-CEDICT retains pinyin `ni3 hao3` as its allowed reading field. | English 1 row. Script tags are not collapsed to `zh`. |
| `de: Haus` | PanLex: 18 Korean relations. Kaikki: 3 entries and 6 English senses (`Haus` noun/name and `haus` verb). | Kaikki retains POS and example availability per sense. Capitalization-distinct source entries remain distinguishable. | Korean 18, English 6; 24 rows. |
| `vi: nước` | KBD: 7 Korean reverse rows. Kaikki: 3 entries and 5 English senses. PanLex does not declare Vietnamese. | KBD retains official sense identity/POS; Kaikki retains noun POS and usage-example availability. | Korean 7, English 5; 12 rows. |

There is currently no source query for which PanLex, Korean Basic Dictionary, and Kaikki all participate. PanLex's 12 foreign source languages and KBD's 11 foreign source languages are disjoint, while Kaikki has no `ko` source pair. Also, current providers that overlap by source language return different result languages (`ko` versus `en`). Therefore the current generated datasets contain no honest same-result-language three-provider sample. The exact multi-source merge path is covered with fixed fake provider results; no dataset coverage or result was fabricated to satisfy an overlap example.

## Conservative identity and merge policy

Synthesis normalizes lexical comparison fields with:

- Unicode NFC;
- leading/trailing trim;
- internal whitespace collapse to one space.

Meaning and headword case are preserved. Provider exact indexes may use case-folded lookup keys, but synthesis does not merge `Wasser` and `wasser` merely because their case differs; German noun capitalization is lexical information.

A base candidate requires the same result-language tag, normalized headword, ordered meaning content, written-form restrictions, and reading restrictions. Examples are deliberately excluded from the dedup key. Merge is refused when:

- one provider contributes more than one candidate to the same base identity, because that can represent homographs or distinct source senses;
- nonblank POS values conflict;
- headword, meaning, result language, or restrictions differ.

This intentionally does not merge semantic neighbors such as `water`/`the water`, `eat`/`to eat`, or `길다`/`기다랗다`.

## Stable key and ordering

The visible key contains length-prefixed components for result language, normalized lexical/semantic content, restrictions/POS discriminator, and the sorted set of contributing source identities. A source identity contains stable provider ID, source entry ID, source sense ID, dataset version, and normalized headword. UI indices and current row positions are never used.

Language groups are ordered by BCP 47 base language: Korean, English, then other tags lexically. Within a group the deterministic provider-role preference is centralized in the synthesizer rather than spread across Composables.

## Primary source and import behavior

Every visible candidate keeps all `SourceContribution` values, including the raw common-domain entry with attribution, license usage policy, and persistence mode. The UI displays all contributing provider names in compact metadata.

One source is selected deterministically for an explicit row tap. The ordered criteria are:

1. an exportable/importable meaning;
2. importable licensed examples;
3. importable reading;
4. POS presence;
5. supplementary transient metadata;
6. provider role: Korean Basic Dictionary, JMdict, CC-CEDICT, Kaikki, PanLex, then unknown providers;
7. stable source identity.

These are ordered predicates, not a numerical quality score. Example richness precedes the role tie-break so that an otherwise identical Kaikki sense can retain its licensed example contribution. The chosen raw entry goes through the existing mapper and contribution ownership code. Supporting source entries remain transient and are not falsely written as additional provenance.

JMdict kana reading, CC-CEDICT pinyin, and Kaikki pronunciation/IPA stay in distinct domain fields. POS is shown once from the primary entry; conflicting POS candidates are not merged. Task 14 promotes allowed Kaikki textual pronunciation and grammatical gender through the existing primary-entry mapper, while forms remain transient. Allowed examples continue to use the enclosing sense's existing `EXAMPLES` provenance.

## Ownership and failures

A result arriving or refreshing never edits the draft. A row tap adds the primary source contribution; a second tap removes only unchanged fields owned by that active contribution. User-authored or modified meanings/examples/readings remain. Headword or source-language context changes clear synthesized selection keys and perform the Task 12.1 stale session-contribution cleanup.

Provider failures become messages in the relevant result-language group. Successful candidates from other providers remain visible, and manual editor save remains independent.

The visible synthesized candidate count controls layout: 1–4 rows use natural height and 5 or more use the existing bounded single `LazyColumn`. Language headings, failure messages, and source metadata do not count as selectable rows.

## Manual QA checklist

| Input/source tag | Expected groups and rows | Tap checks |
|---|---|---|
| `Wasser` / `de` | Korean 4 before English 7 | PanLex Korean row imports PanLex provenance. Kaikki `urine` imports Kaikki provenance, IPA/gender and `Wasser lassen`; forms stay transient. |
| `ăn` / `vi` | Korean 11 before English 14 | KBD and Kaikki rows remain sense-specific; a Kaikki example is removed only with its unchanged contribution. |
| `食べる` / `ja` | Korean 4 before English 2 | JMdict row imports only the tapped sense plus reading/POS; no pronunciation conflation. |
| `你好` / `zh-Hans` | English 1 | CC-CEDICT imports `hello; hi` and permitted pinyin; no implicit `zh` fallback. |
| `Haus` / `de` | Korean 18 before English 6 | `Haus` noun/name and `haus` verb boundaries remain separate. |
| `nước` / `vi` | Korean 7 before English 5 | KBD official senses stay separate and English remains visible below Korean. |

Suggestion synthesis itself remains transient and adds no persistence path. Task 14 subsequently moved pronunciation/gender persistence to Room v6 and backup v5 through the existing generic mapper; forms remain transient.
