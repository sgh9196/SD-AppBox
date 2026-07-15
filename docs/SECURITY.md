# 보안

## API 키

| 키 | 설정 위치 | 용도 |
|----|-----------|------|
| Gemini | `application-local.yml` → `studio.gemini-api-key` | 원고 생성 |
| 공공데이터 | `application-local.yml` → `studio.public-api-key` | 매장 검색 |

설정 방법:

1. `backend/src/main/resources/application-local.yml.example` 복사
2. `application-local.yml` 로 이름 변경 후 키 입력
3. **커밋 금지** (`.gitignore` 대상)

## 네이버 세션

- `output/naver/session.json` — 로그인 쿠키 (로컬 전용, 커밋 금지)

## 테스트 모드

`studio.use-test-mode: true` — 외부 Gemini 호출 없이 mock 원고 사용 (CI·로컬 UI 테스트용).
