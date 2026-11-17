@echo off
cd /d "%~dp0"
call mvn "-Dmaven.repo.local=%~dp0.m2" -B -ntp clean package
if errorlevel 1 goto failed
if not exist release mkdir release
copy /y target\fat-lab.jar release\fat-lab.jar >nul
echo Build and tests passed. release\fat-lab.jar is ready.
exit /b 0
:failed
echo Build failed. Review the output above.
pause
exit /b 1
