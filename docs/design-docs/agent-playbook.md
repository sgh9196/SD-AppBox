# Agent Playbook — 변경·검증 체크리스트

코드 또는 문서를 수정한 뒤 아래 순서로 확인한다.

---

## 필수 (매 변경)

- [ ] `scripts\verify.bat` 통과
- [ ] API 계약 변경 시 `docs/design-docs/api-contract.md` + `frontend/src/api/client.ts` 동기화
- [ ] 새 REST 엔드포인트는 `web` 패키지 Controller에만 추가

---

## 백엔드

- [ ] `repo`가 `service`/`web`에 의존하지 않음 (ArchUnit)
- [ ] 공공 API URL에 `cond[BPLC_NM::LIKE]` 등 대괄호 파라미터 — `StoreRepository` 방식 유지
- [ ] 체험단 HTML은 UTF-8 / MS949 폴백 디코딩 (`CampaignHttpClient`)
- [ ] `application-local.yml` API 키 커밋 금지

---

## 프론트엔드

- [ ] `pages/`에서 `fetch` 직접 호출 금지 → `api/client.ts`
- [ ] 새 페이지는 `App.tsx` 라우트 + `SpaForwardController` 경로 추가

---

## 문서만 변경한 경우

- [ ] `AGENTS.md` 인덱스 링크 유효
- [ ] README 중복 서술 최소화 (상세는 docs/에)

---

## 수동 스모크 (기능 변경 시)

1. `run.bat` → http://localhost:8080
2. 체험단 새로고침·필터
3. 블로그: 매장 검색 → 원고 Job → DOCX
4. 공공데이터: 매장 검색

`use-test-mode: true`면 Gemini 없이 테스트 가능.
