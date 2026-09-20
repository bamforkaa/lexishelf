# 백업 형식과 호환성

LexiShelf의 canonical backup은 UTF-8 JSON이며 `format`은 `local-vocabulary-backup`, 현재 `schemaVersion`은 **8**입니다. 백업 버전은 Room schema 버전과 독립적입니다.

## JSON 구조

최상위 필드는 `format`, `schemaVersion`, `exportedAtEpochMillis`, `entries`, `tags`, `wordbooks`, `reviewStates`, `reviewEvents`입니다. 빈 백업은 빈 목록으로 표현합니다.

| 데이터 | 보존 대상 |
| --- | --- |
| Entry | stable ID, 단어·표현, BCP 47 언어 태그, reading, 발음, 뜻, 메모, 생성·수정 시각, Tag·Wordbook 관계 |
| Sense | stable ID, 뜻, 품사, 문법 성, 예문, 사전 provenance |
| Example | stable ID, 원문, 의미·설명, origin, 콘텐츠 출처, 기록 시각 |
| Pronunciation | stable ID, 표기 종류와 값, 언어 태그, 사전 provenance |
| Tag / Wordbook | 각각 독립된 stable ID와 이름 |
| ReviewState / ReviewEvent | 뜻별 일정, 문제별 출제 방향, 활성 여부, scheduler 버전·generation, 평가·재시도·초기화 이력 |

Sense·Example·Pronunciation 배열 순서는 보존합니다. `tagStableIds`와 `wordbookStableIds`는 해당 최상위 목록을 참조합니다. 정확한 필드·타입·기본값은 [BackupModels.kt](../app/src/main/java/com/example/localvocabulary/backup/domain/BackupModels.kt)와 [ReviewBackupModels.kt](../app/src/main/java/com/example/localvocabulary/backup/domain/ReviewBackupModels.kt)에 정의합니다.

Room 내부 PK, 앱 설정, 인증 정보, 사전 dataset 파일은 백업 대상이 아닙니다.

## 지원 버전

현재 내보내기는 v8, 가져오기는 **v1~v8**를 지원합니다. 구형 파일은 현재 모델로 변환합니다. 지원하지 않는 버전은 거부하며, 구버전 앱이 새 백업을 읽는 호환성은 보장하지 않습니다.

| 버전 | 추가된 형식 |
| --- | --- |
| v1 | Entry·Tag stable ID, 복수 뜻과 문자열 예문, 메모·시각 |
| v2 | 사전 provenance |
| v3 | Reading과 그 provenance |
| v4 | Wordbook과 Entry 관계 |
| v5 | Stable ID를 가진 발음, 문법 성 |
| v6 | Sense·Example stable ID, 객체형 예문과 문맥 metadata |
| v7 | 방향별 ReviewState·ReviewEvent |
| v8 | 뜻별 단일 ReviewState, event의 promptDirection·legacyReviewStateId |

v1~v5의 Sense·Example에는 ID가 없으므로 가져올 때 새 ID를 생성합니다. 한 번의 가져오기에서는 미리보기와 확정에 같은 ID를 사용하지만, 같은 구형 파일을 다시 가져오면 새 ID가 생성됩니다. 문자열이나 배열 위치로 기존 항목의 ID를 추측하지 않습니다.

## Stable ID와 데이터 보존

- ID는 의미를 해석하지 않는 문자열입니다. 새 항목에는 UUID를 생성하며, 백업은 기존 값을 그대로 보존합니다. 허용 형식은 `[A-Za-z0-9._:-]{1,128}`입니다.
- ID는 엔티티 종류별로 고유해야 합니다. Sense·Example ID는 부모 안에서만이 아니라 전체 백업에서 각각 고유해야 하며, v6 이후에는 누락도 거부합니다.
- 단어와 언어가 같아도 Entry ID가 다르면 별개 항목입니다.
- Room migration은 기존 내용·관계·ID를 보존해야 합니다. 새 식별자가 필요한 기존 행에만 ID를 부여하며, destructive migration으로 호환성 문제를 해결하지 않습니다.
- 과거 백업에 없는 필드는 명시된 빈 값·null·기본값으로 변환합니다. 출처나 과거 시각을 만들어 채우지 않습니다.

