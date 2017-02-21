#!/bin/bash
set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: build_in_image.sh <DOCKER_IMAGE>"
    exit 1
fi

IMAGE=$1
DOCKER_BUILD_DIR=/tmp/corda-sgx-build

exec docker run -v $PWD/../..:$DOCKER_BUILD_DIR $IMAGE make -C $DOCKER_BUILD_DIR/sgx-jvm
