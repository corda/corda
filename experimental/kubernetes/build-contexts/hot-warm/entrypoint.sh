#!/bin/sh

set -eux

java -Xmx700m -jar corda.jar --initial-registration \
  --config-file=${CONFIG_FILE} \
	--network-root-truststore /truststore/network-root-truststore.jks \
	--network-root-truststore-password '' || true

exec java -Xmx700m -jar corda.jar \
  --config-file=${CONFIG_FILE} \
  --no-local-shell \
  --log-to-console \
  "$@"
