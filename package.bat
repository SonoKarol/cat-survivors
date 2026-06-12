@echo off
rem Crea la versione portatile per Windows: CatSurvivors.exe con Java incluso.
rem Risultato: dist\CatSurvivors-windows.zip. Gli amici scompattano e giocano,
rem senza installare nulla.
setlocal
cd /d "%~dp0"

call "%~dp0build.bat"
if errorlevel 1 exit /b 1

rem individua jpackage: PATH, poi JAVA_HOME, poi il JDK in Program Files
set "JPKG="
where jpackage >nul 2>nul && set "JPKG=jpackage"
if not defined JPKG if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set "JPKG=%JAVA_HOME%\bin\jpackage.exe"
if not defined JPKG for /d %%J in ("%ProgramFiles%\Java\jdk*") do if exist "%%J\bin\jpackage.exe" set "JPKG=%%J\bin\jpackage.exe"
if not defined JPKG (
    echo Strumento "jpackage" non trovato: serve un JDK 17+ completo.
    exit /b 1
)

rem cartella di staging con solo il jar (jpackage copia tutto il contenuto di --input)
if exist dist\staging rmdir /s /q dist\staging
mkdir dist\staging
copy /y dist\CatSurvivors.jar dist\staging\ >nul

if exist dist\portable rmdir /s /q dist\portable

"%JPKG%" --type app-image ^
    --name CatSurvivors ^
    --input dist\staging ^
    --main-jar CatSurvivors.jar ^
    --main-class catsurvivors.Main ^
    --dest dist\portable ^
    --vendor SonoKarol ^
    --app-version 1.0.0 ^
    --add-modules java.base,java.desktop,java.xml ^
    --java-options "-Dfile.encoding=UTF-8"
if errorlevel 1 (
    echo Creazione dell'app-image fallita.
    exit /b 1
)
rmdir /s /q dist\staging

if exist dist\CatSurvivors-windows.zip del dist\CatSurvivors-windows.zip
powershell -NoProfile -Command "Compress-Archive -Path 'dist\portable\CatSurvivors' -DestinationPath 'dist\CatSurvivors-windows.zip'"
if errorlevel 1 (
    echo Compressione fallita, ma la cartella dist\portable\CatSurvivors e' pronta.
    exit /b 1
)

echo.
echo Fatto! Da condividere con gli amici: dist\CatSurvivors-windows.zip
echo Loro scompattano lo zip e avviano CatSurvivors.exe, niente da installare.
