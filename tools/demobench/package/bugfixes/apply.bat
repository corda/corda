@echo off

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.

set SOURCEDIR=%DIRNAME%java
set BUILDDIR=%DIRNAME%build

if '%1' == '' (
    @echo Need location of rt.jar
    exit /b 1
)
if not "%~nx1" == "rt.jar" (
    @echo File '%1' is not rt.jar
    exit /b 1
)
if not exist %1 (
    @echo %1 not found.
    exit /b 1
)

rem Bugfixes:
rem =========
rem
rem sun.swing.JLightweightFrame:473
rem https://bugs.openjdk.java.net/browse/JDK-8185890

if exist "%BUILDDIR%" rmdir /s /q "%BUILDDIR%"
mkdir "%BUILDDIR%"

for /r "%SOURCEDIR%" %%j in (*.java) do (
    "%JAVA_HOME%\bin\javac" -O -d "%BUILDDIR%" "%%j"
    if ERRORLEVEL 1 (
        @echo "Failed to compile %%j"
        exit /b 1
    )
)

"%JAVA_HOME%\bin\jar" uvf %1 -C "%BUILDDIR%" .
if ERRORLEVEL 1 (
    @echo "Failed to update %1"
    exit /b 1
)

@echo "Completed"
