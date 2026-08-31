# Third-party software notices

Dictionary **data** license는 [dictionary sources](dictionary-sources.md)에서 별도로 관리한다. 이
문서는 base app의 software dependency 감사용이며 license 원문을 대체하지 않는다. 정확한 resolved
전이 dependency는 Gradle release runtime classpath와 release AAB의
`BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb`를 기준으로 재확인한다.

| Software group | 이 앱의 직접 사용 | license/terms reference |
| --- | --- | --- |
| AndroidX, Compose, Material 3, Room, Navigation, DataStore, Lifecycle, Hilt AndroidX integration | Android UI, persistence, state/navigation/background support | Android Open Source/AndroidX의 각 artifact POM 및 포함된 Apache 2.0 notice |
| Dagger/Hilt | dependency injection | [Dagger repository license](https://github.com/google/dagger/blob/master/LICENSE.txt) |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | language/runtime, Flow/coroutines, JSON | 각 JetBrains repository의 Apache 2.0 license |
| OkHttp 5.5.0 and Okio | HTTPS catalog/pack transport and streaming I/O | [OkHttp repository license](https://github.com/square/okhttp/blob/master/LICENSE.txt) and artifact POM notices (Apache 2.0) |
| Google ML Kit Digital Ink Recognition 19.0.0 | on-device handwriting recognition와 on-demand language model | [ML Kit Terms & Privacy](https://developers.google.com/ml-kit/terms) 및 Google APIs terms |

ML Kit는 일반 OSS library와 동일한 한 줄 license로 간주하지 않는다. SDK terms는 input/result가
기기에서 처리된다고 설명하지만, model/compatibility update를 위해 server에 접속할 수 있고
diagnostics/usage analytics metadata 수집 고지를 요구한다. 공개 배포자는
[ML Kit Android data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)를
현재 SDK version과 app 구성에 맞춰 검토해야 한다.

release APK에는 여러 AndroidX artifact의 `META-INF/**/LICENSE.txt`가 포함된다. Gradle packaging은
중복 충돌을 피하기 위해 top-level `META-INF/AL2.0`과 `META-INF/LGPL2.1`만 제외한다. 이 repository
문서는 dependency inventory를 사람이 찾기 쉽게 만들기 위한 보완이며, 공개 배포 시 선택한
store/package 방식에 맞는 완전한 generated open-source notices가 필요한지는 별도로 검토한다.
