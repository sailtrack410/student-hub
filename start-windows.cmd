@echo off
setlocal
cd /d "%~dp0"

if not exist ".env" (
  echo [ERROR] .env not found. Keep .env beside this script.
  pause
  exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java is not installed or not in PATH. Install JDK 17 or newer.
  pause
  exit /b 1
)

echo Starting Student Hub API on port 9000...
java -jar "target\student-hub-api-0.0.1-SNAPSHOT.jar"
if errorlevel 1 pause
