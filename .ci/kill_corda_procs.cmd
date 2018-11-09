@echo off

REM Setlocal EnableDelayedExpansion
FOR /F "tokens=1,2 delims= " %%G IN ('jps -l') DO (call :sub %%H %%G)
goto :eof

:sub

IF %1==net.corda.webserver.WebServer taskkill /F /PID %2
IF %1==net.corda.node.Corda taskkill /F /PID %2
IF %1==corda.jar taskkill /F /PID %2
IF %1==corda-webserver.jar taskkill /F /PID %2
IF %1==org.gradle.launcher.daemon.bootstrap.GradleDaemon taskkill /F /PID %2
goto :eof
