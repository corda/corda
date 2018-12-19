#!/usr/bin/env bash

##in this example the doorman will be running on the host machine on port 8080
##so the container must be launched with "host" networking
docker run -ti --net="host" \
        -e MY_LEGAL_NAME="O=EXAMPLE,L=Berlin,C=DE"     \
        -e MY_PUBLIC_ADDRESS="corda.example-hoster.com"       \
        -e NETWORKMAP_URL="https://map.corda.example.com"    \
        -e DOORMAN_URL="https://doorman.corda.example.com"      \
        -e NETWORK_TRUST_PASSWORD="trustPass"       \
        -e MY_EMAIL_ADDRESS="cordauser@r3.com"      \
        -v $(pwd)/docker/config:/etc/corda          \
        -v $(pwd)/docker/certificates:/opt/corda/certificates \
        corda/corda-4.0-snapshot:latest config-generator --generic

##set memory to 2gb max, and 2cores max
docker run -ti \
        --memory=2048m \
        --cpus=2 \
        -v $(pwd)/docker/config:/etc/corda \
        -v $(pwd)/docker/certificates:/opt/corda/certificates \
        -v $(pwd)/docker/persistence:/opt/corda/persistence \
        -v $(pwd)/docker/logs:/opt/corda/logs \
        -v $(pwd)/samples/bank-of-corda-demo/build/nodes/BankOfCorda/cordapps:/opt/corda/cordapps \
        -p 10200:10200 \
        -p 10201:10201 \
        corda/corda-4.0-snapshot:latest