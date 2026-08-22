# JSON 백업 및 복원

## 범위와 안전 원칙

canonical backup은 앱의 vocabulary aggregate를 담는 UTF-8 JSON입니다. 단어, BCP 47 언어 태그, 순서가 있는 뜻/품사/예문, 메모, 태그와 다대다 관계, 생성·수정 시간을 포함합니다. schema v2는 사용자가 명시적으로 가져온 provider-derived sense의 source/license provenance도 함께 보존합니다. 직접 작성한 sense에는 provenance가 없습니다. 현재 domain model에는 favorite와 review metadata가 없어 backup에도 존재하지 않습니다.

DataStore 설정, API key, credential, secret, Android 설정, Room 내부 PK는 포함하지 않습니다. 앱에는 인터넷 또는 공용 저장소 권한이 없으며 Android Storage Access Framework(SAF)의 시스템 파일 선택기로 사용자가 읽고 쓸 문서를 직접 선택합니다.

## Stable ID

Room의 auto-generated `Long` PK는 한 DB 안의 관계 연결에 적합하지만 DB를 새로 만들면 달라질 수 있어 영구 백업 식별자로 사용하지 않습니다. 단어와 태그에는 별도의 opaque `stableId`가 있습니다.

- 신규 데이터: UUID 문자열
- Room v1에서 v2로 migration되는 기존 데이터: row마다 생성한 고유 32자리 lowercase hex 문자열
- JSON: 이 opaque 값을 그대로 보존하되 형식은 `[A-Za-z0-9._:-]`, 길이 1~128자로 제한
- headword와 언어가 같아도 stable ID가 다르면 서로 독립된 사용자 항목으로 취급

이 방식은 동일 백업을 반복 import할 때만 같은 항목을 확실히 찾으며, 내용이 비슷하다는 이유로 사용자의 다른 항목을 자동 덮어쓰지 않습니다.

## Backup schema version 2

Room schema version과 backup schema version은 독립적입니다. 현재 Room은 version 3이고 canonical JSON은 `schemaVersion: 2`입니다. schema v1 import도 계속 지원하며 v1의 모든 sense는 user-authored semantics, 즉 provenance 없음으로 해석합니다.

```json
{
  "format": "local-vocabulary-backup",
  "schemaVersion": 2,
  "exportedAtEpochMillis": 1787331600000,
  "tags": [
    {
      "stableId": "5ca63c70-6a7c-4de1-a873-650ab9bd5f48",
      "name": "Study"
    }
  ],
  "entries": [
    {
      "stableId": "b84e5373-6504-4ca6-913b-1572685fd067",
      "headword": "dictionary",
      "languageTag": "en",
      "senses": [
        {
          "meaning": "사전",
          "partOfSpeech": "noun",
          "examples": [
            "I checked the dictionary."
          ],
          "provenance": null
        }
      ],
      "notes": "사용자가 작성한 메모",
      "tagStableIds": [
        "5ca63c70-6a7c-4de1-a873-650ab9bd5f48"
      ],
      "createdAtEpochMillis": 1787331000000,
      "modifiedAtEpochMillis": 1787331300000
    }
  ]
}
```

배열 순서 중 의미가 있는 것은 `entries[].senses`와 각 sense의 `examples`입니다. 태그 관계는 `tagStableIds`로 나타내며 모든 참조는 최상위 `tags`의 ID를 가리켜야 합니다. canonical export는 tag/entry/relation ID를 stable ID 순서로 정렬해 동일 데이터의 결과를 결정적으로 만듭니다.

Provider-derived sense의 `provenance`는 다음 구조입니다. `importedFields`는 현재 sense-level 저장 모델에서 실제 복사된 `MEANING`, `PART_OF_SPEECH`, `EXAMPLES`만 허용합니다.

```json
{
  "providerId": "cc-cedict",
  "sourceEntryId": "stable-provider-entry-key",
  "sourceSenseId": "0",
  "sourceName": "CC-CEDICT",
  "sourceUrl": "https://cc-cedict.org/editor/editor.php?handler=Download",
  "licenseName": "Creative Commons Attribution-ShareAlike 4.0 International",
  "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
  "datasetVersion": "2026-08-22T08:27:42Z",
  "importedFields": ["MEANING"],
  "importedAtEpochMillis": 1787331200000,
  "modifiedAfterImport": false
}
```

이 metadata는 provider 원문을 user-authored text로 위장하지 않고 attribution과 수정 사실을 복원하기 위한 최소 정보입니다. API key, credential, dataset binary, transient reading/pinyin은 포함하지 않습니다.

`provenance`는 optional이며 없거나 null이면 user-authored sense입니다. 존재할 때는 provenance 구조의 필수 값과 imported field를 모두 검증합니다. 빈 DB는 빈 `tags`와 `entries` 배열로 표현합니다. JSON의 unknown field도 현재는 거부하여 잘못 이해한 문서를 조용히 적용하지 않습니다.

