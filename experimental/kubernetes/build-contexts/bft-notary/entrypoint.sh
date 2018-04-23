#!/bin/sh

set -eux

export REPLICA_ID=$(echo $HOSTNAME | grep -o "\d\+")

java -Xmx700m -jar corda.jar --log-to-console --no-local-shell
