#!/usr/bin/env bash

die() {
    printf '%s\n' "$1" >&2
    exit 1
}

show_help(){
    echo "usage: generate-config <--testnet>|<--generic>"
    echo -e "\t --testnet is used to generate config and certificates for joining TestNet"
    echo -e "\t --generic is used to generate config and certificates for joining an existing Corda Compatibility Zone"
}

function generateTestnetConfig() {
    : ${RPC_PASSWORD=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)}
    RPC_PASSWORD=${RPC_PASSWORD} \
    DB_PASSWORD=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1) \
    MY_PUBLIC_ADDRESS=${MY_PUBLIC_ADDRESS} \
    MY_P2P_PORT=${MY_P2P_PORT} \
    MY_RPC_PORT=${MY_RPC_PORT} \
    MY_RPC_ADMIN_PORT=${MY_RPC_ADMIN_PORT} \
    NETWORKMAP_URL='https://map.testnet.corda.network' \
    DOORMAN_URL='https://doorman.testnet.corda.network' \
    java -jar config-exporter.jar "TEST-NET-COMBINE" "node.conf" "/opt/corda/starting-node.conf" "${CONFIG_FOLDER}/node.conf"
}

function generateGenericCZConfig(){
    if ! [[ -f ${CONFIG_FOLDER}/node.conf ]] ; then
        echo  'INFO: no existing node config detected, generating config skeleton'
        : ${NETWORKMAP_URL:? '$NETWORKMAP_URL, the Compatibility Zone to join must be set as environment variable'}
        : ${DOORMAN_URL:? '$DOORMAN_URL, the Doorman to use when joining must be set as environment variable'}
        : ${MY_LEGAL_NAME:? '$MY_LEGAL_NAME, the X500 name to use when joining must be set as environment variable'}
        : ${MY_EMAIL_ADDRESS:? '$MY_EMAIL_ADDRESS, the email to use when joining must be set as an environment variable'}
        : ${NETWORK_TRUST_PASSWORD=:? '$NETWORK_TRUST_PASSWORD, the password to the network store to use when joining must be set as environment variable'}

        if [[ ! -f ${CERTIFICATES_FOLDER}/${TRUST_STORE_NAME} ]]; then
            die "Network Trust Root file not found"
        fi
        : ${RPC_PASSWORD=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)}
        RPC_PASSWORD=${RPC_PASSWORD} \
        DB_PASSWORD=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1) \
        MY_PUBLIC_ADDRESS=${MY_PUBLIC_ADDRESS} \
        MY_P2P_PORT=${MY_P2P_PORT} \
        MY_RPC_PORT=${MY_RPC_PORT} \
        MY_RPC_ADMIN_PORT=${MY_RPC_ADMIN_PORT} \
        java -jar config-exporter.jar "GENERIC-CZ" "/opt/corda/starting-node.conf" "${CONFIG_FOLDER}/node.conf"
    fi
        java -Djava.security.egd=file:/dev/./urandom -Dcapsule.jvm.args="${JVM_ARGS}" -jar /opt/corda/bin/corda.jar \
                --initial-registration \
                --base-directory /opt/corda \
                --config-file ${CONFIG_FOLDER}/node.conf \
                --network-root-truststore-password ${NETWORK_TRUST_PASSWORD} \
                --network-root-truststore ${CERTIFICATES_FOLDER}/${TRUST_STORE_NAME} && \
        echo "Successfully registered with ${DOORMAN_URL}, starting corda" && \
        run-corda
}

function downloadTestnetCerts() {
    if [[ ! -f ${CERTIFICATES_FOLDER}/certs.zip ]]; then
        : ${ONE_TIME_DOWNLOAD_KEY:? '$ONE_TIME_DOWNLOAD_KEY must be set as environment variable'}
        : ${LOCALITY:? '$LOCALITY (the locality used when registering for Testnet) must be set as environment variable'}
        : ${COUNTRY:? '$COUNTRY (the country used when registering for Testnet) must be set as environment variable'}
        curl -L -d "{\"x500Name\":{\"locality\":\"${LOCALITY}\", \"country\":\"${COUNTRY}\"}, \"configType\": \"INSTALLSCRIPT\", \"include\": { \"systemdServices\": false, \"cordapps\": false, \"cordaJar\": false, \"cordaWebserverJar\": false, \"scripts\": false} }" \
        -H 'Content-Type: application/json' \
        -X POST "https://testnet.corda.network/api/user/node/generate/one-time-key/redeem/$ONE_TIME_DOWNLOAD_KEY" \
        -o "${CERTIFICATES_FOLDER}/certs.zip"
    fi
    rm -rf ${CERTIFICATES_FOLDER}/*.jks
    unzip ${CERTIFICATES_FOLDER}/certs.zip
}

GENERATE_TEST_NET=0
GENERATE_GENERIC=0

while :; do
    case $1 in
        -h|-\?|--help)
            show_help    # Display a usage synopsis.
            exit
            ;;
        -t|--testnet)
            if [[ ${GENERATE_GENERIC} = 0 ]]; then
                GENERATE_TEST_NET=1
            else
                die 'ERROR: cannot generate config for multiple networks'
            fi
            ;;
        -g|--generic)
            if [[ ${GENERATE_TEST_NET} = 0 ]]; then
                GENERATE_GENERIC=1
            else
                die 'ERROR: cannot generate config for multiple networks'
            fi
            ;;
        --)              # End of all options.
            shift
            break
            ;;
        -?*)
            printf 'WARN: Unknown option (ignored): %s\n' "$1" >&2
            ;;
        *)               # Default case: No more options, so break out of the loop.
            break
    esac
    shift
done

: ${TRUST_STORE_NAME="network-root-truststore.jks"}
: ${JVM_ARGS='-Xmx4g -Xms2g -XX:+UseG1GC'}


if [[ ${GENERATE_TEST_NET} == 1 ]]
then
    : ${MY_PUBLIC_ADDRESS:? 'MY_PUBLIC_ADDRESS must be set as environment variable'}
    downloadTestnetCerts
    generateTestnetConfig
elif [[ ${GENERATE_GENERIC} == 1 ]]
then
    : ${MY_PUBLIC_ADDRESS:? 'MY_PUBLIC_ADDRESS must be set as environment variable'}
    generateGenericCZConfig
else
    show_help
    die "No Valid Configuration requested"
fi

