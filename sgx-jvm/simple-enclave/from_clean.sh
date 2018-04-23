#!/bin/bash

set -xeuo

sx containers prune
sx containers build

sx build .. linux-sgx/build/linux/aesm_service
#sx build ../simple-enclave MODE=Debug unsigned
sx build ../simple-enclave MODE=Release unsigned
sx exec ./gradlew sgx-jvm/hsm-tool:jar

java -jar ../hsm-tool/build/libs/sgx-jvm/hsm-tool-1.0-SNAPSHOT.jar --mode=Sign --source=build/simple_enclave_blob_to_sign.bin --signature=build/simple_enclave.signature.hsm.sha256 --pubkey=build/hsm.public.pem --profile=dev_hsm

sx build ../simple-enclave sigstruct-hsm
sx build ../simple-enclave simple_test
sx exec ./sgx-jvm/simple-enclave/build/simple_test sgx-jvm/simple-enclave/build/simple_enclave.signed.hsm.so

# Dev Cards:
#   ADMIN_CARD
#   SGX_CARD_A
#   SGX_CARD_B
