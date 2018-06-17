.DEFAULT_GOAL=all

jdk8u:
	git clone -b deterministic-jvm8 --single-branch https://github.com/corda/openjdk $@

jdk8u/common/autoconf/configure: jdk8u

jdk8u/build/%/spec.gmk: jdk8u/common/autoconf/configure
	cd jdk8u && $(SHELL) configure

.PHONY: jdk-image clean all
jdk-image: jdk8u/build/linux-x86_64-normal-server-release/spec.gmk
	$(MAKE) -C jdk8u images docs

all: libs/rt.jar libs/jce.jar libs/jsse.jar

clean:
	$(MAKE) -C jdk8u clean

libs:
	mkdir $@

libs/rt.jar libs/jce.jar libs/jsse.jar: libs jdk-image
	cp -f jdk8u/build/*/images/j2re-image/lib/$(@F) $@

