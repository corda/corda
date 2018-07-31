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
BEHAVE_JAR=$(ls build/libs/corda-behave-*.jar | tail -n1)

if [ ! -f "${BEHAVE_JAR}" ]; then
    echo "Building behaveJar ..."
    ../../gradlew behaveJar
fi

STAGING_ROOT="${STAGING_ROOT:-TMPDIR/staging}"

# QA functional (specify -d for dry-run)
CMD="java -DSTAGING_ROOT=${STAGING_ROOT} -DDISABLE_CLEANUP=true -jar ${BEHAVE_JAR} -path ${R3CORDA_HOME}/testing/qa/behave/functional/resources/features/functional.feature"
if [ ! -z "$1" ]
  then
    java -DSTAGING_ROOT=${STAGING_ROOT} -DDISABLE_CLEANUP=true -jar ${BEHAVE_JAR} -path $1
    exit
fi
echo "Executing: ${CMD}"
eval `${CMD}`
