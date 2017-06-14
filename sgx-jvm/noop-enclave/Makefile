.PHONY: all
all: build/Makefile
	$(MAKE) -C $(<D) help
	exit 1

build:
	mkdir -p build

build/Makefile: | build
	cd build/ && cmake ..

%: build/Makefile
	$(MAKE) -C $(<D) $@

clean:
	rm -rf build
