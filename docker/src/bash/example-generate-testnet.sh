#!/usr/bin/env bash

docker run -ti \
        -e MY_PUBLIC_ADDRESS="corda-node.example.com" \
        -e ONE_TIME_DOWNLOAD_KEY="6bbd155f-6fc9-46c5-821a-d5b3835488b3" \
        -e LOCALITY="London" -e COUNTRY="GB" \
        -v $(pwd)/docker/config:/etc/corda \
        -v $(pwd)/docker/certificates:/opt/corda/certificates \
        entdocker.corda.r3cev.com/corda-enterprise-corretto-4.0-snapshot:latest config-generator --testnet

docker run -ti \
        -v $(pwd)/docker/config:/etc/corda \
        -v $(pwd)/docker/certificates:/opt/corda/certificates \
        -v $(pwd)/docker/persistence:/opt/corda/persistence \
        -v $(pwd)/docker/logs:/opt/corda/logs \
        -v $(pwd)/samples/bank-of-corda-demo/build/nodes/BankOfCorda/cordapps:/opt/corda/cordapps \
        entdocker.corda.r3cev.com/corda-enterprise-corretto-4.0-snapshot:latest db-migrate-create-jars

docker run --memory=2048m \
        --cpus=2 \
        -v $(pwd)/docker/config:/etc/corda \
        -v $(pwd)/docker/certificates:/opt/corda/certificates \
        -v $(pwd)/docker/persistence:/opt/corda/persistence \
        -v $(pwd)/docker/logs:/opt/corda/logs \
        -v $(pwd)/samples/bank-of-corda-demo/build/nodes/BankOfCorda/cordapps:/opt/corda/cordapps \
        -p 10200:10200 \
        -p 10201:10201 \
        entdocker.corda.r3cev.com/corda-enterprise-corretto-4.0-snapshot:latest