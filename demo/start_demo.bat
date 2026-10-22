@echo off
setlocal
cd /d D:\jshz-PMO\demo

rem Locate a usable Python interpreter
set "PYEXE="
where python >nul 2>&1
if not errorlevel 1 (
    python -c "import sys" >nul 2>&1
    if not errorlevel 1 set "PYEXE=python"
)
if not defined PYEXE (
    if exist "D:\Program Files\Tencent\Marvis\MarvisAgent\1.60.3000.716\runtime\python311\python.exe" set "PYEXE=D:\Program Files\Tencent\Marvis\MarvisAgent\1.60.3000.716\runtime\python311\python.exe"
)
if not defined PYEXE (
    echo [ERROR] Python not found. Please install Python 3 and add it to PATH.
    pause
    exit /b 1
)

rem Check if port 8000 is already in use
netstat -ano | findstr ":8000" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
    echo [INFO] Port 8000 is already in use. The demo service may already be running.
    echo To restart, close the old window or kill the python process first.
    start http://localhost:8000/
    pause
    exit /b 0
)

echo Starting Expert DB Demo ...
echo Employee page:   http://localhost:8000/
echo Admin page:      http://localhost:8000/admin
echo Press Ctrl+C to stop.
"%PYEXE%" server.py
pause
