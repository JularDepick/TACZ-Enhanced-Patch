@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Building TACZ Enhanced Patch...
call gradlew.bat build
if errorlevel 1 (
    echo.
    echo Build failed with errorlevel %errorlevel%
    pause
    exit /b 1
)
echo.
echo Build successful!
echo.
echo Output:
dir /b release\TACZEnhancedPatch-v*.jar
echo.
echo Full path: %cd%\release\
pause
