@echo off

FOR /R ".\" %%G in (.) DO (
 Pushd %%G
 start java -jar corda.jar
 Popd
)