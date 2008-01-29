MAKEFLAGS = -s

name = vm

build-arch = $(shell uname -m)
ifeq ($(build-arch),i586)
	build-arch = i386
endif
ifeq ($(build-arch),i686)
	build-arch = i386
endif

build-platform = $(shell uname -s | tr [:upper:] [:lower:])

arch = $(build-arch)
platform = $(build-platform)

ifeq ($(platform),windows)
	arch = i386
endif

mode = fast
process = compile

build = build
native-build = $(build)/$(platform)-$(arch)-$(process)-$(mode)
classpath-build = $(build)/classpath
test-build = $(build)/test
src = src
classpath = classpath
test = test

input = List

build-cxx = g++
build-cc = gcc

cxx = $(build-cxx)
cc = $(build-cc)
ar = ar
ranlib = ranlib
objcopy = objcopy
vg = nice valgrind --num-callers=32 --db-attach=yes --freelist-vol=100000000
vg += --leak-check=full
db = gdb --args
javac = javac
jar = jar
strip = :
strip-all = --strip-all

rdynamic = -rdynamic

warnings = -Wall -Wextra -Werror -Wunused-parameter \
	-Winit-self -Wconversion

common-cflags = $(warnings) -fno-rtti -fno-exceptions \
	-I$(JAVA_HOME)/include -idirafter $(src) -I$(native-build) \
	-D__STDC_LIMIT_MACROS -D_JNI_IMPLEMENTATION_

build-cflags = $(common-cflags) -fPIC -fvisibility=hidden \
	-I$(JAVA_HOME)/include/linux -I$(src) -pthread

cflags = $(build-cflags)

common-lflags = -lm -lz

lflags = $(common-lflags) -lpthread -ldl -rdynamic

system = posix
asm = x86

object-arch = i386:x86-64
object-format = elf64-x86-64
pointer-size = 8

ifeq ($(arch),i386)
	object-arch = i386
	object-format = elf32-i386
	pointer-size = 4
endif

ifeq ($(platform),darwin)
	build-cflags = $(common-cflags) -fPIC -fvisibility=hidden \
		-I$(JAVA_HOME)/include/linux -I$(src)
	lflags = $(common-lflags) -ldl -framework CoreFoundation
	strip-all = -S -x
	binaryToMacho = $(native-build)/binaryToMacho
endif

ifeq ($(platform),windows)
	inc = /usr/local/win32/include
	lib = /usr/local/win32/lib

	system = windows
	object-format = pe-i386

	cxx = i586-mingw32msvc-g++
	cc = i586-mingw32msvc-gcc
	dlltool = i586-mingw32msvc-dlltool
	ar = i586-mingw32msvc-ar
	ranlib = i586-mingw32msvc-ranlib
	objcopy = i586-mingw32msvc-objcopy

	rdynamic = -Wl,--export-dynamic
	lflags = -L$(lib) $(common-lflags) -lws2_32 -Wl,--kill-at -mwindows -mconsole
	cflags = $(common-cflags) -I$(inc)
endif

ifeq ($(mode),debug)
	cflags += -O0 -g3
endif
ifeq ($(mode),debug-fast)
	cflags += -O0 -g3 -DNDEBUG
endif
ifeq ($(mode),stress)
	cflags += -O0 -g3 -DVM_STRESS
endif
ifeq ($(mode),stress-major)
	cflags += -O0 -g3 -DVM_STRESS -DVM_STRESS_MAJOR
endif
ifeq ($(mode),fast)
	cflags += -O3 -g3 -DNDEBUG
	strip = strip
endif

cpp-objects = $(foreach x,$(1),$(patsubst $(2)/%.cpp,$(3)/%.o,$(x)))
asm-objects = $(foreach x,$(1),$(patsubst $(2)/%.S,$(3)/%-asm.o,$(x)))
java-classes = $(foreach x,$(1),$(patsubst $(2)/%.java,$(3)/%.class,$(x)))

jni-sources = $(shell find $(classpath) -name '*.cpp')
jni-objects = $(call cpp-objects,$(jni-sources),$(classpath),$(native-build))

generated-code = \
	$(native-build)/type-enums.cpp \
	$(native-build)/type-declarations.cpp \
	$(native-build)/type-constructors.cpp \
	$(native-build)/type-initializations.cpp \
	$(native-build)/type-java-initializations.cpp

vm-depends = \
	$(generated-code) \
	$(src)/allocator.h \
	$(src)/common.h \
	$(src)/system.h \
	$(src)/heap.h \
	$(src)/finder.h \
	$(src)/processor.h \
	$(src)/process.h \
	$(src)/stream.h \
	$(src)/constants.h \
	$(src)/jnienv.h \
	$(src)/machine.h \
	$(src)/util.h \
	$(src)/zone.h

vm-sources = \
	$(src)/$(system).cpp \
	$(src)/finder.cpp \
	$(src)/machine.cpp \
	$(src)/util.cpp \
	$(src)/heap.cpp \
	$(src)/$(process).cpp \
	$(src)/builtin.cpp \
	$(src)/jnienv.cpp \
	$(src)/process.cpp

vm-asm-sources = $(src)/$(asm).S

ifeq ($(process),compile)
	vm-depends += \
		$(src)/compiler.h \
		$(src)/vector.h

	vm-sources += $(src)/compiler.cpp

	vm-asm-sources += $(src)/compile.S
endif

