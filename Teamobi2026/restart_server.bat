@ECHO OFF
cd /d "%~dp0"
echo Server dang khoi dong lai...
timeout /t 3 /nobreak >nul
if exist "Teamobi2026.new.jar" (
    move /y "Teamobi2026.new.jar" "Teamobi2026.jar" >nul
)
call run.bat
