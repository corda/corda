#!/bin/bash
set -euo pipefail

SCRIPT_DIR=$(dirname "$(readlink -f "$0")")

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
AESM_DIR=$SCRIPT_DIR/build/aesm/$TIMESTAMP

mkdir -p $AESM_DIR

SERVICE_FILES="aesm_service le_prod_css.bin libsgx_le.signed.so libsgx_pce.signed.so libsgx_pve.signed.so libsgx_qe.signed.so"

sed -e "s:@aesm_folder@:$AESM_DIR:" $SCRIPT_DIR/linux-sgx/build/linux/aesmd.service | sed -e '/InaccessibleDirectories=/d' | sed -e "s!^\\[Service\\]![Service]\nEnvironment=LD_LIBRARY_PATH=$SCRIPT_DIR/linux-sgx/build/linux:$SCRIPT_DIR/dependencies/root/usr/lib/x86_64-linux-gnu!" > $AESM_DIR/aesmd.service

for FILE in $SERVICE_FILES
do
    ln -s $SCRIPT_DIR/linux-sgx/build/linux/$FILE $AESM_DIR/$FILE
done

sudo systemctl --runtime link $AESM_DIR/aesmd.service

function finish {
    sudo systemctl stop aesmd
    sudo systemctl --runtime disable aesmd
}
trap finish EXIT

sudo systemctl start aesmd
$@
