#!/usr/bin/env bash
: ${JVM_ARGS='-Xmx15g -Xms1g -XX:+UseG1GC'}

java -Djava.security.egd=file:/dev/./urandom -Dcapsule.jvm.args="${JVM_ARGS}" -jar /opt/corda/bin/corda.jar --base-directory=/opt/corda --config-file=/etc/corda/node.conf