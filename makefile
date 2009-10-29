MAKEFLAGS = -s

name = avian
version = 0.2

build-arch := $(shell uname -m | sed 's/^i.86$$/i386/')
ifeq (Power,$(filter Power,$(build-arch)))
	build-arch = powerpc
endif

build-platform := \
	$(shell uname -s | tr [:upper:] [:lower:] \
		| sed 's/^mingw32.*$$/mingw32/' \
		| sed 's/^cygwin.*$$/cygwin/')

arch = $(build-arch)
bootimage-platform = \
	$(subst cygwin,windows,$(subst mingw32,windows,$(build-platform)))
platform = $(bootimage-platform)

mode = fast
process = compile

ifneq ($(process),compile)
	options := -$(process)
endif
ifneq ($(mode),fast)
	options := $(options)-$(mode)
endif
ifeq ($(bootimage),true)
	options := $(options)-bootimage
endif
ifeq ($(heapdump),true)
	options := $(options)-heapdump
endif
ifeq ($(tails),true)
	options := $(options)-tails
endif
ifeq ($(continuations),true)
	options := $(options)-continuations
endif
ifdef gnu
  options := $(options)-gnu
	gnu-sources = $(src)/gnu.cpp
	gnu-jar = $(gnu)/share/classpath/glibj.zip
	gnu-libraries = \
		$(gnu)/lib/classpath/libjavaio.a \
		$(gnu)/lib/classpath/libjavalang.a \
		$(gnu)/lib/classpath/libjavalangreflect.a \
		$(gnu)/lib/classpath/libjavamath.a \
		$(gnu)/lib/classpath/libjavanet.a \
		$(gnu)/lib/classpath/libjavanio.a \
		$(gnu)/lib/classpath/libjavautil.a
	gnu-object-dep = $(build)/gnu-object.dep
	gnu-cflags = -DBOOT_BUILTINS=\"javaio,javalang,javalangreflect,javamath,javanet,javanio,javautil\" -DAVIAN_GNU
	gnu-lflags = -lgmp
	gnu-objects := $(shell find $(build)/gnu-objects -name "*.o") 
endif

root := $(shell (cd .. && pwd))
build = build
native-build = $(build)/$(platform)-$(arch)$(options)
classpath-build = $(build)/classpath
test-build = $(build)/test
src = src
classpath = classpath
test = test

ifdef gnu
	avian-classpath-build = $(build)/avian-classpath
else
	avian-classpath-build = $(classpath-build)
endif

input = List

build-cxx = g++
build-cc = gcc

cxx = $(build-cxx)
cc = $(build-cc)
ar = ar
ranlib = ranlib
dlltool = dlltool
vg = nice valgrind --num-callers=32 --db-attach=yes --freelist-vol=100000000
vg += --leak-check=full --suppressions=valgrind.supp
db = gdb --args
javac = "$(JAVA_HOME)/bin/javac"
jar = "$(JAVA_HOME)/bin/jar"
strip = strip
strip-all = --strip-all

rdynamic = -rdynamic

# note that we suppress the non-virtual-dtor warning because we never
# use the delete operator, which means we don't need virtual
# destructors:
warnings = -Wall -Wextra -Werror -Wunused-parameter -Winit-self \
	-Wno-non-virtual-dtor

common-cflags = $(warnings) -fno-rtti -fno-exceptions -fno-omit-frame-pointer \
	"-I$(JAVA_HOME)/include" -idirafter $(src) -I$(native-build) \
	-D__STDC_LIMIT_MACROS -D_JNI_IMPLEMENTATION_ -DAVIAN_VERSION=\"$(version)\" \
	$(gnu-cflags)

build-cflags = $(common-cflags) -fPIC -fvisibility=hidden \
	"-I$(JAVA_HOME)/include/linux" -I$(src) -pthread

cflags = $(build-cflags)

common-lflags = -lm -lz $(gnu-lflags)

build-lflags =

lflags = $(common-lflags) -lpthread -ldl

system = posix
asm = x86

pointer-size = 8

so-prefix = lib
so-suffix = .so

shared = -shared

native-path = echo

ifeq ($(arch),i386)
	pointer-size = 4
endif
ifeq ($(arch),powerpc)
	asm = powerpc
	pointer-size = 4
