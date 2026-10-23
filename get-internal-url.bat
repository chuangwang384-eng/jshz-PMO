@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem  Production Access URL - Expert System (jshz-PMO)
rem  Purpose : print the internal URL for colleagues to open the
rem            production site in browser, e.g. http://192.168.0.144/
rem  Dynamic IP : if this machine's IP changes, just run this
rem            script again to get the new address.
rem  Pure ASCII only (no Chinese chars, avoid cmd GBK garbled).
rem ============================================================

set NX_PORT=80
set BE_PORT=8080

echo.
echo ============================================================
echo   jshz-PMO Production Access URL
echo   Nginx :%NX_PORT% (frontend)   Backend :%BE_PORT%
echo ============================================================
echo.

echo Detecting local internal IPv4 addresses (excluding virtual adapters)...
echo.

set IP_COUNT=0

for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.IPAddress -match '^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)' -and $_.InterfaceAlias -notmatch 'vEthernet|VMware|VirtualBox|Docker|WSL|Tailscale|Hyper-V|Npcap' } | Sort-Object InterfaceAlias | Select-Object -ExpandProperty IPAddress"`) do (
    set "CUR_IP=%%i"
    set "CUR_IP=!CUR_IP: =!"
    if not "!CUR_IP!"=="" (
        set /a IP_COUNT+=1
        echo [IP] !CUR_IP!
        echo   Production URL : http://!CUR_IP!/
        echo.
    )
)

if "%IP_COUNT%"=="0" (
    echo No reachable internal IPv4 found.
    echo.
)

echo Checking listening status for ports %NX_PORT% and %BE_PORT% ...
echo.

set "PORT_STATES="
for /f "usebackq delims=" %%s in (`powershell -NoProfile -Command "$p=Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty LocalPort; if($p -contains %NX_PORT%){'UP'}else{'DOWN'}; if($p -contains %BE_PORT%){'UP'}else{'DOWN'}"`) do set "PORT_STATES=!PORT_STATES!%%s "

set NX_STATE=UNKNOWN
set BE_STATE=UNKNOWN

for /f "tokens=1,2 delims= " %%a in ("!PORT_STATES!") do (
    set "NX_STATE=%%a"
    set "BE_STATE=%%b"
)

echo   Nginx port %NX_PORT% : %NX_STATE%
echo   Backend  port %BE_PORT% : %BE_STATE%
echo.
echo Done. Copy the Production URL above into your browser (LAN colleagues).
echo If this machine IP changed, run this script again to get the new URL.
echo.
pause
endlocal
