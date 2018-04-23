#!/bin/sh

set -eux

cd /data

java -Xmx700m -jar /app/doorman.jar --config-file /config/doorman.conf \
  --set-network-parameters /config/network-parameters.conf || true
exec java -Xmx700m -jar /app/doorman.jar --config-file /config/doorman.conf
