#!/bin/bash

# Please ensure you run this script using source code (eg. GitHub master, branch or TAG) that reflects the version label defined below
# For example:
#   corda-master   => git clone https://github.com/corda/corda
#   r3corda-master => git clone https://github.com/corda/enterprise

# Please run this script from the corda source code directory
# eg. $ pwd
#     /myprojects/r3corda
#     $ testing/qa/behave/scripts/update-r3corda-cts.sh

VERSION=master
BUILD_DIR=`pwd`
STAGING_DIR=~/staging/corda/r3corda-${VERSION}

# Set up directories
mkdir -p ${STAGING_DIR}/apps

cd ${BUILD_DIR}

echo "*************************************************************"
echo "Building and installing $VERSION from $BUILD_DIR"
echo "to $STAGING_DIR"
echo "*************************************************************"

# Copy Corda capsule into deps
./gradlew clean install
cp -v $(ls node/capsule/build/libs/corda-*.jar | tail -n1) ${STAGING_DIR}/corda.jar

# Copy Corda libraries into apps
cp -v $(ls finance/build/libs/corda-finance-*.jar | tail -n1) ${STAGING_DIR}/apps

# build and distribute Doorman/NMS 
./gradlew network-management:capsule:buildDoormanJAR
cp -v $(ls network-management/capsule/build/libs/doorman-*.jar | tail -n1) ${STAGING_DIR}/doorman.jar

# build and distribute DB Migration tool
./gradlew tools:dbmigration:shadowJar
cp -v $(ls tools/dbmigration/build/libs/*migration-*.jar | tail -n1) ${STAGING_DIR}/dbmigration.jar

# Build rpcProxy (required by CTS Scenario Driver to call Corda 3.0 which continues to use Kryo for RPC)
./gradlew testing:qa:behave:tools:rpc-proxy:rpcProxyJar
cp -v $(ls testing/qa/behave/tools/rpc-proxy/build/libs/corda-rpcProxy*.jar | tail -n1) ${STAGING_DIR}/corda-rpcProxy.jar
cp -v testing/qa/behave/tools/rpc-proxy/startRPCproxy.sh ${STAGING_DIR}