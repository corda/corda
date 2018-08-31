#!/bin/bash

source .ci/ci-gradle-build-cache-init.sh

echo ":client:rpc:integrationTest"
cd client/rpc
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":client:jfx:integrationTest"
cd ../jfx
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":finance:integrationTest"
cd ../../finance
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":testing:node-driver:integrationTest"
cd ../testing/node-driver
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

echo ":webserver:integrationTest"
cd ../../webserver
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --build-cache ${PERFORM_GRADLE_SCAN}

# De-compose node tests
cd ../node

echo ":node:integrationTest --tests net.corda.node.amqp.*"
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.amqp.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.flows.*"
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.flows.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.modes.*"
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.modes.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.persistence.*"
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.persistence.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.utilities.*"
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.utilities.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.*Test*"
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.A*Test* --tests net.corda.node.B*Test* --tests net.corda.node.C*Test* --tests net.corda.node.N*Test* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.services.messaging.*"
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.services.messaging.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.distributed.*"
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.distributed.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.events.*"
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.events.* --build-cache ${PERFORM_GRADLE_SCAN}

# This test consistently causes a Network Broken Pipe error:
#echo ":node:integrationTest --tests net.corda.node.services.network.*"
#${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.network.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.rpc.*"
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.rpc.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.statemachine.*"
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.statemachine.* --build-cache ${PERFORM_GRADLE_SCAN}

echo ":node:integrationTest --tests net.corda.node.services.*Test*"
${GRADLE_EXE} --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean integrationTest --tests net.corda.node.services.A*Test* --tests net.corda.node.services.B*Test* --tests net.corda.node.services.R*Test* --build-cache ${PERFORM_GRADLE_SCAN}