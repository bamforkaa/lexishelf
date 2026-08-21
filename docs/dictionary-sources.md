# 사전 소스 라이선스 조사

조사일: 2026-08-21

이번 마일스톤에는 사전 데이터, API 응답 fixture, provider 구현, 다운로드 코드가 없습니다. 아래 내용은 향후 구현 여부를 결정하기 위한 조사 기록이며 법률 자문이 아닙니다. 서로 모순되거나 구체적 계약이 보이지 않는 항목은 허용으로 추측하지 않습니다.

## Cambridge Dictionary API / licensed data

공식 출처:

- [Cambridge Dictionary Develop](https://dictionary.cambridge.org/develop.html)
- [Cambridge License our data](https://dictionary.cambridge.org/us/license.html)

확인된 사실:

- 공식 Develop 페이지는 Dictionary API가 여러 dictionary/method에 접근을 제공한다고 설명한다.
- 데이터 라이선스 페이지는 사용 목적에 따른 문의와 가격 모델 협의를 안내한다.
- Cambridge 페이지 콘텐츠는 Cambridge University Press & Assessment copyright 대상이다.

확인되지 않아 구현을 막는 항목:

- 개인용 앱이 사용할 구체적인 API access/credential 발급 조건
- API 응답의 영구 저장, 사용자 편집본 저장, 원문 definition 파생 저장 허용 여부
- cache 기간/크기, offline 사용, backup 포함 가능 여부
- 화면별 attribution 문구/링크, rate limit, redistribution 제한
- 오디오/발음 등 각 media의 별도 권리

결정: 공식 계약/terms 원문을 확보하고 위 항목을 확인하기 전에는 호출, cache, raw definition 영구 저장을 구현하지 않는다. 웹 페이지 scraping은 하지 않는다.

## JMdict

공식 출처:

- [EDRDG General Dictionary Licence Statement](https://www.edrdg.org/edrdg/licence.html)
- [JMdict/EDICT project](https://www.edrdg.org/wiki/JMdict-EDICT_Dictionary_Project.html)

확인된 사실:

- license statement는 JMdict의 Japanese/English components를 Creative Commons Attribution-ShareAlike 4.0으로 제공한다고 명시한다.
- share/remix가 가능하지만 attribution과 share-alike 조건이 있다.
- smartphone app은 메뉴에서 접근 가능한 About/Sources 등의 별도 화면에 acknowledgement를 제공해야 하고 documentation/license 링크 또는 사본을 제공해야 한다.
- 사용 앱은 최신 데이터로 정기 갱신하는 절차를 가져야 한다.
- multilingual JMdict의 비영어 번역은 별도 compiler copyright가 적용될 수 있다고 명시되어 있다.

저장/편집/재배포 판단:

- license statement 범위의 데이터는 조건을 충족하면 복사·수정·배포가 가능하다.
- 그러나 어떤 JMdict distribution(영어 전용/다국어)을 선택하는지에 따라 권리 범위가 달라질 수 있다.
- 사용자 단어장으로 추출·편집한 definition을 backup/export할 때 share-alike 적용 범위와 attribution 전달 방식을 제품 정책으로 정해야 한다.

미결정 구현 요구:

- 정확한 download artifact와 checksum/update manifest
- 앱 Sources 화면, license 사본/링크, 데이터 버전 표시
- 정기 update 주기와 offline 실패/rollback 정책
- 사용자가 수정한 JMdict 파생 텍스트의 export license notice

결정: 위 packaging/update/attribution 설계를 별도 ADR로 승인하기 전에는 다운로드하거나 bundle하지 않는다.

## CC-CEDICT

공식 출처:

- [CC-CEDICT project home](https://cc-cedict.org/wiki/)
- [CC-CEDICT download page](https://cc-cedict.org/editor/editor.php?handler=Download)

확인된 사실:

- project home은 downloadable collaborative Chinese-English dictionary라고 설명한다.
- home은 CC Attribution-ShareAlike 3.0이라고 표시한다.
- 2026 download page는 제공되는 current non-verified download를 CC Attribution-ShareAlike 4.0이라고 표시하고 attribution/share-alike를 설명한다.
- download page는 recommended latest release와 editing/review용 non-verified build를 구분한다.

라이선스 충돌과 미결정 사항:

- 공식 페이지 두 곳의 3.0/4.0 표기가 일치하지 않는다.
- recommended release artifact의 정확한 license/version, attribution 문구, update cadence를 artifact와 함께 확인해야 한다.
- 사용자 수정 definition과 JSON export에 share-alike가 어떻게 전달되는지 정책이 필요하다.
- local bundle 크기, update, integrity/checksum, Traditional/Simplified normalization 정책이 필요하다.

결정: 특정 release artifact와 동봉 license가 일치하는지 확인하기 전에는 다운로드, bundle, parser, fixture를 추가하지 않는다.

## 구현 전 공통 승인 체크리스트

- 공식 source URL과 선택한 artifact/version
- license/terms 원문과 보관 사본 또는 안정 링크
- 영구 저장, 편집/파생 저장, cache, backup, redistribution 허용 범위
- 필수 attribution 문구와 노출 위치
- update 의무와 실패/rollback 정책
- source entry ID 및 provenance 보존 방식
- 테스트 fixture의 재배포 허용 여부와 sanitization
- 사용자 편집 내용이 provider refresh로 덮어써지지 않는 테스트
