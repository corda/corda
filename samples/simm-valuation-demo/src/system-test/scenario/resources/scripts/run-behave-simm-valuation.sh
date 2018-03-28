#!/bin/bash

#
# Run this script from the sample directory
#
# $ pwd
# ./IdeaProjects/corda-reviews/samples/simm-valuation-demo
# $ src/system-test/scenario/resources/scripts/run-behave-simm-valuation.sh
#
#

CURRENT_DIR=$PWD

BEHAVE_DIR=$CURRENT_DIR/../../experimental/behave
cd $BEHAVE_DIR
../../gradlew behaveJar

DEMO_DIR=$CURRENT_DIR
cd $DEMO_DIR
../../gradlew scenarioJar

echo $BEHAVE_DIR
java -cp "$BEHAVE_DIR/build/libs/corda-behave.jar:$CURRENT_DIR/build/libs/simm-valuation-demo-behave-test.jar" net.corda.behave.scenarios.ScenarioRunner --glue "net.corda.behave.scenarios" -path ./src/system-test/scenario/resources/features/simm-valuation.feature -d
# -d to perform dry-run