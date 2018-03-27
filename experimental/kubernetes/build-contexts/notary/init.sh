#!/bin/sh

set -eux

export COMPATIBILITY_ZONE_URL=$(eval "echo http://${DOORMAN_PORT_1300_TCP_ADDR}:1300")
export P2P_ADDRESS=$(eval "echo ${HOSTNAME}:10001")
export RPC_ADDRESS=$(eval "echo ${HOSTNAME}:10002")

cd /data

rm -rf *

cp /app/node.conf .

echo 'hello'
java -Xmx1g -jar /app/corda.jar --initial-registration \
                                --base-directory=/data \
                                --network-root-truststore /truststore/network-root-truststore.jks \
                                --network-root-truststore-password ''

java -Xmx1g -jar /app/corda.jar --just-generate-node-info --base-directory=/data

cp nodeInfo* notary-node-info

echo 'DONE_BOOTSTRAPPING'

sleep 3600
