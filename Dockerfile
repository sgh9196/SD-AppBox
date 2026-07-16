# 1단계: Node를 이용한 프론트엔드 정적 빌드
FROM node:24-slim AS frontend-builder
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2단계: Maven을 이용한 백엔드 빌드 (프론트엔드 dist 포함)
FROM maven:3.8.5-openjdk-17 AS backend-builder
WORKDIR /app
# 메이븐 의존성 및 백엔드 소스 복사
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
COPY backend/checkstyle.xml backend/checkstyle.xml
COPY prompt prompt

# 1단계의 프론트엔드 빌드 결과물을 backend 빌드 리소스 경로로 복사
COPY --from=frontend-builder /app/frontend/dist /app/frontend/dist

# studio.jar 빌드 및 런타임 의존성 JAR들을 target/dependency 폴더에 추출
WORKDIR /app/backend
RUN mvn clean package -DskipTests && \
    mvn dependency:copy-dependencies -DoutputDirectory=target/dependency -DskipTests

# 3단계: 가상 화면(VNC) 및 실행 환경 구축
FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive

# 필수 GUI 패키지, Nginx 및 openjdk 설치
RUN apt-get update && apt-get install -y \
    openjdk-17-jre-headless \
    xvfb \
    x11vnc \
    fluxbox \
    novnc \
    websockify \
    nginx \
    curl \
    wget \
    gnupg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 빌드 결과물 및 임시 의존성 폴더 복사
COPY --from=backend-builder /app/backend/target/studio.jar app.jar
COPY --from=backend-builder /app/backend/target/dependency ./dependency
COPY --from=backend-builder /app/prompt ./prompt
COPY entrypoint.sh ./
COPY nginx.conf /etc/nginx/nginx.conf

# Windows 개행 코드(CRLF) 복구 및 실행 권한 부여
RUN sed -i 's/\r$//' entrypoint.sh && chmod +x entrypoint.sh

# 모든 라이브러리를 클래스패스에 바인딩하여 Playwright 및 Chromium 브라우저 완벽 설치
RUN java -cp "app.jar:dependency/*" com.microsoft.playwright.CLI install --with-deps chromium && \
    rm -rf dependency

# VNC 디스플레이 설정
ENV DISPLAY=:99
ENV RESOLUTION=1024x768x16

EXPOSE 8080
ENTRYPOINT ["./entrypoint.sh"]
