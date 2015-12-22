#!/bin/sh

if test $# -eq 0; then
    echo "Usage: $0 [--container <container_name>] -- <command_to_run_in_docker>"
    echo "Ex: $0 make test"
    echo "Ex: $0 ./test/ci.sh"
    echo "Ex: $0 --container joshuawarner32/avian-build-windows -- make platform=windows"
    exit 1
fi

THE_USER="-u $(id -u "${USER}")"

while test $# -gt 1 ; do
    key="$1"
    case $key in
        -c|--container)
            shift
            CONTAINER="$1"
            shift
            ;;
        -r|--root)
            shift
            THE_USER=
            ;;
        --)
            shift
            break
            ;;
        *)
            break
            ;;
    esac
done

if test -z $CONTAINER; then
    CONTAINER=joshuawarner32/avian-build
fi

DIR=$(cd $(dirname "$0") && cd .. && pwd)

docker run --rm -i -t -v "${DIR}":/var/src/avian ${THE_USER} "${CONTAINER}" "${@}"
