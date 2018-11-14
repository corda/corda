#!/usr/bin/env bash
: ${MY_PUBLIC_ADDRESS:? 'MY_PUBLIC_ADDRESS must be set as environment variable'}

: ${MY_P2P_PORT=4443}
: ${MY_RPC_PORT=10021}
: ${MY_RPC_ADMIN_PORT=10020}
: ${JVM_ARGS='-Xmx1g -Xms15g -XX:+UseG1GC'}

function generateTestnetConfig() {
    RPC_PASSWORD=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1) \
    DB_PASSWORD=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1) \
    MY_PUBLIC_ADDRESS=${MY_PUBLIC_ADDRESS} \
    MY_P2P_PORT=${MY_P2P_PORT} \
    MY_RPC_PORT=${MY_RPC_PORT} \
    MY_RPC_ADMIN_PORT=${MY_RPC_ADMIN_PORT} \
    COMPATIBILITY_ZONE='https://map.testnet.corda.network' \
    java -jar config-exporter.jar "TEST-NET-COMBINE" "node.conf" "/opt/corda/starting-node.conf" "${CONFIG_FOLDER}/node.conf"
}

function downloadTestnetCerts() {
    if [[ ! -f ${CERTIFICATES_FOLDER}/certs.zip ]]; then
        : ${ONE_TIME_DOWNLOAD_KEY:? '$ONE_TIME_DOWNLOAD_KEY must be set as environment variable'}
        : ${LOCALITY:? '$LOCALITY (the locality used when registering for testnet) must be set as environment variable'}
        : ${COUNTRY:? 'COUNTRY (the country used when registering for testnet) must be set as environment variable'}
        curl -L -d "{\"x500Name\":{\"locality\":\"${LOCALITY}\", \"country\":\"${COUNTRY}\"}, \"configType\": \"INSTALLSCRIPT\", \"include\": { \"systemdServices\": false, \"cordapps\": false, \"cordaJar\": false, \"cordaWebserverJar\": false, \"scripts\": false} }" \
        -H 'Content-Type: application/json' \
        -X POST "https://testnet.corda.network/api/user/node/generate/one-time-key/redeem/$ONE_TIME_DOWNLOAD_KEY" \
        -o "${CERTIFICATES_FOLDER}/certs.zip"
    fi
    rm -rf ${CERTIFICATES_FOLDER}/*.jks
    unzip ${CERTIFICATES_FOLDER}/certs.zip
}

while getopts ':c:' opt; do
  case $opt in
    c)
      echo 'Generate Config was selected' >&2
       case '$OPTARG' in
       testnet) echo 'Generating Config for TestNet'
            GENERATE_TEST_NET=YES
            ;;
       mainnet) echo 'Generating Config for MainNet'
            ;;
       *)   echo "Generating Config for CZ: $OPTARG"
            ;;
       esac
      ;;
    \?)
      echo 'Invalid option: -$OPTARG' >&2
      ;;
    :)
      echo 'Option -$OPTARG requires an argument.' >&2
      exit 1
  esac
done

if [[ ${GENERATE_TEST_NET} -eq 'YES' ]]
then
    downloadTestnetCerts
    generateTestnetConfig
fi

