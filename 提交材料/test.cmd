@echo off
cd /d "%~dp0"
call mvn "-Dmaven.repo.local=%~dp0.m2" -B -ntp test
if errorlevel 1 exit /b 1
java -cp target\classes cn.edu.oslab.SelfTest
pause
