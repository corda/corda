#!/usr/bin/env bash
NODE_LIST=("dockerNode1" "dockerNode2" "dockerNode3")
NETWORK_NAME=mininet
CORDAPP_VERSION="4.8.1"
DOCKER_IMAGE_VERSION="corda-zulu-4.8.1"

mkdir cordapps
rm -f cordapps/*

wget -O cordapps/finance-contracts.jar          https://software.r3.com/artifactory/list/corda-dev/net/corda/corda-finance-contracts/${CORDAPP_VERSION}/corda-finance-contracts-${CORDAPP_VERSION}.jar
wget -O cordapps/finance-workflows.jar          https://software.r3.com/artifactory/list/corda-dev/net/corda/corda-finance-workflows/${CORDAPP_VERSION}/corda-finance-workflows-${CORDAPP_VERSION}.jar
wget -O cordapps/confidential-identities.jar    https://software.r3.com/artifactory/list/corda-dev/net/corda/corda-confidential-identities/${CORDAPP_VERSION}/corda-confidential-identities-${CORDAPP_VERSION}.jar

rm keystore

keytool -genkey -noprompt \
  -alias alias1 \
  -dname "CN=totally_not_r3, OU=ID, O=definitely_not_r3, L=LONDON, S=LONDON, C=GB" \
  -keystore keystore \
  -storepass password \
  -keypass password \
  -keyalg EC \
  -keysize 256 \
  -sigalg SHA256withECDSA

jarsigner -keystore keystore -storepass password -keypass password cordapps/finance-workflows.jar alias1
jarsigner -keystore keystore -storepass password -keypass password cordapps/finance-contracts.jar alias1
jarsigner -keystore keystore -storepass password -keypass password cordapps/confidential-identities.jar alias1

for NODE in ${NODE_LIST[*]}
do
    echo Building ${NODE} directory
    rm -rf ${NODE}
    mkdir ${NODE}
    mkdir ${NODE}/config
    mkdir ${NODE}/certificates
    mkdir ${NODE}/logs
    mkdir ${NODE}/persistence
done

docker rm -f  netmap
docker network rm -f ${NETWORK_NAME}

docker network create --attachable ${NETWORK_NAME}
docker run -d \
            -p 18080:8080 \
            -p 10200:10200 \
            --name netmap \
            -e PUBLIC_ADDRESS=netmap \
            --network="${NETWORK_NAME}" \
            roastario/notary-and-network-map:latest

let EXIT_CODE=255
while [ ${EXIT_CODE} -gt 0 ]
do
    sleep 2
    echo "Waiting for network map to start"
    curl --max-time 2 -s http://localhost:18080/network-map > /dev/null
    let EXIT_CODE=$?
done

for NODE in ${NODE_LIST[*]}
do
    wget -O ${NODE}/certificates/network-root-truststore.jks http://localhost:18080/truststore
    docker rm -f ${NODE}
    docker run -d \
            -e MY_LEGAL_NAME="O=${NODE},L=Berlin,C=DE"     \
            -e MY_PUBLIC_ADDRESS="${NODE}"                \
            -e NETWORKMAP_URL="http://netmap:8080"      \
            -e DOORMAN_URL="http://netmap:8080"         \
            -e NETWORK_TRUST_PASSWORD="trustpass"       \
            -e MY_EMAIL_ADDRESS="${NODE}@r3.com"      \
            -e MY_RPC_PORT="1100"$(echo ${NODE} | sed 's/[^0-9]*//g')  \
            -e RPC_PASSWORD="testingPassword" \
            -v $(pwd)/${NODE}/config:/etc/corda          \
            -v $(pwd)/${NODE}/certificates:/opt/corda/certificates \
            -v $(pwd)/${NODE}/logs:/opt/corda/logs \
            -v $(pwd)/${NODE}/persistence:/opt/corda/persistence \
            -v $(pwd)/cordapps:/opt/corda/cordapps \
            -p "1100"$(echo ${NODE} | sed 's/[^0-9]*//g'):"1100"$(echo ${NODE} | sed 's/[^0-9]*//g') \
            -p "222$(echo ${NODE} | sed 's/[^0-9]*//g')":"222$(echo ${NODE} | sed 's/[^0-9]*//g')" \
            -e CORDA_ARGS="--sshd --sshd-port=222$(echo ${NODE} | sed 's/[^0-9]*//g')" \
            --name ${NODE} \
            --network="${NETWORK_NAME}" \
            corda/${DOCKER_IMAGE_VERSION}:latest config-generator --generic
done