## 복원 정책

| 정책 | 의미 |
| --- | --- |
| `MERGE_BY_STABLE_ID` (기본) | 같은 Entry ID의 전체 내용·관계·시각을 백업 값으로 갱신하고 새 ID는 추가합니다. 백업에 없는 기존 Entry는 유지합니다. 필드별 병합이나 최신 수정 시각 비교는 하지 않습니다. |
| `REPLACE_ALL` | 현재 Entry·Tag·Wordbook을 제거하고 백업 snapshot을 복원합니다. 제거될 항목 수를 미리 보여주고 명시적 선택·확인 후 실행합니다. |

v6 이후 병합은 같은 부모 아래 같은 ID의 Sense·Example을 갱신하면서 Room PK와 stable ID를 유지하고, 백업에서 빠진 자식 항목은 삭제합니다. 다른 부모에 속한 자식 ID의 재사용은 거부합니다. 전체 교체에서는 Room PK가 바뀔 수 있습니다.

Tag와 Wordbook은 각각 이름의 공백·대소문자를 정규화해 중복을 판별합니다. 병합 시 ID가 달라도 같은 종류에서 정규화된 이름이 같으면 기존 항목을 재사용하고 관계를 연결합니다.

구형 백업으로 충돌 Entry를 복원하면 기존 Sense·Example과 새 문맥 정보가 교체될 수 있습니다. 미리보기에 교체될 기존 Sense 수와 문맥 손실 가능성을 표시하고 확인받습니다.

## 복습 데이터 호환성

- v1~v6에는 복습 정보가 없으며 가져오기로 자동 등록하지 않습니다. v6 병합은 동일 Sense ID가 남아 있으면 기존 복습 상태·이력도 유지합니다. v1~v5의 자식 교체 또는 Sense 삭제는 연결된 복습 데이터도 제거하므로 미리보기에서 제거 수를 알립니다.
- 현재 버전 병합은 동일 ReviewState ID의 일정을 백업 값으로 복원하고, ReviewEvent는 ID로 중복을 제거해 합칩니다. 백업에 없는 기존 상태·이력은 Sense가 유지되는 한 보존합니다. 이전 일정으로 되돌아가는 상태 수를 미리 알립니다.
- 같은 event ID의 내용이 다르거나, 상태 ID가 다른 Sense를 가리키거나, 같은 Sense에 다른 상태 ID가 있으면 병합을 거부합니다. immutable event를 조용히 덮어쓰지 않습니다.
- v7은 원래 방향별 참조·재시도 관계를 검증한 뒤 뜻별로 변환합니다. 두 상태 중 사전식 순서로 앞선 ID를 대표 ID로 사용하고 stage·due는 최솟값, enabled는 하나라도 활성일 때 true로 정합니다. lastReviewedAt은 하나라도 null이면 null, 아니면 더 이른 값입니다. 단일 방향만 있는 경우도 같은 변환을 적용합니다.
- 변환된 event는 ID·평가·시각·generation·RESET·재시도 연결을 보존하고, 원래 mode를 `promptDirection`, 원래 상태 ID를 `legacyReviewStateId`로 기록합니다. 현재 generation은 기존 상태·이력의 최댓값 다음으로 시작합니다(정수 최댓값에서는 유지). 두 과거 generation은 원래 상태 ID와 함께 해석해야 합니다.
- 일부 방향만 담긴 v7 백업을 병합하면 같은 Sense의 기존 상태 ID에 연결하고 현재 generation을 낮추지 않습니다. 일정은 백업 snapshot으로 복원합니다. 이 예외는 v7 변환에만 적용되며 v8 ID 충돌을 자동 해소하지 않습니다. 알 수 없는 scheduler가 섞여 있으면 그 버전을 유지해 출제를 보류합니다.
- `REPLACE_ALL`은 복습 상태·이력도 백업 snapshot으로 교체합니다. 구형 백업에는 이 정보가 없어 제거됩니다. 모든 변경은 vocabulary 복원과 같은 transaction입니다.
- Import는 새 평가를 생성하거나 `nextReviewAt`을 재계산하지 않습니다. 알 수 없는 scheduler 버전은 그대로 보존하되 복습 queue에서 제외합니다. [복습 정책](review.md)을 참고하세요.

