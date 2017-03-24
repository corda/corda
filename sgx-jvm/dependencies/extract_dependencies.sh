#!/bin/bash
set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: extract_dependencies.sh <DOCKER_IMAGE>"
    exit 1
fi

IMAGE=$1
FILES=$(<extracted_files)
HOST_ROOT=$PWD/root

rm -rf $HOST_ROOT
mkdir -p $HOST_ROOT

exec docker run -v $PWD:/tmp/host $IMAGE bash /tmp/host/indocker_copy_dependencies.sh $FILES
