#!/usr/bin/env bash

#docker build . -f DockerfileScanner -t roastario/clair-local-scanner:latest

docker network create --attachable clair-scanning
docker run --network="clair-scanning" -d --name postgres arminc/clair-db:latest
docker run --network="clair-scanning" -d --name clair arminc/clair-local-scan:v2.0.6

( docker rm -f scanner || true )  && docker run --network="clair-scanning" -v /var/run/docker.sock:/var/run/docker.sock -v $(pwd):/output --name scanner \
    roastario/clair-local-scanner:latest \
    clair-scanner --ip scanner --clair="http://clair:6060" --threshold="Low" --report="/output/zulu.json" entdocker.corda.r3cev.com/corda-enterprise-zulu-4.0-snapshot:latest

( docker rm -f scanner || true )  && docker run --network="clair-scanning" -ti -v /var/run/docker.sock:/var/run/docker.sock -v $(pwd):/output --name scanner \
    roastario/clair-local-scanner:latest \
    clair-scanner --ip scanner --clair="http://clair:6060" --threshold="Low" --report="/output/corretto.json" entdocker.corda.r3cev.com/corda-enterprise-corretto-4.0-snapshot:latest

( docker rm -f scanner || true )  && docker run --network="clair-scanning" -ti -v /var/run/docker.sock:/var/run/docker.sock -v $(pwd):/output --name scanner \
    roastario/clair-local-scanner:latest \
    clair-scanner --ip scanner --clair="http://clair:6060" --threshold="Low" --report="/output/alpine-zulu.json" entdocker.corda.r3cev.com/corda-enterprise-alpine-zulu-4.0-snapshot:latest

docker rm -f postgres
docker rm -f clair
docker rm -f scanner
docker network rm clair-scanning
