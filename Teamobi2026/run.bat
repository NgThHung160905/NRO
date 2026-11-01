@ECHO OFF
cd /d "%~dp0"
if exist "Teamobi2026.clean.jar" (
    copy /y "Teamobi2026.clean.jar" "Teamobi2026.jar" >nul 2>&1
    java -server -Dfile.encoding=UTF-8 -jar Teamobi2026.clean.jar
) else (
    java -server -Dfile.encoding=UTF-8 -jar Teamobi2026.jar
)
PAUSE
