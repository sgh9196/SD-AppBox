# 블로그 작성·발행 스펙

## 입력

- 매장명·지역·포스팅 유형(협찬/일반 등)·평점(1–5)
- 공공 API `infoText`, 네이버 지도 `link`
- 사진: `external`, `interior`, `menu`, `product` (multipart)
- (선택) `campaignGuideline` — 체험단 가이드라인 텍스트

## 처리 흐름

1. `POST /api/blog/generate` → `jobId`
2. `BlogJobRunner`: 사진 저장 → `ContentService.generateBlogPost` → `ExportService.createDocx`
3. `GET /api/blog/jobs/{id}` 폴링 → `COMPLETED` 시 `downloadUrl`

## 프롬프트

- `prompt/prompt.txt` — 변수: `{restaurant_name}`, `{info_text}`, `{campaign_guideline}` 등
- `use-test-mode: true` → `output/mock_response.txt` 또는 템플릿 원고

## 네이버 발행 (선택)

1. `POST /api/naver/login/browser` — Playwright 로그인
2. `POST /api/naver/publish` — `{ blogJobId }`
3. Job·로그 폴링

## 구현 위치

- `BlogController`, `BlogJobService`, `BlogJobRunner`
- `ContentService`, `ExportService`, `PromptRepository`
- `frontend/src/pages/BlogPage.tsx`, `NaverPublishPanel.tsx`

## 보안 및 인증 관리

- **인증 방식**: 하드코딩 패스워드 방식을 탈피하고 동적 발급된 암호화 코드로 권한을 통제합니다.
- **관리자 검증**: `admin-ghShin` 전용 코드로 인증 시 새로운 보안 코드 발급 및 활성 코드 삭제(파기)를 즉시 반영합니다.
- **저장소**: 서버 측 로컬의 `${studio.root-dir}/output/codes.enc` 파일에 AES-128 알고리즘으로 암호화하여 사용자 코드를 보관합니다.
- **클라이언트 가드**: 사용자가 보유한 허용 권한 리스트(`allowedApps`)에 따라 라우팅 진입과 사이드바 가시성을 강제 제어합니다.

