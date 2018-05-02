#!/bin/bash

# Please ensure you run this script using source code (eg. GitHub master, branch or TAG) that reflects the version label defined below
# For example:
#   corda-master   => git clone https://github.com/corda/corda
#   r3corda-master => git clone https://github.com/corda/enterprise
#   from v4.x.x    => https://github.com/corda/corda-gradle-plugins

# Please run this script from the corda source code directory
# eg. $ pwd
#     /myprojects/r3corda
#     $ testing/qa/behave/scripts/update-gradle-plugins.sh

VERSION=master
BUILD_DIR=`pwd`
STAGING_DIR=~/staging/corda/corda-${VERSION}

# uses gradle plugins directory
cd ${BUILD_DIR}

# update Gradle plugins (note this is being moved to a new repo https://github.com/corda/corda-gradle-plugins from v4.x.x )
echo "*************************************************************"
echo "Building and installing `cat constants.properties | grep gradlePluginsVersion`"
echo "*************************************************************"

cd publish-utils
../gradlew -u install
cd ../
./gradlew install
cd ..
