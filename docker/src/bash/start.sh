#!/usr/bin/env bash
: ${MY_PUBLIC_ADDRESS:? "MY_PUBLIC_ADDRESS must be set as environment variable"}

: ${MY_P2P_PORT=443}
: ${MY_RPC_PORT=10021}
: ${MY_RPC_ADMIN_PORT=10020}
: ${RPC_PASSWORD=testUser1}
: ${JVM_ARGS="-Xmx1g -Xms15g -XX:+UseG1GC"}

echo ${MY_RPC_PORT}
echo ${MY_RPC_ADMIN_PORT}
echo ${RPC_PASSWORD}
echo ${JVM_ARGS}

#export MY_LEGAL_NAME=${MY_LEGAL_NAME}
#export MY_PUBLIC_ADDRESS=${MY_PUBLIC_ADDRESS}
#export MY_P2P_PORT=${MY_P2P_PORT}
#export MY_RPC_PORT=${MY_RPC_PORT}
#export MY_RPC_ADMIN_PORT=${MY_RPC_ADMIN_PORT}
#export RPC_PASSWORD=${RPC_PASSWORD}
#export JVM_ARGS=${JVM_ARGS}

function generateTestnetConfig() {
    MY_PUBLIC_ADDRESS=${MY_PUBLIC_ADDRESS} MY_P2P_PORT=${MY_P2P_PORT} \
    MY_RPC_PORT=${MY_RPC_PORT} MY_RPC_ADMIN_PORT=${MY_RPC_ADMIN_PORT} RPC_PASSWORD=${RPC_PASSWORD} COMPATIBILITY_ZONE="https://map.testnet.corda.network" \
    java -jar config-exporter.jar "TEST-NET-COMBINE" "node.conf" "starting-node.conf" "${CONFIG_FOLDER}/node.conf"
}

function downloadTestnetCerts() {
    : ${ONE_TIME_DOWNLOAD_KEY:? "ONE_TIME_DOWNLOAD_KEY must be set as environment variable"}
    curl -L -d '{"x500Name":{"locality":"London", "country":"GB"}, "configType": "INSTALLSCRIPT", "include": { "systemdServices": false, "cordapps": false, "cordaJar": false, "cordaWebserverJar": false, "scripts": false} }' \
    -H "Content-Type: application/json" \
    -X POST "https://testnet.corda.network/api/user/node/generate/one-time-key/redeem/$ONE_TIME_DOWNLOAD_KEY" \
    -o "certs.zip"
    unzip certs.zip
}

while getopts ":c:" opt; do
  case $opt in
    c)
      echo "Generate Config was selected" >&2
       case "$OPTARG" in
       testnet) echo "Generating Config for TestNet"
            GENERATE_TEST_NET=YES
            ;;
       mainnet) echo "Generating Config for MainNet"
            ;;
       *)   echo "Generating Config for CZ: $OPTARG"
            ;;
       esac
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      ;;
    :)
      echo "Option -$OPTARG requires an argument." >&2
      exit 1
  esac
done

if [[ ${GENERATE_TEST_NET} -eq "YES" ]]
then
    downloadTestnetCerts
    generateTestnetConfig
fi

