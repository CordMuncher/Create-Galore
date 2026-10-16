@echo off
rem Starts the Create Galore server. Type stop in this window to save and shut down.
rem First it checks for a newer pack and asks whether to update (no / yes / back up the world first).
rem Arguments go on to the server, e.g. start-server.bat nogui
cd /d "%~dp0"
rem One bracketed block: cmd reads it whole before running it, so nothing an update changes can confuse it.
(
  set GALORE_OK=1
  if exist create-galore-updater.jar java -jar create-galore-updater.jar --server-start || set GALORE_OK=
  if defined GALORE_OK call run.bat %*
  if not defined GALORE_OK pause
  exit /b
)
