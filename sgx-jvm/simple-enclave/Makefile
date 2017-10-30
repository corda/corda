MODE ?= Debug # or Release

.PHONY: all clean

all: build/Makefile
	$(MAKE) -C $(<D) help
	exit 1

build:
	mkdir -p build

build/Makefile: | build
	cd build/ && cmake -DCMAKE_BUILD_TYPE=$(MODE) ..

%: build/Makefile
	$(MAKE) -C $(<D) $@

clean:
	rm -rf build
