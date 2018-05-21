SET scriptpath=%~dp0
cmd /C start java -Dspring.profiles.active=NotaryService -jar %scriptpath%#JAR_PATH#
cmd /C start java -Dspring.profiles.active=BankA -jar %scriptpath%#JAR_PATH#
cmd /C start java -Dspring.profiles.active=BankB -jar %scriptpath%#JAR_PATH#
