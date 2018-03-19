#!/bin/bash

# Please ensure you run this script using source code (eg. GitHub master, branch or TAG) that reflects the version label defined below
# For example:
#   corda-master   => git clone https://github.com/corda/corda
#   r3corda-master => git clone https://github.com/corda/enterprise
GRADLE_BUILD_DIR=/Users/josecoll/IdeaProjects/corda-gradle-plugins

# fetch latest from git
cd $GRADLE_BUILD_DIR
git checkout ${VERSION}
git pull

# update Gradle plugins (note this is being moved to a new repo https://github.com/corda/corda-gradle-plugins from v4.x.x )
echo "*************************************************************"
echo "Building and installing `cat constants.properties | grep gradlePluginsVersion`"
echo "*************************************************************"

cd publish-utils
../gradlew -u install
cd ../
./gradlew install
cd ..
