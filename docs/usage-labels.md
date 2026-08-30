# Learner-facing sense labels

Task 18 audits the pinned Kaikki/Wiktextract `2026-08-05` source before exposing any source tag.
The reproducible reports are produced with:

```powershell
python -X utf8 -m tools.report_kaikki_usage_labels `
  --output build\task18-audit\kaikki-usage-labels-12.json

python -X utf8 -m tools.report_kaikki_usage_labels `
  --languages de --include-english `
  --output build\task18-audit\kaikki-usage-labels-de-en.json
```

The first command reads the 12 filtered production sources. The second streams the pinned full
source once to measure English, which is otherwise retained only as a morphology index.

## Actual source structures

- Entry `tags` contain entry-wide morphology/classification data; entry `categories` contain large
  source-maintenance and lexical category lists.
- Sense `tags` are normalized Wiktextract tags and are the only Kaikki input to the whitelist.
- Sense `raw_tags` are sparse free text such as `with auf` or `of a person`; they are audited but
  never displayed automatically.
- Sense `topics` contain domain labels such as computing, medicine, chemistry, and law. Domain
  taxonomy is deferred.
- Sense `categories` largely repeat maintenance, etymology, form, and lexical classifications.
- Form `tags` describe inflection mechanics such as plural, case, person, tense, and mood. They
  remain governed by the separate form/morphology policy.

The converter never treats these fields as interchangeable. Entry/category/form metadata is not
flattened into a sense label.

## Coverage

Coverage counts indexed senses with at least one whitelisted label. One sense may have multiple
labels, so label totals do not sum to the labeled-sense count.

| Language | Indexed entries | Senses | Labeled senses | Coverage |
| --- | ---: | ---: | ---: | ---: |
| de | 369,967 | 631,694 | 21,867 | 3.4616% |
| hi | 38,856 | 57,527 | 5,662 | 9.8423% |
| pl | 197,832 | 264,955 | 51,771 | 19.5395% |
| nl | 145,878 | 189,727 | 38,292 | 20.1827% |
| pt | 445,173 | 525,560 | 18,908 | 3.5977% |
| tr | 45,617 | 59,779 | 6,796 | 11.3685% |
| cs | 72,027 | 85,100 | 4,566 | 5.3655% |
| sv | 312,058 | 345,896 | 10,820 | 3.1281% |
| uk | 59,433 | 80,902 | 18,514 | 22.8845% |
| vi | 46,170 | 56,466 | 5,415 | 9.5898% |
| th | 20,939 | 27,665 | 5,377 | 19.4361% |
| id | 39,662 | 55,557 | 8,790 | 15.8216% |
| **12-language total** | **1,793,612** | **2,380,828** | **196,778** | **8.2651%** |
| en source audit | 1,486,439 | 1,779,278 | 414,843 | 23.3152% |

## Normalized taxonomy and decision matrix

All mappings are exact, case-sensitive values from normalized `sense.tags`. Unknown values are
ignored. Counts are occurrences across the 12 production languages; English is shown separately.

| Category | Normalized label / exact raw tag | 12 languages | English | Learner value | Ambiguity risk | UI value | Persistence value | Decision |
| --- | --- | ---: | ---: | --- | --- | --- | --- | --- |
| Register | `formal` | 18,669 | 838 | High | Low; exact source label | High | Not yet established | Show transiently |
| Register | `informal` | 6,431 | 19,142 | High | Low; distinct from colloquial/slang | High | Not yet established | Show transiently |
| Register | `colloquial` | 25,634 | 7,352 | High | Low | High | Not yet established | Show transiently |
| Register | `slang` | 6,438 | 28,647 | High | Low | High | Not yet established | Show transiently |
| Register | `vulgar` | 3,509 | 5,057 | High | Low; usage warning, not moderation | High | Not yet established | Show transiently |
| Register | `offensive` | 1,009 | 2,145 | High | Low; not a moderation classification | High | Not yet established | Show transiently |
| Register | `derogatory` | 6,949 | 9,142 | High | Low; distinct from offensive/vulgar | High | Not yet established | Show transiently |
| Register | `literary` | 5,981 | 1,012 | Medium-high | Low | Medium-high | Not yet established | Show transiently |
| Temporal | `archaic` | 14,362 | 24,946 | High | Low; not merged with obsolete/dated | High | Not yet established | Show transiently |
| Temporal | `obsolete` | 14,107 | 40,789 | High | Low; does not alter lookup eligibility | High | Not yet established | Show transiently |
| Temporal | `dated` | 21,342 | 11,446 | High | Low; distinct from archaic | High | Not yet established | Show transiently |
| Temporal | `rare` | 6,584 | 18,993 | Medium-high | Medium; source frequency is qualitative | Medium | Not yet established | Show transiently |
| Grammar | `transitive` | 45,705 | 37,351 | High | Low; 45,664/45,705 occurrences are verbs | High | Candidate only | Show transiently |
| Grammar | `intransitive` | 21,381 | 14,928 | High | Low; 21,337/21,381 are verbs | High | Candidate only | Show transiently |
| Grammar | `countable` | 3,351 | 103,831 | High | Low; 3,291/3,351 are nouns | High | Candidate only | Show transiently |
| Grammar | `uncountable` | 26,562 | 239,058 | High | Low; 23,881/26,562 are nouns | High | Candidate only | Show transiently |
| Grammar | `auxiliary` | 244 | 120 | Medium-high | Low | Medium | Not yet established | Show transiently |
| Grammar | `impersonal` | 1,414 | 44 | Medium-high | Low; 1,386/1,414 are verbs | Medium | Not yet established | Show transiently |
| Region | `regional` | 1,908 | 629 | Medium-high | Medium; no place identity | Medium | Low | Show transiently |
| Region | `dialectal` | 4,889 | 5,201 | Medium-high | Medium; named dialect ontology deferred | Medium | Low | Show transiently |

No label is persisted in Room or backup. Persistence value is not yet high enough to justify
copying provider reference metadata into user-owned vocabulary or changing edit semantics.

## Sense boundaries and ordering

Labels stay on the source sense that supplied them. For example, German `Wissenschaft` has a
countable “branch of knowledge” sense and uncountable “science as a whole” senses. `sehen` has
separate transitive and intransitive senses, plus a source sense explicitly marked as both.

UI ordering is deterministic:

1. register/warning labels;
2. temporal labels;
3. grammar labels;
4. region labels.

Duplicate normalized labels are removed. The suggestion row shows at most three labels followed
by `+N`; accessibility semantics retain the complete ordered list. Labels from a supporting
provider contribution are not promoted onto a different primary contribution during synthesis.

## Actual QA samples

| Label | Language / headword / POS | First gloss | Relevant raw tags |
| --- | --- | --- | --- |
| informal | de `Uhr` noun | clockwise direction | `feminine, informal` |
| informal | de `England` name | Great Britain | `informal, neuter, proper-noun` |
| informal | de `England` name | United Kingdom | `informal, neuter, proper-noun` |
| slang | de `lost` adjective | clueless, confused | `slang` |
| slang | de `Russisch` noun | intercrural sex | `neuter, no-plural, slang, strong` |
| slang | de `deep` adjective | intellectually profound | `not-comparable, slang` |
| archaic | de `nu` adverb | alternative form of `nun` | `alt-of, alternative, archaic, colloquial` |
| archaic | de `nu` interjection | alternative form of `nun` | `alt-of, alternative, archaic, colloquial` |
| archaic | de `dar` adverb | only used in compounds | `archaic` |
| transitive | de `sehen` verb | to perceive by vision | `class-5, strong, transitive` |
| transitive | de `sehen` verb | to see | `class-5, strong, transitive` |
| transitive | de `sehen` verb | to realize or notice | `class-5, intransitive, strong, transitive` |
| intransitive | de `sehen` verb | to have sight | `class-5, intransitive, strong` |
| intransitive | de `sehen` verb | to realize or notice | `class-5, intransitive, strong, transitive` |
| intransitive | de `sehen` verb | to look or watch | `class-5, intransitive, strong` |
| countable | de `Wissenschaft` noun | a branch of knowledge | `countable, feminine` |
| countable | de `Fisch` noun | fish | `countable, masculine, strong` |
| countable | de `Fisch` noun | archaic broad sense of fish | `archaic, broadly, countable, masculine, strong` |
| uncountable | de `Wissenschaft` noun | science as a whole | `feminine, uncountable` |
| uncountable | de `Wissenschaft` noun | academia/scholarship | `feminine, uncountable` |
| uncountable | de `Wissenschaft` noun | academic community | `feminine, uncountable` |
| regional | de `nu` interjection | yes/yeah | `colloquial, regional` |
| regional | de `nu` interjection | pause filler | `colloquial, regional` |
| regional | de `dat` article | alternative form of `das` | `Ruhrdeutsch, alt-of, colloquial, regional` |

The full report contains per-language samples for cross-language QA.

Cross-language spot checks use exact rows from the pinned source rather than invented examples:

| Language | Headword / POS | Raw sense tags | Normalized labels |
| --- | --- | --- | --- |
| de | `sehen` / verb | `class-5, strong, transitive` | `transitive` |
| pt | `abater` / verb | `intransitive` | `intransitive` |
| pl | `pies` / noun | `derogatory, slang` | `slang, derogatory` |
| tr | `ten` / noun | `dialectal` | `dialectal` |
| vi | `sun` / verb | `transitive` | `transitive` |
| hi | `कुत्ता` / noun | `figuratively, masculine, vulgar` | `vulgar` |
| th | `ไทย` / adjective | `derogatory, sarcastic, slang` | `slang, derogatory` |
| id | `barter` / noun | `countable, uncountable` | `countable, uncountable` |

## Rejected and deferred metadata

The most frequent non-whitelisted sense tags across the production languages demonstrate why a
blacklist would be unsafe. The top 50 are:

`form-of`, `singular`, `plural`, `masculine`, `feminine`, `genitive`, `strong`, `neuter`,
`second-person`, `indicative`, `first-person`, `third-person`, `present`, `accusative`,
`nominative`, `definite`, `mixed`, `dative`, `weak`, `subjunctive`, `indefinite`, `superlative`,
`comparative`, `participle`, `future`, `imperative`, `preterite`, `imperfect`, `past`, `alt-of`,
`common-gender`, `inanimate`, `not-comparable`, `conditional`, `infinitive`, `imperfective`,
`personal`, `alternative`, `person`, `pluperfect`, `perfective`, `subjunctive-ii`, `vocative`,
`instrumental`, `subjunctive-i`, `passive`, `locative`, `indeclinable`, `no-diminutive`, `animate`.

- Form/meta classifications remain in form/morphology policy and are not usage badges.
- `historical`, `uncommon`, `nonstandard`, `poetic`, `euphemistic`, and similar near-synonyms are
  deferred rather than over-normalized.
- Named regions such as `US`, `UK`, `Austria`, `Switzerland`, and `Bavaria` are deferred until a
  bounded reviewed mapping exists. They are not converted to BCP 47 regions.
- Topics/domains are measured but deferred; no domain ontology is added.
- Unknown future tags are ignored by production mapping and remain visible only in the audit.

## Other provider compatibility

JMdict schema v1 already preserves sense-level `misc`, `field`, and `dialect` arrays. The current
artifact has 253,268 senses and 79,337 senses with at least one of these arrays. Useful exact
values include `archaic` (3,795), `rare term` (3,151), `colloquial` (2,504), `slang` (1,405),
`obsolete term` (741), `dated term` (629), `derogatory` (477), and `vulgar expression or word`
(238). Dialect details include `Kansai-ben` (292). The provider-neutral model can represent these,
but mapping is deferred because combined values such as `formal or literary term` and JMdict POS
values such as `transitive verb` need a provider-specific review without changing current import
semantics.

Korean Basic Dictionary schema v1 currently retains official entry/sense identity, POS, and
translations only. Its source features are not guessed into this taxonomy. CC-CEDICT and PanLex
do not expose structured sense usage metadata through their current reviewed indexes.

## Persistence and generated schema

Kaikki payload v3 gains an optional compact `u` list on a sense. Absence means “no whitelisted
label”, so existing v3 packs remain readable and the SQLite DDL, indices, manifest contract, and
strict schema validation do not change. A generated schema v4 would be required if label presence
became mandatory or required new tables/indices; neither is true here.

Labels are reference metadata only. Explicit row import still copies only fields permitted by the
existing licensing/import policy. Word Detail does not fabricate labels that were never persisted.
Room remains v6 and canonical backup remains v5.

## Generated-size impact

The Task 18 rebuild keeps SQLite and manifest schema 3. Across the 12 dictionary DBs, optional
whitelisted sense labels increased generated size from `1,212,227,584` to `1,217,822,720` bytes
(`+5,595,136`, about 0.46%). Their packs increased from `301,274,186` to `302,282,267` bytes
(`+1,008,081`, about 0.33%). The unchanged English morphology-only DB/pack brings the current
13-artifact totals to `1,304,256,512` and `331,605,223` bytes. Normalization happens during
conversion; runtime performs only bounded JSON decoding and an exact code-to-enum mapping on the
returned suggestion senses. Room and vocabulary-list queries do not touch this metadata.

On the API 37 `Medium_Phone_Test` AVD, the production bundled-pack bootstrap and real Kaikki
integration test returned source-attested labels for German `sehen` and `Wissenschaft`. The first
German exact lookup took `17ms`; the existing morphology-plus-lemma check took `13ms`. The debug
APK bundling core dictionaries, `de`, `vi`, and English morphology measured `339,955,336` bytes.
