#!/bin/bash

#
# Run this script from the experimental/behave directory
#
# $ pwd
# ./IdeaProjects/corda-reviews/experimental/behave
# $ src/scenario/resources/scripts/run-behave-features.sh
#
# Note: please ensure you have configured your staging environment by running the top-level script: prepare.sh

BUILD_DIR=$PWD
cd $BUILD_DIR
../../gradlew behaveJar

BEHAVE_JAR=$(ls build/libs/corda-behave-*.jar | tail -n1)
STAGING_ROOT=~/staging 

# startup
java -DSTAGING_ROOT=${STAGING_ROOT} -jar ${BEHAVE_JAR} --glue net.corda.behave.scenarios -path ./src/scenario/resources/features/startup/logging.feature

# cash
java -DSTAGING_ROOT=${STAGING_ROOT} -jar ${BEHAVE_JAR} --glue net.corda.behave.scenarios -path ./src/scenario/resources/features/cash/currencies.feature

# database
java -DSTAGING_ROOT=${STAGING_ROOT} -jar ${BEHAVE_JAR} --glue net.corda.behave.scenarios -path ./src/scenario/resources/features/cash/currencies.feature
