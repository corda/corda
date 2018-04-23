#!/bin/sh

set -eux

export LEGAL_NAME="C=GB,L=London,O=T-$HOSTNAME"

export P2P_ADDRESS=$(eval "echo $P2P_ADDRESS")

export RPC_ADDRESS=$(eval "echo $RPC_ADDRESS")

export ADMIN_ADDRESS=$(eval "echo $ADMIN_ADDRESS")
export COMPATIBILITY_ZONE_URL="http://$DOORMAN_SERVICE_HOST:1300"

env

# TODO: fix
cp ${CONFIG_FILE} /app/node.conf

java -Xmx700m -jar corda.jar --initial-registration \
  --config-file=${CONFIG_FILE} \
	--network-root-truststore /truststore/network-root-truststore.jks \
	--network-root-truststore-password ''

exec java -Xmx700m -jar corda.jar \
  --config-file=${CONFIG_FILE} \
  --no-local-shell \
  --log-to-console \
  "$@"
