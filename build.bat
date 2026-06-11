@echo off
rem Compila il gioco e crea il jar eseguibile in dist\
setlocal
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 --release 17 -d out src\catsurvivors\*.java
if errorlevel 1 (
    echo Errore di compilazione.
    exit /b 1
)
if not exist dist mkdir dist

rem individua lo strumento jar: PATH, poi JAVA_HOME, poi il JDK in Program Files
set "JAR="
where jar >nul 2>nul && set "JAR=jar"
if not defined JAR if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jar.exe" set "JAR=%JAVA_HOME%\bin\jar.exe"
if not defined JAR for /d %%J in ("%ProgramFiles%\Java\jdk*") do if exist "%%J\bin\jar.exe" set "JAR=%%J\bin\jar.exe"
if not defined JAR (
    echo Strumento "jar" non trovato: salto la creazione del jar.
    echo Il gioco si avvia comunque con run.bat ^(usa le classi in out\^).
    exit /b 0
)

"%JAR%" --create --file dist\CatSurvivors.jar --main-class catsurvivors.Main -C out .
if errorlevel 1 (
    echo Creazione del jar fallita.
    exit /b 1
)
echo Build completata: dist\CatSurvivors.jar