vm-cpp-objects = $(call cpp-objects,$(vm-sources),$(src),$(native-build))
vm-asm-objects = $(call asm-objects,$(vm-asm-sources),$(src),$(native-build))
vm-objects = $(vm-cpp-objects) $(vm-asm-objects)

driver-sources = $(src)/main.cpp

driver-object = $(call cpp-objects,$(driver-sources),$(src),$(native-build))

generator-headers =	$(src)/constants.h
generator-sources = $(src)/type-generator.cpp
generator-objects = \
	$(call cpp-objects,$(generator-sources),$(src),$(native-build))
generator = $(native-build)/generator

libvm = $(native-build)/lib$(name).a
vm = $(native-build)/$(name)

classpath-sources = $(shell find $(classpath) -name '*.java')
classpath-classes = \
	$(call java-classes,$(classpath-sources),$(classpath),$(classpath-build))
classpath-object = $(native-build)/classpath-jar.o
classpath-dep = $(classpath-build)/dep

test-sources = $(wildcard $(test)/*.java)
test-classes = $(call java-classes,$(test-sources),$(test),$(test-build))
test-dep = $(test-build)/dep

class-name = $(patsubst $(1)/%.class,%,$(2))
class-names = $(foreach x,$(2),$(call class-name,$(1),$(x)))

flags = -cp $(test-build)

args = $(flags) $(input)

.PHONY: build
build: $(vm) $(libvm) $(classpath-dep) $(test-dep)

$(test-classes): $(classpath-dep)

.PHONY: run
run: build
	$(vm) $(args)

.PHONY: debug
debug: build
	gdb --args $(vm) $(args)

.PHONY: vg
vg: build
	$(vg) $(vm) $(args)

.PHONY: test
test: build
	/bin/bash $(test)/test.sh 2>/dev/null \
		$(vm) $(mode) "$(flags)" \
		$(call class-names,$(test-build),$(test-classes))

.PHONY: clean
clean:
	@echo "removing build"
	rm -rf build

.PHONY: clean-native
clean-native:
	@echo "removing $(native-build)"
	rm -rf $(native-build)

gen-arg = $(shell echo $(1) | sed -e 's:$(native-build)/type-\(.*\)\.cpp:\1:')
$(generated-code): %.cpp: $(src)/types.def $(generator) $(classpath-dep)
	@echo "generating $(@)"
	@mkdir -p $(dir $(@))
	$(generator) $(classpath-build) $(call gen-arg,$(@)) < $(<) > $(@)

$(native-build)/type-generator.o: \
	$(generator-headers)

$(classpath-build)/%.class: $(classpath)/%.java
	@echo $(<)

$(classpath-dep): $(classpath-sources)
	@echo "compiling classpath classes"
	@mkdir -p $(dir $(@))
	$(javac) -d $(dir $(@)) -bootclasspath $(classpath-build) \
		$(shell make -s $(classpath-classes))
	@touch $(@)

$(test-build)/%.class: $(test)/%.java
	@echo $(<)

$(test-dep): $(test-sources)
	@echo "compiling test classes"
	@mkdir -p $(dir $(@))
	$(javac) -d $(dir $(@)) -bootclasspath $(classpath-build) \
		$(shell make -s $(test-classes))
	@touch $(@)

define compile-object
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(cflags) -c $(<) -o $(@)
endef

$(vm-cpp-objects): $(native-build)/%.o: $(src)/%.cpp $(vm-depends)
	$(compile-object)

$(vm-asm-objects): $(native-build)/%-asm.o: $(src)/%.S
	$(compile-object)

$(driver-object): $(native-build)/%.o: $(src)/%.cpp
	$(compile-object)

$(build)/classpath.jar: $(classpath-dep)
	(wd=$$(pwd); \
	 cd $(classpath-build); \
	 $(jar) c0f $${wd}/$(@) $$(find . -name '*.class'))

$(binaryToMacho): $(src)/binaryToMacho.cpp
	$(cxx) $(^) -o $(@)

$(classpath-object): $(build)/classpath.jar $(binaryToMacho)
ifeq ($(platform),darwin)
	$(binaryToMacho) $(build)/classpath.jar \
		__binary_classpath_jar_start __binary_classpath_jar_size > $(@)
else
	(wd=$$(pwd); \
	 cd $(build); \
	 $(objcopy) -I binary classpath.jar \
		 -O $(object-format) -B $(object-arch) $${wd}/$(@))
endif

$(generator-objects): $(native-build)/%.o: $(src)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(build-cxx) -DPOINTER_SIZE=$(pointer-size) -O0 -g3 $(build-cflags) \
		-c $(<) -o $(@)

$(jni-objects): $(native-build)/%.o: $(classpath)/%.cpp
	$(compile-object)

$(libvm): $(vm-objects) $(jni-objects)
	@echo "creating $(@)"
	rm -rf $(@)
	$(ar) cru $(@) $(^)
	$(ranlib) $(@)

$(vm): $(vm-objects) $(classpath-object) $(jni-objects) $(driver-object)
	@echo "linking $(@)"
ifeq ($(platform),windows)
	$(dlltool) -z $(@).def $(^)
	$(dlltool) -k -d $(@).def -e $(@).exp
	$(cc) $(@).exp $(^) $(lflags) -o $(@)
else
	$(cc) $(^) $(lflags) -o $(@)
endif
	$(strip) $(strip-all) $(@)

$(generator): $(generator-objects)
	@echo "linking $(@)"
	$(build-cc) $(^) -o $(@)

