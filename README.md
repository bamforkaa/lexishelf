<h1 align="center">LexiShelf</h1>

<p align="center">
  Android용 local-first 다국어 단어장과 오프라인 사전 도구
</p>

<p align="center">
  <a href="https://github.com/Bamfor/lexishelf/releases">Releases</a> ·
  <a href="#설치">설치</a> ·
  <a href="#기능">기능</a> ·
  <a href="#사전-pack">사전 데이터</a> ·
  <a href="PRIVACY.md">개인정보</a> ·
  <a href="#라이선스와-데이터-출처">라이선스</a>
</p>

<p align="center">
  <img alt="Android 6.0+" src="https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&amp;logoColor=white">
  <img alt="Version 0.1.0" src="https://img.shields.io/badge/version-0.1.0-4f46e5">
  <img alt="Local first" src="https://img.shields.io/badge/storage-local--first-334155">
  <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/license-MIT-0f766e"></a>
</p>

## 개요

LexiShelf는 단어와 표현을 직접 정리하고 기기 안에서 계속 사용할 수 있는 개인용 Android
단어장입니다. 여러 뜻, 예문, 메모, 태그와 단어장을 편집할 수 있고, 필요한 경우에만 검토된
사전 pack을 내려받아 입력을 보조합니다. 사전에 없는 표현도 항상 직접 저장할 수 있습니다.

사용자 단어장은 Room에 로컬로 저장됩니다. 계정, 프로젝트 운영 backend, cloud sync, 광고와
앱 자체 analytics가 없습니다. 사전 pack을 설치한 뒤 단어장과 로컬 사전 검색은 offline에서도
동작합니다.

## 기능

- 언어 태그가 있는 단어와 multi-word expression 저장
- 여러 뜻, 품사, 예문, reading, 발음, 문법 성, 메모와 즐겨찾기 편집
- 서로 독립적인 태그와 단어장, 로컬 검색과 필터
- 선택한 local dictionary 결과를 검토하고 수정한 뒤 저장
- 영어 활용형을 lemma로 찾는 morphology 보조 검색
- ML Kit의 기기 내 손글씨 인식과 언어 모델 선택 설치
- session-only Writing Practice
- versioned JSON backup 내보내기, 검증과 conflict policy가 있는 복원
- 사전 데이터가 하나도 없어도 동작하는 manual vocabulary workflow

단어와 구는 같은 `VocabularyEntry` 모델을 사용합니다. idiom, phrasal verb, collocation과 일반
phrase를 별도 entity 없이 저장하고, 필요하면 기존 태그로 분류할 수 있습니다.

## Screenshots

개인 단어와 식별 가능한 emulator 데이터가 없는 실제 화면을 검수한 뒤 추가합니다. 현재는 가짜
mockup이나 placeholder 이미지를 싣지 않습니다.

## 설치

공개 `v0.1.0`이 게시되면 다음 순서로 설치합니다.

