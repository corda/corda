#!/bin/bash

set -x

export GRADLE_BUILD_CACHE_URL="${GRADLE_BUILD_CACHE_URL:-http://localhost:5071/cache/}"
export USE_GRADLE_DAEMON="${USE_GRADLE_DAEMON:-false}"
export GRADLE_CACHE_DEBUG="${GRADLE_CACHE_DEBUG:-false}"
export PERFORM_GRADLE_SCAN="${PERFORM_GRADLE_SCAN:---scan}"

# cd %teamcity.build.checkoutDir%
echo "Using Gradle Build Cache: $GRADLE_BUILD_CACHE_URL"

# GRADLE HOME
if [ -z ${GRADLE_HOME+x} ]; then echo "Please set GRADLE_HOME variable"; else echo "GRADLE_HOME is set to '$GRADLE_HOME'"; fi
export GRADLE_HOME=~/gradle/install
export GRADLE_EXE=${GRADLE_HOME}/bin/gradle

echo "Gradle version:"
${GRADLE_EXE} --version
