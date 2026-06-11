@echo off
rem Compila (se serve) e avvia Cat Survivors
setlocal
cd /d "%~dp0"
if not exist out\catsurvivors\Main.class (
    call build.bat
    if errorlevel 1 exit /b 1
)
java -cp out catsurvivors.Main %*
