@echo off
setlocal
chcp 65001 >nul
set "MAVEN_OPTS=-Dfile.encoding=UTF-8 %MAVEN_OPTS%"
cd /d "%~dp0\.."

call mvnw.cmd -pl jieqi-app -am install -DskipTests
if errorlevel 1 exit /b %errorlevel%

call mvnw.cmd -f jieqi-app\pom.xml exec:java
exit /b %errorlevel%
