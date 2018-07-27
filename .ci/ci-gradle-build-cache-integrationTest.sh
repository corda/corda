#!/bin/bash

set -x

USE_GRADLE_DAEMON="${USE_GRADLE_DAEMON:-false}"
GRADLE_CACHE_DEBUG="${GRADLE_CACHE_DEBUG:-false}"
PERFORM_GRADLE_SCAN="${PERFORM_GRADLE_SCAN:---scan}"

#echo ":core:integrationTest"
#cd ../core
#time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

#echo ":serialization:integrationTest"
#cd ../serialization
#time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

#echo ":node-api:integrationTest"
#cd ../node-api
#time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":client:rpc:integrationTest"
cd ../client/rpc
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

#echo ":confidential-identities:integrationTest"
#cd ../../confidential-identities
#time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":finance:integrationTest"
cd ../finance
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

#echo ":client:jackson:integrationTest"
#cd ../client/jackson
#time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":client:jfx:integrationTest"
cd ../client/jfx
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":testing:node-driver:integrationTest"
cd ../../testing/node-driver
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

#echo ":webserver:integrationTest"
#cd ../../webserver
#time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

# De-compose node tests
echo ":node:integrationTest --tests net.corda.node.services.distributed.*"
cd ../../node
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.distributed.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.events.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.events.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.messaging.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.messaging.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.schema.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.network.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.rpc.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.rpc.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.statemachine.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.statemachine.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.transactions.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.transactions.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.vault.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.vault.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.*Test"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.A*Test --tests net.corda.node.B*Test --tests net.corda.node.M*Test --tests net.corda.node.R*Test --tests net.corda.node.T*Test --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.messaging.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.messaging.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.integrationTestMessage.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.testMessage.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.utilities.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.utilities.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.amqp.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.amqp.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.modes.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.modes.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.persistence.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.persistence.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.flows.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.flows.* --build-cache ${PERFORM_GRADLE_SCAN}

#echo ":node:integrationTest --tests net.corda.serialization"
#../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.serialization.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.*Test"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.A*Test --tests net.corda.node.N*Test --tests net.corda.node.B*Test --build-cache ${PERFORM_GRADLE_SCAN}

