# 아키텍처 — 글로벌 (Geul-o-bel) Portal Hub

Java/Spring Boot 백엔드 + React SPA 프론트엔드. 단일 JAR(`studio.jar`)에 정적 파일을 포함해 `http://localhost:8080`에서 서빙.

포털 허브(Portal Hub) 아키텍처를 기반으로 설계되어 각 서비스 앱(`blog`, `marketing`, `influencer` 등)이 최상위 패키지 수준에서 독립적으로 격리 및 다중 확장될 수 있는 구조입니다.

---

## 레이어 규칙 (ArchUnit 검증)

```
com.sanaiddalgi.hub.<app>.<layer>
```

```
web (Controller)  →  service  →  repo
                      ↓
                   model / config
```

| 레이어 | 패키지 패턴 | 역할 |
|--------|--------|------|
| web | `com.sanaiddalgi.hub..web` | REST API, SPA 포워딩 및 비동기 Job 컨트롤러 |
| service | `com.sanaiddalgi.hub..service` | 비즈니스 로직, 오케스트레이션 및 도메인 기능 처리 |
| repo | `com.sanaiddalgi.hub..repo` | 외부 연동(Gemini API, 공공데이터 REST, HTTP 스크래퍼) 및 데이터 접근 |
| model | `com.sanaiddalgi.hub..model` | DTO, 엔티티 및 도메인 값 객체 |
| config | `com.sanaiddalgi.hub.config` | 글로벌 속성(`StudioProperties`), CORS 및 빈 설정 |

**금지 규칙:** `repo` → `service` / `web` 의존 금지, `service` → `web` 의존 금지. 타 개별 앱 간 직접적인 자바 수준 의존 관계 배제.

---

## 서비스 앱 구조

### 1. 블로그 마케팅 앱 (`com.sanaiddalgi.hub.blog`)

블로그 마케팅과 관련된 아래의 세부 기능 모듈들로 일원화되어 구성되어 있습니다.

#### campaign — 체험단
- **Fetcher:** `DinnerqueenFetcher`, `GabojaFetcher`, `GangnamMatzipFetcher`
- **병합·캐시:** `CampaignService` → `output/campaigns_cache.json`
- **비동기 갱신:** `CampaignRefreshService` + `CampaignRefreshRunner`
- **API:** `CampaignController` (`/api/campaigns/*`)

#### store — 공공데이터 매장
- `StoreRepository` — 공공데이터포털 REST 연동 (쿼리 파라미터 LIKE 수동 인코딩)
- `StoreController` (`/api/stores/*`)

#### blog (Core) — 블로그 원고 작성
- `ContentService` — Gemini API 호출(사진 멀티모달 처리) 또는 테스트 모드
- `PromptRepository` — `prompt/prompt.txt` 템플릿 변수 치환
- `ExportService` — 사진 마커 추출 및 DOCX 포맷팅 다운로드 (`output/preview.docx`)
- `BlogController` — 원고 생성 요청 및 `BlogJob` 비동기 처리

#### naver — 네이버 블로그 자동 발행
- Playwright 기반 `NaverBlogAutomation` 임시 저장 자동화
- 세션 파일: `output/naver/session.json`
- `NaverController` (`/api/naver/*`)

### 2. 마케팅 성과 분석 앱 (`com.sanaiddalgi.hub.marketing`) - 향후 추가 예정

### 3. 인플루언서 랭킹 앱 (`com.sanaiddalgi.hub.influencer`) - 향후 추가 예정

---

## 빌드·배포 흐름

```
frontend npm run build  →  frontend/dist/
        ↓
mvnw package (copy-resources)  →  BOOT-INF/classes/static/
        ↓
studio.jar  →  java -jar (run.bat)
```

---

## 설정 핵심 (`studio.*`)

| 키 | 설명 |
|----|------|
| `gemini-api-key` | Gemini API 키 (local 프로필) |
| `public-api-key` | 공공데이터포털 API 키 |
| `use-test-mode` | true 설정 시 Gemini 호출 생략 후 Mock 원고 반환 |
| `campaign-cache-file` | 체험단 JSON 캐시 경로 |
| `prompt-file` | 블로그 프롬프트 템플릿 파일 경로 |

상세 설정: `application.yml`, `application-local.yml`

---

## 관련 문서
- [docs/design-docs/api-contract.md](docs/design-docs/api-contract.md)
- [docs/DESIGN.md](docs/DESIGN.md)
- [AGENTS.md](AGENTS.md)
