.PHONY: container sgsdk

SHELL           = /bin/bash
MAKEFILE_DIR   := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))

container: Dockerfile
	docker build -t sgx-ra-core .

sgxsdk:
	@mkdir -p ~/.container/sgx
	@if [ ! -e ~/.container/sgx/sgxsdk ]; then \
	  docker run --rm \
	    -v ${HOME}/.container/sgx:/sgx \
	    -v ${MAKEFILE_DIR}/../../../../:/code sgx-ra-core bash \
	    /code/sgx-jvm/linux-sgx/linux/installer/bin/build-installpkg.sh sdk; \
	  docker run --rm \
	    -v ${HOME}/.container/sgx:/sgx \
	    -v ${MAKEFILE_DIR}/../../../../:/code -it sgx-ra-core bash \
	    /code/sgx-jvm/linux-sgx/linux/installer/bin/sgx_linux_x64_sdk_1.9.100.39124.bin \
	    -prefix=/sgx; \
	fi
