#!/usr/bin/env bash

docker run -ti \
        -e MY_PUBLIC_ADDRESS=stefano.azure.io \
        -e ONE_TIME_DOWNLOAD_KEY="694c0a8f-b91c-4718-a9a2-c25e1eca490d" \
        -e LOCALITY="London" -e COUNTRY="GB" \
        -v $(pwd)/docker/config:/etc/corda \
        -v $(pwd)/docker/certificates:/opt/corda/certificates \
        corda/corda-4.0-snapshot:latest config-generator -c testnet

docker run -ti \
        -e JVM_ARGS="-Xmx5g -Xms2g -XX:+UseG1GC" \
        -v $(pwd)/docker/config:/etc/corda \
        -v $(pwd)/docker/certificates:/opt/corda/certificates \
        -v $(pwd)/docker/persistence:/opt/corda/persistence \
        -v $(pwd)/docker/logs:/opt/corda/logs \
        -v $(pwd)/samples/bank-of-corda-demo/build/nodes/BankOfCorda/cordapps:/opt/corda/cordapps \
        corda/corda-4.0-snapshot:latest