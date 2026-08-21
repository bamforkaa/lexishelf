# ADR-0002: 버전형 JSON과 stable ID 기반 복원

- 상태: Accepted
- 날짜: 2026-08-22

## Context

사용자 소유 Room 데이터를 uninstall이나 기기 이동 전에 lossless하게 내보내고 복원해야 한다. Room의 auto-generated `Long` PK는 DB 내부 관계에는 적합하지만 새 DB에서 같은 값을 보장하지 않으므로 외부 계약으로 고정하면 persistence 구현이 백업 형식에 노출된다. Import는 malformed/unknown 파일로 기존 데이터를 일부 변경해서는 안 되며, 개인용 단어장의 현재 데이터도 기본 동작에서 지우지 않아야 한다.

## Decision

Room schema와 독립된 명시적 `schemaVersion`을 가진 UTF-8 JSON을 canonical backup으로 사용한다. schema v1은 사용자 단어 aggregate와 공유 태그를 stable ID로 표현한다. entry와 tag에 Room PK와 별도의 opaque backup ID를 추가하며 신규 row에는 UUID를, v1→v2 migration 대상 row에는 고유 random hex ID를 부여한다.

가져온 문서는 version별 DTO로 strict parse하고 모든 필드/참조를 검증한 뒤에만 `ValidatedBackup`으로 repository에 전달한다. SAF file I/O와 JSON codec은 UI 밖에 둔다. 실제 적용은 하나의 Room transaction이다.

UI는 두 정책을 명시적으로 제공한다.

- 기본 `MERGE_BY_STABLE_ID`: 같은 stable ID의 aggregate 전체 갱신, 신규 추가, 백업에 없는 기존 데이터 유지
- `REPLACE_ALL`: 기존 단어와 태그를 제거하고 백업 snapshot으로 교체

태그는 normalized name identity를 공유하여 같은 의미의 tag row가 import로 중복되지 않게 한다. field-level merge는 하지 않는다.

## Consequences

장점:

- DB가 재생성되어도 같은 백업 계보의 항목과 관계를 안정적으로 찾는다.
- Room 구현 PK가 외부 schema에 노출되지 않는다.
- validation/preview 전에는 write가 없고 import 실패는 전체 rollback된다.
- 기본 병합이 현재 사용자 데이터를 삭제하지 않으면서 반복 import를 예측 가능하게 처리한다.
- Room version과 backup version을 독립적으로 진화시킬 수 있다.

비용:

- 기존 DB에 stable ID column과 migration이 필요하다.
- 같은 단어를 서로 독립적으로 만든 두 DB는 stable ID가 다르므로 자동 중복 판정하지 않는다.
- 충돌 entry는 aggregate 전체를 교체하므로 양쪽 편집 내용을 합치는 field-level merge는 제공하지 않는다.
- future schema마다 DTO, validation, migration function과 fixture test를 유지해야 한다.

## Rejected alternatives

- Room `Long` PK를 영구 ID로 export: persistence 세부사항을 계약으로 만들고 독립 DB 사이 충돌 의미가 불명확하다.
- headword/language를 identity로 사용: 사용자가 같은 표현을 여러 목적으로 저장할 수 있고 내용 유사성으로 잘못 덮어쓸 수 있다.
- 기본 전체 교체: 복원 파일 선택 실수의 데이터 손실 범위가 크다.
- 자동 field-level merge: 뜻/예문 순서와 삭제 의도를 판정하기 어려워 초기 개인용 앱에 비해 복잡하고 예측 불가능하다.
- 앱 전용 파일 경로나 공용 저장소 권한: 사용자가 위치를 선택하기 어렵거나 불필요한 권한을 요구한다. SAF가 현재 범위에 적합하다.
