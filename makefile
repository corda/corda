#MAKEFLAGS = -s

input = $(cls)/Memory.class

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

build-dir = build/$(build-platform)/$(build-arch)
bld = build/$(platform)/$(arch)/$(mode)
cls = build/classes
src = src
classpath = classpath
test = test

build-cxx = g++
build-cc = gcc

cxx = $(build-cxx)
cc = $(build-cc)
vg = nice valgrind --suppressions=valgrind.supp --undef-value-errors=no \
	--num-callers=32 --db-attach=yes --freelist-vol=100000000
db = gdb --args
javac = javac
strip = :
show-size = :

rdynamic = -rdynamic
thread-cflags = -pthread
shared = -shared
so-prefix = lib
so-extension = so
ld-library-path = LD_LIBRARY_PATH

warnings = -Wall -Wextra -Werror -Wunused-parameter \
	-Winit-self -Wconversion

cflags = $(warnings) -fPIC -fno-rtti -fno-exceptions -fvisibility=hidden \
	-I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux -I$(src) -I$(bld) \
	$(thread-cflags) -D__STDC_LIMIT_MACROS

lflags = -lpthread -ldl -lm -lz

system = posix
asm = x86

ifeq ($(platform),darwin)
	rdynamic =
	thread-cflags =
	shared = -dynamiclib
	so-extension = jnilib
	ld-library-path = DYLD_LIBRARY_PATH
endif
ifeq ($(platform),windows)
	inc = ../6.0/shared/include/msw
	lib = ../6.0/shared/lib/native/msw

	system = windows

  cxx = i586-mingw32msvc-g++
  cc = i586-mingw32msvc-gcc
  dlltool = i586-mingw32msvc-dlltool

	rdynamic = -Wl,--export-dynamic
  so-prefix =
  so-extension = dll
	thread-cflags =
  lflags = -L$(lib) -lm -lz -lws2_32 -Wl,--kill-at
  cflags = $(warnings) -fno-rtti -fno-exceptions $(thread-cflags) \
		-D__STDC_LIMIT_MACROS -DBUILTIN_LIBRARIES=\"natives\" \
		-I$(bld) -I$(JAVA_HOME)/include -I$(inc) -idirafter $(src)
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
jni-library = $(bld)/$(so-prefix)natives.$(so-extension)

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
generator-objects = $(call \
	cpp-objects,$(generator-sources),$(src),$(build-dir))
generator-executable = $(build-dir)/generator

executable = $(bld)/vm

classpath-sources = $(shell find $(classpath) -name '*.java')
classpath-classes = $(call java-classes,$(classpath-sources),$(classpath))

classpath-objects = $(classpath-classes) $(jni-library)

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
	$(ld-library-path)=$(bld) $(executable) $(args)

.PHONY: debug
debug: build
	$(ld-library-path)=$(bld) gdb --args $(executable) $(args)

.PHONY: vg
vg: build
	$(ld-library-path)=$(bld) $(vg) $(executable) $(args)

.PHONY: test
test:
	$(ld-library-path)=$(bld) /bin/bash $(test)/test.sh 2>/dev/null \
		$(executable) $(mode) "$(flags)" $(call class-names,$(test-classes))

.PHONY: clean
clean:
	@echo "removing build"
	rm -rf build

.PHONY: clean-native
clean-native:
	@echo "removing $(bld) and $(build-dir)"
	rm -rf $(bld) $(build-dir)

gen-arg = $(shell echo $(1) | sed -e 's:$(bld)/type-\(.*\)\.cpp:\1:')
$(generated-code): %.cpp: $(src)/types.def $(generator-executable)
	@echo "generating $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(generator-executable) $(call gen-arg,$(@)) < $(<) > $(@)

$(build-dir)/type-generator.o: \
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

$(generator-objects): $(build-dir)/%.o: $(src)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(build-cxx) -DPOINTER_SIZE=$(pointer-size) $(cflags) -c $(<) -o $(@)

$(jni-objects): $(bld)/%.o: $(classpath)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p -m 1777 $(dir $(@))
	$(cxx) $(jni-cflags) -c $(<) -o $(@)	

$(jni-library): $(jni-objects)
	@echo "linking $(@)"
	$(cc) $(^) $(lflags) $(shared) -o $(@)

ifeq ($(platform),windows)
$(executable): $(interpreter-objects) $(jni-objects)
	@echo "linking $(@)"
	$(dlltool) --export-all-symbols -z $(@).def $(^)
	$(dlltool) -k -d $(@).def -e $(@).exp
	$(cc) $(^) $(lflags) $(@).exp -o $(@)
	@$(strip) --strip-all $(@)
	@$(show-size) $(@)
else
$(executable): $(interpreter-objects)
	@echo "linking $(@)"
	$(cc) $(^) $(lflags) $(rdynamic) -o $(@)
	@$(strip) --strip-all $(@)
	@$(show-size) $(@)
endif

$(generator-executable): $(generator-objects)
	@echo "linking $(@)"
	$(build-cc) $(^) -o $(@)
