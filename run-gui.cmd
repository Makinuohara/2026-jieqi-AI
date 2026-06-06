@echo off
setlocal
cd /d "%~dp0"

call mvnw.cmd -pl jieqi-gui -am install -DskipTests
if errorlevel 1 exit /b %errorlevel%

call mvnw.cmd -f jieqi-gui\pom.xml javafx:run
exit /b %errorlevel%
