#!/bin/bash

source .ci/ci-gradle-build-cache-init.sh

echo ":core:test"
cd core
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":serialization:test"
cd ../serialization
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node-api:test"
cd ../node-api
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":client:rpc:test"
cd ../client/rpc
../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":confidential-identities:test"
cd ../../confidential-identities
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":finance:test"
cd ../finance
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":client:jackson:test"
cd ../client/jackson
../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":client:jfx:test"
cd ../jfx
../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":testing:node-driver:test"
cd ../../testing/node-driver
../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":testing:test-utils:test"
cd ../test-utils
../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

#echo ":webserver:test"
#cd ../../webserver
#../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

# De-compose node tests
echo ":node:test --tests net.corda.node.services.vault.*"
cd ../../node
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.vault.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.services.statemachine.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.statemachine.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.services.transactions.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.transactions.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.services.schema.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.schema.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.services.persistence.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.persistence.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.services.network.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.network.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.services.keys.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.keys.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.services.identity.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.identity.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.services.events.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.events.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.services.config.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.config.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.services.*Test*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.F*Test* --tests net.corda.node.services.N*Test* --tests net.corda.node.services.R*Test* --tests net.corda.node.services.S*Test* --tests net.corda.node.services.T*Test* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.utilities.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.utilities.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.serialization.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.serialization.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.modes.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.modes.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.messaging.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.messaging.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.internal.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.internal.* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?

echo ":node:test --tests net.corda.node.*Test*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.C*Test* --tests net.corda.node.N*Test* --tests S*Test* --build-cache ${PERFORM_GRADLE_SCAN}; echo $?
