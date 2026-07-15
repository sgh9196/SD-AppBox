# 웹 앱 흐름

## 실행

```bat
setup\setup.bat   # 최초 1회
run.bat           # 빌드 + http://localhost:8080
```

개발: backend `mvnw spring-boot:run` + frontend `npm run dev` (:5173)

## 라우팅

| URL | 컴포넌트 | 기능 |
|-----|----------|------|
| `/` | AppBoxPage | SD-App Box 허브 (보안 인증 및 관리자 대시보드) |
| `/campaign` | CampaignPage | 체험단 정보 탐색 및 캐시 관리 |
| `/blog` | BlogPage | 블로그 매장 리뷰 원고 작성 및 발행 |

`RegionContext` — 지역 쿼리 파라미터 공유

## API 레이어

- `frontend/src/api/client.ts` — 모든 REST 호출
- Vite dev proxy: `/api` → `:8080`

## 정적 배포

`frontend/dist` → Maven `copy-resources` → JAR `static/`
