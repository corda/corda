#!/bin/bash

set -x

# Please ensure you run this script using source code (eg. GitHub master, branch or TAG) that reflects the version label defined below
# For example:
#   corda-master   => git clone https://github.com/corda/corda
#   r3corda-master => git clone https://github.com/corda/enterprise
VERSION=r3corda-master

STAGING_DIR="${STAGING_ROOT:-$TMPDIR/staging}"
echo "Staging directory: $STAGING_DIR"

CORDA_DIR=${STAGING_DIR}/corda/${VERSION}
echo "Corda staging directory: $CORDA_DIR"

CORDAPP_DIR=${CORDA_DIR}/apps
echo "CorDapp staging directory: $CORDAPP_DIR"

DRIVERS_DIR=${STAGING_DIR}/drivers
echo "Drivers staging directory: $DRIVERS_DIR"

# Set up directories
mkdir -p ${STAGING_DIR} || { echo "Unable to create directory $STAGING_DIR"; exit; }
mkdir -p ${CORDA_DIR}
mkdir -p ${CORDAPP_DIR}
mkdir -p ${DRIVERS_DIR}

# Copy Corda capsule into staging
cd ../..
./gradlew :node:capsule:buildCordaJar :finance:jar
cp -v $(ls node/capsule/build/libs/corda-*.jar | tail -n1) ${CORDA_DIR}/corda.jar

# Copy finance library
cp -v $(ls finance/build/libs/corda-finance-*.jar | tail -n1) ${CORDAPP_DIR}

# Copy sample Cordapps

# SIMM valuation demo
./gradlew samples:simm-valuation-demo:jar
cp -v $(ls samples/simm-valuation-demo/build/libs/simm-valuation-demo-*.jar | tail -n1) ${CORDAPP_DIR}
./gradlew samples:simm-valuation-demo:contracts-states:jar
cp -v $(ls samples/simm-valuation-demo/contracts-states/build/libs/contracts-states-*.jar | tail -n1) ${CORDAPP_DIR}
./gradlew samples:simm-valuation-demo:flows:jar
cp -v $(ls samples/simm-valuation-demo/flows/build/libs/flows-*.jar | tail -n1) ${CORDAPP_DIR}

# Download database drivers
curl "https://search.maven.org/remotecontent?filepath=com/h2database/h2/1.4.196/h2-1.4.196.jar" > ${DRIVERS_DIR}/h2-1.4.196.jar
curl -L "http://central.maven.org/maven2/org/postgresql/postgresql/42.1.4/postgresql-42.1.4.jar" > ${DRIVERS_DIR}/postgresql-42.1.4.jar
curl -L "https://github.com/Microsoft/mssql-jdbc/releases/download/v6.2.2/mssql-jdbc-6.2.2.jre8.jar" > ${DRIVERS_DIR}/mssql-jdbc-6.2.2.jre8.jar
curl -L "http://www.datanucleus.org/downloads/maven2/oracle/ojdbc6/11.2.0.3/ojdbc6-11.2.0.3.jar" > ${DRIVERS_DIR}/ojdbc6.jar
# The following download requires an account authenticated against the Oracle Technology Network.
#curl -L "http://download.oracle.com/otn/utilities_drivers/jdbc/122010/ojdbc8.jar" > ${DRIVERS_DIR}/ojdbc8.jar

# Build Network Bootstrapper
./gradlew tools:bootstrapper:jar
cp -v $(ls tools/bootstrapper/build/libs/*.jar | tail -n1) ${CORDA_DIR}/network-bootstrapper.jar

# TODO: resolve Doorman/NMS artifacts from new artifactory location.
# Doorman/NMS has moved to a separate repo: https://github.com/corda/network-services,
# now follows a different release cycle (and versioning scheme), and is published to a new artifactory location:
# https://ci-artifactory.corda.r3cev.com/artifactory/r3-corda-releases/com/r3/corda/networkservices/doorman/

# build and distribute DB Migration tool
./gradlew tools:dbmigration:shadowJar
cp -v $(ls tools/dbmigration/build/libs/database-manager-*.jar | tail -n1) ${CORDA_DIR}/database-manager.jar

# Build rpcProxy (required by CTS Scenario Driver to call Corda 3.0 which continues to use Kryo for RPC)
./gradlew testing:qa:behave:tools:rpc-proxy:rpcProxyJar
cp -v $(ls testing/qa/behave/tools/rpc-proxy/build/libs/corda-rpcProxy*.jar | tail -n1) ${CORDA_DIR}/corda-rpcProxy.jar
cp -v testing/qa/behave/tools/rpc-proxy/startRPCproxy.sh ${CORDA_DIR}

