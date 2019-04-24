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
sleep 10s
docker run --network="${NETWORK_NAME}" -d --name clair arminc/clair-local-scan:v2.0.6


for image ; do
    image_base=$(basename ${image})
    image_name=(${image_base//:/ })
    ( docker rm -f ${SCANNER_CONTAINER_NAME} || true )  && docker run --network="${NETWORK_NAME}" -v /var/run/docker.sock:/var/run/docker.sock -v $(pwd)/scanning_output:/output -v $(pwd)/cve_suppressions.yaml:/cve_suppressions.yaml --name ${SCANNER_CONTAINER_NAME} \
        corda/clair-local-scanner:latest \
        clair-scanner --ip scanner --clair="http://clair:6060" -w "cve_suppressions.yaml" --threshold="Medium" --report="/output/${image_base}.json" ${image}
done

tidy_up
