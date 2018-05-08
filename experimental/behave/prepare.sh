#!/bin/bash

set -x

# Please ensure you run this script using source code (eg. GitHub master, branch or TAG) that reflects the version label defined below
# For example:
#   corda-master   => git clone https://github.com/corda/corda
#   r3corda-master => git clone https://github.com/corda/enterprise
VERSION=r3corda-master
STAGING_DIR=~/staging
CORDA_DIR=${STAGING_DIR}/corda/${VERSION}
CORDAPP_DIR=${CORDA_DIR}/apps
DRIVERS_DIR=${STAGING_DIR}/drivers

# Set up directories
mkdir -p ${STAGING_DIR}
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
./gradlew samples:simm-valuation-demo:jar
cp -v $(ls samples/simm-valuation-demo/build/libs/simm-valuation-demo-*.jar | tail -n1) ${CORDAPP_DIR}

# Download database drivers
curl "https://search.maven.org/remotecontent?filepath=com/h2database/h2/1.4.196/h2-1.4.196.jar" > ${DRIVERS_DIR}/h2-1.4.196.jar
curl -L "http://central.maven.org/maven2/org/postgresql/postgresql/42.1.4/postgresql-42.1.4.jar" > ${DRIVERS_DIR}/postgresql-42.1.4.jar
curl -L "https://github.com/Microsoft/mssql-jdbc/releases/download/v6.2.2/mssql-jdbc-6.2.2.jre8.jar" > ${DRIVERS_DIR}/mssql-jdbc-6.2.2.jre8.jar

# Build Network Bootstrapper
./gradlew buildBootstrapperJar
cp -v $(ls tools/bootstrapper/build/libs/*.jar | tail -n1) ${CORDA_DIR}/network-bootstrapper.jar

# build and distribute Doorman/NMS
./gradlew network-management:capsule:buildDoormanJAR
cp -v $(ls network-management/capsule/build/libs/doorman-*.jar | tail -n1) ${CORDA_DIR}/doorman.jar

# build and distribute DB Migration tool
./gradlew tools:dbmigration:shadowJar
cp -v $(ls tools/dbmigration/build/libs/*migration-*.jar | tail -n1) ${CORDA_DIR}/dbmigration.jar

