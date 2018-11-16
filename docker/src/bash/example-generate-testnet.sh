#!/usr/bin/env bash

docker run -ti \
        -e MY_PUBLIC_ADDRESS="corda-node.example.com" \
        -e ONE_TIME_DOWNLOAD_KEY="bbcb189e-9e4f-4b27-96db-134e8f592785" \
        -e LOCALITY="London" -e COUNTRY="GB" \
        -v $(pwd)/docker/config:/etc/corda \
        -v $(pwd)/docker/certificates:/opt/corda/certificates \
        corda/corda-4.0-snapshot:latest config-generator --testnet

docker run -ti \
        --memory=2048m \
        --cpus=2 \
        -v $(pwd)/docker/config:/etc/corda \
        -v $(pwd)/docker/certificates:/opt/corda/certificates \
        -v $(pwd)/docker/persistence:/opt/corda/persistence \
        -v $(pwd)/docker/logs:/opt/corda/logs \
        -v $(pwd)/samples/bank-of-corda-demo/build/nodes/BankOfCorda/cordapps:/opt/corda/cordapps \
        corda/corda-4.0-snapshot:latest