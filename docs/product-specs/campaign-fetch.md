# 체험단 수집 스펙

## 플랫폼

| 플랫폼 | 소스 | 비고 |
|--------|------|------|
| 디너의여왕 | `dinnerqueen.net` taste_list AJAX | 릴스·클립 제외, 혜택 상세 병렬 조회 |
| 가보자 | `gaboja.com` | 대전 지역 필터 |
| 강남맛집 | `xn--939au0g4vj8sq.net` | `/cp/` + `_list_cmp_tpl.php` 페이지네이션 |

## 병합·캐시

- `CampaignService.fetchLiveAll()` — 3 fetcher 결과 병합
- `output/campaigns_cache.json` — TTL `studio.campaign-cache-ttl-minutes` (기본 30)
- `refresh=true` 또는 만료 시 `CampaignRefreshService` 비동기 갱신

## UI 필터

- 플랫폼: 전체 / 디너의여왕 / 가보자 / 강남맛집
- 지역·카테고리·정렬(마감순 등)
- 카드 → 블로그 작성 (`storeName`, `region`, `postType=협찬` 쿼리)

## 구현 위치

- `backend/.../campaign/repo/*Fetcher.java`
- `CampaignService`, `CampaignRefreshService`
- `frontend/src/pages/CampaignPage.tsx`
