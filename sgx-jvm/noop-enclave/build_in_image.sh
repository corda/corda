#!/bin/bash
set -euo pipefail

if [ $# -le 1 ]; then
    echo "Usage: build_in_image.sh <DOCKER_IMAGE> <MAKEFILE OPTIONS>"
    exit 1
fi

IMAGE=$1
shift
ARGUMENTS=$@

DOCKER_BUILD_DIR=/tmp/corda-sgx-build

GID=$(id -g $USER)

exec docker run -v $PWD/../..:$DOCKER_BUILD_DIR -v $PWD/../docker-.gradle:/root/.gradle --user=$UID:$GID -it $IMAGE make -C $DOCKER_BUILD_DIR/sgx-jvm/noop-enclave $ARGUMENTS
