@echo off
setlocal
echo ============================================================
echo  jshz-PMO full release  (Preflight / Publish / Rollback)
echo  Admin PowerShell is required for -Mode Publish / -Mode Rollback
echo ============================================================
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release-full.ps1" %*
set RC=%ERRORLEVEL%
echo.
echo ============================================================
echo  exit code: %RC%   (0 = OK, 1 = aborted / failed)
echo  log: D:\jshz-PMO\logs\release-full-YYYYMMDD_HHMMSS.log
echo ============================================================
exit /b %RC%
