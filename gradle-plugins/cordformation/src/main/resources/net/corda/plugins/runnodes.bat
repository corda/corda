@echo off

REM Change to the directory of this script
cd /d %~dp0

FOR /R ".\" %%G in (.) DO (
 Pushd %%G
 start java -jar corda.jar
 Popd
)