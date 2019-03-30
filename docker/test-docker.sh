#!/usr/bin/env bash
# Tests Corda docker by registering with test doorman
IMAGE=${1:-corda/corda-corretto-4.0}

# Start up test-doorman, if not already running
if [ ! "$(docker ps -q -f name=test-doorman)" ]; then
    if [ "$(docker ps -aq -f status=exited -f name=test-doorman)" ]; then
        echo "TEST-DOCKER: test-doorman is in a status=exited state. I will remove."
        docker rm -f test-doorman
    fi
    echo "TEST-DOCKER: test-doorman is not running. I will start."
    docker run -d --rm --name test-doorman -p 8080:8080 \
    -e NMS_MONGO_CONNECTION_STRING=embed \
    -e NMS_TLS=false \
    -e NMS_DOORMAN=true \
    -e NMS_CERTMAN=false \
    cordite/network-map
else
    echo "TEST-DOCKER: test-door man is already running. I will use this instance."
fi

# Wait for test-doorman and then download truststore
while [[ "$(curl -s -o network-root-truststore.jks -w ''%{http_code}'' http://localhost:8080/network-map/truststore)" != "200" ]]; do
    echo "TEST-DOCKER: waiting 5 seconds for test-doorman to serve..."
    sleep 5
done

# Test corda docker
echo "TEST-DOCKER: Run config-generator in corda docker with image: ${IMAGE}"
SALT=${RANDOM}
docker run -d --name corda-test-${SALT} --network="host" \
        -e MY_LEGAL_NAME="O=Test-${SALT},L=Berlin,C=DE"     \
        -e MY_PUBLIC_ADDRESS="localhost"       \
        -e NETWORKMAP_URL="http://localhost:8080"    \
        -e DOORMAN_URL="http://localhost:8080"      \
        -e NETWORK_TRUST_PASSWORD="trustpass"       \
        -e MY_EMAIL_ADDRESS="cordauser@r3.com"      \
        -v $(pwd)/network-root-truststore.jks:/opt/corda/certificates/network-root-truststore.jks \
        -e CORDA_ARGS="--log-to-console --no-local-shell" \
        $IMAGE  config-generator --generic

# Succesfully registered with http://localhost:8080
docker logs -f corda-test-${SALT} | grep -q "Succesfully registered"
if [ ! "$(docker ps -q -f name=corda-test-${SALT})" ]; then
    echo "TEST-DOCKER: FAIL corda-test has exited."
    docker logs corda-test-${SALT}
    rm -f $(pwd)/network-root-truststore.jks
    docker rm -f corda-test-${SALT}
    exit 1
else
    echo "TEST-DOCKER: SUCCESS : Succesfully registered with http://localhost:8080"
fi

# Node for "Test-2294" started up and registered in 22.84 sec
docker logs -f corda-test-${SALT} | grep -q "started up and registered in"
if [ ! "$(docker ps -q -f name=corda-test-${SALT})" ]; then
    echo "TEST-DOCKER: FAIL corda-test has exited."
    docker logs corda-test-${SALT}
    rm -f $(pwd)/network-root-truststore.jks
    docker rm -f corda-test-${SALT}
    exit 1
else
    echo "TEST-DOCKER:  SUCCESS : Node started up and registered"
    rm -f $(pwd)/network-root-truststore.jks
    docker rm -f corda-test-${SALT}
    exit 0
fi
