#!/usr/bin/env bash
: ${JVM_ARGS='-XX:+UseG1GC'}
##IT IS IMPORTANT THAT WE DOCUMENT THE FACT THAT IN DOCKER, USERS SHOULD NOT SPECIFY -Xmx AS JAVA WILL TRANSPARENTLY SET MAXIMUM BASED ON CONTAINER LIMITS
JVM_ARGS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap "${JVM_ARGS}

java -Djava.security.egd=file:/dev/./urandom -Dcapsule.jvm.args="${JVM_ARGS}" -jar /opt/corda/bin/corda.jar --base-directory=/opt/corda --config-file=/etc/corda/node.conf