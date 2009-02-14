MAKEFLAGS = -s

name = avian
version = 0.1.1

build-arch = $(shell uname -m | sed 's/^i.86$$/i386/')

build-platform = \
	$(shell uname -s | tr [:upper:] [:lower:] \
		| sed 's/^mingw32.*$$/mingw32/' \
		| sed 's/^cygwin.*$$/cygwin/')

arch = $(build-arch)
platform = $(subst cygwin,windows,$(subst mingw32,windows,$(build-platform)))

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
dlltool = dlltool
objcopy = objcopy
vg = nice valgrind --num-callers=32 --db-attach=yes --freelist-vol=100000000
vg += --leak-check=full --suppressions=valgrind.supp
db = gdb --args
javac = "$(JAVA_HOME)/bin/javac"
jar = "$(JAVA_HOME)/bin/jar"
strip = :
strip-all = --strip-all

rdynamic = -rdynamic

# note that we supress the non-virtual-dtor warning because we never
# use the delete operator, which means we don't need virtual
# destructors:
warnings = -Wall -Wextra -Werror -Wunused-parameter -Winit-self \
	-Wno-non-virtual-dtor

common-cflags = $(warnings) -fno-rtti -fno-exceptions -fno-omit-frame-pointer \
	"-I$(JAVA_HOME)/include" -idirafter $(src) -I$(native-build) \
	-D__STDC_LIMIT_MACROS -D_JNI_IMPLEMENTATION_ -DAVIAN_VERSION=\"$(version)\" \

build-cflags = $(common-cflags) -fPIC -fvisibility=hidden \
	"-I$(JAVA_HOME)/include/linux" -I$(src) -pthread

cflags = $(build-cflags)

common-lflags = -lm -lz

build-lflags =

lflags = $(common-lflags) -lpthread -ldl

system = posix
asm = x86

object-arch = i386:x86-64
object-format = elf64-x86-64
pointer-size = 8

so-prefix = lib
so-suffix = .so

shared = -shared

native-path = echo

ifeq ($(arch),i386)
	object-arch = i386
	object-format = elf32-i386
	pointer-size = 4
endif

ifeq ($(platform),darwin)
	build-cflags = $(common-cflags) -fPIC -fvisibility=hidden -I$(src)
	lflags = $(common-lflags) -ldl -framework CoreFoundation
	ifeq ($(bootimage),true)
		bootimage-lflags = -Wl,-segprot,__BOOT,rwx,rwx
	endif
	rdynamic =
	strip-all = -S -x
	binaryToMacho = $(native-build)/binaryToMacho
	so-suffix = .jnilib
	shared = -dynamiclib
endif

ifeq ($(platform),windows)
	inc = "$(root)/win32/include"
	lib = "$(root)/win32/lib"

	system = windows
	object-format = pe-i386

	so-prefix =
	so-suffix = .dll
	exe-suffix = .exe

	lflags = -L$(lib) $(common-lflags) -lws2_32 -mwindows -mconsole
	cflags = $(common-cflags) -I$(inc)

	ifeq (,$(filter mingw32 cygwin,$(build-platform)))
		cxx = i586-mingw32msvc-g++
		cc = i586-mingw32msvc-gcc
		dlltool = i586-mingw32msvc-dlltool
		ar = i586-mingw32msvc-ar
		ranlib = i586-mingw32msvc-ranlib
		objcopy = i586-mingw32msvc-objcopy
		strip = i586-mingw32msvc-strip
	else
		common-cflags += "-I$(JAVA_HOME)/include/win32"
		build-cflags = $(common-cflags) -I$(src) -mthreads
		ifeq ($(build-platform),cygwin)
			build-lflags += -mno-cygwin
			build-cflags += -mno-cygwin
			lflags += -mno-cygwin
			cflags += -mno-cygwin
			native-path = cygpath -m
		endif
	endif
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
endif
ifeq ($(mode),small)
	cflags += -Os -g3 -DNDEBUG
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
	$(src)/heapwalk.h \
	$(src)/bootimage.h

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

heapwalk-sources = $(src)/heapwalk.cpp 
heapwalk-objects = \
	$(call cpp-objects,$(heapwalk-sources),$(src),$(native-build))

ifeq ($(heapdump),true)
	vm-sources += $(src)/heapdump.cpp
	vm-heapwalk-objects = $(heapwalk-objects)
	cflags += -DAVIAN_HEAPDUMP
endif

bootimage-generator-sources = $(src)/bootimage.cpp 
bootimage-generator-objects = \
	$(call cpp-objects,$(bootimage-generator-sources),$(src),$(native-build))
bootimage-generator = \
	$(build)/$(build-platform)-$(build-arch)-compile-fast/bootimage-generator

bootimage-bin = $(native-build)/bootimage.bin
bootimage-object = $(native-build)/bootimage-bin.o

ifeq ($(bootimage),true)
	ifneq ($(build-arch),$(arch))
		error "can't cross-build a bootimage"
	endif

	vm-classpath-object = $(bootimage-object)
	cflags += -DBOOT_IMAGE=\"bootimageBin\"
else
	vm-classpath-object = $(classpath-object)
	cflags += -DBOOT_CLASSPATH=\"[classpathJar]\"
