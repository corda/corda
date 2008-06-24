#MAKEFLAGS = -s

name = avian
version = 0.1.1

build-arch = $(shell uname -m)
ifeq ($(build-arch),Power)
	build-arch = powerpc
endif
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

root = $(shell (cd .. && pwd))
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
vg += --leak-check=full --suppressions=valgrind.supp
db = gdb --args
javac = javac
jar = jar
strip = :
strip-all = --strip-all

rdynamic = -rdynamic

warnings = -Wall -Wextra -Werror -Wunused-parameter -Winit-self

common-cflags = $(warnings) -fno-rtti -fno-exceptions \
	-I$(JAVA_HOME)/include -idirafter $(src) -I$(native-build) \
	-D__STDC_LIMIT_MACROS -D_JNI_IMPLEMENTATION_ -DAVIAN_VERSION=\"$(version)\" \
	-DBOOT_CLASSPATH=\"[classpathJar]\"

build-cflags = $(common-cflags) -fPIC -fvisibility=hidden \
	-I$(JAVA_HOME)/include/linux -I$(src) -pthread

cflags = $(build-cflags)

common-lflags = -lm -lz

lflags = $(common-lflags) -lpthread -ldl

system = posix
asm = x86

object-arch = i386:x86-64
object-format = elf64-x86-64
pointer-size = 8

so-prefix = lib
so-suffix = .so

shared = -shared

ifeq ($(arch),i386)
	object-arch = i386
	object-format = elf32-i386
	pointer-size = 4
endif
ifeq ($(arch),powerpc)
	asm = powerpc
	object-arch = powerpc
	object-format = elf32-powerpc
	pointer-size = 4
endif

ifeq ($(platform),darwin)
	build-cflags = $(common-cflags) -fPIC -fvisibility=hidden \
		-I$(JAVA_HOME)/include/linux -I$(src)
	lflags = $(common-lflags) -ldl -framework CoreFoundation
	rdynamic =
	strip-all = -S -x
	binaryToMacho = $(native-build)/binaryToMacho
	so-suffix = .jnilib
	shared = -dynamiclib
endif

ifeq ($(platform),windows)
	inc = $(root)/win32/include
	lib = $(root)/win32/lib

	system = windows
	object-format = pe-i386

	so-prefix =
	so-suffix = .dll

	cxx = i586-mingw32msvc-g++
	cc = i586-mingw32msvc-gcc
	dlltool = i586-mingw32msvc-dlltool
	ar = i586-mingw32msvc-ar
	ranlib = i586-mingw32msvc-ranlib
	objcopy = i586-mingw32msvc-objcopy

	rdynamic = -Wl,--export-dynamic
	lflags = -L$(lib) $(common-lflags) -lws2_32 -mwindows -mconsole
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
	$(src)/zone.h \
	$(src)/assembler.h \
	$(src)/compiler.h \
	$(src)/$(asm).h

vm-sources = \
	$(src)/$(system).cpp \
	$(src)/finder.cpp \
	$(src)/machine.cpp \
	$(src)/util.cpp \
	$(src)/heap.cpp \
	$(src)/$(process).cpp \
	$(src)/builtin.cpp \
	$(src)/jnienv.cpp \
	$(src)/process.cpp \
	$(src)/$(asm).cpp

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

driver-source = $(src)/main.cpp
driver-object = $(native-build)/main.o
driver-dynamic-object = $(native-build)/main-dynamic.o

boot-source = $(src)/boot.cpp
boot-object = $(native-build)/boot.o

generator-headers =	$(src)/constants.h
generator-sources = $(src)/type-generator.cpp
generator-objects = \
	$(call cpp-objects,$(generator-sources),$(src),$(native-build))
generator = $(native-build)/generator

static-library = $(native-build)/lib$(name).a
executable = $(native-build)/$(name)
dynamic-library = $(native-build)/$(so-prefix)$(name)$(so-suffix)
executable-dynamic = $(native-build)/$(name)-dynamic

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
build: $(static-library) $(executable) $(dynamic-library) \
	$(executable-dynamic) $(classpath-dep) $(test-dep)

$(test-classes): $(classpath-dep)

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
		$(executable) $(mode) "$(flags)" \
		$(call class-names,$(test-build),$(test-classes))

.PHONY: javadoc
javadoc:
	javadoc -sourcepath classpath -d build/javadoc -subpackages java \
		-windowtitle "Avian v$(version) Class Library API" \
		-doctitle "Avian v$(version) Class Library API" \
		-header "Avian v$(version)" \
		-bottom "<a href=\"http://oss.readytalk.com/avian/\">http://oss.readytalk.com/avian</a>"

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
		$(shell make -s --no-print-directory $(classpath-classes))
	@touch $(@)

$(test-build)/%.class: $(test)/%.java
	@echo $(<)

$(test-dep): $(test-sources)
	@echo "compiling test classes"
	@mkdir -p $(dir $(@))
	$(javac) -d $(dir $(@)) -bootclasspath $(classpath-build) \
		$(shell make -s --no-print-directory $(test-classes))
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

$(driver-object): $(driver-source)
	$(compile-object)

$(driver-dynamic-object): $(driver-source)
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(cflags) -DBOOT_LIBRARY=\"$(so-prefix)$(name)$(so-suffix)\" \
		-c $(<) -o $(@)

$(boot-object): $(boot-source)
	$(compile-object)

$(build)/classpath.jar: $(classpath-dep)
	(wd=$$(pwd); \
	 cd $(classpath-build); \
	 $(jar) c0f $${wd}/$(@) $$(find . -name '*.class'))

$(binaryToMacho): $(src)/binaryToMacho.cpp
	$(cxx) $(^) -o $(@)

$(classpath-object): $(build)/classpath.jar $(binaryToMacho)
	@echo "creating $(@)"
ifeq ($(platform),darwin)
	$(binaryToMacho) $(asm) $(build)/classpath.jar \
		__binary_classpath_jar_start __binary_classpath_jar_end > $(@)
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

$(static-library): $(vm-objects) $(jni-objects)
	@echo "creating $(@)"
	rm -rf $(@)
	$(ar) cru $(@) $(^)
	$(ranlib) $(@)

$(executable): \
		$(vm-objects) $(classpath-object) $(jni-objects) $(driver-object) \
		$(boot-object)
	@echo "linking $(@)"
ifeq ($(platform),windows)
	$(dlltool) -z $(@).def $(^)
	$(dlltool) -k -d $(@).def -e $(@).exp
	$(cc) $(@).exp $(^) $(lflags) -o $(@)
else
	$(cc) $(^) $(rdynamic) $(lflags) -o $(@)
endif
	$(strip) $(strip-all) $(@)

$(dynamic-library): \
		$(vm-objects) $(classpath-object) $(dynamic-object) $(jni-objects) \
		$(boot-object)
	@echo "linking $(@)"
	$(cc) $(^) $(shared) $(lflags) -o $(@)
	$(strip) $(strip-all) $(@)

$(executable-dynamic): $(driver-dynamic-object) $(dynamic-library)
	@echo "linking $(@)"
	$(cc) $(^) $(lflags) -o $(@)
	$(strip) $(strip-all) $(@)

$(generator): $(generator-objects)
	@echo "linking $(@)"
	$(build-cc) $(^) -o $(@)

