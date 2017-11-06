cmd /C start java -Dspring.profiles.active=NotaryService -jar #JAR_PATH#
cmd /C start java -Dspring.profiles.active=BankA -jar #JAR_PATH#
cmd /C start java -Dspring.profiles.active=BankB -jar #JAR_PATH#