1. [GitHub Releases](https://github.com/Bamfor/lexishelf/releases)에서
   `LexiShelf-v0.1.0.apk`를 받습니다.
2. APK를 연 브라우저 또는 파일 앱에 설치 권한을 허용합니다.
3. LexiShelf를 실행합니다.
4. **설정 → 사전 데이터**에서 자신의 언어와 목적에 맞는 pack만 내려받습니다.

APK에는 사전 DB나 ML Kit 언어 모델이 포함되지 않습니다. 일반 사용자는 converter,
`local.properties`, raw DB 또는 개발용 dictionary workspace를 준비할 필요가 없습니다.

공식 sideload build는 GitHub `v0.1.0` Release에 첨부된 `LexiShelf-v0.1.0.apk`뿐입니다.
소스에서 만든 unsigned archive나 제3자가 다시 서명한 APK는 공식 배포 APK가 아닙니다.

### 선택 사항: 다운로드 검증

Release의 `SHA256SUMS.txt`와 다운로드한 APK의 SHA-256을 비교하면 파일 무결성을 확인할 수
있습니다. [release 문서](docs/release.md)의 signing certificate fingerprint는 APK의 서명자
identity를 확인하기 위한 공개 정보입니다. Android는 같은 폴더의 checksum 파일을 자동으로
검증하지 않으므로 이 확인은 별도 도구를 사용하는 선택 절차입니다.

## 사전 pack

| 데이터 소스 | 적합한 용도 | 방향 | 선택 다운로드 |
| --- | --- | --- | --- |
| 한국어기초사전 | 수록 범위 안의 한국어 뜻 | 11개 지원 언어 ↔ 한국어 | 67,967,805 bytes |
| PanLex pinned snapshot | 한국어 lexical fallback | 선택 12개 언어 ↔ 한국어 | 72,462,030 bytes |
| CC-CEDICT | 중국어 병음과 상세 영어 gloss | 중국어 → 영어 | 3,971,412 bytes |
| Kaikki language packs | 품사·발음·활용형·예문이 포함될 수 있는 영어 fallback | 언어별 → 영어 | 언어별 독립 pack |
| English Morphology | `is → be`, `went → go` 같은 form 검색 | 영어 form → lemma | 29,322,956 bytes |

Kaikki는 `de`, `hi`, `pl`, `nl`, `pt`, `tr`, `cs`, `sv`, `uk`, `vi`, `th`, `id`를 각각
독립 pack으로 제공합니다. 600MB 이상의 aggregate bundle이나 “모두 설치” 기본 동작은 없습니다.
화면에서 다운로드 크기와 설치 후 예상 크기를 먼저 보여 줍니다.

한국어기초사전이나 PanLex를 설치해도 모든 단어에 한국어 뜻이 생기는 것은 아닙니다. dataset의
coverage는 제한적이고 PanLex는 완전한 dictionary가 아니라 폭넓은 lexical fallback입니다.

JMdict provider와 converter는 local/development 사용을 위해 유지되지만, EDRDG가 요구하는 정기
업데이트 절차를 공개 v1에서 맡지 않으므로 **JMdict pack은 public catalog와 GitHub Release에
포함하지 않습니다.**

정확한 pack version, archive/payload/source checksum, 설치 크기와 artifact별 배포 결정은
[public artifact audit](docs/public-dictionary-artifacts.md)과
[catalog JSON](distribution/dictionary-catalog-v1.json)에 있습니다. 사전 내용에는 각 데이터
소스의 별도 license가 적용됩니다.

## 개인정보

- 단어, 뜻, 예문, 메모, 태그와 단어장은 기기 안에 저장됩니다.
- 계정, 프로젝트 운영 backend와 앱 자체 analytics가 없습니다.
- 손글씨 stroke와 recognition candidate는 Room/backup에 저장되지 않고 인식은 기기에서
  수행됩니다.
- 네트워크는 catalog/pack 다운로드, ML Kit model/SDK 동작, 사용자가 누른 외부 NAVER 사전
  링크에 사용될 수 있습니다.
- 앱은 사용자 vocabulary를 업로드하지 않습니다.

ML Kit SDK가 수집할 수 있는 diagnostics/usage metadata와 GitHub/NAVER로 전달되는 정보까지
포함한 전체 고지는 [PRIVACY.md](PRIVACY.md)에 있습니다.

## 소스에서 빌드

필요한 환경은 최신 stable Android Studio, Android SDK 37, compatible JDK와 Android emulator
또는 USB device입니다. Gradle은 별도 설치하지 말고 repository의 Wrapper를 사용합니다.

```powershell
git clone https://github.com/Bamfor/lexishelf.git
cd lexishelf
$env:JAVA_HOME = '<ANDROID_STUDIO_INSTALL>\jbr'
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

macOS/Linux에서는 `./gradlew`를 사용합니다. Android SDK 위치는 gitignored
`local.properties`가 관리합니다. 사전 원본 재생성과 public release signing은 일반 build의
전제조건이 아닙니다. 전체 절차는 [setup](docs/setup.md), [signing](docs/signing.md),
[release checklist](docs/release-checklist.md)에 있습니다.

## 라이선스와 데이터 출처

Application source code is available under the [MIT License](LICENSE). 이 라이선스는 LexiShelf가
작성한 source code에 적용됩니다.

Dictionary content, 변환된 pack과 third-party software에는 각각의 별도 라이선스가 적용되며
MIT로 재라이선스되지 않습니다.
[NOTICE.md](NOTICE.md), [dictionary source review](docs/dictionary-sources.md),
[artifact audit](docs/public-dictionary-artifacts.md)를 확인하세요. 사전 데이터의 정확성이나 특정
목적 적합성은 보증하지 않습니다.

## 개발 문서

- [Architecture](docs/architecture.md)
- [Setup and environment](docs/setup.md)
- [Dictionary pack format and lifecycle](docs/dictionary-packs.md)
- [Dictionary sources and licenses](docs/dictionary-sources.md)
- [Backup format](docs/backup.md)
- [Handwriting](docs/handwriting.md)
- [Writing Practice](docs/writing-practice.md)
- [Release and signing](docs/release.md)
- [Architecture decisions](docs/decisions/)
