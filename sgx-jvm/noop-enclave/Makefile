.PHONY: all
all: build/noop-enclave.so

build:
	mkdir -p build

build/Makefile: | build
	cd build/ && cmake ..

build/noop-enclave.so: build/Makefile
	$(MAKE) -C $(<D) VERBOSE=1

clean:
	rm -rf build
