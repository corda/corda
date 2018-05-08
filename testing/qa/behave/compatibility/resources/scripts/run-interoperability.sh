#!/bin/bash

#
# Run this script from your ${R3CORDA_HOME}/experimental/behave directory, where R3CORDA_HOME refers to R3 Corda source code (eg. GitHub master, branch or TAG)
# For example:
#   R3CORDA_HOME => git clone https://github.com/corda/enterprise
#
# $ testing/qa/behave/compatibility/resources/scripts/run-interoperability.sh

R3CORDA_HOME=$PWD
BEHAVE_DIR=${R3CORDA_HOME}/experimental/behave
cd ${BEHAVE_DIR}
../../gradlew behaveJar

# QA interoperability
java -jar ${BEHAVE_DIR}/build/libs/corda-behave.jar -d --glue net.corda.behave.scenarios -path ${R3CORDA_HOME}/testing/qa/behave/compatibility/resources/features/interoperability.feature
