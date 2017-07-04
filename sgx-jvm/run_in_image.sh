#!/bin/bash
set -euo pipefail

if [ $# -le 1 ]; then
    echo "Usage: run_in_image.sh <DOCKER_IMAGE> <COMMAND>"
    exit 1
fi

SCRIPT_DIR=$(dirname "$(readlink -f "$0")")

IMAGE=$1
shift
ARGUMENTS=$@

DOCKER_BUILD_DIR=/tmp/corda-sgx-build

GID=$(id -g $USER)

exec docker run \
     -v $SCRIPT_DIR/..:$DOCKER_BUILD_DIR \
     -v /usr/src:/usr/src \
     -v /lib/modules:/lib/modules \
     --user=$UID:$GID \
     --workdir=$DOCKER_BUILD_DIR \
     -it $IMAGE \
     $ARGUMENTS
