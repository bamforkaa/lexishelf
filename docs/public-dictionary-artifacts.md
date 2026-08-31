# Public dictionary artifact audit

Generated from the exact reviewed `.dictpack` archives selected for catalog
`v0.1.0`. This is release metadata, not legal advice. Updating any source or
archive requires a new artifact-level audit; a provider-wide license assumption is not enough.

## Public v1 provider decision

| Provider | Public v1? | Pack(s) | License basis | Maintenance concern |
| --- | --- | --- | --- | --- |
| Korean Basic Dictionary | Yes | `korean-basic.multilingual` | Official text under CC BY-SA 2.0 KR; multimedia excluded | Recheck each export date, text policy and conversion |
| CC-CEDICT | Yes | `cc-cedict.zh-en` | Exact MDBG release under CC BY-SA 4.0 | Recheck release timestamp, terms and attribution |
| PanLex | Yes, pinned artifact only | `panlex.ko-fallback` | Embedded CC0 grant in checksum-pinned 2019-09-01 snapshot | Never generalize this decision to another snapshot |
| Kaikki / English Wiktionary | Yes | 12 language packs plus English morphology | Selected CC BY-SA 4.0 reuse path | Recheck dump, filter policy, schema and external-material exclusions |
| JMdict | **No** | None | EDRDG CC BY-SA 4.0 plus a regular-update procedure | Public v1 does not assume the continuing update obligation; local/developer integration remains |

## Exact public artifacts

`Source SHA-256` identifies the reviewed publisher artifact. `Archive SHA-256` identifies the
project-generated release asset. Download and installed sizes are bytes. Every row has
`redistributionAllowed=true`; otherwise the generator refuses to emit the public catalog.

| Pack ID | Provider ID | Dataset / schema | Source artifact / SHA-256 | Release archive / SHA-256 | Download / installed | License | Decision |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| `cc-cedict.zh-en` | `cc-cedict` | `2026-08-22T08:27:42Z` / `1` | `cedict_1_0_ts_utf-8_mdbg.txt.gz`<br>`f552a8f4e3beddd2fcf2b5ad670cff24668ee8c60da839489492722e361f8dc5` | `cc-cedict.zh-en-2026-08-22T08_27_42Z.dictpack`<br>`4387661a039a8698396918927e8fb7ebe2dee2dff49d8534fda4ef1c1433b06b` | 3,971,412 / 3,969,462 | CC BY-SA 4.0<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |
| `korean-basic.multilingual` | `korean-basic-dictionary` | `2026-08-19` / `1` | `korean-basic-dictionary-json.zip`<br>`7cf41e62a2a36158a8be2b6d2f84c086221e9b29d4345c44e5497eebf21c8c40` | `korean-basic.multilingual-2026-08-19.dictpack`<br>`96299228644b29a89b205c3d66f2d4d774b8267dc4fa249ace11da3402067a63` | 67,967,805 / 211,701,760 | CC BY-SA 2.0 KR<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |
| `panlex.ko-fallback` | `panlex` | `2019-09-01` / `1` | `panlex-20190901-csv.zip`<br>`e7a53a3851ce4cecd8e60497a2e0d0c3285cdfb67d4ce65da466beee315bb3be` | `panlex.ko-fallback-2019-09-01.dictpack`<br>`d613ba1a2308bed5a7e34f658e45c5e37b1ee7988de9b3273d3bbda88e537a66` | 72,462,030 / 189,714,432 | CC0 1.0 (pinned artifact only)<br>ShareAlike: No | PUBLIC_DISTRIBUTION_ALLOWED |
| `kaikki.de-en` | `kaikki` | `enwiktionary-2026-08-05` / `3` | `raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz`<br>`e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65` | `kaikki.de-en-enwiktionary-2026-08-05.dictpack`<br>`7d882f44a6ece49e2f26bdc221bdfe6c4ea3ad97328192df5d3533fb80949738` | 75,425,130 / 278,564,864 | CC BY-SA 4.0<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |
| `kaikki.hi-en` | `kaikki` | `enwiktionary-2026-08-05` / `3` | `raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz`<br>`e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65` | `kaikki.hi-en-enwiktionary-2026-08-05.dictpack`<br>`947689b87b85ae5e3860af31b7fd0c8e275e30586990867ebd2c94717dd4b324` | 12,101,389 / 80,261,120 | CC BY-SA 4.0<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |
| `kaikki.pl-en` | `kaikki` | `enwiktionary-2026-08-05` / `3` | `raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz`<br>`e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65` | `kaikki.pl-en-enwiktionary-2026-08-05.dictpack`<br>`ab1e66aa187e1154a0762a032b6799fa9f67cd5ecadfaefad8942e822b3ccd88` | 36,313,162 / 139,321,344 | CC BY-SA 4.0<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |
| `kaikki.nl-en` | `kaikki` | `enwiktionary-2026-08-05` / `3` | `raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz`<br>`e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65` | `kaikki.nl-en-enwiktionary-2026-08-05.dictpack`<br>`bf138387627302211e7723dbd1d5ab164467c969454fffe22f8dbd6e1e7a3638` | 21,186,398 / 69,165,056 | CC BY-SA 4.0<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |
| `kaikki.pt-en` | `kaikki` | `enwiktionary-2026-08-05` / `3` | `raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz`<br>`e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65` | `kaikki.pt-en-enwiktionary-2026-08-05.dictpack`<br>`53664633f81e21f91c224c1a0f839af2d8069b723c1b0d9fa4dabe14fe14c49b` | 54,897,494 / 215,515,136 | CC BY-SA 4.0<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |
| `kaikki.tr-en` | `kaikki` | `enwiktionary-2026-08-05` / `3` | `raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz`<br>`e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65` | `kaikki.tr-en-enwiktionary-2026-08-05.dictpack`<br>`73c44ee6e545eaff769ce401b0b5f0a9c63de315c3007ad731c602e3cb3282f3` | 14,497,191 / 79,482,880 | CC BY-SA 4.0<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |
| `kaikki.cs-en` | `kaikki` | `enwiktionary-2026-08-05` / `3` | `raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz`<br>`e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65` | `kaikki.cs-en-enwiktionary-2026-08-05.dictpack`<br>`76df5a1a590f5008e0dde0424b1aa820e619adaac486df2404cb72810dd4bddc` | 18,573,780 / 78,475,264 | CC BY-SA 4.0<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |
| `kaikki.sv-en` | `kaikki` | `enwiktionary-2026-08-05` / `3` | `raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz`<br>`e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65` | `kaikki.sv-en-enwiktionary-2026-08-05.dictpack`<br>`f508722266882c3212cfc1c80bbe2401b862bfdee073f4ed1a287891203dfa6b` | 35,261,263 / 131,198,976 | CC BY-SA 4.0<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |
| `kaikki.uk-en` | `kaikki` | `enwiktionary-2026-08-05` / `3` | `raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz`<br>`e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65` | `kaikki.uk-en-enwiktionary-2026-08-05.dictpack`<br>`4a04aafa70f9db5bbc7b02974d2ff73935dd09e9ff27ec4f10a0c9f0d8d944c8` | 18,832,406 / 100,118,528 | CC BY-SA 4.0<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |
| `kaikki.vi-en` | `kaikki` | `enwiktionary-2026-08-05` / `3` | `raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz`<br>`e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65` | `kaikki.vi-en-enwiktionary-2026-08-05.dictpack`<br>`82277df9ebfe931f63e498dcb2e86477815e6ec40cf44987b42f7b07a5c75a7f` | 5,612,545 / 16,904,192 | CC BY-SA 4.0<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |
| `kaikki.th-en` | `kaikki` | `enwiktionary-2026-08-05` / `3` | `raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz`<br>`e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65` | `kaikki.th-en-enwiktionary-2026-08-05.dictpack`<br>`c5c2480052258d7bd23f97e328d172de664280a3df7fe448b1f310bf34b2e878` | 2,712,695 / 9,351,168 | CC BY-SA 4.0<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |
| `kaikki.id-en` | `kaikki` | `enwiktionary-2026-08-05` / `3` | `raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz`<br>`e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65` | `kaikki.id-en-enwiktionary-2026-08-05.dictpack`<br>`92e280fcac081318afdb5129b88dff459c464a7161f1c8e735ec784d086c62b8` | 6,868,814 / 19,464,192 | CC BY-SA 4.0<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |
| `kaikki.en-morphology` | `kaikki` | `enwiktionary-2026-08-05` / `3` | `raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz`<br>`e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65` | `kaikki.en-morphology-enwiktionary-2026-08-05.dictpack`<br>`4d55f17056d419c582b00ec35c76ae6b79a0d99a981edd8b66f9335d0b1b7fdf` | 29,322,956 / 86,433,792 | CC BY-SA 4.0<br>ShareAlike: Yes | PUBLIC_DISTRIBUTION_ALLOWED |

