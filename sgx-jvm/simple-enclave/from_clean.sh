#!/bin/bash

set -xeuo

docker rm $(docker ps -a -q) || true
docker rmi $(docker images -a -q) || true
docker build -t minimal ../dependencies/docker-minimal

bash ../run_in_image.sh minimal make -C sgx-jvm linux-sgx/build/linux/aesm_service
#bash ../run_in_image.sh minimal make -C sgx-jvm/simple-enclave MODE=Debug unsigned
bash ../run_in_image.sh minimal make -C sgx-jvm/simple-enclave MODE=Release unsigned
bash ../run_in_image.sh minimal ./gradlew sgx-jvm/hsm-tool:jar
ke
java -jar ../hsm-tool/build/libs/sgx-jvm/hsm-tool-1.0-SNAPSHOT.jar --mode=Sign --source=build/simple_enclave_blob_to_sign.bin --signature=build/simple_enclave.signature.hsm.sha256 --pubkey=build/hsm.public.pem --profile=dev_hsm

bash ../run_in_image.sh minimal make -C sgx-jvm/simple-enclave sigstruct-hsm
bash ../run_in_image.sh minimal make -C sgx-jvm/simple-enclave simple_test
bash ../with_isgx.sh bash ../with_aesmd.sh bash ../with_ld_library_path.sh simple-enclave/build/simple_test simple-enclave/build/simple_enclave.signed.hsm.so

# Dev Cards:
#   ADMIN_CARD
#   SGX_CARD_A
#   SGX_CARD_B
