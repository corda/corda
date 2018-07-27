#!/bin/bash

set -x

USE_GRADLE_DAEMON="${USE_GRADLE_DAEMON:-false}"
GRADLE_CACHE_DEBUG="${GRADLE_CACHE_DEBUG:-false}"
PERFORM_GRADLE_SCAN="${PERFORM_GRADLE_SCAN:---scan}"

# Samples
echo ":samples:attachment-demo"
cd  ../samples/attachment-demo
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":samples:bank-of-corda-demo"
cd ../bank-of-corda-demo
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":samples:cordapp-configuration"
cd ../cordapp-configuration
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean build --build-cache ${PERFORM_GRADLE_SCAN}

echo ":samples:irs-demo"
cd ../irs-demo
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":samples:network-verifier"
cd ../network-verifier
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}

echo ":samples:notary-demo"
cd ../notary-demo
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean build --build-cache ${PERFORM_GRADLE_SCAN}

echo ":samples:simm-valuation-demo"
cd ../simm-valuation-demo
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":samples:trader-demo"
cd ../trader-demo
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

# docs example code
echo ":docs:source:example-code"
cd ../docs/source/example-code
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test integrationTest --build-cache ${PERFORM_GRADLE_SCAN}
