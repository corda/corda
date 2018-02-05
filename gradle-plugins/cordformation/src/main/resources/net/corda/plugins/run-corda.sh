#!/bin/sh

# If variable not present use default values
: ${CORDA_HOME:=/opt/corda}
: ${JAVA_OPTIONS:=-Xmx512m}

export CORDA_HOME JAVA_OPTIONS

cd ${CORDA_HOME}
java $JAVA_OPTIONS -jar ${CORDA_HOME}/corda.jar 2>&1