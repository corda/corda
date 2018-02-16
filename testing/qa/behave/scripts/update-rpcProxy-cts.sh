#!/bin/bash

# Please ensure you run this script using source code (eg. GitHub master, branch or TAG) that reflects the version label defined below
# For example:
#   corda-master   => git clone https://github.com/corda/corda
#   r3corda-master => git clone https://github.com/corda/enterprise

# Please run this script from the corda source code directory
# eg. $ pwd
#     /myprojects/r3corda
#     $ testing/qa/behave/scripts/update-rpcProxy-cts.sh

VERSION=master
BUILD_DIR=`pwd`
STAGING_DIR=~/staging/corda/corda-${VERSION}

# Set up directories
mkdir -p ${STAGING_DIR}/apps

cd ${BUILD_DIR}

# Build rpcProxy (required by CTS Scenario Driver to call Corda 3.0 which continues to use Kryo for RPC)
./gradlew testing:qa:behave:tools:rpc-proxy:rpcProxyJar
cp -v $(ls testing/qa/behave/tools/rpc-proxy/build/libs/corda-rpcProxy*.jar | tail -n1) ${STAGING_DIR}/corda-rpcProxy.jar
cp -v testing/qa/behave/tools/rpc-proxy/startRPCproxy.sh ${STAGING_DIR}
