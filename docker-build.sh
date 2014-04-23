#!/bin/sh

if test $# -eq 0; then
    echo "Usage: $0 <command_to_run_in_docker>"
    echo "Ex: $0 make test"
    echo "Ex: $0 ./test/ci.sh"
    exit 1
fi

docker run --rm -i -t -v $(cd $(dirname "$0") && pwd):/var/avian -u $(id -u "${USER}") joshuawarner32/avian-build "${@}"
