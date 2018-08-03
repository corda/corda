#!/bin/bash

source .ci/ci-gradle-build-cache-init.sh

echo ":client:rpc:integrationTest"
cd client/rpc
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":client:jfx:integrationTest"
cd ../jfx
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":finance:integrationTest"
cd ../../finance
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":testing:node-driver:integrationTest"
cd ../testing/node-driver
time ../../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":webserver:integrationTest"
cd ../../webserver
time ../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

# De-compose node tests
cd ../node

echo ":node:integrationTest --tests net.corda.node.amqp.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.amqp.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.flows.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.flows.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.modes.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.modes.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.persistence.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.persistence.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.utilities.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.utilities.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.*Test*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.A*Test* --tests net.corda.node.B*Test* --tests net.corda.node.C*Test* --tests net.corda.node.N*Test* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.services.messaging.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.services.messaging.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.distributed.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.distributed.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.events.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.events.* --build-cache ${PERFORM_GRADLE_SCAN}

# This test consistently causes a Network Broken Pipe error:
#echo ":node:integrationTest --tests net.corda.node.services.network.*"
#../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.network.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.rpc.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.rpc.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.statemachine.*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.statemachine.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.*Test*"
../gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.A*Test* --tests net.corda.node.services.B*Test* --tests net.corda.node.services.R*Test* --build-cache ${PERFORM_GRADLE_SCAN}