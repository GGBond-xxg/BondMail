@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Collect-BondMailLogs.ps1" %*
if errorlevel 1 echo Collection failed. Please copy the error message above.
pause