endif
ifeq ($(arch),arm)
  lflags := -L/opt/crosstool/gcc-4.1.0-glibc-2.3.2/arm-unknown-linux-gnu/arm-unknown-linux-gnu/lib -L$(root)/arm/lib $(lflags)
  cflags := -I/opt/crosstool/gcc-4.1.0-glibc-2.3.2/arm-unknown-linux-gnu/arm-unknown-linux-gnu/include -I$(root)/arm/include $(cflags)

  asm = arm
  object-arch = arm 
  object-format = elf32-littlearm
  pointer-size = 4 
  cxx = arm-unknown-linux-gnu-g++
  cc = arm-unknown-linux-gnu-gcc
  ar = arm-unknown-linux-gnu-ar
  ranlib = arm-unknown-linux-gnu-ranlib
  objcopy = arm-unknown-linux-gnu-objcopy
  strip = arm-unknown-linux-gnu-strip
endif

ifeq ($(platform),darwin)
	build-cflags = $(common-cflags) -fPIC -fvisibility=hidden -I$(src)
	lflags = $(common-lflags) -ldl -framework CoreFoundation -framework CoreServices
	ifeq ($(bootimage),true)
		bootimage-lflags = -Wl,-segprot,__RWX,rwx,rwx
	endif
	rdynamic =
	strip-all = -S -x
	so-suffix = .jnilib
	shared = -dynamiclib
endif

ifeq ($(platform),windows)
	inc = "$(root)/win32/include"
	lib = "$(root)/win32/lib"

	system = windows

	so-prefix =
	so-suffix = .dll
	exe-suffix = .exe

	lflags = -L$(lib) $(common-lflags) -lws2_32 -mwindows -mconsole
	cflags = -I$(inc) $(common-cflags)

	ifeq (,$(filter mingw32 cygwin,$(build-platform)))
		cxx = i586-mingw32msvc-g++
		cc = i586-mingw32msvc-gcc
		dlltool = i586-mingw32msvc-dlltool
		ar = i586-mingw32msvc-ar
		ranlib = i586-mingw32msvc-ranlib
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

	ifeq ($(arch),x86_64)
		cxx = x86_64-w64-mingw32-g++
		cc = x86_64-w64-mingw32-gcc
		dlltool = x86_64-w64-mingw32-dlltool
		ar = x86_64-w64-mingw32-ar
		ranlib = x86_64-w64-mingw32-ranlib
		strip = x86_64-w64-mingw32-strip
		inc = "$(root)/win64/include"
		lib = "$(root)/win64/lib"
	endif
endif

ifeq ($(mode),debug)
	cflags += -O0 -g3
	strip = :
endif
ifeq ($(mode),debug-fast)
	cflags += -O0 -g3 -DNDEBUG
	strip = :
endif
ifeq ($(mode),stress)
	cflags += -O0 -g3 -DVM_STRESS
	strip = :
endif
ifeq ($(mode),stress-major)
	cflags += -O0 -g3 -DVM_STRESS -DVM_STRESS_MAJOR
	strip = :
endif
ifeq ($(mode),fast)
	cflags += -O3 -g3 -DNDEBUG
endif
ifeq ($(mode),small)
	cflags += -Os -g3 -DNDEBUG
endif

output = -o $(1)
as := $(cc)
ld := $(cc)
build-ld := $(build-cc)

ifdef msvc
	windows-java-home := $(shell cygpath -m "$(JAVA_HOME)")
	zlib := $(shell cygpath -m "$(root)/win32/msvc")
	cxx = "$(msvc)/BIN/cl.exe"
	cc = $(cxx)
	ld = "$(msvc)/BIN/link.exe"
	mt = "mt.exe"
	cflags = -nologo -DAVIAN_VERSION=\"$(version)\" -D_JNI_IMPLEMENTATION_ \
		-Fd$(native-build)/$(name).pdb -I"$(zlib)/include" -I$(src) \
		-I"$(native-build)" -I"$(windows-java-home)/include" \
		-I"$(windows-java-home)/include/win32"
	shared = -dll
	lflags = -nologo -LIBPATH:"$(zlib)/lib" -DEFAULTLIB:ws2_32 \
		-DEFAULTLIB:zlib -MANIFEST
	output = -Fo$(1)

	ifeq ($(mode),debug)
		cflags += -Od -Zi -MDd
		lflags += -debug
	endif
	ifeq ($(mode),debug-fast)
		cflags += -Od -Zi -DNDEBUG
		lflags += -debug
	endif
	ifeq ($(mode),fast)
		cflags += -Ob2it -GL -Zi -DNDEBUG
		lflags += -LTCG
	endif
	ifeq ($(mode),small)
		cflags += -O1s -Zi -GL -DNDEBUG
		lflags += -LTCG
	endif

	strip = :