## Provenance와 문맥 호환성

- 사전 provenance는 provider·원문 식별자, 출처·라이선스, dataset 버전, 복사한 필드, 가져온 시각, 수정 여부를 보존합니다. 없거나 null이라는 이유로 사용자 창작이라고 단정하지 않습니다. 라이선스·attribution은 [dictionary-sources.md](dictionary-sources.md)를 따릅니다.
- 예문의 `meaning`은 사용자가 입력한 해석·설명입니다. `origin`은 `UNKNOWN`(출처 미상), `DICTIONARY`(사전), `CAPTURED`(접한 콘텐츠), `USER`(직접 작성) 중 하나입니다.
- `sourceTitle`, `sourceUrl`, `sourceLocator`는 예문을 만난 콘텐츠의 출처이며 사전 provenance와 별개입니다. `capturedAt`은 nullable Unix epoch milliseconds입니다.
- v1~v5 문자열 예문은 객체로 변환하되 `meaning`은 빈 문자열, `origin`은 `UNKNOWN`, 출처와 `capturedAt`은 null로 둡니다. 부모 Sense에 provenance가 있어도 예문의 출처를 추정하지 않습니다.

## 검증과 rollback

1. 파일 크기는 최대 25 MiB이며, 잘못된 UTF-8·JSON, 필수 필드 누락, 알 수 없는 필드·enum 값, 미지원 버전은 거부합니다.
2. 전체 문서의 format, ID 형식·중복, 분류 이름 중복, 참조 관계, 필수 텍스트, 언어 태그, 시각, provenance를 검증합니다. 복습은 뜻별 상태의 유일성, event 종류·방향, 재시도의 부모·방향·generation 관계도 검증합니다.
3. 검증된 데이터로 충돌·추가·갱신·제거와 구형 복원의 영향을 미리 보여줍니다. 사용자 확인 전에는 DB를 변경하지 않습니다.
4. 복원은 하나의 Room transaction으로 실행합니다. 관계·소유권 충돌이나 쓰기 실패가 발생하면 삭제를 포함한 모든 변경을 rollback합니다.

내보내기는 일관된 DB snapshot을 읽으며 DB를 변경하지 않습니다. 복원 transaction의 rollback 보장은 내보내기 대상 파일의 원자적 교체를 보장한다는 의미는 아닙니다.

## 향후 형식 변경 규칙

1. JSON 필드·타입·enum 또는 의미가 바뀌면 backup schema version을 올립니다. JSON 계약에 영향이 없는 Room 변경만으로는 올리지 않습니다.
2. 버전별 decoding과 현재 모델로의 변환을 유지하고, 새 필드의 기본값·누락 처리·기존 ID 보존 방식을 명시합니다. 과거 파일의 의미를 바꾸거나 알 수 없는 데이터를 조용히 버리지 않습니다.
3. 모든 지원 버전의 fixture, 현재 버전 round-trip, ID·순서·관계·provenance·문맥 보존, 두 복원 정책, 잘못된 입력 거부와 transaction rollback 테스트를 유지합니다. Fixture에는 합성 데이터만 사용합니다.
4. 구형 복원이 현재 정보를 잃게 한다면 미리보기·확인 절차를 함께 갱신합니다. 지원 버전과 이 문서를 같은 변경에서 갱신합니다.
