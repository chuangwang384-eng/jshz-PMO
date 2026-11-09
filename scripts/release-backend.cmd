@echo off
setlocal
echo ============================================================
echo  jshz-PMO backend release (blue/green, no downtime)
echo  Run as Administrator: right-click this file - Run as administrator
echo ============================================================
echo.
net session >nul 2>&1
if %ERRORLEVEL% NEQ 0 goto NOTADMIN
echo [1/1] launching release wrapper ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release-backend.ps1"
set RC=%ERRORLEVEL%
goto REPORT
:NOTADMIN
echo [ABORT] Current window is NOT elevated.
echo         Close it, then right-click this file - Run as administrator.
set RC=1
:REPORT
echo.
echo ============================================================
echo  deploy exit code: %RC%   - 0 means success, 1 means aborted or rolled back
echo  log: D:\jshz-PMO\logs\deploy-backend.YYYYMMDD_HHMMSS.log (UTF-8)
echo ============================================================
pause
exit /b %RC%
