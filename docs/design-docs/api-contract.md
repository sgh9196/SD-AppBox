# API 계약

Base URL: `http://localhost:8080` (프로덕션 빌드·개발 프록시 동일 prefix `/api`)

프론트는 `frontend/src/api/client.ts`만 사용한다.

---

## Campaigns `/api/campaigns`

| Method | Path | 설명 |
|--------|------|------|
| GET | `/sorted` | 페이징 목록 (`refresh`, `sortBy`, `platform`, `region`, `page`, `size`) |
| GET | `/regions` | 캐시 기준 지역 목록 |
| GET | `/cache-age` | `{ label }` 캐시 시각 |
| POST | `/refresh` | 비동기 전체 수집 시작 |
| GET | `/refresh/preview` | 수집 중 미리보기 |
| GET | `/refresh/status` | 수집 상태 |
| GET | `/refresh/logs` | 수집 로그 (`after` 커서) |

### CampaignCard (요약)

`platform`, `campaignId`, `title`, `storeName`, `region`, `district`, `channel`, `benefit`, `applied`, `recruit`, `deadline`, `thumbnailUrl`, `detailUrl`, `competition`

---

## Stores `/api/stores`

| Method | Path | 설명 |
|--------|------|------|
| GET | `/search` | `storeName`, `region` → 공공 API 후보 배열 |
| GET | `/info` | 매장 상세 텍스트 (`bplcNm`, `roadNmAddr` 선택 시 정확도 향상) |
| GET | `/hours` | 영업시간 |
| GET | `/map-link` | 네이버 지도 URL |

---

## Blog `/api/blog`

| Method | Path | 설명 |
|--------|------|------|
| POST | `/generate` | multipart: `storeName`, `region`, `postType`, `rating`, `infoText`, `link`, `campaignGuideline`, 사진 필드 |
| GET | `/jobs/{id}` | Job 상태·메시지·`downloadUrl` |
| GET | `/jobs/{id}/download` | DOCX 스트림 |

Job 상태: `PENDING` → `RUNNING` → `COMPLETED` / `FAILED`

---

## Naver `/api/naver`

| Method | Path | 설명 |
|--------|------|------|
| GET | `/session` | 로그인 세션 상태 |
| POST | `/login/browser` | Playwright 브라우저 로그인 |
| POST | `/logout` | 세션 삭제 |
| POST | `/publish` | `{ blogJobId }` 발행 Job 시작 |
| GET | `/jobs/{id}` | 발행 Job 상태 |
| GET | `/jobs/{id}/logs` | 발행 로그 |

---

## Auth `/api/auth`

| Method | Path | 설명 |
|--------|------|------|
| POST | `/verify` | `code` → 보안 코드 유효성 검사 및 허용된 앱 목록 반환 |
| POST | `/issue` | `{ adminCode, newCode, allowedApps }` → 신규 보안 코드 발급 (관리자 전용) |
| GET | `/codes` | `adminCode` → 현재 발급된 활성 보안 코드 전체 조회 (관리자 전용) |
| DELETE | `/codes/{code}` | `{ code }`, `adminCode` → 특정 보안 코드 파기 및 권한 차단 (관리자 전용) |

---

## SPA 라우트 (비 API)

`GET /`, `/campaign`, `/blog` → `index.html` forward

---

## 오류

- 4xx/5xx — 프론트는 `res.ok` 검사 후 `Error` throw
- 공공 API 쿼리 파라미터 `cond[...]` 는 URL 수동 조립 필수 (Spring UriComponentsBuilder 사용 금지)
