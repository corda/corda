#!/usr/bin/env bash

mkdir -p $(pwd)/config
mkdir -p $(pwd)/certificates
mkdir -p $(pwd)/persistence
mkdir -p $(pwd)/logs

docker run -ti \
        -e MY_PUBLIC_ADDRESS="corda-node.example.com" \
        -e ONE_TIME_DOWNLOAD_KEY="7776c00a-0298-4d27-8cfa-30aa1a664e40" \
        -e LOCALITY="London" -e COUNTRY="GB" \
        -v $(pwd)/config:/etc/corda \
        -v $(pwd)/certificates:/opt/corda/certificates \
        entdocker.software.r3.com/corda-enterprise-5.0-snapshot:latest config-generator --testnet && \

docker run -ti \
        -v $(pwd)/config:/etc/corda \
        -v $(pwd)/certificates:/opt/corda/certificates \
        -v $(pwd)/persistence:/opt/corda/persistence \
        -v $(pwd)/logs:/opt/corda/logs \
        -v $(pwd)/cordapps:/opt/corda/cordapps \
        entdocker.software.r3.com/corda-enterprise-5.0-snapshot:latest db-migrate-execute-migration

docker run --memory=2048m \
        --cpus=2 \
        -v $(pwd)/config:/etc/corda \
        -v $(pwd)/certificates:/opt/corda/certificates \
        -v $(pwd)/persistence:/opt/corda/persistence \
        -v $(pwd)/logs:/opt/corda/logs \
        -v $(pwd)/samples/bank-of-corda-demo/build/nodes/BankOfCorda/cordapps:/opt/corda/cordapps \
        -p 10200:10200 \
        -p 10201:10201 \
        entdocker.software.r3.com/corda-enterprise-5.0-snapshot:latest


