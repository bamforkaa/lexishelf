# 로컬 손글씨 입력

## 선택한 API

Word Editor는 Google ML Kit Digital Ink Recognition Android artifact
`com.google.mlkit:digital-ink-recognition:19.0.0`을 사용합니다. 공식 Android 가이드는 최소
API 23, 300개가 넘는 언어/스크립트 모델, 언어별 약 20MB의 on-demand download를 명시합니다.

- [Android Digital Ink 가이드](https://developers.google.com/ml-kit/vision/digital-ink-recognition/android)
- [공식 base model 목록](https://developers.google.com/ml-kit/vision/digital-ink-recognition/base-models)
- [Model identifier API](https://developers.google.com/android/reference/com/google/mlkit/vision/digitalink/recognition/DigitalInkRecognitionModelIdentifier)
- [ML Kit terms and privacy](https://developers.google.com/ml-kit/terms)

이는 bitmap OCR가 아닙니다. Canvas pointer event에서 획 순서와 각 point의 `x`, `y`,
millisecond timestamp를 기록하고 ML Kit `Ink`로 변환합니다. 같은 좌표 단위를 writing area에도
제공합니다. 모델은 한 줄의 자연스러운 획 순서를 전제로 하며 한 세션에서 여러 문자/단어 후보를
반환할 수 있지만 정확도는 언어와 필체에 따라 달라집니다.

## 언어와 모델

사용자가 Canvas 아래에서 선택한 canonical BCP 47 tag를
`DigitalInkRecognitionModelIdentifier.fromLanguageTag`에 전달합니다. SDK가 반환한 실제 model
tag를 UI에 표시합니다. 이 공식 resolver는 exact match와
문서화된 language/script/region 근사 규칙을 사용합니다.

- `ja`, `ko`, `en`은 같은 tag의 모델을 사용합니다.
- `pt-BR`, `en-GB`처럼 공식 region model이 있으면 유지합니다.
- `sr-Latn`은 공식 Latin-script Serbian model인 `sr-Latn-RS`로 resolve됩니다.
- 공식 모델 목록의 중국어는 `zh-Hani`와 region variant입니다. `zh-Hans`/`zh-Hant`는 SDK의
  문서화된 Han-script 매칭을 사용하며 앱이 임의로 Latin이나 다른 언어 모델로 바꾸지 않습니다.
- 파싱할 수 없거나 SDK가 모델을 반환하지 않는 tag는 unsupported 상태입니다.

raw Ink만 보고 언어를 자동 판정하지 않습니다. Digital Ink recognizer는 recognition 전에
language-specific model을 요구하고 Han/Latin처럼 script를 공유하는 언어가 있기 때문입니다.
언어 chip은 현재 editor 언어, 최근 선택 5개, 설치 모델, 저장된 vocabulary 언어 순으로 만들며
나머지는 공용 BCP 47 picker에서 선택합니다. 최근 선택만 별도 DataStore preference에 저장하고
Room/backup에는 넣지 않습니다. 다른 언어를 선택해도 editor의 source language는 바꾸지 않습니다.

모든 앱 언어를 미리 내려받지 않습니다. `RemoteModelManager`로 설치 여부를 확인하고 사용자가
누른 경우에만 해당 모델을 download합니다. 같은 경계가 향후 delete UI도 지원할 수 있도록
`check/download/delete`를 분리하지만 이번 화면에는 delete를 추가하지 않았습니다.

## UI와 상태

Word Editor headword와 vocabulary 검색 field의 action은 같은 modal 입력 UI를 엽니다. 고정 높이
Dialog에서 제목, 240dp Canvas와 undo/clear를 상단에 고정하고, 언어·상태·후보만 아래 영역에서
스크롤합니다. 따라서 후보나 model 상태가 바뀌어도 Canvas의 화면 좌표와 크기가 변하지 않습니다.
configuration/layout으로 실제 writing area가 바뀌면 기존 vector Ink를 새 local 좌표계 비율로
명시적으로 변환합니다.

Canvas는 model 선택 전에도 primary pointer 하나를 획으로 수집하고 추가 pointer는 무시합니다.
언어를 선택하면 같은 Ink로 model 확인과 인식을 시작하며, model download 중에도 Ink를 유지합니다.
마지막 획 취소와 전체 지우기를 제공하며, 준비된 model에서는 획 종료 350ms 뒤 자동 인식합니다.
pointer move마다 SDK를 호출하지 않습니다. 새 획, 언어 변경, undo, clear, close는 실행 중 인식을
취소하고 generation identity가 오래된 결과의 반영을 막습니다.

후보는 자동 입력되지 않습니다. Word Editor에서 후보 chip을 누르면 기존 headword가 비어 있을 때는
그 문자열이 되고, 텍스트가 있으면 뒤에 붙습니다. 현재 Word Editor가 `String`만 보존하고 cursor
selection을 상태로 관리하지 않으므로 안전한 append 정책을 사용합니다. 기존 전체 문자열을
묵시적으로 대체하지 않습니다. 선택 결과는 반드시 기존 `HeadwordChanged` action을 거쳐 사전
검색, 이전 session contribution 정리와 duplicate 검사를 재사용합니다. vocabulary 검색에서는
후보가 기존 query를 대체하고 기존 Room substring 검색 및 language/tag/wordbook filter를 그대로
사용합니다.

`is`, `are`, `was`가 외부 dictionary suggestion에서 `be`로 연결되지 않는 것은 현재 provider index가
normalized exact headword/key를 조회하고 morphology/lemma 경계를 제공하지 않기 때문입니다. 반면
저장된 vocabulary 검색은 headword/reading/meaning/example/notes의 substring 검색입니다. 이번 변경은
불규칙 동사 표를 하드코딩하지 않습니다. 후속 작업에서는 provider capability로 morphology를
표현하고, license와 크기를 검토한 Kaikki form index 또는 언어별 morphological provider가 원형
후보를 반환하도록 설계하는 편이 안전합니다.

획과 후보는 각 destination 범위의 공용 handwriting ViewModel에만 있습니다. configuration change
동안은 유지되지만 dialog를 닫거나 destination을 떠나면 지워집니다. process-death 복구나
handwriting history는 범위 밖이며
Room schema v6와 backup schema v5에는 어떤 stroke/model 상태도 추가하지 않았습니다.

## Offline 및 privacy

모델 다운로드 때문에 Manifest에 `INTERNET` permission이 있습니다. 모델이 설치된 뒤 handwriting
input과 recognition output은 기기에서 처리되며 앱은 획을 cloud handwriting/OCR/AI 서비스로
업로드하지 않습니다. ML Kit 공식 terms는 input과 output을 Google server에 보내지 않는다고
명시하는 동시에 SDK가 model/bug fix/hardware compatibility 정보를 받기 위해 server에 접속하고
API 성능·사용 지표를 Google에 전송할 수 있다고 설명합니다. 이 동작을 “완전한 무통신”으로
표현하지 않습니다.

모델이 없고 download가 실패해도 manual headword, meanings, notes, tags/wordbooks와 Save는 영향을
받지 않습니다. raw exception은 UI에 표시하지 않고 model/recognition 상태만 안내합니다.

## Manual QA

1. Test가 아닌 Manual AVD 또는 기기에 최신 debug APK를 설치합니다.
2. 첫 획을 쓴 뒤 후보/상태가 변해도 Canvas가 움직이지 않는지 확인합니다.
3. 같은 위치에 두 번째/세 번째 획을 이어 씁니다.
4. Canvas 아래에서 `zh-Hans`, `ja`, `ko`, `en`을 바꾸고 같은 Ink가 유지되는지 확인합니다.
5. 처음 사용하는 언어라면 model을 내려받고 기존 Ink로 후보가 나오는지 확인합니다.
6. 후보가 자동 입력되지 않고 tap한 후보만 기존 headword 뒤에 붙는지 확인합니다.
7. tap 후 기존 400ms dictionary suggestion이 갱신되고 과거 session import가 남지 않는지 확인합니다.
8. 홈 검색에서 손글씨를 열어 후보를 선택하고 query 교체와 언어 filter를 확인합니다.
9. 모델 설치 후 network를 끄고 인식이 계속 되는지 확인합니다.

자동 테스트는 fake service만 사용하며 실제 모델 download나 SDK network를 요구하지 않습니다.
