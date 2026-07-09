@echo off
setlocal
chcp 65001 >nul
set "MAVEN_OPTS=-Dfile.encoding=UTF-8 %MAVEN_OPTS%"
cd /d "%~dp0\.."

call mvnw.cmd -pl jieqi-gui -am install -DskipTests
if errorlevel 1 exit /b %errorlevel%

call mvnw.cmd -f jieqi-gui\pom.xml javafx:run
exit /b %errorlevel%
