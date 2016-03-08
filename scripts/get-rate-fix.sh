#!/usr/bin/env bash

# This needs the buyer node to be running first.

if [ ! -e ./gradlew ]; then
    echo "Run from the root directory please"
    exit 1
fi

if [ ! -d build/install/r3prototyping ]; then
    ./gradlew installDist
fi

if [ ! -e buyer/identity-public ]; then
    echo "You must run scripts/trade-demo.sh buyer before running this script (and keep it running)"
    exit 1
fi

# Upload the rates to the buyer node
curl -F rates=@scripts/example.rates.txt http://localhost:31338/upload/interest-rates

build/install/r3prototyping/bin/get-rate-fix --network-address=localhost:31300 --oracle=localhost --oracle-identity-file=buyer/identity-public