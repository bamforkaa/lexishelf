# ADR-0009: 대용량 사전은 설치 가능한 generic pack으로 분리한다

## 상태

Accepted — 2026-08-23

## 결정

Base APK에는 provider code/descriptor와 작은 metadata만 두고 dataset payload는 generic `.dictpack`으로 설치합니다. provider data source는 asset이 아니라 `DictionaryPackResolver`에서 stable provider ID에 해당하는 active payload를 받습니다. pack은 `noBackupFilesDir/dictionary-packs`에 두며 user Room과 lifecycle을 공유하지 않습니다.

설치는 checksum과 payload validation을 통과한 candidate만 activation pointer로 전환하고 직전 version을 rollback 대상으로 보존합니다. provider별 downloader/installer는 만들지 않습니다. 현재 전달은 SAF local import이며 remote HTTPS catalog는 후속 범위입니다.

## 이유와 결과

새 provider가 늘어도 base APK가 dataset 크기의 합으로 증가하지 않고 zero/one/all pack 상태를 같은 코드로 처리할 수 있습니다. pack 삭제가 user vocabulary를 삭제하지 않으며 failed update도 active pack을 손상시키지 않습니다. 반면 최초 사용자가 pack 파일을 별도로 전달받아 설치해야 하고, 공개 배포 전에는 신뢰할 수 있는 signed catalog/signature 정책을 추가로 결정해야 합니다.