The catalog and each pack manifest preserve source, version, license, attribution, source URL and
the transformation identity. Transformation details are in
[`dictionary-catalog-v1.json`](../distribution/dictionary-catalog-v1.json); archive checksums are
also in [`SHA256SUMS.txt`](../distribution/SHA256SUMS.txt).

## Size implications

All 16 public packs total **476,006,470 download bytes** and **1,709,642,166 installed
bytes**. The app does not offer “install all” as a default.

| Example selection | Download bytes | Installed bytes |
| --- | ---: | ---: |
| Korean-first basic | 140,429,835 | 401,416,192 |
| Chinese detail | 71,939,217 | 215,671,222 |
| German with Korean + English fallback | 147,887,160 | 468,279,296 |

Coverage is inherently incomplete. Korean Basic installation does not guarantee a Korean meaning
for every expression, and PanLex is a broad lexical fallback rather than a complete dictionary.

## License and transformation details

- **Korean Basic Dictionary:** official full JSON export dated 2026-08-19, source SHA-256
  `7cf41e62a2a36158a8be2b6d2f84c086221e9b29d4345c44e5497eebf21c8c40`. The converter indexes text only and excludes image, audio and
  other multimedia.
- **CC-CEDICT:** exact MDBG artifact `cedict_1_0_ts_utf-8_mdbg.txt.gz`, release
  2026-08-22T08:27:42Z. It is repackaged without changing the source text; attribution and
  ShareAlike remain.
- **PanLex:** only `panlex-20190901-csv.zip` with source SHA-256 `e7a53a3851ce4cecd8e60497a2e0d0c3285cdfb67d4ce65da466beee315bb3be`. The
  generated subset indexes selected language varieties and direct co-denotation relations. The
  artifact's embedded CC0 grant is not asserted for another PanLex release.
- **Kaikki / Wiktionary:** English Wiktionary dump `enwiktionary-2026-08-05`, source SHA-256
  `e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65`. Each language is independent; no aggregate pack is published. The
  converter excludes externally attributed quotations, media/audio and separately licensed
  material and selects the CC BY-SA 4.0 reuse path.
