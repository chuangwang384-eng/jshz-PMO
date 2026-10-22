@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem  Internal Access URLs - Expert System
rem  Auto-detect all reachable internal IPv4 addresses and show
rem  frontend / backend / health check URLs.
rem  Pure ASCII only (no Chinese chars, avoid cmd GBK garbled).
rem ============================================================

set FE_PORT=5173
set BE_PORT=8080

echo.
echo ============================================================
echo   Internal Access URLs
echo   Frontend port: %FE_PORT%   Backend port: %BE_PORT%
echo ============================================================
echo.

echo Detecting local internal IPv4 addresses (excluding virtual adapters)...
echo.

set IP_COUNT=0

for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.InterfaceAlias -notmatch 'vEthernet|VMware|VirtualBox|Docker|WSL|Tailscale|Hyper-V|Npcap' } | Sort-Object InterfaceAlias | Select-Object -ExpandProperty IPAddress"`) do (
    set "CUR_IP=%%i"
    set "CUR_IP=!CUR_IP: =!"
    if not "!CUR_IP!"=="" (
        set /a IP_COUNT+=1
        echo [IP] !CUR_IP!
        echo   Frontend : http://!CUR_IP!:!FE_PORT!
        echo   Backend  : http://!CUR_IP!:!BE_PORT!
        echo   Health   : http://!CUR_IP!:!BE_PORT!/health
        echo.
    )
)

if "%IP_COUNT%"=="0" (
    echo No reachable internal IPv4 found.
    echo.
)

echo Checking listening status for ports %FE_PORT% and %BE_PORT% ...
echo.

set "PORT_STATES="
for /f "usebackq delims=" %%s in (`powershell -NoProfile -Command "$p=Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty LocalPort; if($p -contains %FE_PORT%){'UP'}else{'DOWN'}; if($p -contains %BE_PORT%){'UP'}else{'DOWN'}"`) do set "PORT_STATES=!PORT_STATES!%%s "

set FE_STATE=UNKNOWN
set BE_STATE=UNKNOWN

for /f "tokens=1,2 delims= " %%a in ("!PORT_STATES!") do (
    set "FE_STATE=%%a"
    set "BE_STATE=%%b"
)

echo   Frontend port %FE_PORT% : %FE_STATE%
echo   Backend  port %BE_PORT% : %BE_STATE%
echo.
echo Done. Copy any URL above into your browser to access the system.
echo.
pause
endlocal
