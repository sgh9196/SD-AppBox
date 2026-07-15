# 글로벌 (Geul-o-bel) — Agent 진입 지도

**역할:** 레포 **진입 지도**. 모듈·코드 위치 + **어떤 문서가 무엇을 담는지** 안내만 한다. 규칙·빌드·API **본문은 각 문서에**.

---

## 문서 인덱스

| 필요한 것 | 문서 |
|-----------|------|
| 시스템·패키지·레이어 구조 | [ARCHITECTURE.md](ARCHITECTURE.md) |
| 제품 흐름·기능 스펙 | [docs/DESIGN.md](docs/DESIGN.md) |
| REST API 계약 | [docs/design-docs/api-contract.md](docs/design-docs/api-contract.md) |
| 변경·PR 전 체크 | [docs/design-docs/agent-playbook.md](docs/design-docs/agent-playbook.md) |
| API 키·보안 | [docs/SECURITY.md](docs/SECURITY.md) |
| 빠른 시작 | [README.md](README.md) |

---

## 프로젝트 범위

| 디렉터리 | 역할 |
|----------|------|
| `backend/` | Spring Boot 3.2 · REST API · 체험단 스크래핑 · Gemini 원고 · DOCX · 네이버 발행 |
| `frontend/` | React 19 + Vite 8 SPA (`/campaign`, `/blog`, `/public`) |
| `prompt/` | Gemini 프롬프트 템플릿 (`prompt.txt` 등) |
| `output/` | 캐시·업로드·DOCX·네이버 세션 (런타임 산출물) |
| `setup/` | Java 17 / Node 24 환경 설치 |
| `scripts/` | 하네스 검증 (`verify.bat`) |

---

## 어디를 수정할까

| 변경 내용 | 주 수정 위치 |
|----------|-------------|
| 체험단 수집·필터 | `backend/.../blog/campaign/repo/*Fetcher.java`, `CampaignService` |
| 블로그 원고·Gemini | `ContentService`, `PromptRepository`, `prompt/` |
| Word보내기 | `ExportService` |
| 공공 API 매장 | `StoreRepository`, `StoreService` |
| REST 엔드포인트 | `backend/.../blog/.../web/*Controller`, `naver/web/NaverController` |
| UI 화면 | `frontend/src/blog/pages/*`, `frontend/src/app-box/pages/*` |
| API 호출 규칙 | `frontend/src/api/client.ts` (pages는 fetch 직접 사용 금지) |

---

## 하네스 검증 (필수)

코드·문서 변경 후 반드시 실행:

```bat
scripts\verify.bat
```

| 단계 | 내용 |
|------|------|
| 1 | Frontend ESLint |
| 2 | Frontend Vitest (import smoke) |
| 3 | Backend Checkstyle + JUnit + ArchUnit |
| 4 | Frontend production build |

실패 시 해당 단계 로그를 수정 후 재실행한다.

---

## 진입점

| 용도 | 경로 |
|------|------|
| Spring Boot | `backend/.../HubApplication.java` |
| SPA 라우팅 | `frontend/src/App.tsx`, `SpaForwardController` |
| 설정 | `backend/src/main/resources/application.yml`, `application-local.yml` |
| 실행 | `setup\setup.bat` → `run.bat` |