## Validation과 적용 순서

가져오기는 다음 단계를 순서대로 실행합니다.

1. 선택한 URI를 최대 25 MiB까지 읽고 malformed UTF-8을 거부합니다.
2. JSON root object를 parsing합니다.
3. `schemaVersion`을 먼저 확인합니다. 1과 2만 지원합니다.
4. 해당 version DTO 전체를 strict decoding하고 필수/unknown field를 확인합니다. v1은 별도 순수 변환으로 provenance 없는 v2 import model이 됩니다.
5. format, stable ID, 중복 ID/태그 이름, BCP 47 태그, 필수 text, timestamps, sense/example, provenance, tag reference를 검증하고 canonicalize합니다.
6. 현재 DB와 비교해 entry/sense/example/tag, 충돌, 신규, 갱신, 건너뜀, 전체 교체 시 제거 수를 계산합니다.
7. 사용자가 정책과 preview를 확인합니다. 이 시점까지 DB write는 없습니다.
8. 확인 후 하나의 Room transaction에서 반영합니다. 어떤 DAO 작업이라도 실패하면 전체 rollback합니다.

parse 또는 validation 실패 파일은 `ValidatedBackup`이 될 수 없으므로 repository import를 호출할 수 없습니다.

## 충돌 정책

검토한 전략은 다음과 같습니다.

| 전략 | 장점 | 위험/제한 | 초기 지원 |
| --- | --- | --- | --- |
| 기존 데이터 전체 교체 | 백업 시점과 정확히 같은 snapshot 복원 | 현재 데이터 삭제 위험이 가장 큼 | 명시적 선택으로 지원 |
| 기존 유지 + 새 데이터만 병합 | 현재 데이터 보호 | 같은 stable ID의 백업 변경을 복원하지 못함 | 별도 정책으로 미지원 |
| 동일 항목만 update | 기존 항목 복구에 명확 | 백업의 신규 항목을 복원하지 못함 | 별도 정책으로 미지원 |
| 사용자 선택 | 목적에 맞춰 merge/replace 가능 | UI와 테스트 정책이 필요 | 두 정책 중 선택 지원 |

기본값은 `MERGE_BY_STABLE_ID`입니다. 같은 entry stable ID는 sense/example/tag 관계와 timestamps를 포함한 aggregate 전체를 백업 값으로 갱신하고 새 stable ID는 추가하며 백업에 없는 기존 entry는 유지합니다. 서로 다른 두 버전의 필드를 섞는 field-level merge는 하지 않습니다. preview의 충돌/갱신 수로 이 동작을 미리 알 수 있습니다.

`REPLACE_ALL`은 현재 entry/tag를 transaction 안에서 제거하고 백업 snapshot만 복원합니다. UI가 제거될 기존 entry 수를 표시하며 사용자가 선택하고 확인해야 실행합니다.

태그는 whitespace를 정규화하고 `Locale.ROOT` lowercase identity를 사용합니다. import stable ID가 달라도 normalized identity가 같으면 기존 row를 재사용하여 여러 단어가 공유하던 태그를 중복 생성하지 않습니다.

## Versioning 정책

새 필드나 의미 변경이 필요하면 기존 DTO를 수정해서 과거 파일의 의미를 바꾸지 않습니다.

1. 새 version DTO와 validation을 추가하고 기존 DTO는 수정하지 않습니다.
2. root의 `schemaVersion`으로 decoder를 명시적으로 분기합니다.
3. v1을 현재 canonical import model로 올리는 순수 migration 함수를 추가합니다.
4. v1 fixture가 계속 import되는 migration/round-trip 테스트를 유지합니다.
5. 정보 손실이나 정책 변경이 있는 migration은 자동 추측하지 않고 import를 중단해 사용자에게 알립니다.

현재 v1→v2 decoder는 기존 단어/뜻/예문/태그/관계/timestamp를 그대로 옮기고 provenance만 null로 둡니다. 향후 Room schema가 바뀌더라도 JSON 의미가 그대로라면 backup schema는 올릴 필요가 없습니다. 반대로 DB 변경 없이 JSON 계약이 바뀌면 backup schema만 올릴 수 있습니다.

## 수동 확인

1. 목록 화면에서 `백업`을 누릅니다.
2. `내보내기`를 누르고 시스템 파일 선택기에서 이름과 위치를 선택합니다.
3. 성공 메시지를 확인합니다. export 실패는 Room 데이터에 영향을 주지 않습니다.
4. `가져오기`에서 JSON을 선택하고 preview 수치를 확인합니다.
5. 기본 병합 또는 전체 교체를 선택합니다. 전체 교체의 제거 수를 특히 확인합니다.
6. `확인 후 복원`을 누르고 완료/실패 메시지를 확인합니다.
