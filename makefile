#MAKEFLAGS = -s

build-arch = $(shell uname -m)
ifeq ($(build-arch),i586)
	build-arch = i386
endif
ifeq ($(build-arch),i686)
	build-arch = i386
endif

build-platform = $(shell uname -s | tr [:upper:] [:lower:])

ifeq ($(build-platform),cygwin_nt-5.1)
	build-platform = windows
endif

arch = $(build-arch)

platform = $(build-platform)

process = interpret

mode = debug

build = build
native-build = $(build)/$(platform)/$(arch)/$(mode)
classpath-build = $(build)/classpath
test-build = $(build)/test
src = src
classpath = classpath
test = test

input = $(test-build)/Hello.class

build-cxx = g++
build-cc = gcc

pthread = -pthread
lpthread = -lpthread

cxx = $(build-cxx)
cc = $(build-cc)
ar = ar
ranlib = ranlib
vg = nice valgrind --suppressions=valgrind.supp --undef-value-errors=no \
	--num-callers=32 --db-attach=yes --freelist-vol=100000000
db = gdb --args
javac = javac
zip = zip
strip = :
show-size = :

rdynamic = -rdynamic
shared = -shared

warnings = -Wall -Wextra -Werror -Wunused-parameter \
	-Winit-self -Wconversion

common-cflags = $(warnings) -fno-rtti -fno-exceptions \
	-I$(JAVA_HOME)/include -idirafter $(src) -I$(native-build) \
	-D__STDC_LIMIT_MACROS -D_JNI_IMPLEMENTATION_

build-cflags = $(common-cflags) -fPIC -fvisibility=hidden \
	-I$(JAVA_HOME)/include/linux -I$(src) $(pthread)

cflags = $(build-cflags)

lflags = $(lpthread) -ldl -lm -lz

system = posix
asm = x86
begin-merge-archive = -Wl,--whole-archive
end-merge-archive = -Wl,--no-whole-archive

ifeq ($(platform),darwin)
	rdynamic =
	thread-cflags =
	shared = -dynamiclib
	pthread =
	lpthread =
	begin-merge-archive = -Wl,-all_load
	end-merge-archive =
endif
ifeq ($(platform),windows)
	inc = /usr/local/win32/include
	lib = /usr/local/win32/lib

	system = windows

	cxx = i586-mingw32msvc-g++
	cc = i586-mingw32msvc-gcc
	dlltool = i586-mingw32msvc-dlltool
	ar = i586-mingw32msvc-ar
	ranlib = i586-mingw32msvc-ranlib

	rdynamic = -Wl,--export-dynamic
	lflags = -L$(lib) -lm -lz -lws2_32 -Wl,--kill-at -mwindows -mconsole
	cflags = $(common-cflags) -I$(inc)
endif

ifeq ($(mode),debug)
	cflags += -O0 -g3
endif
ifeq ($(mode),stress)
	cflags += -O0 -g3 -DVM_STRESS
endif
ifeq ($(mode),stress-major)
	cflags += -O0 -g3 -DVM_STRESS -DVM_STRESS_MAJOR
endif
ifeq ($(mode),fast)
	cflags += -O3 -DNDEBUG
	strip = strip
	show-size = ls -l
endif

ifeq ($(arch),i386)
	pointer-size = 4
else
	pointer-size = 8
endif

cpp-objects = $(foreach x,$(1),$(patsubst $(2)/%.cpp,$(3)/%.o,$(x)))
asm-objects = $(foreach x,$(1),$(patsubst $(2)/%.S,$(3)/%-asm.o,$(x)))
java-classes = $(foreach x,$(1),$(patsubst $(2)/%.java,$(3)/%.class,$(x)))

jni-sources = $(shell find $(classpath) -name '*.cpp')
jni-objects = $(call cpp-objects,$(jni-sources),$(classpath),$(native-build))
jni-cflags = $(cflags)

generated-code = \
	$(native-build)/type-enums.cpp \
	$(native-build)/type-declarations.cpp \
	$(native-build)/type-constructors.cpp \
	$(native-build)/type-initializations.cpp \
	$(native-build)/type-java-initializations.cpp

interpreter-depends = \
	$(generated-code) \
	$(src)/common.h \
	$(src)/system.h \
	$(src)/heap.h \
	$(src)/finder.h \
	$(src)/processor.h \
	$(src)/process.h \
	$(src)/stream.h \
	$(src)/constants.h \
	$(src)/jnienv.h \
	$(src)/machine.h

interpreter-sources = \
	$(src)/$(system).cpp \
	$(src)/finder.cpp \
	$(src)/machine.cpp \
	$(src)/heap.cpp \
	$(src)/$(process).cpp \
	$(src)/builtin.cpp \
	$(src)/jnienv.cpp

interpreter-asm-sources = $(src)/$(asm).S

ifeq ($(process),compile)
	interpreter-asm-sources += $(src)/compile.S
endif

interpreter-cpp-objects = \
	$(call cpp-objects,$(interpreter-sources),$(src),$(native-build))
interpreter-asm-objects = \
	$(call asm-objects,$(interpreter-asm-sources),$(src),$(native-build))
interpreter-objects = \
	$(interpreter-cpp-objects) \
	$(interpreter-asm-objects)

driver-sources = $(src)/main.cpp

