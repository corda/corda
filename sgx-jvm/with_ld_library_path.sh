#!/bin/bash
set -euo pipefail

SCRIPT_DIR=$(dirname "$(readlink -f "$0")")

exec env LD_LIBRARY_PATH=${LD_LIBRARY_PATH:-}:$SCRIPT_DIR/linux-sgx/build/linux:$SCRIPT_DIR/dependencies/root/usr/lib/x86_64-linux-gnu $@
