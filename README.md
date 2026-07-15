# 글로벌 (Geul-o-bel) (Java/Spring/React)

대전 체험단 검색 · 블로그 원고 작성 · 공공데이터 확인

## 스택

| 영역 | 버전 |
|------|------|
| Java | 17 |
| Spring Boot | 3.2.2 |
| Node | 24.16.0 |
| Vite | 8.0.12 |
| React Router | v7 |

## 빠른 시작

```bat
setup\setup.bat   # 없는 항목만 설치/빌드 (이미 있으면 건너뜀)
run.bat
```

브라우저: http://localhost:8080

### setup.bat 확인 순서

| 항목 | 있으면 | 없으면 |
|------|--------|--------|
| Java 17 | `[건너뜀]` | C:\Java17 또는 PATH에서 탐색 → 없으면 Adoptium JDK 설치 |
| Node v24 | `[건너뜀]` | C:\Node24 또는 PATH에서 탐색 → 없으면 공식 zip 설치 |
| Maven Wrapper | `[건너뜀]` | `mvn wrapper:wrapper` |
| frontend node_modules | `[건너뜀]` | `npm ci` |
| backend studio.jar | `[건너뜀]` | frontend 빌드 + `mvnw package` |

## 개발 모드

```bat
cd backend && mvnw.cmd spring-boot:run
cd frontend && npm run dev
```

프론트: http://localhost:5173 (API는 8080으로 프록시)

## 하네스 검증

```bat
scripts\verify.bat
```

변경 후 반드시 실행. 상세: [AGENTS.md](AGENTS.md)

## API 키

`backend/src/main/resources/application-local.yml.example` 를
`application-local.yml` 로 복사 후 키 입력.

## 동등성 체크리스트 (Python 대비)

- [ ] 체험단 3플랫폼 목록 표시
- [ ] 체험단 → 블로그 매장명 자동 입력
- [ ] 공공데이터 매장 검색
- [ ] 사진 업로드 + Gemini 원고 + preview.docx 다운로드
- [ ] `scripts\verify.bat` 통과

## 문서

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [docs/DESIGN.md](docs/DESIGN.md)
- [docs/design-docs/api-contract.md](docs/design-docs/api-contract.md)
