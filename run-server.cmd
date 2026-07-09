@echo off
setlocal
cd /d "%~dp0"
call scripts\run-server.cmd %*
exit /b %errorlevel%
