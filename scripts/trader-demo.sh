#!/bin/bash

mode=$1

if [ ! -e ./gradlew ]; then
    echo "Run from the root directory please"
    exit 1
fi

if [ ! -d build/install/r3prototyping ]; then
    ./gradlew installDist
fi

if [[ "$mode" == "buyer" ]]; then
    if [ ! -d buyer ]; then
        mkdir buyer
        echo "myLegalName = Bank of Zurich" >buyer/config
    fi

    build/install/r3prototyping/bin/r3prototyping --dir=buyer --service-fake-trades --network-address=localhost
elif [[ "$mode" == "seller" ]]; then
    if [ ! -d seller ]; then
        mkdir seller
        echo "myLegalName = Bank of London" >seller/config
    fi

    build/install/r3prototyping/bin/r3prototyping --dir=seller --fake-trade-with=localhost --network-address=localhost:31340 --timestamper-identity-file=buyer/identity-public --timestamper-address=localhost
else
    echo "Run like this, one in each tab:"
    echo
    echo "  scripts/trader-demo.sh buyer"
    echo "  scripts/trader-demo.sh seller"
fi