endif

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
executable = $(native-build)/$(name)${exe-suffix}
dynamic-library = $(native-build)/$(so-prefix)$(name)$(so-suffix)
executable-dynamic = $(native-build)/$(name)-dynamic${exe-suffix}

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
	/bin/sh $(test)/test.sh 2>/dev/null \
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
		$(shell $(MAKE) -s --no-print-directory $(classpath-classes))
	@touch $(@)

$(test-build)/%.class: $(test)/%.java
	@echo $(<)

$(test-dep): $(test-sources)
	@echo "compiling test classes"
	@mkdir -p $(dir $(@))
	$(javac) -d $(dir $(@)) -bootclasspath $(classpath-build) \
		$(shell $(MAKE) -s --no-print-directory $(test-classes))
	@touch $(@)

define compile-object
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(cflags) -c $(<) -o $(@)
endef

define compile-asm-object
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cc) -I$(src) -c $(<) -o $(@)
endef

$(vm-cpp-objects): $(native-build)/%.o: $(src)/%.cpp $(vm-depends)
	$(compile-object)

$(vm-asm-objects): $(native-build)/%-asm.o: $(src)/%.S
	$(compile-asm-object)

$(bootimage-generator-objects): $(native-build)/%.o: $(src)/%.cpp $(vm-depends)
	$(compile-object)

$(heapwalk-objects): $(native-build)/%.o: $(src)/%.cpp $(vm-depends)
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
	 $(jar) c0f "$$($(native-path) "$${wd}/$(@)")" $$(find . -name '*.class'))

$(binaryToMacho): $(src)/binaryToMacho.cpp
	$(cxx) $(^) -o $(@)

$(classpath-object): $(build)/classpath.jar $(binaryToMacho)
	@echo "creating $(@)"
ifeq ($(platform),darwin)
	$(binaryToMacho) $(build)/classpath.jar __TEXT __text \
		__binary_classpath_jar_start __binary_classpath_jar_end > $(@)
else
	(wd=$$(pwd); \
	 cd $(build); \
	 $(objcopy) -I binary classpath.jar \
		 -O $(object-format) -B $(object-arch) "$${wd}/$(@)")
endif

$(generator-objects): $(native-build)/%.o: $(src)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(build-cxx) -DPOINTER_SIZE=$(pointer-size) -O0 -g3 $(build-cflags) \
		-c $(<) -o $(@)

$(jni-objects): $(native-build)/%.o: $(classpath)/%.cpp
	$(compile-object)

$(static-library): $(vm-objects) $(jni-objects) $(vm-heapwalk-objects)
	@echo "creating $(@)"
	rm -rf $(@)
	$(ar) cru $(@) $(^)
	$(ranlib) $(@)

$(bootimage-bin): $(bootimage-generator)
	$(<) $(classpath-build) > $(@)

$(bootimage-object): $(bootimage-bin) $(binaryToMacho)
	@echo "creating $(@)"
ifeq ($(platform),darwin)
	$(binaryToMacho) $(<) __BOOT __boot \
		__binary_bootimage_bin_start __binary_bootimage_bin_end > $(@)
else
	(wd=$$(pwd); \
	 cd $(native-build); \
	 $(objcopy) --rename-section=.data=.boot -I binary bootimage.bin \
		-O $(object-format) -B $(object-arch) "$${wd}/$(@).tmp"; \
	 $(objcopy) --set-section-flags .boot=alloc,load,code "$${wd}/$(@).tmp" \
		"$${wd}/$(@)")
endif

$(executable): \
		$(vm-objects) $(jni-objects) $(driver-object) $(vm-heapwalk-objects) \
		$(boot-object) $(vm-classpath-object)
	@echo "linking $(@)"
ifeq ($(platform),windows)
	$(dlltool) -z $(@).def $(^)
	$(dlltool) -d $(@).def -e $(@).exp
	$(cc) $(@).exp $(^) $(lflags) -o $(@)
else
	$(cc) $(^) $(rdynamic) $(lflags) $(bootimage-lflags) -o $(@)
endif
	$(strip) $(strip-all) $(@)

$(bootimage-generator):
	(unset MAKEFLAGS && \
	 make mode=fast process=compile \
		arch=$(build-arch) \
		platform=$(build-platform) \
		bootimage-generator= \
		build-bootimage-generator=$(bootimage-generator) \
		$(bootimage-generator))

$(build-bootimage-generator): \
		$(vm-objects) $(classpath-object) $(jni-objects) $(heapwalk-objects) \
		$(bootimage-generator-objects)
	@echo "linking $(@)"
ifeq ($(platform),windows)
	$(dlltool) -z $(@).def $(^)
	$(dlltool) -d $(@).def -e $(@).exp
	$(cc) $(@).exp $(^) $(lflags) -o $(@)
else
	$(cc) $(^) $(rdynamic) $(lflags) -o $(@)
endif

$(dynamic-library): \
		$(vm-objects) $(dynamic-object) $(jni-objects) $(vm-heapwalk-objects) \
		$(boot-object) $(vm-classpath-object)
	@echo "linking $(@)"
	$(cc) $(^) $(shared) $(lflags) $(bootimage-lflags) -o $(@)
	$(strip) $(strip-all) $(@)

$(executable-dynamic): $(driver-dynamic-object) $(dynamic-library)
	@echo "linking $(@)"
	$(cc) $(^) $(lflags) -o $(@)
	$(strip) $(strip-all) $(@)

$(generator): $(generator-objects)
	@echo "linking $(@)"
	$(build-cc) $(^) $(build-lflags) -o $(@)

