#!/bin/bash

#
# Run this script from the experimental/behave directory
#
# $ pwd
# ./IdeaProjects/corda-reviews/experimental/behave
# $ src/scenario/resources/scripts/run-behave-features.sh

BUILD_DIR=$PWD
cd $BUILD_DIR
../../gradlew behaveJar

# startup
java -jar $BUILD_DIR/build/libs/corda-behave.jar --glue net.corda.behave.scenarios -path ./src/scenario/resources/features/startup/logging.feature:6
#java -jar $BUILD_DIR/build/libs/corda-behave.jar --glue net.corda.behave.scenarios -path ./src/scenario/resources/features/startup/logging.feature

# cash
#java -jar $BUILD_DIR/build/libs/corda-behave.jar -d --glue net.corda.behave.scenarios -path ./src/scenario/resources/features/cash/currencies.feature

# database
#java -jar $BUILD_DIR/build/libs/corda-behave.jar -d --glue net.corda.behave.scenarios -path ./src/scenario/resources/features/cash/currencies.feature
