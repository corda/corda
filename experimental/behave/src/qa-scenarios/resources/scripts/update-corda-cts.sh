#!/bin/bash

# Please ensure you run this script using source code (eg. GitHub master, branch or TAG) that reflects the version label defined below
# For example:
#   corda-master   => git clone https://github.com/corda/corda
#   r3corda-master => git clone https://github.com/corda/enterprise
VERSION=master
BUILD_DIR=/Users/josecoll/IdeaProjects/corda-master
STAGING_DIR=/Users/josecoll/IdeaProjects/corda-reviews/experimental/behave/deps/corda/corda-${VERSION}

# Set up directories
rm -rf ${STAGING_DIR}
mkdir -p ${STAGING_DIR}/apps

# fetch latest from git
cd $BUILD_DIR
git checkout ${VERSION}
git pull

echo "*************************************************************"
echo "Building and installing $VERSION from $BUILD_DIR"
echo " to $STAGING_DIR"
echo "*************************************************************"

# Copy Corda capsule into deps
./gradlew clean install
cp -v $(ls node/capsule/build/libs/corda-*.jar | tail -n1) ${STAGING_DIR}/corda.jar
cp -v $(ls finance/build/libs/corda-finance-*.jar | tail -n1) ${STAGING_DIR}/apps

# Build Network Bootstrapper
./gradlew buildBootstrapperJar
cp -v $(ls tools/bootstrapper/build/libs/*.jar | tail -n1) ${STAGING_DIR}/network-bootstrapper.jar

# Build rpcProxy (required by CTS Scenario Driver to call Corda 3.0 which continues to use Kryo for RPC)
# cd experimental/behave
# ../../gradlew rpcProxyJar
# cp -v $(ls build/libs/corda-rpcProxy*.jar | tail -n1) ${STAGING_DIR}/corda-rpcProxy.jar
