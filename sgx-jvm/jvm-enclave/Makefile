.PHONY: all
all: standalone/build/standalone_sgx_verify jni/build/libuntrusted_corda_sgx.so

standalone/build:
	mkdir -p standalone/build

standalone/build/Makefile: | standalone/build
	cd standalone/build/ && cmake ..

.PHONY: standalone/build/standalone_sgx_verify
standalone/build/standalone_sgx_verify: standalone/build/Makefile ../../verify-enclave/build/libs/corda-enclavelet.jar
	$(MAKE) -C $(<D) VERBOSE=1

jni/build:
	mkdir -p jni/build

jni/build/Makefile: | jni/build
	cd jni/build/ && cmake ..

../../verify-enclave/build/libs/corda-enclavelet.jar: $(shell find ../../*/src -name "*.kt")
	cd ../.. && ./gradlew verify-enclave:jar

../../verify-enclave/build/native/include/jni_sgx_api.h: ../../verify-enclave/src/main/kotlin/com/r3/enclaves/txverify/NativeSgxApi.kt
	cd ../.. && ./gradlew verify-enclave:generateNativeSgxHeaders

.PHONY: jni/build/libuntrusted_corda_sgx.so
jni/build/libuntrusted_corda_sgx.so: jni/build/Makefile ../../verify-enclave/build/native/include/jni_sgx_api.h ../../verify-enclave/build/libs/corda-enclavelet.jar
	$(MAKE) -C $(<D) VERBOSE=1

.PHONY: clean
clean:
	$(RM) -r {standalone,common,enclave,jni}/build
