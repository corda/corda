#!/bin/bash

source .ci/ci-gradle-build-cache-init.sh

# This test is consistently re-run (cache entry miss)
echo ":core:smokeTest"
cd core
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean smokeTest --build-cache ${PERFORM_GRADLE_SCAN}

# This test is consistently re-run (cache entry miss)
echo ":client:rpc:smokeTest"
cd ../client/rpc
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean smokeTest --build-cache ${PERFORM_GRADLE_SCAN}
