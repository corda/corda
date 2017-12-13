.PHONY: host host-native docs \
        unit-tests unit-tests-force \
        integration-tests integration-tests-force \
        run run-local run-remote run-sgx \
        clean

# === GENERAL PARAMETERS ==========================================================================

SHELL                    = /bin/bash
MAKEFILE_DIR            := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
GRADLE                  ?= $(MAKEFILE_DIR)/../gradlew

# TODO Update to Dokka 0.9.16 (when available) to fix: https://github.com/Kotlin/dokka/issues/184
DOKKA_FILTER             = grep -v "Can't find node by signature"

# === PSEUDO TARGETS ==============================================================================

host:
	$(GRADLE) compileKotlin

host-native:
	make -C native all

docs:
	$(GRADLE) dokka | $(DOKKA_FILTER)

docs-force:
	rm -rf $(MAKEFILE_DIR)/build/javadoc
	$(GRADLE) dokka | $(DOKKA_FILTER)

unit-tests: host-native
	$(GRADLE) test

unit-tests-force: host-native
	$(GRADLE) cleanTest test

integration-tests: host-native
	$(GRADLE) integrationTest

integration-tests-force: host-native
	$(GRADLE) cleanIntegrationTest integrationTest

~/.classpath:
	$(GRADLE) -q getClasspath > ~/.classpath

clean:
	$(GRADLE) clean
	rm -f ~/.classpath

# === TEST TARGETS ================================================================================

JAVA_PROG  = $(JAVA_HOME)/bin/java
SGX_GDB    = /sgx/sgxsdk/bin/sgx-gdb
HOST       = localhost:2000
MAIN_CLASS = net.corda.sgx.cli.Program
JVM_DEFS   = -Dcorda.sgx.enclave.path=/code/sgx-jvm/remote-attestation/enclave/build \
             -Djava.library.path=/code/sgx-jvm/remote-attestation/host/native/build \
             -Dfile.encoding=US-ASCII -Duser.country=US -Duser.language=en -Duser.variant \
             -Dattestation.home=/code/sgx-jvm/remote-attestation/host/build/logs
JVM_ARGS   = -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005

run: ~/.classpath
	$(JAVA_PROG) $(JVM_DEFS) $(JVM_ARGS) -cp `cat ~/.classpath` $(MAIN_CLASS)

run-local: ~/.classpath
	gdb --args $(JAVA_PROG) $(JVM_DEFS) $(JVM_ARGS) -cp `cat ~/.classpath` $(MAIN_CLASS)

run-sgx: ~/.classpath
	$(SGX_GDB) --args $(JAVA_PROG) $(JVM_DEFS) $(JVM_ARGS) -cp `cat ~/.classpath` $(MAIN_CLASS)

run-remote: ~/.classpath
	gdbserver $(HOST) $(JAVA_PROG) $(JVM_DEFS) $(JVM_ARGS) -cp `cat ~/.classpath` $(MAIN_CLASS)
