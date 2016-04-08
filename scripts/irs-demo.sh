#!/bin/bash

mode=$1

if [ ! -e ./gradlew ]; then
    echo "Run from the root directory please"
    exit 1
fi

if [ ! -d build/install/r3prototyping ]; then
    ./gradlew installDist
fi

if [[ "$mode" == "nodeA" ]]; then
    if [ ! -d nodeA ]; then
        mkdir nodeA
        echo "myLegalName = Bank A" >nodeA/config
    fi

    RC=83
    while [ $RC -eq 83 ]
    do
        build/install/r3prototyping/bin/irsdemo --dir=nodeA --network-address=localhost --fake-trade-with-address=localhost:31340 --fake-trade-with-identity=nodeB/identity-public --timestamper-identity-file=nodeA/identity-public --timestamper-address=localhost --rates-oracle-address=localhost:31340 --rates-oracle-identity-file=nodeB/identity-public
        RC=$?
    done
elif [[ "$mode" == "nodeB" ]]; then
    if [ ! -d nodeB ]; then
        mkdir nodeB
        echo "myLegalName = Bank B" >nodeB/config
    fi

    # enable job control
    set -o monitor

    RC=83
    while [ $RC -eq 83 ]
    do
        build/install/r3prototyping/bin/irsdemo --dir=nodeB --network-address=localhost:31340 --fake-trade-with-address=localhost --fake-trade-with-identity=nodeA/identity-public --timestamper-identity-file=nodeA/identity-public --timestamper-address=localhost --rates-oracle-address=localhost:31340 --rates-oracle-identity-file=nodeB/identity-public &
        while ! curl -F rates=@scripts/example.rates.txt http://localhost:31341/upload/interest-rates; do
            echo "Retry to upload interest rates to oracle after 5 seconds"
            sleep 5
        done
        fg %1
        RC=$?
    done
elif [[ "$mode" == "trade" && "$2" != "" ]]; then
    tradeID=$2
    echo "Uploading tradeID ${tradeID}"
    sed "s/tradeXXX/${tradeID}/g" scripts/example-irs-trade.json | curl -H "Content-Type: application/json" -d @- http://localhost:31338/api/irs/deals
elif [[ "$mode" == "date" && "$2" != "" ]]; then
    demodate=$2
    echo "Setting demo date to ${demodate}"
    echo "\"$demodate\"" | curl -H "Content-Type: application/json" -X PUT -d @- http://localhost:31338/api/irs/demodate
else
    echo "Run like this, one in each tab:"
    echo
    echo "  scripts/irs-demo.sh nodeA"
    echo "  scripts/irs-demo.sh nodeB"
    echo
    echo "To upload a trade as e.g. trade10"
    echo "  scripts/irs-demo.sh trade trade10"
    echo
    echo "To set the demo date, and post fixings in the interval, to e.g. 2017-01-30"
    echo "  scripts/irs-demo.sh date 2017-01-30"
fi
