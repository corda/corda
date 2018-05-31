#!/bin/bash

# Please ensure you run this script using source code (eg. GitHub master, branch or TAG) that reflects the version label defined below
# For example:
#   corda-master   => git clone https://github.com/corda/corda
#   r3corda-master => git clone https://github.com/corda/enterprise

# Please run this script from the corda source code directory
# eg. $ pwd
#     /myprojects/r3corda
#     $ testing/qa/behave/scripts/update-r3cordapps-cts.sh

VERSION=master
BUILD_DIR=`pwd`
SAMPLES_ROOT=`pwd`/../samples
STAGING_DIR=~/staging/corda/r3corda-${VERSION}

# Set up directories
mkdir -p ${STAGING_DIR}/apps

# Corda repository pinned cordapps
cd ${BUILD_DIR}

./gradlew samples:simm-valuation-demo:jar
cp -v $(ls samples/simm-valuation-demo/build/libs/simm-valuation-demo-*.jar | tail -n1) ${STAGING_DIR}/apps/simm-valuation-demo.jar
cp -v $(ls samples/simm-valuation-demo/build/libs/simm-valuation-demo-*.jar | tail -n1) ${STAGING_DIR}/proxy/simm-valuation-demo.jar

# Corda Enterprise only
./gradlew tools:notaryhealthcheck:cordaCompileJar
cp -v $(ls tools/notaryhealthcheck/build/libs/notaryhealthcheck-*.jar | tail -n1) ${STAGING_DIR}/apps/notaryhealthcheck.jar
cp -v $(ls tools/notaryhealthcheck/build/libs/notaryhealthcheck-*.jar | tail -n1) ${STAGING_DIR}/proxy/notaryhealthcheck.jar

# Independent cordapps
cd ${SAMPLES_ROOT}

# Options sample
cd cordapp-option
./gradlew clean jar publishToMavenLocal 
cp -v $(ls build/libs/cordapp-option-*.jar | tail -n1) ${STAGING_DIR}/apps
cp -v $(ls build/libs/cordapp-option-*.jar | tail -n1) ${STAGING_DIR}/proxy
