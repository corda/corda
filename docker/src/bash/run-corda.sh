#!/usr/bin/env bash

if [[ ${JVM_ARGS} == *"Xmx"* ]]; then
  echo "WARNING: the use of the -Xmx flag is not recommended within docker containers. Use the --memory option passed to the container to limit heap size"
fi

# Unpack Corda from the read-only corda.jar on every start, so changes to the unpacked copy do not survive a restart
# Do not run this while a node is already running in the same container: it would delete files the running node still loads.
rm -rf "${CAPSULE_CACHE_DIR:-${HOME}/.capsule}"/apps/net.corda.node.Corda_*

count=$(grep -c dataSourceProperties ${CONFIG_FOLDER}/node.conf)

if [ "$count" -eq 0 ]; then
  java -Djava.security.egd=file:/dev/./urandom -Dcapsule.jvm.args="${JVM_ARGS}" -Dcorda.dataSourceProperties.dataSource.url="jdbc:h2:file:"${PERSISTENCE_FOLDER}"/persistence;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0;LOCK_TIMEOUT=10000" -jar /opt/corda/bin/corda.jar --base-directory /opt/corda --config-file ${CONFIG_FOLDER}/node.conf ${CORDA_ARGS}
else
  java -Djava.security.egd=file:/dev/./urandom -Dcapsule.jvm.args="${JVM_ARGS}" -jar /opt/corda/bin/corda.jar --base-directory /opt/corda --config-file ${CONFIG_FOLDER}/node.conf ${CORDA_ARGS}
fi