@ECHO off

SETLOCAL ENABLEEXTENSIONS

IF NOT DEFINED CLASSPATH (SET CLASSPATH=)

IF DEFINED DEBUG (
    SET DEBUG_PORT=5005
    SET DEBUG_AGENT=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=%DEBUG_PORT%
    ECHO Opening remote debugging session on port %DEBUG_PORT%
) ELSE (
    SET DEBUG_AGENT=
)

CALL java %DEBUG_AGENT% -cp "%CLASSPATH%;.;tmp;%~dp0\corda-djvm-cli.jar" net.corda.djvm.tools.cli.Program %*
