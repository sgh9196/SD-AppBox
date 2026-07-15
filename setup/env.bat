@echo off
REM 공통 환경 해석 — setup / verify / run 에서 call
REM 반환: 0=OK, 1=Java 문제, 2=Node 문제
REM 호출 전: setlocal EnableDelayedExpansion

set "JAVA_TARGET=C:\Java17"
set "NODE_TARGET=C:\Node24"
set "JAVA_HOME="
set "NODE_HOME="
set "NODE_VER="

if exist "%JAVA_TARGET%\bin\java.exe" (
    set "JAVA_HOME=%JAVA_TARGET%"
    goto :check_java
)
for /f "delims=" %%J in ('where java 2^>nul') do (
    for %%I in ("%%~dpJ..") do set "JAVA_HOME=%%~fI"
    goto :check_java
)
exit /b 1

:check_java
"%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr "17. 18. 19. 21. 22. 23. 24. 25." >nul 2>&1
if errorlevel 1 exit /b 1

if exist "%NODE_TARGET%\node.exe" (
    set "NODE_HOME=%NODE_TARGET%\"
    goto :check_node
)
for /f "delims=" %%N in ('where node 2^>nul') do (
    set "NODE_HOME=%%~dpN"
    goto :check_node
)
exit /b 2

:check_node
"%NODE_HOME%node.exe" -v 2>&1 | findstr "v24." >nul 2>&1
if errorlevel 1 exit /b 2
for /f "delims=" %%V in ('"%NODE_HOME%node.exe" -v 2^>nul') do set "NODE_VER=%%V"

set "PATH=%JAVA_HOME%\bin;%NODE_HOME%;%PATH%"
exit /b 0
