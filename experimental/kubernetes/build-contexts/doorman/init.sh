#!/bin/sh

set -eux

cd /data

rm -rf *

mkdir -p /data/doorman

yes '' | java -jar /app/doorman.jar --config-file ${NODE_INIT_CONFIG:-"/config/doorman-init.conf"} --mode ROOT_KEYGEN
yes '' | java -jar /app/doorman.jar --config-file ${NODE_INIT_CONFIG:-"/config/doorman-init.conf"} --mode CA_KEYGEN

java -Xmx700m -jar /app/doorman.jar --config-file ${NODE_INIT_CONFIG:-"/config/doorman-init.conf"}
