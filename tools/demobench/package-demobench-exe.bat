@echo off

@rem Creates an EXE installer for DemoBench.
@rem Assumes that Inno Setup 5+ has already been installed (http://www.jrsoftware.org/isinfo.php)

if not defined JAVA_HOME goto NoJavaHome

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.

call %DIRNAME%\..\..\gradlew -PpackageType=exe javapackage %*
if ERRORLEVEL 1 goto Fail
@echo
@echo Wrote installer to %DIRNAME%\build\javapackage\bundles\
@echo
goto end

:NoJavaHome
@echo Please set JAVA_HOME correctly.
exit /b 1

:Fail
@echo Failed to write installer.
exit /b 1

:end
