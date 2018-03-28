#!/bin/bash

# Please ensure you run this script using source code (eg. GitHub master, branch or TAG) that reflects the version label defined below
# For example:
#   corda-master   => git clone https://github.com/corda/corda
#   r3corda-master => git clone https://github.com/corda/enterprise
VERSION=master
BUILD_DIR=/Users/josecoll/IdeaProjects/r3corda
SAMPLES_ROOT=/Users/josecoll/IdeaProjects
STAGING_DIR=/Users/josecoll/IdeaProjects/corda-reviews/experimental/behave/deps/corda/r3corda-${VERSION}

# Set up directories
mkdir -p ${STAGING_DIR}/apps
mkdir -p ${STAGING_DIR}/proxy

# Corda repository pinned cordapps
cd $BUILD_DIR

./gradlew samples:simm-valuation-demo:jar
cp -v $(ls samples/simm-valuation-demo/build/libs/simm-valuation-demo-*.jar | tail -n1) ${STAGING_DIR}/apps

# Independent cordapps
cd $SAMPLES_ROOT

# Options sample
cd cordapp-option
./gradlew clean jar publishToMavenLocal 
cp -v $(ls build/libs/cordapp-option-*.jar | tail -n1) ${STAGING_DIR}/apps
cp -v $(ls build/libs/cordapp-option-*.jar | tail -n1) ${STAGING_DIR}/proxy
