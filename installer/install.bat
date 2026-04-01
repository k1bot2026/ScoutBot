@echo off
setlocal enabledelayedexpansion

:: ============================================================
:: CoxScout Installer for Windows
:: Clones the repo, builds the JAR, and installs to DreamBot.
:: ============================================================

set "REPO_URL=https://github.com/k1bot2026/ScoutBot.git"
set "CLONE_DIR=%TEMP%\coxscout-install-%RANDOM%"
set "DREAMBOT_SCRIPTS=%USERPROFILE%\DreamBot\Scripts"

:: --- Check Git ---
echo [+] Checking for Git...
where git >nul 2>&1
if errorlevel 1 (
    echo [x] Git is not installed.
    echo.
    echo   Install with winget:
    echo     winget install Git.Git
    echo.
    echo   Or download from https://git-scm.com/download/win
    goto :fail
)
for /f "delims=" %%v in ('git --version') do echo [+] Found: %%v

:: --- Check Java 11+ ---
echo [+] Checking for Java 11+...
where java >nul 2>&1
if errorlevel 1 (
    echo [x] Java is not installed.
    echo.
    echo   Install with winget:
    echo     winget install Microsoft.OpenJDK.11
    echo.
    echo   Or download from https://adoptium.net
    goto :fail
)

for /f "tokens=3 delims= " %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VER_RAW=%%~v"
)
for /f "tokens=1 delims=." %%m in ("!JAVA_VER_RAW!") do set "JAVA_MAJOR=%%m"
if !JAVA_MAJOR! LSS 11 (
    echo [x] Java 11+ is required, but found Java !JAVA_MAJOR!.
    goto :fail
)
echo [+] Java found: !JAVA_VER_RAW!

:: --- Check DreamBot ---
echo [+] Checking for DreamBot...
if not exist "%DREAMBOT_SCRIPTS%" (
    echo [x] DreamBot Scripts folder not found at: %DREAMBOT_SCRIPTS%
    echo.
    echo   Make sure DreamBot is installed and has been run at least once.
    goto :fail
)
echo [+] DreamBot Scripts folder found.

:: --- Clone repo ---
echo [+] Cloning repository...
git clone --depth 1 "%REPO_URL%" "%CLONE_DIR%"
if errorlevel 1 (
    echo [x] Failed to clone repository.
    goto :cleanup
)

:: --- Build ---
echo [+] Building CoxScout...
pushd "%CLONE_DIR%"
call gradlew.bat :scripts:coxscout:jar --no-daemon -q
if errorlevel 1 (
    echo [x] Build failed.
    popd
    goto :cleanup
)
popd

:: --- Find and copy JAR ---
set "JAR_FILE=%CLONE_DIR%\scripts\coxscout\build\libs\CoxScout.jar"
if not exist "%JAR_FILE%" (
    echo [x] Build failed — CoxScout.jar not found.
    goto :cleanup
)

copy /Y "%JAR_FILE%" "%DREAMBOT_SCRIPTS%\CoxScout.jar" >nul
if errorlevel 1 (
    echo [x] Failed to copy JAR to DreamBot Scripts folder.
    goto :cleanup
)

echo.
echo [+] CoxScout installed successfully!
echo   JAR location: %DREAMBOT_SCRIPTS%\CoxScout.jar
echo   Open DreamBot and select 'COX Scout' from the script list.
goto :cleanup

:fail
echo.
echo Installation failed. Fix the issues above and try again.

:cleanup
if exist "%CLONE_DIR%" (
    rmdir /S /Q "%CLONE_DIR%" >nul 2>&1
)
endlocal
pause
