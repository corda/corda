#!/usr/bin/env bash

docker run -ti --net="host" \
        -e  MY_LEGAL_NAME="O=ALL,L=Berlin,C=DE"     \
        -e MY_PUBLIC_ADDRESS=stefano.azure.io       \
        -e COMPATIBILITY_ZONE="http://corda.net"    \
        -e DOORMAN_URL="http://localhost:8080"      \
        -e NETWORK_TRUST_PASSWORD="password"       \
        -v $(pwd)/docker/config:/etc/corda          \
        -v $(pwd)/docker/certificates:/opt/corda/certificates \
        corda/corda-4.0-snapshot:latest config-generator --generic

docker run -ti \
        -e JVM_ARGS="-Xmx5g -Xms2g -XX:+UseG1GC" \
        -v $(pwd)/docker/config:/etc/corda \
        -v $(pwd)/docker/certificates:/opt/corda/certificates \
        -v $(pwd)/docker/persistence:/opt/corda/persistence \
        -v $(pwd)/docker/logs:/opt/corda/logs \
        -v $(pwd)/samples/bank-of-corda-demo/build/nodes/BankOfCorda/cordapps:/opt/corda/cordapps \
        corda/corda-4.0-snapshot:latest