#!/bin/sh

set -eux

java -Xmx700m -jar corda.jar --initial-registration \
	--network-root-truststore /truststore/network-root-truststore.jks \
	--network-root-truststore-password '' || true

exec java -Xmx700m -jar corda.jar \
  --no-local-shell \
  --log-to-console
