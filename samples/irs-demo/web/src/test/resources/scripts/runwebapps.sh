#!/bin/bash -x

function run_webapp() {
    if [ ! -z "$TMUX" ]; then
        tmux new-window -n $1 "$2"; [ $? -eq 0 -o $? -eq 143 ] || sh
    else
        xterm -T $1 -e "$2"; [ $? -eq 0 -o $? -eq 143 ] || sh
    fi;
}

run_webapp "NotaryService" "cd \"#DIR#\" && java -Dspring.profiles.active=NotaryService -jar #JAR_PATH#" &
run_webapp "BankA" "cd \"#DIR#\" && java -Dspring.profiles.active=BankA -jar #JAR_PATH#" &
run_webapp "BankB" "cd \"#DIR#\" && java -Dspring.profiles.active=BankB -jar #JAR_PATH#" &

