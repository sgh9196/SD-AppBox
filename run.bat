@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "ROOT=%~dp0"
set "JAR=%ROOT%backend\target\studio.jar"

call "%ROOT%setup\env.bat"
set "ENV_ERR=%errorlevel%"
if "%ENV_ERR%"=="1" (
    echo [ERROR] Java 17 not found. Run setup\setup.bat
    pause
    exit /b 1
)
if "%ENV_ERR%"=="2" (
    echo [ERROR] Node v24 not found. Run setup\setup.bat
    pause
    exit /b 1
)

echo ============================================================
echo   Blog Studio
echo   http://localhost:8080
echo ============================================================
echo.

set "PORT=8080"

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%PORT%" ^| findstr "LISTENING"') do (
    echo [STOP] PID %%a
    taskkill /F /PID %%a >nul 2>&1
)

rem JAR 파일 핸들이 완전히 해제될 때까지 최대 15초 대기
if exist "%JAR%" (
    set "WAIT=0"
    :wait_jar
    del /f "%JAR%" >nul 2>&1
    if exist "%JAR%" (
        if !WAIT! LSS 15 (
            ping 127.0.0.1 -n 2 >nul
            set /a WAIT+=1
            goto :wait_jar
        ) else (
            echo [WARN] studio.jar still locked after 15s, attempting force delete...
        )
    )
)

echo [BUILD] Frontend...
pushd "%ROOT%frontend"
call npm run build
set "FE_ERR=!errorlevel!"
popd
if not "!FE_ERR!"=="0" (
    echo [ERROR] Frontend build failed. Run setup\setup.bat
    pause
    exit /b 1
)

echo [BUILD] Backend clean package...
pushd "%ROOT%backend"
if exist "mvnw.cmd" (
    call mvnw.cmd -q -DskipTests clean package
) else (
    call mvn -q -DskipTests clean package
)
set "BE_ERR=!errorlevel!"
if not "!BE_ERR!"=="0" (
    echo [WARN] clean package failed, retry package only...
    if exist "mvnw.cmd" (
        call mvnw.cmd -q -DskipTests package
    ) else (
        call mvn -q -DskipTests package
    )
    set "BE_ERR=!errorlevel!"
)
popd
if not "!BE_ERR!"=="0" (
    echo [ERROR] Backend build failed. Stop any running studio.jar and retry.
    pause
    exit /b 1
)

if not exist "%JAR%" (
    echo [ERROR] studio.jar not found: %JAR%
    pause
    exit /b 1
)

echo [START] Server running. Press Ctrl+C to stop.
echo.
"%JAVA_HOME%\bin\java.exe" -jar "%JAR%"
