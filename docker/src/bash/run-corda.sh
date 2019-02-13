#!/usr/bin/env bash
: ${JVM_ARGS='-XX:+UseG1GC'}

JVM_ARGS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap "${JVM_ARGS}

if [[ ${JVM_ARGS} == *"Xmx"* ]]; then
  echo "WARNING: the use of the -Xmx flag is not recommended within docker containers. Use the --memory option passed to the container to limit heap size"
fi

java -Djava.security.egd=file:/dev/./urandom -Dcapsule.jvm.args="${JVM_ARGS}" -jar /opt/corda/bin/corda.jar --base-directory=/opt/corda --config-file=/etc/corda/node.conf ${CORDA_ARGS}