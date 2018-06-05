#!/bin/bash

#
# Run this script from your ${R3CORDA_HOME}/experimental/behave directory, where R3CORDA_HOME refers to Corda Enterprise source code (eg. GitHub master, branch or TAG)
# For example:
#   R3CORDA_HOME => git clone https://github.com/corda/enterprise
#
# $ testing/qa/behave/compatibility/resources/scripts/run-interoperability.sh

R3CORDA_HOME=$PWD
BEHAVE_DIR=${R3CORDA_HOME}/experimental/behave
cd ${BEHAVE_DIR}
../../gradlew behaveJar

BEHAVE_JAR=$(ls build/libs/corda-behave-*.jar | tail -n1)
STAGING_ROOT="${STAGING_ROOT:-TMPDIR/staging}"

# QA interoperability (specify -d for dry-run)
java -DSTAGING_ROOT=${STAGING_ROOT} -DDISABLE_CLEANUP=true -jar ${BEHAVE_JAR} --glue net.corda.behave.scenarios -path ${R3CORDA_HOME}/testing/qa/behave/compatibility/resources/features/interoperability.feature
