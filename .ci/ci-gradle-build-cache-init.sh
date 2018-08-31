#!/bin/bash

set -x

export GRADLE_BUILD_CACHE_URL="${GRADLE_BUILD_CACHE_URL:-http://localhost:5071/cache/}"
export USE_GRADLE_DAEMON="${USE_GRADLE_DAEMON:-false}"
export GRADLE_CACHE_DEBUG="${GRADLE_CACHE_DEBUG:-false}"
export PERFORM_GRADLE_SCAN="${PERFORM_GRADLE_SCAN:---scan}"

# cd %teamcity.build.checkoutDir%
echo "Using Gradle Build Cache: $GRADLE_BUILD_CACHE_URL"

# GRADLE HOME
# Required to use custom patched Gradle 4.9 with KEEP-ALIVE timeout fix
# Currently built on Azure "gradle-build-client" machine at:
# export GRADLE_HOME=~/gradle/install
if [[ -v name_of_var ]]; then
    echo "Please set GRADLE_HOME variable"
    exit;
else
    echo "GRADLE_HOME is set to '$GRADLE_HOME'"
fi

echo "Gradle version:"
${GRADLE_EXE} --version
