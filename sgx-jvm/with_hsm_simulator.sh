#!/bin/bash
set -euo pipefail

if [ $# -le 1 ]; then
    echo "Usage: with_hsm_simulator.sh <UTIMACO_HSM_DIR> <COMMAND>"
    exit 1
fi

SCRIPT_DIR=$(dirname "$(readlink -f "$0")")
UTIMACO_HSM_DIR=$1
shift

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
SIMULATOR_RUN_DIR=$SCRIPT_DIR/build/hsm_simulator/$TIMESTAMP

mkdir -p $SIMULATOR_RUN_DIR

script -q -c $UTIMACO_HSM_DIR/SDK/Linux/bin/cs_sim.sh -f $SIMULATOR_RUN_DIR/stdout > /dev/null &

function finish {
    kill -- -$$
}
trap finish EXIT
$@
