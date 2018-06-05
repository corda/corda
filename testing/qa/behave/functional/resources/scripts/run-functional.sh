#!/bin/bash

#
# Run this script from your $R3CORDA_HOME directory, where R3CORDA_HOME refers to R3 Corda source code (eg. GitHub master, branch or TAG)
# For example:
#   R3CORDA_HOME => git clone https://github.com/corda/enterprise
#
# $ testing/qa/behave/functional/resources/scripts/run-functional.sh


R3CORDA_HOME=$PWD
BEHAVE_DIR=${R3CORDA_HOME}/experimental/behave
cd ${BEHAVE_DIR}
../../gradlew behaveJar

BEHAVE_JAR=$(ls build/libs/corda-behave-*.jar | tail -n1)
STAGING_ROOT="${STAGING_ROOT:-TMPDIR/staging}"

# QA functional (specify -d for dry-run)
java -DSTAGING_ROOT=${STAGING_ROOT} -DDISABLE_CLEANUP=true -jar ${BEHAVE_JAR} --glue net.corda.behave.scenarios -path ${R3CORDA_HOME}/testing/qa/behave/functional/resources/features/functional.feature