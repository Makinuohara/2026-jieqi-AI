@echo off
setlocal
cd /d "%~dp0"
call scripts\run-gui.cmd %*
exit /b %errorlevel%