endif

cpp-objects = $(foreach x,$(1),$(patsubst $(2)/%.cpp,$(3)/%.o,$(x)))
asm-objects = $(foreach x,$(1),$(patsubst $(2)/%.S,$(3)/%-asm.o,$(x)))
java-classes = $(foreach x,$(1),$(patsubst $(2)/%.java,$(3)/%.class,$(x)))

jni-sources := $(shell find $(classpath) -name '*.cpp')
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
	$(src)/$(asm).h \
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
	$(gnu-sources)

vm-asm-sources = $(src)/$(asm).S

ifeq ($(process),compile)
	vm-depends += \
		$(src)/compiler.h \
		$(src)/vector.h

	vm-sources += \
		$(src)/compiler.cpp \
		$(src)/$(asm).cpp

	vm-asm-sources += $(src)/compile-$(asm).S
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

ifeq ($(tails),true)
	cflags += -DAVIAN_TAILS
endif

ifeq ($(continuations),true)
	cflags += -DAVIAN_CONTINUATIONS
	asmflags += -DAVIAN_CONTINUATIONS
endif

bootimage-generator-sources = $(src)/bootimage.cpp 
bootimage-generator-objects = \
	$(call cpp-objects,$(bootimage-generator-sources),$(src),$(native-build))
bootimage-generator = \
	$(build)/$(bootimage-platform)-$(build-arch)$(options)/bootimage-generator

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

converter-objects = \
	$(native-build)/binaryToObject-main.o \
	$(native-build)/binaryToObject-elf64.o \
	$(native-build)/binaryToObject-elf32.o \
	$(native-build)/binaryToObject-mach-o64.o \
	$(native-build)/binaryToObject-mach-o32.o \
	$(native-build)/binaryToObject-pe.o
converter = $(native-build)/binaryToObject

static-library = $(native-build)/lib$(name).a
executable = $(native-build)/$(name)${exe-suffix}
dynamic-library = $(native-build)/$(so-prefix)$(name)$(so-suffix)
executable-dynamic = $(native-build)/$(name)-dynamic${exe-suffix}

classpath-sources := $(shell find $(classpath) -name '*.java')
classpath-classes = \
	$(call java-classes,$(classpath-sources),$(classpath),$(classpath-build))
classpath-object = $(native-build)/classpath-jar.o
classpath-dep = $(classpath-build).dep

gnu-blacklist = \
	java/lang/AbstractStringBuffer.class \
	java/lang/reflect/Proxy.class

