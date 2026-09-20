# 복습 정책

Today Review는 **Sense 하나당 하나의 진행 상태**를 사용합니다. Editor에서 새 expression을 저장하면 첫 뜻만 기본 등록하며, 뜻별로 editor 또는 detail에서 등록을 끄거나 켤 수 있습니다. 기존 항목의 수정·migration·backup import만으로 자동 등록하지 않습니다. Editor의 선택은 vocabulary 저장과 같은 transaction에서 적용합니다. Writing Practice는 별개의 철자·쓰기 연습입니다.

## 출제 방향

정규 문제는 의미→표현과 표현→의미 중 해당 상태의 정규 이력이 적은 방향을 선택합니다. 단, 한 방향에 편중된 구버전 이력을 보정할 때도 같은 방향을 세 번 연속 출제하지 않습니다. 횟수가 같으면 직전 정규 문제의 반대 방향을 선택하고, 이력이 없으면 stable ID에 따른 결정적 선택을 사용합니다. 시각이 같은 이력은 event ID로 순서를 정합니다. RESET과 RETRY는 방향 횟수에 포함하지 않습니다. 재시도는 원래 문제의 방향을 유지하며 방향과 무관하게 같은 일정이 갱신됩니다.

## Scheduler `steps-v1`

장기 단계는 신규(-1), 1·3·7·14·30·60일(0~5)입니다. `ReviewPolicy`에 간격을 모으고 `ReviewScheduler`는 상태·평가·시각·timezone을 받아 다음 상태를 계산합니다.

| 평가 | 단계 | 다음 정규 복습 |
| --- | --- | --- |
| 기억남 | 한 단계 상승, 최종 단계 유지 | 새 단계의 간격; 신규 성공은 1일, 최종은 계속 60일 |
| 어렵게 기억남 | 유지 | 현재 단계 간격과 3일 중 짧은 값; 신규는 1일 |
| 기억 안 남 | 한 단계 하향, 신규보다 낮아지지 않음 | 1일 |
| 세션 재시도 | 변경 없음 | lastReviewedAt·nextReviewAt 모두 유지 |

Overdue는 실제 평가 시점에서 한 단계만 처리합니다. 놓친 날짜만큼 단계를 올리거나 복습을 강요하지 않습니다. 간격은 현재 기기 timezone의 달력 일수로 계산해 DST 전환에도 현지 시각을 유지합니다. 일일 한도는 해당 timezone의 자정부터 다음 자정까지이며, timezone 변경 시 새 현지 날짜로 집계합니다.

## Queue와 세션

- 등록은 학습 대기에 추가하는 동작입니다. 신규 한도는 등록 버튼 횟수가 아닌 **뜻별 첫 정규 평가 수**입니다. 기본 신규 15개, 전체 40개이며 각각 0~100, 1~500 범위에서 신규 ≤ 전체를 보장합니다.
- `nextReviewAt ≤ 현재 시각`인 활성 상태만 후보입니다. 과거 due → 오늘 due → 신규 순으로, due 시각과 stable ID로 정렬합니다. 당일 완료한 정규 event 수를 차감하므로 재진입해도 한도를 새로 받지 않습니다.
- 한 뜻은 현지 날짜당 최대 한 번 정규 평가합니다. 신규 수는 event의 `wasNew`로 집계합니다. RESET·RETRY는 한도에 포함하지 않습니다.
- 같은 Sense의 재시도가 바로 이어지지 않도록 다른 후보를 먼저 선택합니다.
- 실패한 정규 항목은 다른 정규 문제를 최소 하나 거친 뒤 세션 뒤쪽에서 최대 한 번 재시도합니다. 중간 문제가 없거나 바로 같은 Sense를 반복하게 되면 생략합니다. 재시도 자체의 실패는 추가 재시도를 만들지 않습니다.
- Process recreation에는 세션과 평가 token을 복원합니다. 화면을 나갔다 다시 진입하면 남은 정규 queue를 새로 구성하며, 끝난 세션의 재시도를 다시 강제하지 않습니다.

## 영구 기록과 수정

복습 시각은 모두 Unix epoch milliseconds로 저장합니다. 현지 날짜는 일일 한도와 다음 간격 계산에만 사용합니다.

`ReviewState`는 stable ID, Sense stable ID, enabled, stage, lastReviewedAt, nextReviewAt, schedulerVersion, generation을 저장합니다. Sense 참조는 unique이며 Sense 삭제 시 FK cascade로 연결 상태·이력이 삭제됩니다. 비활성화는 삭제나 초기화가 아니며 재활성화하면 기존 일정으로 돌아갑니다.

`ReviewEvent`는 stable event ID, 상태 참조, 시각, nullable rating, SCHEDULED/RETRY/RESET 종류, schedulerVersion, generation, wasNew, session ID, 재시도의 원래 event ID와 실제 `promptDirection`을 저장합니다. 새 RESET에는 방향이 없습니다. 이전 방향별 상태에서 변환한 event는 원래 방향·generation·`legacyReviewStateId`를 함께 보존합니다. 원래 정규 event당 재시도는 unique입니다.

평가 event 삽입과 상태 변경은 하나의 transaction입니다. 같은 평가 token을 다시 보내도 event를 추가하지 않으며, 오래된 화면의 generation·lastReviewedAt과 현재 상태가 다르면 거부합니다. 평가만으로 vocabulary 수정 시각을 바꾸지 않습니다.

공백·줄바꿈·문장부호만 바뀐 의미는 학습 상태를 유지합니다. 나머지 텍스트 변경은 의미를 자동 추론하지 않고 이력이 있는 뜻마다 유지/초기화를 선택하게 합니다. 기본은 유지입니다. 초기화는 새 generation과 RESET event를 남기고 과거 이력·문맥은 보존합니다. 초기화해도 당일 완료한 정규 복습의 한도 차감은 유지합니다. 백업 병합 후에도 초기화 generation은 기존 이력의 최대 generation보다 큽니다. 정수 한도에 도달하면 초기화를 거부하며 음수로 넘기지 않습니다.

답 공개 전에는 원문 예문을 숨깁니다. 의미 단서에도 target expression이 포함되면 단서를 생략합니다. 공개 후 자기 평가하며 문자열로 정답을 판정하지 않습니다. 선택적인 내 문장은 기존 Example 모델에 `origin=USER`와 stable ID로 저장하고 평가에는 영향을 주지 않습니다.

복원은 저장된 일정을 그대로 사용합니다. 지원하지 않는 scheduler 버전은 보존하고 queue에서 제외합니다. 버전 교체 시 기존 상태를 조용히 재해석하지 않으며 명시적인 변환·테스트가 필요합니다. 백업 호환성은 [backup.md](backup.md)를 따릅니다.
