#!/usr/bin/env bash

#docker build . -f DockerfileScanner -t corda/clair-local-scanner:latest
SCANNER_CONTAINER_NAME=scanner
NETWORK_NAME=clair-scanning
mkdir scanning_output

function tidy_up()
{
    (docker rm -f postgres || true) && \
    (docker rm -f clair || true) && \
    (docker rm -f ${SCANNER_CONTAINER_NAME} || true) && \
    (docker network rm clair-scanning || true)
}

tidy_up

docker network create --attachable clair-scanning
docker run --network="${NETWORK_NAME}" -d --name postgres arminc/clair-db:latest
docker run --network="${NETWORK_NAME}" -d --name clair arminc/clair-local-scan:v2.0.6


for image ; do
    image_base=$(basename ${image})
    ( docker rm -f ${SCANNER_CONTAINER_NAME} || true )  && docker run --network="${NETWORK_NAME}" -v /var/run/docker.sock:/var/run/docker.sock -v $(pwd)/scanning_output:/output --name ${SCANNER_CONTAINER_NAME} \
        corda/clair-local-scanner:latest \
        clair-scanner --ip scanner --clair="http://clair:6060" --threshold="Low" --report="/output/${image_base}.json" ${image}
done

tidy_up