gnu-overrides = \
	avian/*.class \
	avian/resource/*.class \
	java/lang/Class.class \
	java/lang/Class\$$*.class \
	java/lang/Enum.class \
	java/lang/InheritableThreadLocal.class \
	java/lang/Object.class \
	java/lang/StackTraceElement.class \
	java/lang/String.class \
	java/lang/String\$$*.class \
	java/lang/StringBuffer.class \
	java/lang/StringBuilder.class \
	java/lang/StringBuilder\$$*.class \
	java/lang/Thread.class \
	java/lang/Thread\$$*.class \
	java/lang/ThreadGroup.class \
	java/lang/ThreadLocal.class \
	java/lang/Throwable.class \
	java/lang/ref/PhantomReference.class \
	java/lang/ref/Reference.class \
	java/lang/ref/ReferenceQueue.class \
	java/lang/ref/SoftReference.class \
	java/lang/ref/WeakReference.class \
	java/lang/reflect/AccessibleObject.class \
	java/lang/reflect/Constructor.class \
	java/lang/reflect/Field.class \
	java/lang/reflect/Method.class

test-sources = $(wildcard $(test)/*.java)
test-classes = $(call java-classes,$(test-sources),$(test),$(test-build))
test-dep = $(test-build).dep

test-extra-sources = $(wildcard $(test)/extra/*.java)
test-extra-classes = \
	$(call java-classes,$(test-extra-sources),$(test),$(test-build))
test-extra-dep = $(test-build)-extra.dep

class-name = $(patsubst $(1)/%.class,%,$(2))
class-names = $(foreach x,$(2),$(call class-name,$(1),$(x)))

flags = -cp $(test-build)

args = $(flags) $(input)

.PHONY: build
build: $(static-library) $(executable) $(dynamic-library) \
	$(executable-dynamic) $(classpath-dep) $(test-dep) $(test-extra-dep)

$(test-dep): $(classpath-dep)

$(test-extra-dep): $(classpath-dep)

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

.PHONY: tarball
tarball:
	@echo "creating build/avian-$(version).tar.bz2"
	@mkdir -p build
	(cd .. && tar --exclude=build --exclude='.*' --exclude='*~' -cjf \
		avian/build/avian-$(version).tar.bz2 avian)

.PHONY: javadoc
javadoc:
	javadoc -sourcepath classpath -d build/javadoc -subpackages avian:java \
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

$(classpath-dep): $(classpath-sources) $(gnu-jar)
	@echo "compiling classpath classes"
	@mkdir -p $(avian-classpath-build)
	$(javac) -d $(avian-classpath-build) \
		-bootclasspath $(avian-classpath-build) \
		$(shell $(MAKE) -s --no-print-directory $(classpath-classes))
ifdef gnu
	(wd=$$(pwd) && \
	 cd $(avian-classpath-build) && \
	 $(jar) c0f "$$($(native-path) "$${wd}/$(build)/overrides.jar")" \
		 $(gnu-overrides))
	@mkdir -p $(classpath-build)
	(wd=$$(pwd) && \
	 cd $(classpath-build) && \
	 $(jar) xf $(gnu-jar) && \
	 rm $(gnu-blacklist) && \
	 jar xf "$$($(native-path) "$${wd}/$(build)/overrides.jar")")
endif
	@touch $(@)

$(test-build)/%.class: $(test)/%.java
	@echo $(<)

$(test-dep): $(test-sources)
	@echo "compiling test classes"
	@mkdir -p $(test-build)
	files="$(shell $(MAKE) -s --no-print-directory $(test-classes))"; \
	if test -n "$${files}"; then \
		$(javac) -d $(test-build) -bootclasspath $(classpath-build) $${files}; \
	fi
	$(javac) -source 1.2 -target 1.1 -XDjsrlimit=0 -d $(test-build) \
		test/Subroutine.java
	@touch $(@)

$(test-extra-dep): $(test-extra-sources)
	@echo "compiling extra test classes"
	@mkdir -p $(test-build)
	files="$(shell $(MAKE) -s --no-print-directory $(test-extra-classes))"; \
	if test -n "$${files}"; then \
		$(javac) -d $(test-build) -bootclasspath $(classpath-build) $${files}; \
	fi
	@touch $(@)

define compile-object
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(cflags) -c $(<) $(call output,$(@))
endef

define compile-asm-object
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(as) -I$(src) $(asmflags) -c $(<) -o $(@)
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
		-c $(<) $(call output,$(@))

$(boot-object): $(boot-source)
	$(compile-object)

$(build)/classpath.jar: $(classpath-dep)
	(wd=$$(pwd) && \
	 cd $(classpath-build) && \
	 $(jar) c0f "$$($(native-path) "$${wd}/$(@)")" .)

$(native-build)/binaryToObject-main.o: $(src)/binaryToObject/main.cpp
	$(build-cxx) -c $(^) -o $(@)

$(native-build)/binaryToObject-elf64.o: $(src)/binaryToObject/elf.cpp
	$(build-cxx) -DBITS_PER_WORD=64 -c $(^) -o $(@)

$(native-build)/binaryToObject-elf32.o: $(src)/binaryToObject/elf.cpp
	$(build-cxx) -DBITS_PER_WORD=32 -c $(^) -o $(@)

$(native-build)/binaryToObject-mach-o64.o: $(src)/binaryToObject/mach-o.cpp
	$(build-cxx) -DBITS_PER_WORD=64 -c $(^) -o $(@)

$(native-build)/binaryToObject-mach-o32.o: $(src)/binaryToObject/mach-o.cpp
	$(build-cxx) -DBITS_PER_WORD=32 -c $(^) -o $(@)

$(native-build)/binaryToObject-pe.o: $(src)/binaryToObject/pe.cpp
	$(build-cxx) -c $(^) -o $(@)

$(converter): $(converter-objects)
	$(build-cxx) $(^) -o $(@)

$(classpath-object): $(build)/classpath.jar $(converter)
	@echo "creating $(@)"
	$(converter) $(<) $(@) _binary_classpath_jar_start \
		_binary_classpath_jar_end $(platform) $(arch)

$(generator-objects): $(native-build)/%.o: $(src)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(build-cxx) -DPOINTER_SIZE=$(pointer-size) -O0 -g3 $(build-cflags) \
		-c $(<) -o $(@)

$(jni-objects): $(native-build)/%.o: $(classpath)/%.cpp
	$(compile-object)

$(static-library): $(gnu-object-dep)
$(static-library): $(vm-objects) $(jni-objects) $(vm-heapwalk-objects)
	@echo "creating $(@)"
	rm -rf $(@)
	$(ar) cru $(@) $(^) $(call gnu-objects)
	$(ranlib) $(@)

$(bootimage-bin): $(bootimage-generator)
	$(<) $(classpath-build) $(@)

$(bootimage-object): $(bootimage-bin) $(converter)
	@echo "creating $(@)"
	$(converter) $(<) $(@) _binary_bootimage_bin_start \
		_binary_bootimage_bin_end $(platform) $(arch) $(pointer-size) \
		writable executable

$(gnu-object-dep): $(gnu-libraries)
	@mkdir -p $(build)/gnu-objects
	(cd $(build)/gnu-objects && \
	 for x in $(gnu-libraries); do ar x $${x}; done)
	@touch $(@)

$(executable): $(gnu-object-dep)
$(executable): \
		$(vm-objects) $(jni-objects) $(driver-object) $(vm-heapwalk-objects) \
		$(boot-object) $(vm-classpath-object)
	@echo "linking $(@)"
ifeq ($(platform),windows)
ifdef msvc
	$(ld) $(lflags) $(^) -out:$(@) -PDB:$(@).pdb -IMPLIB:$(@).lib \
		-MANIFESTFILE:$(@).manifest
	$(mt) -manifest $(@).manifest -outputresource:"$(@);1"
else
	$(dlltool) -z $(@).def $(^) $(call gnu-objects)
	$(dlltool) -d $(@).def -e $(@).exp
	$(ld) $(@).exp $(^) $(call gnu-objects) $(lflags) -o $(@)
endif
else
	$(ld) $(^) $(call gnu-objects) $(rdynamic) $(lflags) $(bootimage-lflags) \
		-o $(@)
endif
	$(strip) $(strip-all) $(@)

$(bootimage-generator):
	$(MAKE) mode=$(mode) \
		arch=$(build-arch) \
		platform=$(bootimage-platform) \
		bootimage-generator= \
		build-bootimage-generator=$(bootimage-generator) \
		$(bootimage-generator)

$(build-bootimage-generator): \
		$(vm-objects) $(classpath-object) $(jni-objects) $(heapwalk-objects) \
		$(bootimage-generator-objects)
	@echo "linking $(@)"
ifeq ($(platform),windows)
ifdef msvc
	$(ld) $(lflags) $(^) -out:$(@) -PDB:$(@).pdb -IMPLIB:$(@).lib \
		-MANIFESTFILE:$(@).manifest
	$(mt) -manifest $(@).manifest -outputresource:"$(@);1"
else
	$(dlltool) -z $(@).def $(^)
	$(dlltool) -d $(@).def -e $(@).exp
	$(ld) $(@).exp $(^) $(lflags) -o $(@)
endif
else
	$(ld) $(^) $(rdynamic) $(lflags) -o $(@)
endif

$(dynamic-library): $(gnu-object-dep)
$(dynamic-library): \
		$(vm-objects) $(dynamic-object) $(jni-objects) $(vm-heapwalk-objects) \
		$(boot-object) $(vm-classpath-object) $(gnu-libraries)
	@echo "linking $(@)"
ifdef msvc
	$(ld) $(shared) $(lflags) $(^) -out:$(@) -PDB:$(@).pdb \
		-IMPLIB:$(native-build)/$(name).lib -MANIFESTFILE:$(@).manifest
	$(mt) -manifest $(@).manifest -outputresource:"$(@);2"
else
	$(ld) $(^) $(call gnu-objects) $(shared) $(lflags) $(bootimage-lflags) \
		-o $(@)
endif
	$(strip) $(strip-all) $(@)

$(executable-dynamic): $(driver-dynamic-object) $(dynamic-library)
	@echo "linking $(@)"
ifdef msvc
	$(ld) $(lflags) -LIBPATH:$(native-build) -DEFAULTLIB:$(name) \
		-PDB:$(@).pdb -IMPLIB:$(@).lib $(<) -out:$(@) -MANIFESTFILE:$(@).manifest
	$(mt) -manifest $(@).manifest -outputresource:"$(@);1"
else
	$(ld) $(^) $(lflags) -o $(@)
endif
	$(strip) $(strip-all) $(@)

$(generator): $(generator-objects)
	@echo "linking $(@)"
	$(build-ld) $(^) $(build-lflags) -o $(@)