driver-object = $(call cpp-objects,$(driver-sources),$(src),$(native-build))

generator-headers = \
	$(src)/input.h \
	$(src)/output.h
generator-sources = $(src)/type-generator.cpp
generator-objects = \
	$(call cpp-objects,$(generator-sources),$(src),$(native-build))
generator = $(native-build)/generator

bin2c-sources = $(src)/bin2c.cpp
bin2c-objects = $(call cpp-objects,$(bin2c-sources),$(src),$(native-build))
bin2c = $(native-build)/bin2c

archive = $(native-build)/libvm.a
interpreter = $(native-build)/vm

classpath-sources = $(shell find $(classpath) -name '*.java')
classpath-classes = \
	$(call java-classes,$(classpath-sources),$(classpath),$(classpath-build))
classpath-object = $(native-build)/classpath.o

test-sources = $(shell find $(test) -name '*.java')
test-classes = $(call java-classes,$(test-sources),$(test),$(test-build))

class-name = $(patsubst $(1)/%.class,%,$(2))
class-names = $(foreach x,$(1),$(call class-name,$(x)))

flags = -cp $(test-build)
args = $(flags) $(call class-name,$(test-build),$(input))

.PHONY: build
build: $(interpreter) $(classpath-classes) $(test-classes)

$(input): $(classpath-classes)

.PHONY: run
run: build
	$(interpreter) $(args)

.PHONY: debug
debug: build
	gdb --args $(interpreter) $(args)

.PHONY: vg
vg: build
	$(vg) $(interpreter) $(args)

.PHONY: test
test: build
	/bin/bash $(test)/test.sh 2>/dev/null \
		$(interpreter) $(mode) "$(flags)" $(call class-names,$(test-classes))

.PHONY: clean
clean:
	@echo "removing build"
	rm -rf build

.PHONY: clean-native
clean-native:
	@echo "removing $(native-build)"
	rm -rf $(native-build)

gen-arg = $(shell echo $(1) | sed -e 's:$(native-build)/type-\(.*\)\.cpp:\1:')
$(generated-code): %.cpp: $(src)/types.def $(generator)
	@echo "generating $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(generator) $(call gen-arg,$(@)) < $(<) > $(@)

$(native-build)/type-generator.o: \
	$(generator-headers)

define compile-class
	@echo "compiling $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(javac) -bootclasspath $(classpath) -classpath $(classpath) \
		-d $(1) $(<)
	@touch $(@)
endef

$(classpath-build)/%.class: $(classpath)/%.java
	@echo "compiling $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(javac) -bootclasspath $(classpath) -classpath $(classpath) \
		-d $(classpath-build) $(<)
	@touch $(@)

$(test-build)/%.class: $(test)/%.java
	@echo "compiling $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(javac) -bootclasspath $(classpath) -classpath $(classpath) \
		-d $(test-build) $(<)
	@touch $(@)

define compile-object
	@echo "compiling $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(cxx) $(cflags) -c $(<) -o $(@)
endef

$(interpreter-cpp-objects): \
		$(native-build)/%.o: $(src)/%.cpp $(interpreter-depends)
	$(compile-object)

$(interpreter-asm-objects): $(native-build)/%-asm.o: $(src)/%.S
	$(compile-object)

$(driver-object): $(native-build)/%.o: $(src)/%.cpp
	$(compile-object)

$(bin2c-objects): $(native-build)/%.o: $(src)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(build-cxx) $(build-cflags) -c $(<) -o $(@)

$(build)/classpath.zip: $(classpath-classes)
	echo $(classpath-classes)
	(wd=$$(pwd); \
	 cd $(classpath-build); \
	 $(zip) -q -0 $${wd}/$(@) $$(find -name '*.class'))

$(build)/classpath.c: $(build)/classpath.zip $(bin2c)
	$(bin2c) $(<) vmClasspath >$(@)

$(classpath-object): $(build)/classpath.c
	$(cxx) $(cflags) -c $(<) -o $(@)

$(generator-objects): $(native-build)/%.o: $(src)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(build-cxx) -DPOINTER_SIZE=$(pointer-size) $(build-cflags) -c $(<) -o $(@)

$(jni-objects): $(native-build)/%.o: $(classpath)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(cxx) $(jni-cflags) -c $(<) -o $(@)

$(archive): $(interpreter-objects) $(jni-objects) $(classpath-object)
ifeq ($(platform),windows)
	@echo "creating $(@)"
	$(dlltool) -z $(@).def $(^)
	$(dlltool) -k -d $(@).def -e $(@).exp
	$(ar) cru $(@) $(@).exp $(^)
	$(ranlib) $(@)
else
	@echo "creating $(@)"
	$(ar) cru $(@) $(^)
	$(ranlib) $(@)
endif

$(interpreter): $(archive) $(driver-object)
	@echo "linking $(@)"
	$(cc) $(begin-merge-archive) $(^) $(end-merge-archive) \
		$(lflags) $(rdynamic) -o $(@)
	@$(strip) --strip-all $(@)
	@$(show-size) $(@)

$(generator): $(generator-objects)
	@echo "linking $(@)"
	$(build-cc) $(^) -o $(@)

$(bin2c): $(bin2c-objects)
	@echo "linking $(@)"
	$(build-cc) $(^) -o $(@)
