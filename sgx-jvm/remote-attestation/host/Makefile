.PHONY: host host-native docs clean \
        unit-tests integration-tests \
        run run-local run-remote run-sgx

# === GENERAL PARAMETERS ==========================================================================

SHELL                    = /bin/bash
MAKEFILE_DIR            := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
GRADLE_HOME             ?= $(MAKEFILE_DIR)/../.gradle/
GRADLE                  ?= $(MAKEFILE_DIR)/../gradlew -g $(GRADLE_HOME)

# === PSEUDO TARGETS ==============================================================================

host:
	$(GRADLE) compileKotlin

host-native:
	make -C native all

docs:
	$(GRADLE) cleanDokka dokka

unit-tests:
	$(GRADLE) --rerun-tasks -q -Pdebug=$(DEBUG) test

integration-tests:
	$(GRADLE) --rerun-tasks -q -Pdebug=$(DEBUG) integrationTest

clean:
	$(GRADLE) clean

# === TEST TARGETS ================================================================================

run:
	$(GRADLE) -q -Pdebug=$(DEBUG) runFlow

run-local:
	$(GRADLE) -q -Pdebug=$(DEBUG) runFlowWithNativeDebugger

run-sgx:
	$(GRADLE) -q -Pdebug=$(DEBUG) runFlowWithNativeSgxDebugger

run-remote:
	$(GRADLE) -q -Pdebug=$(DEBUG) runFlowWithNativeDebugServer
