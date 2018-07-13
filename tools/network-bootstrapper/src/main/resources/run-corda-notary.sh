#!/bin/bash

: ${CORDA_HOME:=/opt/corda}
: ${JAVA_OPTIONS:=-Xmx512m}
: ${OUR_NAME? Need a value for the expected FQDN of this node}
: ${OUR_PORT? Need a value for the port to bind to}

export CORDA_HOME JAVA_OPTIONS
echo "p2pAddress=\"${OUR_NAME}:${OUR_PORT}\"" >> node.conf
cp additional-node-infos/network-params/network-parameters .

bash node_info_watcher.sh &

cd ${CORDA_HOME}


if java ${JAVA_OPTIONS} -jar ${CORDA_HOME}/corda.jar 2>&1 ; then
    echo "Corda exited with zero exit code"
else
    echo "Corda exited with nonzero exit code, sleeping to allow log collection"
    sleep 10000
fi



