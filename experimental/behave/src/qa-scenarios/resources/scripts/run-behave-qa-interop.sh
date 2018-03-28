#!/bin/bash

#
# Run this script from the experimental/behave directory
#
# $ pwd
# ./IdeaProjects/corda-reviews/experimental/behave
# $ src/qa-scenarios/resources/scripts/run-behave-qa.sh

BUILD_DIR=$PWD
cd $BUILD_DIR
../../gradlew behaveJar

# QA interoperability
java -jar $BUILD_DIR/build/libs/corda-behave.jar -d --glue net.corda.behave.scenarios -path ./src/qa-scenarios/resources/features/qa-interop-testing.feature
