tell app "Terminal"
    activate
    tell app "System Events" to tell process "Terminal" to keystroke "t" using command down
    delay 0.5
    do script "bash -c 'cd \"#DIR#\"  && java -Dspring.profiles.active=NotaryService -jar #JAR_PATH# && exit'" in selected tab of the front window
    tell app "System Events" to tell process "Terminal" to keystroke "t" using command down
    delay 0.5
    do script "bash -c 'cd \"#DIR#\" && java -Dspring.profiles.active=BankA -jar #JAR_PATH# && exit'" in selected tab of the front window
    tell app "System Events" to tell process "Terminal" to keystroke "t" using command down
    delay 0.5
    do script "bash -c 'cd \"#DIR#\" && java -Dspring.profiles.active=BankB -jar #JAR_PATH# && exit'" in selected tab of the front window
end tell