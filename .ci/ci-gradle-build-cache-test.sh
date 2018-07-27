#!/bin/bash

set -x

USE_GRADLE_DAEMON="${USE_GRADLE_DAEMON:-false}"
GRADLE_CACHE_DEBUG="${GRADLE_CACHE_DEBUG:-false}"
PERFORM_GRADLE_SCAN="${PERFORM_GRADLE_SCAN:---scan}"

echo ":core:test"
cd ../core
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}

echo ":serialization:test"
cd ../serialization
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node-api:test"
cd ../node-api
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}

echo ":client:rpc:test"
cd ../client/rpc
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}

echo ":confidential-identities:test"
cd ../../confidential-identities
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}

echo ":finance:test"
cd ../finance
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}

echo ":client:jackson:test"
cd ../client/jackson
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}

echo ":client:jfx:test"
cd ../jfx
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}

echo ":testing:node-driver:test"
cd ../../testing/node-driver
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}

echo ":testing:test-utils:test"
cd ../test-utils
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}

#echo ":webserver:test"
#cd ../../webserver
#time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --build-cache ${PERFORM_GRADLE_SCAN}

# De-compose node tests
echo ":node:test --tests net.corda.node.services.vault.*"
cd ../../node
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.vault.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.services.statemachine.*"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.statemachine.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.services.transactions.*"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.transactions.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.services.schema.*"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.schema.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.services.persistence.*"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.persistence.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.services.network.*"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.network.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.services.keys.*"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.keys.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.services.identity.*"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.identity.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.services.events.*"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.events.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.services.config.*"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.services.config.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.utilities.*"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.utilities.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.serialization.*"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.serialization.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.modes.*"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.modes.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.messaging.*"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.messaging.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.internal.*"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.internal.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:test --tests net.corda.node.*Test"
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test --tests net.corda.node.CordaRPCOpsImplTest --tests net.corda.node.NodeArgsPArserTest --tests SerialFilterTests --build-cache ${PERFORM_GRADLE_SCAN}
