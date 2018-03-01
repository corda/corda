#!/bin/bash

# Please ensure you run this script using source code (eg. GitHub master, branch or TAG) that reflects the version label defined below
# For example:
#   corda-master   => git clone https://github.com/corda/corda
#   r3corda-master => git clone https://github.com/corda/enterprise
VERSION=corda-master

# Set up directories
mkdir -p deps/corda/${VERSION}/apps
mkdir -p deps/drivers

# Copy Corda capsule into deps
cp -v $(ls ../../node/capsule/build/libs/corda-*.jar | tail -n1) deps/corda/${VERSION}/corda.jar

# Download database drivers
curl "https://search.maven.org/remotecontent?filepath=com/h2database/h2/1.4.196/h2-1.4.196.jar" > deps/drivers/h2-1.4.196.jar
curl -L "https://github.com/Microsoft/mssql-jdbc/releases/download/v6.2.2/mssql-jdbc-6.2.2.jre8.jar" > deps/drivers/mssql-jdbc-6.2.2.jre8.jar

# Build required artefacts
cd ../..
./gradlew buildBootstrapperJar
./gradlew :finance:jar

# Copy build artefacts into deps
cd experimental/behave
cp -v $(ls ../../tools/bootstrapper/build/libs/*.jar | tail -n1) deps/corda/${VERSION}/network-bootstrapper.jar
cp -v $(ls ../../finance/build/libs/corda-finance-*.jar | tail -n1) deps/corda/${VERSION}/apps/corda-finance.jar

# Build rpcProxy (required for by Driver to call Corda 3.0 which continues to use Kryo for RPC)
../../gradlew rpcProxyJar
cp -v $(ls build/libs/corda-rpcProxy*.jar | tail -n1) deps/corda/${VERSION}/corda-rpcProxy.jar

