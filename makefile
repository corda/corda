#MAKEFLAGS = -s

input = $(cls)/Hello.class

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

bld = build/$(platform)/$(arch)/$(mode)
cls = build/classes
src = src
classpath = classpath
test = test

build-cxx = g++
build-cc = gcc

cxx = $(build-cxx)
cc = $(build-cc)
ar = ar
ranlib = ranlib
vg = nice valgrind --suppressions=valgrind.supp --undef-value-errors=no \
	--num-callers=32 --db-attach=yes --freelist-vol=100000000
db = gdb --args
javac = javac
strip = :
show-size = :

rdynamic = -rdynamic
shared = -shared

warnings = -Wall -Wextra -Werror -Wunused-parameter \
	-Winit-self -Wconversion

common-cflags = $(warnings) -fno-rtti -fno-exceptions \
	-I$(JAVA_HOME)/include -idirafter $(src) -I$(bld) -D__STDC_LIMIT_MACROS \
	-DBUILTIN_LIBRARIES=\"natives,tlscontext,scaler\"

cflags = $(common-cflags) -fPIC -fvisibility=hidden \
	-I$(JAVA_HOME)/include/linux -I$(src) -pthread

lflags = -lpthread -ldl -lm -lz

system = posix
asm = x86

ifeq ($(platform),darwin)
	rdynamic =
	thread-cflags =
	shared = -dynamiclib
endif
ifeq ($(platform),windows)
	inc = ../6.0/shared/include/msw
	lib = ../6.0/shared/lib/native/msw

	system = windows

	cxx = i586-mingw32msvc-g++
	cc = i586-mingw32msvc-gcc
	dlltool = i586-mingw32msvc-dlltool
	ar = i586-mingw32msvc-ar
	ranlib = i586-mingw32msvc-ranlib

	rdynamic = -Wl,--export-dynamic
	lflags = -L$(lib) -lm -lz -lws2_32 -Wl,--kill-at
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
java-classes = $(foreach x,$(1),$(patsubst $(2)/%.java,$(cls)/%.class,$(x)))

jni-sources = $(shell find $(classpath) -name '*.cpp')
jni-objects = $(call cpp-objects,$(jni-sources),$(classpath),$(bld))
jni-cflags = $(cflags)

generated-code = \
	$(bld)/type-enums.cpp \
	$(bld)/type-declarations.cpp \
	$(bld)/type-constructors.cpp \
	$(bld)/type-initializations.cpp \
	$(bld)/type-java-initializations.cpp

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
	$(src)/jnienv.cpp \
	$(src)/main.cpp

interpreter-asm-sources = $(src)/$(asm).S

ifeq ($(process),compile)
	interpreter-asm-sources += $(src)/compile.S
endif

interpreter-cpp-objects = \
	$(call cpp-objects,$(interpreter-sources),$(src),$(bld))
interpreter-asm-objects = \
	$(call asm-objects,$(interpreter-asm-sources),$(src),$(bld))
interpreter-objects = \
	$(interpreter-cpp-objects) \
	$(interpreter-asm-objects)

generator-headers = \
	$(src)/input.h \
	$(src)/output.h
generator-sources = \
	$(src)/type-generator.cpp
generator-objects = $(call cpp-objects,$(generator-sources),$(src),$(bld))
generator-executable = $(bld)/generator

archive = $(bld)/libvm.a
executable = $(bld)/vm

classpath-sources = $(shell find $(classpath) -name '*.java')
classpath-classes = $(call java-classes,$(classpath-sources),$(classpath))

classpath-objects = $(classpath-classes)

test-sources = $(shell find $(test) -name '*.java')
test-classes = $(call java-classes,$(test-sources),$(test))

class-name = $(patsubst $(cls)/%.class,%,$(1))
class-names = $(foreach x,$(1),$(call class-name,$(x)))

flags = -cp $(cls)
args = $(flags) $(call class-name,$(input))

.PHONY: build
build: $(executable) $(classpath-objects) $(test-classes)

$(input): $(classpath-classes)

.PHONY: run
run: build
	$(executable) $(args)

.PHONY: debug
debug: build
	gdb --args $(executable) $(args)

.PHONY: vg
vg: build
	$(vg) $(executable) $(args)

.PHONY: test
test: build
	/bin/bash $(test)/test.sh 2>/dev/null \
		$(executable) $(mode) "$(flags)" $(call class-names,$(test-classes))

.PHONY: clean
clean:
	@echo "removing build"
	rm -rf build

.PHONY: clean-native
clean-native:
	@echo "removing $(bld)"
	rm -rf $(bld)

gen-arg = $(shell echo $(1) | sed -e 's:$(bld)/type-\(.*\)\.cpp:\1:')
$(generated-code): %.cpp: $(src)/types.def $(generator-executable)
	@echo "generating $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(generator-executable) $(call gen-arg,$(@)) < $(<) > $(@)

$(bld)/type-generator.o: \
	$(generator-headers)

define compile-class
	@echo "compiling $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(javac) -bootclasspath $(classpath) -classpath $(classpath) \
		-d $(cls) $(<)
	@touch $(@)
endef

$(cls)/%.class: $(classpath)/%.java
	$(compile-class)

$(cls)/%.class: $(test)/%.java
	$(compile-class)

define compile-object
	@echo "compiling $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(cxx) $(cflags) -c $(<) -o $(@)
endef

$(interpreter-cpp-objects): $(bld)/%.o: $(src)/%.cpp $(interpreter-depends)
	$(compile-object)

$(interpreter-asm-objects): $(bld)/%-asm.o: $(src)/%.S
	$(compile-object)

$(generator-objects): $(bld)/%.o: $(src)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(build-cxx) -DPOINTER_SIZE=$(pointer-size) $(cflags) -c $(<) -o $(@)

$(jni-objects): $(bld)/%.o: $(classpath)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(cxx) $(jni-cflags) -c $(<) -o $(@)

ifeq ($(platform),windows)
$(archive): $(interpreter-objects) $(jni-objects)
	@echo "creating $(@)"
	$(dlltool) --export-all-symbols -z $(@).def $(^)
	$(dlltool) -k -d $(@).def -e $(@).exp
	$(ar) cru $(@) $(@).exp $(^)
	$(ranlib) $(@)
else
$(archive): $(interpreter-objects) $(jni-objects)
	@echo "creating $(@)"
	$(ar) cru $(@) $(^)
	$(ranlib) $(@)
endif

$(executable): $(archive)
	@echo "linking $(@)"
	$(cc) -Wl,--whole-archive $(^) -Wl,--no-whole-archive \
		$(lflags) $(rdynamic) -o $(@)
	@$(strip) --strip-all $(@)
	@$(show-size) $(@)

$(generator-executable): $(generator-objects)
	@echo "linking $(@)"
	$(build-cc) $(^) -o $(@)
