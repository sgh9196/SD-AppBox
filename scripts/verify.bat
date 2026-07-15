@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "ROOT=%~dp0.."

echo ============================================================
echo   Blog Studio - harness verify
echo ============================================================
echo.

call "%ROOT%\setup\env.bat"
set "ENV_ERR=%errorlevel%"
if %ENV_ERR%==1 (
    echo [FAIL] Java 17 missing. Run setup\setup.bat
    exit /b 1
)
if %ENV_ERR%==2 (
    echo [FAIL] Node v24 missing. Run setup\setup.bat
    exit /b 1
)

set "STEP=1"
set "TOTAL=4"

echo [Step !STEP! of !TOTAL!] Frontend lint...
pushd "%ROOT%\frontend"
call npm run lint
set "ERR=!errorlevel!"
popd
if not "!ERR!"=="0" goto :fail
set /a STEP+=1

echo [Step !STEP! of !TOTAL!] Frontend test...
pushd "%ROOT%\frontend"
call npm run test
set "ERR=!errorlevel!"
popd
if not "!ERR!"=="0" goto :fail
set /a STEP+=1

echo [Step !STEP! of !TOTAL!] Backend checkstyle + test...
pushd "%ROOT%\backend"
if exist "mvnw.cmd" (
    call mvnw.cmd -q test
) else (
    call mvn -q test
)
set "ERR=!errorlevel!"
popd
if not "!ERR!"=="0" goto :fail
set /a STEP+=1

echo [Step !STEP! of !TOTAL!] Frontend build...
pushd "%ROOT%\frontend"
call npm run build
set "ERR=!errorlevel!"
popd
if not "!ERR!"=="0" goto :fail

echo.
echo ============================================================
echo   VERIFY OK
echo ============================================================
exit /b 0

:fail
echo.
echo [FAIL] harness verify error exit !ERR!
exit /b !ERR!