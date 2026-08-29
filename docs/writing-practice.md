# 쓰기 연습

## 범위와 진입점

홈의 더보기 → `쓰기 연습`은 전체 범위 setup을 열고, 단어장 상세의 필기 아이콘은 해당
Wordbook을 미리 선택해 엽니다. setup에서 전체, canonical BCP 47 언어, Wordbook, Tag 중 하나와
10개, 20개, 전체 세션 길이를 선택합니다. Tag와 Wordbook relation은 읽기만 하며 변경하지 않습니다.

## 문제 자격과 힌트

문제는 nonblank headword, 유효한 BCP 47 언어 태그와 안전하게 표시할 수 있는 힌트가 하나 이상인
저장 entry입니다. DAO는 scope 조건을 SQL `EXISTS`로 적용하고 다음 compact field만 읽습니다.

- entry ID, headword, language tag
- 첫 nonblank meaning, reading, textual pronunciation
- 첫 nonblank POS/gender, 첫 example

전체 `VocabularyEntry` aggregate를 setup에서 모두 읽지 않습니다. 힌트 우선순위는 meaning,
reading, pronunciation, POS/gender, example입니다. meaning이 없으면 다음 안전한 hint가 primary가
됩니다. NFC/trim/연속 공백 정규화 후 headword와 같은 hint는 숨기고, example에 같은 headword가
case-insensitive exact substring으로 들어 있으면 example 전체를 숨깁니다. 형태소 분석이나 NLP
masking은 하지 않습니다.

## 세션과 정답 판정

세션 시작 시 eligible ID를 한 번 shuffle하고 선택한 길이로 snapshot합니다. recomposition이나
중간 vocabulary Flow 갱신은 문제 순서를 바꾸지 않으며 같은 entry ID를 반복하지 않습니다.
intentional duplicate headword는 서로 다른 entry ID이므로 별도 문제일 수 있습니다.

정답은 저장된 `VocabularyEntry.headword` 하나입니다. 사용자 답과 정답 모두 Unicode NFC,
앞뒤 공백 제거, 연속 whitespace 한 칸 축약만 적용한 뒤 case-sensitive exact 비교합니다. 따라서
German noun capitalization을 보존합니다. alternate spelling, synonym, inflection, edit distance,
fuzzy/semantic/AI 판정은 사용하지 않습니다.

## 손글씨와 키보드

Writing Practice는 Word Editor와 검색에서 사용하는 `HandwritingInputViewModel`과 ML Kit adapter를
그대로 사용합니다. 문제 언어가 기본 model context이고 사용자는 기존 언어 selector로 바꿀 수
있습니다. 후보를 눌러도 답 문자열만 채우며 반드시 `제출`을 따로 눌러야 합니다.

새 문제와 retry는 handwriting session을 다시 열어 Ink, 후보, 선택 답과 stale recognition job을
지웁니다. retry는 같은 문제와 힌트를 유지합니다. model이 없으면 기존 다운로드 action을 제공하고,
미지원·다운로드 실패·offline 상태에서도 키보드 mode로 답할 수 있습니다. 이 기능은 필체나 획순을
평가하지 않습니다.

## 결과와 저장 경계

첫 제출만 first-attempt correct/incorrect 통계에 기록합니다. 오답 뒤 `다시 쓰기`로 맞혀도 최초
오답 한 건은 유지되고 retry가 통계를 중복 증가시키지 않습니다. 마지막 문제 뒤에는 총 문제,
첫 시도 정답과 오답만 표시합니다.

문제 순서, Ink, 후보, 답안과 통계는 ViewModel 메모리에만 있습니다. Room v6, DataStore와 backup
v5에 쓰지 않으며 앱 process가 종료되면 세션도 끝납니다. 장기 이력, mastery, SRS, streak와
stroke-order/shape score는 후속 범위입니다.

## Manual QA

1. 홈과 단어장 상세에서 각각 진입하고 전체/언어/Wordbook/Tag count를 확인합니다.
2. Japanese `먹다` + `たべる` → `食べる`, Chinese `안녕` + pinyin → `你好`, German `물` →
   `Wasser`, English `긴` → `long`을 확인합니다.
3. 후보 tap만으로 제출되지 않는지, 정답/오답/retry/next/summary가 맞는지 확인합니다.
4. 새 문제에서 이전 Ink·후보·답이 없고 새 language model context가 적용되는지 확인합니다.
5. 미설치 model 다운로드, offline 실패, unsupported language에서 키보드 fallback을 확인합니다.
