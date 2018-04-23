#!/bin/sh

set -eux

exec java -Xmx700m -jar app.jar ${TARGET_HOST}:${RPC_PORT}
