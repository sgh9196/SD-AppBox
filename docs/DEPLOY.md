# 블로그 스튜디오 클라우드 배포 가이드 (noVNC 가상 브라우저 탑재)

이 가이드는 로컬 윈도우 환경과 100% 동일하게 작동하는 **noVNC 원격 브라우저** 기반의 클라우드 배포 절차를 안내합니다. 이 방식을 통해 네이버 로그인 2차 인증, 캡차, 그리고 임시저장 후 최종 발행 검수 단계를 클라우드 환경에서도 완벽하게 수행할 수 있습니다.

---

## 🏗️ 전체 아키텍처 개요

단일 컨테이너(Docker) 내에 Java 백엔드, React 프론트엔드, 그리고 가상 디스플레이 인프라(Xvfb, VNC, websockify, Nginx)를 함께 패키징하여 배포합니다.

* **통합 포트 (`8080`)**:
  * 내부의 Nginx가 포트 `8080`을 점유하여 외부 트래픽을 단일 통로로 받습니다.
  * 일반 웹 접속 및 API 요청은 Spring Boot 백엔드(`8081`)로 프록싱합니다.
  * 원격 화면 중계 요청은 noVNC 웹소켓 서버(`6080`)로 웹소켓 프록싱을 진행합니다.
* **가상 화면**: 컨테이너가 켜지면 백그라운드에 가상 모니터(`Xvfb`)가 켜지고, Playwright는 이 가상 화면 위에 크롬 브라우저를 GUI 모드(`headless=false`)로 실행합니다.

---

## ☁️ 플랫폼 선택 가이드

noVNC 스트리밍은 실시간 네트워크 연결을 유지해야 하므로, 서버가 잠들지 않는 환경이 적합합니다.

### 옵션 1. Render (Web Service) — 가장 추천 (배포가 제일 간단함)
* **비용**: 무료 또는 유료 (월 $7 이상 권장)
* **특징**: GitHub 저장소를 연결해 두면 푸시할 때마다 자동으로 빌드되고 배포됩니다.
* **유의사항**: 무료 플랜을 사용하면 15분 미사용 시 서버가 휴면 상태로 변합니다. 다시 접속할 때 깨어나는 데 1분 가량 소요되므로, 쾌적한 다중 사용자 환경을 위해 **월 $7 유료 플랜** 사용을 권장합니다.

### 옵션 2. Google Cloud Run (GCP)
* **비용**: 사용한 리소스 만큼만 과금 (무료 할당량 존재)
* **유의사항**: 기본 서버리스 옵션에서는 미접속 시 CPU가 비활성화되므로, VNC 세션 유지를 위해 배포 시 `--no-cpu-throttling` (CPU 상시 할당) 옵션을 반드시 켜 주어야 합니다.

---

## 🚀 플랫폼별 상세 배포 단계

### 1️⃣ Render로 배포하기
1. **Render 가입 및 연동**: [Render Dashboard](https://dashboard.render.com)에 로그인 후, GitHub 계정을 연결합니다.
2. **Web Service 생성**: 
   * **New +** ➡️ **Web Service**를 클릭합니다.
   * 본 프로젝트의 GitHub 리포지토리를 선택합니다.
3. **설정값 기입**:
   * **Name**: `blog-studio` (자유롭게 입력)
   * **Region**: `Singapore` 또는 `Oregon` (사용자와 가까운 리전 선택)
   * **Runtime**: **`Docker`** 🌟 (반드시 Docker로 지정해야 도커파일을 빌드합니다)
   * **Instance Type**: `Starter` (월 $7 플랜, RAM 512MB 이상 필요) 또는 `Free`
4. **환경 변수(Environment Variables) 추가**:
   * `GEMINI_API_KEY`: 본인의 구글 Gemini API Key 입력
   * `SPRING_PROFILES_ACTIVE`: `prod`
   * `RESOLUTION`: `1280x800x24`
5. **배포 시작**: 맨 아래 **Create Web Service**를 누릅니다. 약 5~10분 후 Docker 빌드가 완료되고 주소(`https://blog-studio.onrender.com`)가 발급됩니다.

---

### 2️⃣ GCP Cloud Run으로 배포하기
1. **GCP 프로젝트 구성**:
   ```bash
   gcloud auth login
   gcloud config set project <YOUR_GCP_PROJECT_ID>
   gcloud services enable run.googleapis.com artifactregistry.googleapis.com
   ```
2. **배포 명령어 실행**:
   프로젝트 루트 디렉토리에서 아래 명령을 실행합니다.
   ```bash
   gcloud run deploy blog-studio \
     --source . \
     --region asia-northeast3 \
     --allow-unauthenticated \
     --port 8080 \
     --cpu 2 --memory 2Gi \
     --no-cpu-throttling \
     --set-env-vars="GEMINI_API_KEY=<Gemini_API_키>,SPRING_PROFILES_ACTIVE=prod,RESOLUTION=1280x800x24"
   ```
   * `--no-cpu-throttling`: VNC 세션 유지를 위해 웹 요청이 진행 중이지 않을 때도 컨테이너에 CPU를 계속 할당하게 만듭니다.

---

## 👁️ VNC 원격 화면 접속 및 발행 사용법

배포가 정상적으로 완료되면 다음 단계에 따라 네이버 발행을 테스트합니다.

1. **원격 화면 모니터링**:
   * 사용 중인 배포 주소 뒤에 `/novnc/` 경로를 붙여 접속합니다.
   * 예: `https://blog-studio.onrender.com/novnc/`
   * 화면 중앙의 **Connect**를 누르면 서버 내부의 빈 가상 바탕화면(Fluxbox)이 나타납니다.
2. **네이버 로그인 진행**:
   * 웹 포털 서비스에서 원고 작성을 마친 뒤, **"네이버 로그인 및 발행"** 버튼을 실행합니다.
   * 원격 화면(noVNC)으로 전환하면, 자동으로 크롬 브라우저가 실행되며 네이버 로그인 화면이 뜹니다.
   * 이 화면에 마우스 클릭 및 키보드 입력을 통해 **네이버 아이디/비밀번호를 입력하고 로그인**합니다.
   * **2차 인증번호 입력(OTP) 혹은 캡차 문자가 나타나면 직접 화면 상에서 클릭 및 입력해 해제합니다.**
3. **자동 글쓰기 감시**:
   * 로그인이 성공하면 Playwright가 자동으로 네이버 블로그 스마트에디터 글쓰기 창으로 진입합니다.
   * 원격 화면을 통해 제목과 내용, 사진 마커들이 자동으로 주입(타이핑)되는 과정을 실시간으로 볼 수 있습니다.
4. **최종 발행**:
   * 본문 채워넣기가 완료되면 임시 저장 상태로 화면이 정지합니다.
   * 사용자는 원격 화면의 네이버 에디터에서 글을 검수한 뒤, 상단의 **발행** 버튼을 직접 클릭하여 포스팅을 완전히 마칩니다.
