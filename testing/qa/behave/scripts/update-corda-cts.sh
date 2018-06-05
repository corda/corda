#!/bin/bash

# Please ensure you run this script using source code (eg. GitHub master, branch or TAG) that reflects the version label defined below
# For example:
#   corda-master   => git clone https://github.com/corda/corda
#   r3corda-master => git clone https://github.com/corda/enterprise

# Please run this script from the corda source code directory
# eg. $ pwd
#     /myprojects/corda
#     $ testing/qa/behave/scripts/update-corda-cts.sh

VERSION=master
BUILD_DIR=`pwd`
STAGING_DIR="${STAGING_ROOT:-$TMPDIR/staging}"
echo "Staging directory: $STAGING_DIR"

CORDA_DIR=${STAGING_DIR}/corda/corda-${VERSION}
echo "Corda staging directory: $CORDA_DIR"

# Set up directories
mkdir -p ${STAGING_DIR}/apps

cd ${BUILD_DIR}

echo "*************************************************************"
echo "Building and installing $VERSION from $BUILD_DIR"
echo " to $CORDA_DIR"
echo "*************************************************************"

# Copy Corda capsule into deps
./gradlew clean install
cp -v $(ls node/capsule/build/libs/corda-*.jar | tail -n1) ${CORDA_DIR}/corda.jar
cp -v $(ls finance/build/libs/corda-finance-*.jar | tail -n1) ${CORDA_DIR}/apps

# Build Network Bootstrapper
./gradlew buildBootstrapperJar
cp -v $(ls tools/bootstrapper/build/libs/*.jar | tail -n1) ${CORDA_DIR}/network-bootstrapper.jar

# Build rpcProxy (required by CTS Scenario Driver to call Corda 3.0 which continues to use Kryo for RPC)
./gradlew testing:qa:behave:tools:rpc-proxy:rpcProxyJar
cp -v $(ls testing/qa/behave/tools/rpc-proxy/build/libs/corda-rpcProxy*.jar | tail -n1) ${CORDA_DIR}/corda-rpcProxy.jar
cp -v testing/qa/behave/tools/rpc-proxy/startRPCproxy.sh ${CORDA_DIR}