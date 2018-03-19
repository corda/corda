#!/bin/bash

# Please ensure you run this script using source code (eg. GitHub master, branch or TAG) that reflects the version label defined below
# For example:
#   corda-master   => git clone https://github.com/corda/corda
#   r3corda-master => git clone https://github.com/corda/enterprise
BUILD_DIR=/Users/josecoll/IdeaProjects/corda-reviews
STAGING_DIR=/Users/josecoll/IdeaProjects/corda-reviews/experimental/behave/deps/corda/r3corda-master

cd $BUILD_DIR

# Build rpcProxy (required for by Driver to call Corda 3.0 which continues to use Kryo for RPC)
cd experimental/behave
../../gradlew rpcProxyJar
cp -v $(ls build/libs/corda-rpcProxy*.jar | tail -n1) ${STAGING_DIR}/corda-rpcProxy.jar