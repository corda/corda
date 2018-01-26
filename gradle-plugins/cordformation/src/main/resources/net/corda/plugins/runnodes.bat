@echo off

REM Change to the directory of this script (%~dp0)
Pushd %~dp0

java -jar runnodes.jar %*

Popd