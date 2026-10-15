@echo off
rem Builds (or updates) a Create Galore dedicated server.
rem Usage: install-server.bat C:\CreateGaloreServer
rem Needs Java 21. Run it again after the pack changes to update the mods.
setlocal
set NEOFORGE=21.1.259
set HERE=%~dp0
for %%I in ("%HERE%..\pack\pack.toml") do set "PACK=%%~fI"
if "%~1"=="" (
  echo Give the server folder, for example: install-server.bat C:\CreateGaloreServer
  exit /b 1
)
set "DEST=%~f1"
if not exist "%DEST%" mkdir "%DEST%"
cd /d "%DEST%"

if not exist run.bat (
  echo Installing NeoForge %NEOFORGE% server...
  curl -fL -o neoforge-installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/%NEOFORGE%/neoforge-%NEOFORGE%-installer.jar" || exit /b 1
  java -jar neoforge-installer.jar --install-server . || exit /b 1
  del neoforge-installer.jar neoforge-installer.jar.log 2>nul
)

if not exist packwiz-installer-bootstrap.jar curl -fL -o packwiz-installer-bootstrap.jar https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar

if not exist packwiz-installer.jar curl -fL -o packwiz-installer.jar https://github.com/packwiz/packwiz-installer/releases/latest/download/packwiz-installer.jar

echo Downloading the server-side mods and configs...
java -jar packwiz-installer-bootstrap.jar --bootstrap-no-update -g -s server "%PACK%" || exit /b 1

rem First run only: memory settings and server settings. Your own edits are kept on later runs.
if not exist user_jvm_args.txt.galore (
  copy /y "%HERE%user_jvm_args.txt" user_jvm_args.txt >nul
  type nul > user_jvm_args.txt.galore
)
if not exist server.properties copy "%HERE%server.properties" server.properties >nul

if not exist mods\twilightforest-*.jar (
  echo.
  echo ONE MANUAL STEP: put twilightforest-1.21.1-4.8.3345-universal.jar ^(CurseForge^) into %DEST%\mods
)
findstr /c:"eula=true" eula.txt >nul 2>&1 || echo Read https://aka.ms/MinecraftEULA, then put eula=true in %DEST%\eula.txt
echo Done. Start the server with run.bat
endlocal
