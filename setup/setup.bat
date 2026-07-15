@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "ROOT=%~dp0.."

echo ============================================================
echo   사나이 딸기 Studio - 환경 설정
echo   (이미 설치된 항목은 건너뜁니다)
echo ============================================================
echo.

REM --- Java 17 ---
call "%~dp0env.bat"
set "ENV_ERR=%errorlevel%"
if %ENV_ERR%==1 (
    echo [설치] Java 17 — 없음
    call :install_java
    if errorlevel 1 goto :fail
    call "%~dp0env.bat"
    set "ENV_ERR=%errorlevel%"
    if %ENV_ERR% neq 0 goto :fail
) else if %ENV_ERR%==2 (
    goto :need_node_only
) else (
    echo [건너뜀] Java — 이미 있음: !JAVA_HOME!
    "%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /i "version"
    goto :after_java
)

:after_java
echo.

REM --- Node 24 ---
:need_node_only
call "%~dp0env.bat"
set "ENV_ERR=%errorlevel%"
if %ENV_ERR%==2 (
    echo [설치] Node v24 — 없음
    call :install_node
    if errorlevel 1 goto :fail
    call "%~dp0env.bat"
    set "ENV_ERR=%errorlevel%"
    if %ENV_ERR% neq 0 goto :fail
) else if %ENV_ERR%==1 (
    goto :fail
) else (
    echo [건너뜀] Node — 이미 있음: !NODE_HOME! ^(!NODE_VER!^)
)
echo.

REM --- Maven Wrapper ---
if exist "%ROOT%\backend\mvnw.cmd" (
    echo [건너뜀] Maven Wrapper — 이미 있음
) else (
    echo [설치] Maven Wrapper...
    where mvn >nul 2>&1
    if errorlevel 1 (
        echo [오류] mvn 이 없습니다. Maven 설치 후 다시 실행하세요.
        goto :fail
    )
    pushd "%ROOT%\backend"
    call mvn -N wrapper:wrapper "-Dmaven=3.9.9"
    if errorlevel 1 (
        popd
        goto :fail
    )
    popd
    echo [완료] Maven Wrapper
)
echo.

REM --- Frontend node_modules ---
set "NEED_NPM=0"
if not exist "%ROOT%\frontend\node_modules\react\package.json" set "NEED_NPM=1"
if !NEED_NPM!==0 if exist "%ROOT%\frontend\package-lock.json" (
    for %%F in ("%ROOT%\frontend\package-lock.json") do set "LOCK_TIME=%%~tF"
    for %%F in ("%ROOT%\frontend\node_modules\.package-lock.json") do set "NM_TIME=%%~tF"
    if "!LOCK_TIME!" gtr "!NM_TIME!" set "NEED_NPM=1"
)
if !NEED_NPM!==1 (
    echo [설치] Frontend npm...
    pushd "%ROOT%\frontend"
    if exist "package-lock.json" (
        call npm ci --legacy-peer-deps
    ) else (
        call npm install --legacy-peer-deps
    )
    if errorlevel 1 (
        popd
        goto :fail
    )
    popd
) else (
    echo [건너뜀] Frontend node_modules — 이미 있음
)
echo.

REM --- Backend JAR ---
set "NEED_BACKEND=0"
if not exist "%ROOT%\backend\target\studio.jar" set "NEED_BACKEND=1"
if !NEED_BACKEND!==0 (
    for %%F in ("%ROOT%\backend\pom.xml") do set "POM_TIME=%%~tF"
    for %%F in ("%ROOT%\backend\target\studio.jar") do set "JAR_TIME=%%~tF"
    if "!POM_TIME!" gtr "!JAR_TIME!" set "NEED_BACKEND=1"
)
if !NEED_BACKEND!==1 (
    echo [설치] Backend 빌드...
  if not exist "%ROOT%\frontend\dist\index.html" (
        echo [설치] Frontend dist 빌드...
        pushd "%ROOT%\frontend"
        call npm run build
        if errorlevel 1 (
            popd
            goto :fail
        )
        popd
    )
    pushd "%ROOT%\backend"
    call mvnw.cmd -q -DskipTests package
    if errorlevel 1 (
        popd
        goto :fail
    )
    popd
) else (
    echo [건너뜀] Backend JAR — 이미 있음
)

echo.
echo ============================================================
echo   설정 완료
echo ============================================================
echo   Java : %JAVA_HOME%
echo   Node : %NODE_HOME%
echo   run.bat
echo   scripts\verify.bat
echo.
pause
exit /b 0

:install_java
echo        JDK 17 → C:\Java17
set "JDK_ZIP=%TEMP%\blog-studio-jdk17.zip"
powershell -NoProfile -Command "try { Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' -OutFile '%JDK_ZIP%' -UseBasicParsing } catch { Write-Host $_; exit 1 }"
if errorlevel 1 exit /b 1
if not exist "C:\Java17" mkdir "C:\Java17"
powershell -NoProfile -Command "Expand-Archive -Path '%JDK_ZIP%' -DestinationPath '%TEMP%\jdk17extract' -Force; $d=Get-ChildItem '%TEMP%\jdk17extract' -Directory | Select-Object -First 1; if(-not $d){exit 1}; Get-ChildItem $d.FullName | Copy-Item -Destination 'C:\Java17' -Recurse -Force"
del "%JDK_ZIP%" 2>nul
echo [완료] JDK 설치
exit /b 0

:install_node
echo        Node 24.16.0 → C:\Node24
set "NODE_ZIP=%TEMP%\node-v24.16.0-win-x64.zip"
powershell -NoProfile -Command "try { Invoke-WebRequest -Uri 'https://nodejs.org/dist/v24.16.0/node-v24.16.0-win-x64.zip' -OutFile '%NODE_ZIP%' -UseBasicParsing } catch { Write-Host $_; exit 1 }"
if errorlevel 1 exit /b 1
if exist "C:\Node24" rmdir /s /q "C:\Node24" 2>nul
powershell -NoProfile -Command "Expand-Archive -Path '%NODE_ZIP%' -DestinationPath '%TEMP%\nodeextract' -Force; $d=Get-ChildItem '%TEMP%\nodeextract' -Directory | Where-Object { $_.Name -like 'node-v*' } | Select-Object -First 1; if(-not $d){exit 1}; Move-Item -Path $d.FullName -Destination 'C:\Node24'"
del "%NODE_ZIP%" 2>nul
echo [완료] Node 설치
exit /b 0

:fail
echo.
echo [실패] setup 중 오류가 발생했습니다.
pause
exit /b 1
