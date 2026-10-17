@echo off
rem Builds (or updates) a Create Galore dedicated server.
rem Usage: install-server.bat C:\CreateGaloreServer [--clean]
rem   --clean  also resets every config to the pack's version (config, defaultconfigs, each world's serverconfig).
rem            For bug hunting. Mods aren't downloaded again; the world, server.properties, memory and whitelist stay.
rem Needs Java 21. Start the server with start-server.bat: it asks before applying pack updates.
setlocal
set NEOFORGE=21.1.262
set HERE=%~dp0
for %%I in ("%HERE%..\pack\pack.toml") do set "PACK=%%~fI"
if "%~1"=="" (
  echo Give the server folder, for example: install-server.bat C:\CreateGaloreServer
  exit /b 1
)
set "DEST=%~f1"
if not exist "%DEST%" mkdir "%DEST%"
cd /d "%DEST%"

rem Installs NeoForge on the first run, and again when the pack moves to another NeoForge version.
set NEED_NF=
if not exist run.bat set NEED_NF=1
if not exist "libraries\net\neoforged\neoforge\%NEOFORGE%" set NEED_NF=1
if defined NEED_NF (
  echo Installing NeoForge %NEOFORGE% server...
  curl -fL -o neoforge-installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/%NEOFORGE%/neoforge-%NEOFORGE%-installer.jar" || exit /b 1
  java -jar neoforge-installer.jar --install-server . || exit /b 1
  del neoforge-installer.jar neoforge-installer.jar.log 2>nul
)

rem packwiz-installer remembers what it installed (packwiz.json), so a re-run only downloads what changed,
rem removes mods the pack dropped and leaves config edits alone unless the pack changed that file.
if not exist packwiz-installer-bootstrap.jar curl -fL -o packwiz-installer-bootstrap.jar https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v0.0.3/packwiz-installer-bootstrap.jar

if not exist packwiz-installer.jar curl -fL -o packwiz-installer.jar https://github.com/packwiz/packwiz-installer/releases/download/v0.5.14/packwiz-installer.jar

echo Downloading the server-side mods and configs that changed...
java -jar packwiz-installer-bootstrap.jar --bootstrap-no-update -g -s server "%PACK%" || exit /b 1

if /i "%~2"=="--clean" (
  echo Resetting every config to the pack's version ^(the world is kept^)...
  if exist config rmdir /s /q config
  if exist defaultconfigs rmdir /s /q defaultconfigs
  for /d %%W in (*) do if exist "%%W\level.dat" if exist "%%W\serverconfig" rmdir /s /q "%%W\serverconfig"
  rem Without its record packwiz-installer re-checks every file: mods match and stay, the deleted configs come back.
  del packwiz.json 2>nul
  java -jar packwiz-installer-bootstrap.jar --bootstrap-no-update -g -s server "%PACK%" || exit /b 1
)

rem The start script and its update check (asks no / yes / back up first when the pack on GitHub is newer).
copy /y "%HERE%start-server.bat" start-server.bat >nul
if exist "%HERE%create-galore-updater.jar" (
  copy /y "%HERE%create-galore-updater.jar" create-galore-updater.jar >nul
) else (
  curl -fsSL -o create-galore-updater.jar https://github.com/emresahin0906-code/Create-Galore/releases/latest/download/create-galore-updater.jar || echo Couldn't download create-galore-updater.jar, so start-server.bat won't check for updates
)
set VERSION=
for /f "tokens=2 delims==" %%V in ('findstr /b /c:"version" "%PACK%"') do for %%A in (%%V) do set "VERSION=%%~A"
> .galore-pack.txt echo # Create Galore pack version this server has, for the update check at start
>> .galore-pack.txt echo pack=%VERSION%
>> .galore-pack.txt echo installer=script

rem First run only: memory settings and server settings. Your own edits are kept on later runs.
if not exist user_jvm_args.txt.galore (
  copy /y "%HERE%user_jvm_args.txt" user_jvm_args.txt >nul
  type nul > user_jvm_args.txt.galore
)
if not exist server.properties copy "%HERE%server.properties" server.properties >nul
rem Older installs got view 8 / simulation 6: raise them unless you changed them yourself.
findstr /x /c:"view-distance=8" server.properties >nul 2>&1 && findstr /x /c:"simulation-distance=6" server.properties >nul 2>&1 && powershell -NoProfile -Command "(Get-Content server.properties) -replace '^view-distance=8$','view-distance=12' -replace '^simulation-distance=6$','simulation-distance=10' | Set-Content -Encoding ascii server.properties"

if not exist mods\twilightforest-*.jar (
  echo.
  echo ONE MANUAL STEP: put twilightforest-1.21.1-4.8.3345-universal.jar ^(CurseForge^) into %DEST%\mods
)
findstr /c:"eula=true" eula.txt >nul 2>&1 || echo Read https://aka.ms/MinecraftEULA, then put eula=true in %DEST%\eula.txt
echo Done. Start the server with start-server.bat (it checks for pack updates first, then opens the server window with the live memory and player panel; use start-server.bat nogui for console only)
endlocal
