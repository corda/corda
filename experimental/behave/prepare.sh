#!/bin/bash

VERSION=3.0.0

# Set up directories
mkdir -p deps/corda/${VERSION}/apps
mkdir -p deps/drivers

# Copy Corda capsule into deps
cp $(ls ../../node/capsule/build/libs/corda-*.jar | tail -n1) deps/corda/${VERSION}/corda.jar

# Download database drivers
curl "https://search.maven.org/remotecontent?filepath=com/h2database/h2/1.4.196/h2-1.4.196.jar" > deps/drivers/h2-1.4.196.jar
curl -L "https://github.com/Microsoft/mssql-jdbc/releases/download/v6.2.2/mssql-jdbc-6.2.2.jre8.jar" > deps/drivers/mssql-jdbc-6.2.2.jre8.jar

# Build required artefacts
cd ../..
./gradlew buildBootstrapperJar
./gradlew :finance:jar

# Copy build artefacts into deps
cd experimental/behave
cp ../../tools/bootstrapper/build/libs/*.jar deps/corda/${VERSION}/network-bootstrapper.jar
cp ../../finance/build/libs/corda-finance-*.jar deps/corda/${VERSION}/apps/corda-finance.jar
