#!/bin/bash

set -x

USE_GRADLE_DAEMON="${USE_GRADLE_DAEMON:-false}"
GRADLE_CACHE_DEBUG="${GRADLE_CACHE_DEBUG:-false}"
PERFORM_GRADLE_SCAN="${PERFORM_GRADLE_SCAN:---scan}"

echo "Using Gradle Build Cache: $(cat settings.gradle | grep ^\ *url)"

echo ":core:smokeTest"
cd ../core
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean smokeTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":client:rpc:smokeTest"
cd ../client/rpc
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean smokeTest --build-cache ${PERFORM_GRADLE_SCAN}
