#!/bin/bash
# 1. 포트 치환 (Render / Cloud Run의 동적 포트에 대응)
PORT_TO_USE=${PORT:-8080}
sed -i "s/listen 8080;/listen ${PORT_TO_USE};/g" /etc/nginx/nginx.conf

# 2. 가상 디스플레이 Xvfb 시작
Xvfb :99 -screen 0 $RESOLUTION -ac +extension RANDR &
sleep 2

# 3. fluxbox 창 관리자 기동
fluxbox &

# 4. x11vnc 시작 (비밀번호 없음, localhost 리슨)
x11vnc -display :99 -nopw -listen localhost -forever -shared &

# 5. websockify 및 noVNC 실행
websockify --web /usr/share/novnc 6080 localhost:5900 &

# 6. Nginx 리버스 프록시 시작
nginx -c /etc/nginx/nginx.conf &

# 7. Spring Boot 백엔드 어플리케이션 실행 (포트 8081로 기동)
java -Dserver.port=8081 -jar app.jar
