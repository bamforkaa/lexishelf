# ADR-0010: 외부 사전 링크는 content provider와 분리한다

## 상태

Accepted — 2026-08-23

## 결정

`ExternalDictionaryReferenceProvider`는 BCP 47 source language와 headword만 받아 검증된 공식 검색 URI를 만듭니다. 첫 구현은 NAVER Dictionary이며 사용자가 Word Editor 링크를 누른 경우에만 `ACTION_VIEW`를 보냅니다.

이 경계는 `DictionaryProvider`가 아닙니다. HTML/API/audio를 fetch, scrape, parse, cache, import하지 않고 NAVER를 vocabulary provenance로 자동 등록하지 않습니다. mapping이 없거나 URI가 http(s)가 아니거나 headword가 비면 링크를 만들지 않습니다.

## 결과

URL 변경은 NAVER 구현 한 곳에 격리되고 provider search/persistence/license 경계와 섞이지 않습니다. 외부 페이지를 참고한 뒤 사용자가 직접 쓴 내용은 user-authored data입니다. 링크 목적지는 서비스 변경 시 재검증해야 하며 background availability check는 추가하지 않습니다.
