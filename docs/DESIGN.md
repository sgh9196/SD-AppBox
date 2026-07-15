# 설계 개요

글로벌 (Geul-o-bel)은 **대전·전국 체험단 탐색**, **공공데이터 기반 매장 정보 조회**, **Gemini 블로그 원고 생성**, **Word 미리보기**, **네이버 블로그 발행**을 한 웹 UI에서 처리한다.

---

## 사용자 흐름

1. **체험단** — 플랫폼·지역·카테고리 필터 → 카드 선택 → 블로그 작성으로 이동
2. **블로그 (글로벌)** — 매장명·지역 → 공공 API 실시간 조회 및 지도 연동 → 사진 업로드 → 원고 생성 → DOCX 다운로드 → (선택) 네이버 발행
3. **보안 인증** — 보안 코드를 통한 서비스별 접근 제어 및 관리자의 동적 권한 발급/파기 대시보드 운영

---

## 플랫폼 (체험단)

| 플랫폼 | Fetcher |
|--------|---------|
| 디너의여왕 | `DinnerqueenFetcher` |
| 가보자 | `GabojaFetcher` |
| 강남맛집 | `GangnamMatzipFetcher` |

수집 결과는 JSON 캐시에 병합되며, TTL(기본 30분) 내 재사용한다.

---

## 사진 마커 규칙

원고 본문에 `[external_1]`, `[interior_2]` 형태 마커 → DOCX 생성 시 해당 카테고리 사진 삽입.

카테고리: `external`, `interior`, `menu`, `product`

---

## 상세 스펙

- [product-specs/index.md](product-specs/index.md)
- [product-specs/campaign-fetch.md](product-specs/campaign-fetch.md)
- [product-specs/blog-posting-flow.md](product-specs/blog-posting-flow.md)
- [product-specs/web-app-flow.md](product-specs/web-app-flow.md)
- [design-docs/api-contract.md](design-docs/api-contract.md)

---

## 검증

```bat
scripts\verify.bat
```

[design-docs/agent-playbook.md](design-docs/agent-playbook.md) 참고.
