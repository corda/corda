MAKEFLAGS = -s

name = avian
version = 0.6

build-arch := $(shell uname -m \
	| sed 's/^i.86$$/i386/' \
	| sed 's/^x86pc$$/i386/' \
	| sed 's/amd64/x86_64/' \
	| sed 's/^arm.*$$/arm/' \
	| sed 's/ppc/powerpc/')

ifeq (Power,$(filter Power,$(build-arch)))
	build-arch = powerpc
endif

build-platform := \
	$(shell uname -s | tr [:upper:] [:lower:] \
		| sed 's/^mingw32.*$$/mingw32/' \
		| sed 's/^cygwin.*$$/cygwin/')

arch = $(build-arch)
target-arch = $(arch)
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
ifneq ($(lzma),)
	options := $(options)-lzma
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

root := $(shell (cd .. && pwd))
build = build/$(platform)-$(arch)$(options)
classpath-build = $(build)/classpath
test-build = $(build)/test
src = src
classpath-src = classpath
test = test
win32 ?= $(root)/win32
win64 ?= $(root)/win64

classpath = avian

test-executable = $(shell pwd)/$(executable)
boot-classpath = $(classpath-build)
embed-prefix = /avian-embedded

native-path = echo

ifeq ($(build-platform),cygwin)
	native-path = cygpath -m
endif

windows-path = echo

path-separator = :

ifneq (,$(filter mingw32 cygwin,$(build-platform)))
	path-separator = ;
endif

library-path-variable = LD_LIBRARY_PATH

ifeq ($(build-platform),darwin)
	library-path-variable = DYLD_LIBRARY_PATH
endif

library-path = $(library-path-variable)=$(build)

ifneq ($(openjdk),)
	openjdk-arch = $(arch)
	ifeq ($(arch),x86_64)
		openjdk-arch = amd64
	endif

	ifneq ($(openjdk-src),)
		include openjdk-src.mk
	  options := $(options)-openjdk-src
		classpath-objects = $(openjdk-objects) $(openjdk-local-objects)
		classpath-cflags = -DAVIAN_OPENJDK_SRC -DBOOT_JAVAHOME
		openjdk-jar-dep = $(build)/openjdk-jar.dep
		classpath-jar-dep = $(openjdk-jar-dep)
		javahome = $(embed-prefix)/javahomeJar
		javahome-files = lib/zi lib/currency.data lib/security/java.security \
			lib/security/java.policy lib/security/cacerts

		local-policy = lib/security/local_policy.jar
		ifeq ($(shell test -e "$(openjdk)/$(local-policy)" && echo found),found)
			javahome-files += $(local-policy)
		endif

		export-policy = lib/security/US_export_policy.jar
		ifeq ($(shell test -e "$(openjdk)/$(export-policy)" && echo found),found)
			javahome-files += $(export-policy)
		endif

		ifeq ($(platform),windows)
			javahome-files += lib/tzmappings
		endif
		javahome-object = $(build)/javahome-jar.o
		boot-javahome-object = $(build)/boot-javahome.o
	else
	  options := $(options)-openjdk
		test-executable = $(shell pwd)/$(executable-dynamic)
		ifeq ($(build-platform),darwin)
			library-path = \
				$(library-path-variable)=$(build):$(openjdk)/jre/lib
		else
			library-path = \
				$(library-path-variable)=$(build):$(openjdk)/jre/lib/$(openjdk-arch)
		endif
		javahome = "$$($(native-path) "$(openjdk)/jre")"
	endif

  classpath = openjdk
	boot-classpath := "$(boot-classpath)$(path-separator)$$($(native-path) "$(openjdk)/jre/lib/rt.jar")"
	build-javahome = $(openjdk)/jre
endif

ifeq ($(classpath),avian)
	jni-sources := $(shell find $(classpath-src) -name '*.cpp')
	jni-objects = $(call cpp-objects,$(jni-sources),$(classpath-src),$(build))
	classpath-objects = $(jni-objects)
endif

input = List

ifeq ($(use-clang),true)
	build-cxx = clang -std=c++11
	build-cc = clang
else
	build-cxx = g++
	build-cc = gcc
endif

mflag =
ifneq ($(platform),darwin)
	ifeq ($(arch),i386)
		mflag = -m32
	endif
	ifeq ($(arch),x86_64)
		mflag = -m64
	endif
endif

target-format = elf

cxx = $(build-cxx) $(mflag)
cc = $(build-cc) $(mflag)

ar = ar
ranlib = ranlib
dlltool = dlltool
vg = nice valgrind --num-callers=32 --db-attach=yes --freelist-vol=100000000
vg += --leak-check=full --suppressions=valgrind.supp
db = gdb --args
javac = "$(JAVA_HOME)/bin/javac"
javah = "$(JAVA_HOME)/bin/javah"
jar = "$(JAVA_HOME)/bin/jar"
strip = strip
strip-all = --strip-all

rdynamic = -rdynamic

# note that we suppress the non-virtual-dtor warning because we never
# use the delete operator, which means we don't need virtual
# destructors:
warnings = -Wall -Wextra -Werror -Wunused-parameter -Winit-self \
	-Wno-non-virtual-dtor

target-cflags = -DTARGET_BYTES_PER_WORD=$(pointer-size)

common-cflags = $(warnings) -fno-rtti -fno-exceptions -I$(classpath-src) \
	"-I$(JAVA_HOME)/include" -idirafter $(src) -I$(build) $(classpath-cflags) \
	-D__STDC_LIMIT_MACROS -D_JNI_IMPLEMENTATION_ -DAVIAN_VERSION=\"$(version)\" \
	-DUSE_ATOMIC_OPERATIONS -DAVIAN_JAVA_HOME=\"$(javahome)\" \
	-DAVIAN_EMBED_PREFIX=\"$(embed-prefix)\" $(target-cflags)

asmflags = $(target-cflags)

ifneq (,$(filter i386 x86_64,$(arch)))
	ifeq ($(use-frame-pointer),true)
		common-cflags += -fno-omit-frame-pointer -DAVIAN_USE_FRAME_POINTER
		asmflags += -DAVIAN_USE_FRAME_POINTER
	endif
endif

build-cflags = $(common-cflags) -fPIC -fvisibility=hidden \
	"-I$(JAVA_HOME)/include/linux" -I$(src) -pthread

converter-cflags = -D__STDC_CONSTANT_MACROS -Isrc/binaryToObject -Isrc/ \
	-fno-rtti -fno-exceptions \
	-DAVIAN_TARGET_ARCH=AVIAN_ARCH_UNKNOWN \
	-DAVIAN_TARGET_FORMAT=AVIAN_FORMAT_UNKNOWN \
	-Wall -Wextra -Werror -Wunused-parameter -Winit-self -Wno-non-virtual-dtor

cflags = $(build-cflags)

common-lflags = -lm -lz $(classpath-lflags)

build-lflags = -lz -lpthread -ldl

lflags = $(common-lflags) -lpthread -ldl

version-script-flag = -Wl,--version-script=openjdk.ld

build-system = posix

system = posix
asm = x86

pointer-size = 8

so-prefix = lib
so-suffix = .so

shared = -shared

no-error = -Wno-error

openjdk-extra-cflags = -fvisibility=hidden

bootimage-cflags = -DTARGET_BYTES_PER_WORD=$(pointer-size)

developer-dir := $(shell if test -d /Developer; then echo /Developer; \
	else echo /Applications/Xcode.app/Contents/Developer; fi)

ifeq ($(build-arch),powerpc)
	ifneq ($(arch),$(build-arch))
		bootimage-cflags += -DTARGET_OPPOSITE_ENDIAN
	endif
endif

ifeq ($(arch),i386)
	pointer-size = 4
endif

ifeq ($(arch),powerpc)
	asm = powerpc
	pointer-size = 4

	ifneq ($(arch),$(build-arch))
		bootimage-cflags += -DTARGET_OPPOSITE_ENDIAN
	endif

	ifneq ($(platform),darwin)
		ifneq ($(arch),$(build-arch))
			cxx = powerpc-linux-gnu-g++
			cc = powerpc-linux-gnu-gcc
			ar = powerpc-linux-gnu-ar
			ranlib = powerpc-linux-gnu-ranlib
			strip = powerpc-linux-gnu-strip
		endif
	endif
endif

ifeq ($(arch),arm)
	asm = arm
	pointer-size = 4

	ifeq ($(build-platform),darwin)
		ios = true
	else
		no-psabi = -Wno-psabi
		cflags += -marm $(no-psabi)
	endif

	ifneq ($(arch),$(build-arch))
		ifeq ($(platform),darwin)
			ios-bin = $(developer-dir)/Platforms/iPhoneOS.platform/Developer/usr/bin
			cxx = $(ios-bin)/g++
			cc = $(ios-bin)/gcc
			ar = $(ios-bin)/ar
			ranlib = $(ios-bin)/ranlib
			strip = $(ios-bin)/strip
		else
			cxx = arm-linux-gnueabi-g++
			cc = arm-linux-gnueabi-gcc
			ar = arm-linux-gnueabi-ar
			ranlib = arm-linux-gnueabi-ranlib
			strip = arm-linux-gnueabi-strip
		endif
	endif
endif

ifeq ($(ios),true)
	cflags += -DAVIAN_IOS
endif

ifeq ($(build-platform),darwin)
	build-cflags = $(common-cflags) -fPIC -fvisibility=hidden -I$(src)
	cflags += -I/System/Library/Frameworks/JavaVM.framework/Headers/
	build-lflags += -framework CoreFoundation
endif

ifeq ($(platform),qnx)
	cflags = $(common-cflags) -fPIC -fvisibility=hidden -I$(src)
	lflags = $(common-lflags) -lsocket
	ifeq ($(build-platform),qnx)
		build-cflags = $(common-cflags) -fPIC -fvisibility=hidden -I$(src)
		build-lflags = $(common-lflags)
	else
		ifeq ($(arch),i386)
			prefix = i486-pc-nto-qnx6.5.0-
		else
			prefix = arm-unknown-nto-qnx6.5.0-
		endif
	endif
	cxx = $(prefix)g++
	cc = $(prefix)gcc
	ar = $(prefix)ar
	ranlib = $(prefix)ranlib
	strip = $(prefix)strip
	rdynamic = -Wl,--export-dynamic
endif

ifeq ($(platform),freebsd)
# There is no -ldl on FreeBSD
	build-lflags = $(common-lflags) -lz -lpthread
	lflags = $(common-lflags) -lpthread
# include/freebsd instead of include/linux
	build-cflags = $(common-cflags) -fPIC -fvisibility=hidden \
		"-I$(JAVA_HOME)/include/freebsd" -I$(src) -pthread
	cflags = $(build-cflags)
endif

ifeq ($(platform),darwin)
	target-format = macho
	ifeq (${OSX_SDK_SYSROOT},)
		OSX_SDK_SYSROOT = 10.4u
	endif
	ifeq (${OSX_SDK_VERSION},)
		OSX_SDK_VERSION = 10.4
	endif
	ifneq ($(build-platform),darwin)
		cxx = i686-apple-darwin8-g++ $(mflag)
		cc = i686-apple-darwin8-gcc $(mflag)
		ar = i686-apple-darwin8-ar
		ranlib = i686-apple-darwin8-ranlib
		strip = i686-apple-darwin8-strip
		sysroot = /opt/mac/SDKs/MacOSX${OSX_SDK_SYSROOT}.sdk
		cflags = -I$(sysroot)/System/Library/Frameworks/JavaVM.framework/Versions/1.5.0/Headers/ \
			$(common-cflags) -fPIC -fvisibility=hidden -I$(src)
	endif

	version-script-flag =
	lflags = $(common-lflags) -ldl -framework CoreFoundation

	ifeq (,$(shell ld -v 2>&1 | grep cctools))
		lflags += -Wl,-compatibility_version,1.0.0
	endif

	ifneq ($(arch),arm)
		lflags +=	-framework CoreServices -framework SystemConfiguration \
			-framework Security
	endif
	ifeq ($(bootimage),true)
		bootimage-lflags = -Wl,-segprot,__RWX,rwx,rwx
	endif
	rdynamic =
	strip-all = -S -x
	so-suffix = .dylib
	shared = -dynamiclib

	sdk-dir = $(developer-dir)/Platforms/iPhoneOS.platform/Developer/SDKs

	ifeq ($(arch),arm)
		ios-version := \
			$(shell if test -d $(sdk-dir)/iPhoneOS5.1.sdk; then echo 5.1; \
				elif test -d $(sdk-dir)/iPhoneOS5.0.sdk; then echo 5.0; \
				elif test -d $(sdk-dir)/iPhoneOS4.3.sdk; then echo 4.3; \
				elif test -d $(sdk-dir)/iPhoneOS4.2.sdk; then echo 4.2; \
				else echo; fi)

		ifeq ($(ios-version),)
			x := $(error "couldn't find SDK for iOS version")
		endif

		flags = -arch armv7 -isysroot \
			$(sdk-dir)/iPhoneOS$(ios-version).sdk/
		openjdk-extra-cflags += $(flags)
		cflags += $(flags)
		asmflags += $(flags)
		lflags += $(flags)
	endif

	ifeq ($(arch),powerpc)
		openjdk-extra-cflags += -arch ppc -mmacosx-version-min=${OSX_SDK_VERSION}
		cflags += -arch ppc -mmacosx-version-min=${OSX_SDK_VERSION}
		asmflags += -arch ppc -mmacosx-version-min=${OSX_SDK_VERSION}
		lflags += -arch ppc -mmacosx-version-min=${OSX_SDK_VERSION}
	endif

	ifeq ($(arch),i386)
		openjdk-extra-cflags += -arch i386 -mmacosx-version-min=${OSX_SDK_VERSION}
		cflags += -arch i386 -mmacosx-version-min=${OSX_SDK_VERSION}
		asmflags += -arch i386 -mmacosx-version-min=${OSX_SDK_VERSION}
		lflags += -arch i386 -mmacosx-version-min=${OSX_SDK_VERSION}
	endif

	ifeq ($(arch),x86_64)
		openjdk-extra-cflags += -arch x86_64
		cflags += -arch x86_64
		asmflags += -arch x86_64
		lflags += -arch x86_64
	endif
endif

ifeq ($(platform),windows)
	target-format = pe

	inc = "$(win32)/include"
	lib = "$(win32)/lib"

	embed-prefix = c:/avian-embedded

	system = windows

	so-prefix =
	so-suffix = .dll
	exe-suffix = .exe

	lflags = -L$(lib) $(common-lflags) -lws2_32 -liphlpapi -mwindows -mconsole
	cflags = -I$(inc) $(common-cflags) -DWINVER=0x0500

	ifeq (,$(filter mingw32 cygwin,$(build-platform)))
		openjdk-extra-cflags += -I$(src)/openjdk/caseSensitive
		prefix := $(shell i686-w64-mingw32-gcc --version >/dev/null 2>&1 \
			&& echo i686-w64-mingw32- || echo x86_64-w64-mingw32-)
		cxx = $(prefix)g++ -m32
		cc = $(prefix)gcc -m32
		dlltool = $(prefix)dlltool -mi386 --as-flags=--32 
		ar = $(prefix)ar
		ranlib = $(prefix)ranlib
		strip = $(prefix)strip --strip-all
	else
		build-system = windows
		common-cflags += "-I$(JAVA_HOME)/include/win32"
		build-cflags = $(common-cflags) -I$(src) -I$(inc) -mthreads
		openjdk-extra-cflags =
		build-lflags = -L$(lib) $(common-lflags)
		ifeq ($(build-platform),cygwin)
			build-cxx = i686-w64-mingw32-g++
			build-cc = i686-w64-mingw32-gcc
			dlltool = i686-w64-mingw32-dlltool
			ar = i686-w64-mingw32-ar
			ranlib = i686-w64-mingw32-ranlib
			strip = i686-w64-mingw32-strip
		endif
	endif

	ifeq ($(arch),x86_64)
		ifeq ($(build-platform),cygwin)
			build-cxx = x86_64-w64-mingw32-g++
			build-cc = x86_64-w64-mingw32-gcc
		endif
		cxx = x86_64-w64-mingw32-g++ $(mflag)
		cc = x86_64-w64-mingw32-gcc $(mflag)
		dlltool = x86_64-w64-mingw32-dlltool
		ar = x86_64-w64-mingw32-ar
		ranlib = x86_64-w64-mingw32-ranlib
		strip = x86_64-w64-mingw32-strip
		inc = "$(win64)/include"
		lib = "$(win64)/lib"
	else
		shared += -Wl,--add-stdcall-alias
	endif
endif

ifeq ($(mode),debug)
	optimization-cflags = -O0 -g3
	converter-cflags += -O0 -g3
	strip = :
endif
ifeq ($(mode),debug-fast)
	optimization-cflags = -O0 -g3 -DNDEBUG
	strip = :
endif
ifeq ($(mode),stress)
	optimization-cflags = -O0 -g3 -DVM_STRESS
	strip = :
endif
ifeq ($(mode),stress-major)
	optimization-cflags = -O0 -g3 -DVM_STRESS -DVM_STRESS_MAJOR
	strip = :
endif
ifeq ($(mode),fast)
	ifeq ($(use-clang),true)
		optimization-cflags = -O4 -g3 -DNDEBUG
	else
		optimization-cflags = -O3 -g3 -DNDEBUG
	endif
	use-lto = true
endif
ifeq ($(mode),small)
	ifeq ($(use-clang),true)
		optimization-cflags = -Oz -g3 -DNDEBUG
	else
		optimization-cflags = -Os -g3 -DNDEBUG
	endif
	use-lto = true
endif

ifeq ($(use-lto),true)
	ifeq ($(use-clang),true)
		optimization-cflags += -flto
		lflags += $(optimization-cflags)
	else
# only try to use LTO when GCC 4.6.0 or greater is available
		gcc-major := $(shell $(cc) -dumpversion | cut -f1 -d.)
		gcc-minor := $(shell $(cc) -dumpversion | cut -f2 -d.)
		ifeq ($(shell expr 4 \< $(gcc-major) \
				\| \( 4 \<= $(gcc-major) \& 6 \<= $(gcc-minor) \)),1)
			optimization-cflags += -flto
			no-lto = -fno-lto
			lflags += $(optimization-cflags)
		endif
	endif
endif

cflags += $(optimization-cflags)

ifneq ($(platform),darwin)
ifeq ($(arch),i386)
# this is necessary to support __sync_bool_compare_and_swap:
	cflags += -march=i586
	lflags += -march=i586
endif
endif

output = -o $(1)
as := $(cc)
ld := $(cc)
build-ld := $(build-cc)

ifdef msvc
	no-error =
	windows-path = $(native-path)
	windows-java-home := $(shell $(windows-path) "$(JAVA_HOME)")
	zlib := $(shell $(windows-path) "$(win32)/msvc")
	cxx = "$(msvc)/BIN/cl.exe"
	cc = $(cxx)
	ld = "$(msvc)/BIN/link.exe"
	mt = "mt.exe"
	cflags = -nologo -DAVIAN_VERSION=\"$(version)\" -D_JNI_IMPLEMENTATION_ \
		-DUSE_ATOMIC_OPERATIONS -DAVIAN_JAVA_HOME=\"$(javahome)\" \
		-DAVIAN_EMBED_PREFIX=\"$(embed-prefix)\" \
		-Fd$(build)/$(name).pdb -I"$(zlib)/include" -I$(src) -I$(classpath-src) \
		-I"$(build)" \
		-I"$(windows-java-home)/include" -I"$(windows-java-home)/include/win32" \
		-DTARGET_BYTES_PER_WORD=$(pointer-size)

  ifneq ($(lzma),)
		cflags += -I$(shell $(windows-path) "$(lzma)")
	endif

	shared = -dll
	lflags = -nologo -LIBPATH:"$(zlib)/lib" -DEFAULTLIB:ws2_32 \
		-DEFAULTLIB:zlib -MANIFEST -debug
	output = -Fo$(1)

	ifeq ($(mode),debug)
		cflags += -Od -Zi -MDd
	endif
	ifeq ($(mode),debug-fast)
		cflags += -Od -Zi -DNDEBUG
	endif
	ifeq ($(mode),fast)
		cflags += -O2 -GL -Zi -DNDEBUG
		lflags += -LTCG
	endif
	ifeq ($(mode),small)
		cflags += -O1s -Zi -GL -DNDEBUG
		lflags += -LTCG
	endif

	strip = :
endif

c-objects = $(foreach x,$(1),$(patsubst $(2)/%.c,$(3)/%.o,$(x)))
cpp-objects = $(foreach x,$(1),$(patsubst $(2)/%.cpp,$(3)/%.o,$(x)))
asm-objects = $(foreach x,$(1),$(patsubst $(2)/%.S,$(3)/%-asm.o,$(x)))
java-classes = $(foreach x,$(1),$(patsubst $(2)/%.java,$(3)/%.class,$(x)))

generated-code = \
	$(build)/type-enums.cpp \
	$(build)/type-declarations.cpp \
	$(build)/type-constructors.cpp \
	$(build)/type-initializations.cpp \
	$(build)/type-java-initializations.cpp \
	$(build)/type-name-initializations.cpp \
	$(build)/type-maps.cpp

vm-depends := $(generated-code) $(wildcard $(src)/*.h)

vm-sources = \
	$(src)/$(system).cpp \
	$(src)/finder.cpp \
	$(src)/machine.cpp \
	$(src)/util.cpp \
	$(src)/heap.cpp \
	$(src)/$(process).cpp \
	$(src)/classpath-$(classpath).cpp \
	$(src)/builtin.cpp \
	$(src)/jnienv.cpp \
	$(src)/process.cpp

vm-asm-sources = $(src)/$(asm).S

target-asm = $(asm)

ifeq ($(process),compile)
	vm-sources += \
		$(src)/compiler.cpp \
		$(src)/$(target-asm).cpp

	vm-asm-sources += $(src)/compile-$(asm).S
endif

vm-cpp-objects = $(call cpp-objects,$(vm-sources),$(src),$(build))
vm-asm-objects = $(call asm-objects,$(vm-asm-sources),$(src),$(build))
vm-objects = $(vm-cpp-objects) $(vm-asm-objects)

heapwalk-sources = $(src)/heapwalk.cpp 
heapwalk-objects = \
	$(call cpp-objects,$(heapwalk-sources),$(src),$(build))

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
ifneq ($(lzma),)
	bootimage-generator-sources += $(src)/lzma-encode.cpp
endif
bootimage-generator-objects = \
	$(call cpp-objects,$(bootimage-generator-sources),$(src),$(build))
bootimage-generator = $(build)/bootimage-generator

bootimage-object = $(build)/bootimage-bin.o
codeimage-object = $(build)/codeimage-bin.o

ifeq ($(bootimage),true)
	vm-classpath-objects = $(bootimage-object) $(codeimage-object)
	cflags += -DBOOT_IMAGE -DAVIAN_CLASSPATH=\"\"
else
	vm-classpath-objects = $(classpath-object)
	cflags += -DBOOT_CLASSPATH=\"[classpathJar]\" \
		-DAVIAN_CLASSPATH=\"[classpathJar]\"
endif

cflags += $(extra-cflags)
lflags += $(extra-lflags)

openjdk-cflags += $(extra-cflags)

driver-source = $(src)/main.cpp
driver-object = $(build)/main.o
driver-dynamic-objects = \
	$(build)/main-dynamic.o

boot-source = $(src)/boot.cpp
boot-object = $(build)/boot.o

generator-depends := $(wildcard $(src)/*.h)
generator-sources = \
	$(src)/type-generator.cpp \
	$(src)/$(build-system).cpp \
	$(src)/finder.cpp

ifneq ($(lzma),)
	common-cflags += -I$(lzma) -DAVIAN_USE_LZMA -D_7ZIP_ST

	vm-sources += \
		$(src)/lzma-decode.cpp

	generator-sources += \
		$(src)/lzma-decode.cpp

	lzma-decode-sources = \
		$(lzma)/C/LzmaDec.c

	lzma-decode-objects = \
		$(call c-objects,$(lzma-decode-sources),$(lzma)/C,$(build))

	lzma-encode-sources = \
		$(lzma)/C/LzmaEnc.c \
		$(lzma)/C/LzFind.c

	lzma-encode-objects = \
		$(call c-objects,$(lzma-encode-sources),$(lzma)/C,$(build))

	lzma-encoder = $(build)/lzma/lzma

	lzma-encoder-cflags = -D__STDC_CONSTANT_MACROS -fno-rtti -fno-exceptions \
		-I$(lzma)/C

	lzma-encoder-sources = \
		$(src)/lzma/main.cpp

	lzma-encoder-objects = \
		$(call cpp-objects,$(lzma-encoder-sources),$(src),$(build))

	lzma-encoder-lzma-sources = $(lzma-encode-sources) $(lzma-decode-sources)

	lzma-encoder-lzma-objects = \
		$(call generator-c-objects,$(lzma-encoder-lzma-sources),$(lzma)/C,$(build))

	lzma-loader = $(build)/lzma/load.o
endif

generator-cpp-objects = \
	$(foreach x,$(1),$(patsubst $(2)/%.cpp,$(3)/%-build.o,$(x)))
generator-c-objects = \
	$(foreach x,$(1),$(patsubst $(2)/%.c,$(3)/%-build.o,$(x)))
generator-objects = \
	$(call generator-cpp-objects,$(generator-sources),$(src),$(build))
generator-lzma-objects = \
	$(call generator-c-objects,$(lzma-decode-sources),$(lzma)/C,$(build))
generator = $(build)/generator

converter-depends = \
	$(src)/binaryToObject/tools.h \
	$(src)/binaryToObject/endianness.h

converter-sources = \
	$(src)/binaryToObject/tools.cpp \
	$(src)/binaryToObject/elf.cpp \
	$(src)/binaryToObject/mach-o.cpp \
	$(src)/binaryToObject/pe.cpp

converter-tool-sources = \
	$(src)/binaryToObject/main.cpp

converter-objects = $(call cpp-objects,$(converter-sources),$(src),$(build))
converter-tool-objects = $(call cpp-objects,$(converter-tool-sources),$(src),$(build))
converter = $(build)/binaryToObject/binaryToObject

static-library = $(build)/lib$(name).a
executable = $(build)/$(name)${exe-suffix}
dynamic-library = $(build)/$(so-prefix)jvm$(so-suffix)
executable-dynamic = $(build)/$(name)-dynamic${exe-suffix}

ifneq ($(classpath),avian)
# Assembler, ConstantPool, and Stream are not technically needed for a
# working build, but we include them since our Subroutine test uses
# them to synthesize a class:
	classpath-sources := \
		$(classpath-src)/avian/Addendum.java \
		$(classpath-src)/avian/Assembler.java \
		$(classpath-src)/avian/Callback.java \
		$(classpath-src)/avian/CallbackReceiver.java \
		$(classpath-src)/avian/ClassAddendum.java \
		$(classpath-src)/avian/Classes.java \
		$(classpath-src)/avian/ConstantPool.java \
		$(classpath-src)/avian/Continuations.java \
		$(classpath-src)/avian/FieldAddendum.java \
		$(classpath-src)/avian/IncompatibleContinuationException.java \
		$(classpath-src)/avian/Machine.java \
		$(classpath-src)/avian/MethodAddendum.java \
		$(classpath-src)/avian/Singleton.java \
		$(classpath-src)/avian/Stream.java \
		$(classpath-src)/avian/SystemClassLoader.java \
		$(classpath-src)/avian/VMClass.java \
		$(classpath-src)/avian/VMField.java \
		$(classpath-src)/avian/VMMethod.java \
		$(classpath-src)/avian/avian_vm_resource/Handler.java

	ifneq ($(openjdk),)
		classpath-sources := $(classpath-sources) \
			$(classpath-src)/avian/OpenJDK.java
	endif
else
	classpath-sources := $(shell find $(classpath-src) -name '*.java')
endif

classpath-classes = \
	$(call java-classes,$(classpath-sources),$(classpath-src),$(classpath-build))
classpath-object = $(build)/classpath-jar.o
classpath-dep = $(classpath-build).dep

vm-classes = \
	avian/*.class \
	avian/resource/*.class

test-support-sources = $(shell find $(test)/avian/ -name '*.java')
test-sources = $(wildcard $(test)/*.java)
test-cpp-sources = $(wildcard $(test)/*.cpp)
test-sources += $(test-support-sources)
test-support-classes = $(call java-classes, $(test-support-sources),$(test),$(test-build))
test-classes = $(call java-classes,$(test-sources),$(test),$(test-build))
test-cpp-objects = $(call cpp-objects,$(test-cpp-sources),$(test),$(test-build))
test-library = $(build)/$(so-prefix)test$(so-suffix)
test-dep = $(test-build).dep

test-extra-sources = $(wildcard $(test)/extra/*.java)
test-extra-classes = \
	$(call java-classes,$(test-extra-sources),$(test),$(test-build))
test-extra-dep = $(test-build)-extra.dep

ifeq ($(continuations),true)
	continuation-tests = \
		extra.Continuations \
		extra.Coroutines \
		extra.DynamicWind
endif

ifeq ($(tails),true)
	tail-tests = \
		extra.Tails
endif

ifeq ($(target-arch),i386)
	cflags += -DAVIAN_TARGET_ARCH=AVIAN_ARCH_X86
endif

ifeq ($(target-arch),x86_64)
	cflags += -DAVIAN_TARGET_ARCH=AVIAN_ARCH_X86_64
endif

ifeq ($(target-arch),powerpc)
	cflags += -DAVIAN_TARGET_ARCH=AVIAN_ARCH_POWERPC
endif

ifeq ($(target-arch),arm)
	cflags += -DAVIAN_TARGET_ARCH=AVIAN_ARCH_ARM
endif

ifeq ($(target-format),elf)
	cflags += -DAVIAN_TARGET_FORMAT=AVIAN_FORMAT_ELF
endif

ifeq ($(target-format),pe)
	cflags += -DAVIAN_TARGET_FORMAT=AVIAN_FORMAT_PE
endif

ifeq ($(target-format),macho)
	cflags += -DAVIAN_TARGET_FORMAT=AVIAN_FORMAT_MACHO
endif

class-name = $(patsubst $(1)/%.class,%,$(2))
class-names = $(foreach x,$(2),$(call class-name,$(1),$(x)))

test-flags = -Djava.library.path=$(build) -cp $(build)/test

test-args = $(test-flags) $(input)

.PHONY: build
build: $(static-library) $(executable) $(dynamic-library) $(lzma-loader) \
	$(lzma-encoder) $(executable-dynamic) $(classpath-dep) $(test-dep) \
	$(test-extra-dep)

$(test-dep): $(classpath-dep)

$(test-extra-dep): $(classpath-dep)

.PHONY: run
run: build
	$(library-path) $(test-executable) $(test-args)

.PHONY: debug
debug: build
	$(library-path) gdb --args $(test-executable) $(test-args)

.PHONY: vg
vg: build
	$(library-path) $(vg) $(test-executable) $(test-args)

.PHONY: test
test: build
	$(library-path) /bin/sh $(test)/test.sh 2>/dev/null \
		$(test-executable) $(mode) "$(test-flags)" \
		$(call class-names,$(test-build),$(filter-out $(test-support-classes), $(test-classes))) \
		$(continuation-tests) $(tail-tests)

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

$(build)/compile-x86-asm.o: $(src)/continuations-x86.S

gen-arg = $(shell echo $(1) | sed -e 's:$(build)/type-\(.*\)\.cpp:\1:')
$(generated-code): %.cpp: $(src)/types.def $(generator) $(classpath-dep)
	@echo "generating $(@)"
	@mkdir -p $(dir $(@))
	$(generator) $(boot-classpath) $(<) $(@) $(call gen-arg,$(@))

$(classpath-build)/%.class: $(classpath-src)/%.java
	@echo $(<)

$(classpath-dep): $(classpath-sources)
	@echo "compiling classpath classes"
	@mkdir -p $(classpath-build)
	$(javac) -d $(classpath-build) -bootclasspath $(boot-classpath) \
		$(shell $(MAKE) -s --no-print-directory build=$(build) \
			$(classpath-classes))
	@touch $(@)

$(test-build)/%.class: $(test)/%.java
	@echo $(<)

$(test-dep): $(test-sources) $(test-library)
	@echo "compiling test classes"
	@mkdir -p $(test-build)
	files="$(shell $(MAKE) -s --no-print-directory build=$(build) $(test-classes))"; \
	if test -n "$${files}"; then \
		$(javac) -d $(test-build) -bootclasspath $(boot-classpath) $${files}; \
	fi
	$(javac) -source 1.2 -target 1.1 -XDjsrlimit=0 -d $(test-build) \
		-bootclasspath $(boot-classpath) test/Subroutine.java
	@touch $(@)

$(test-extra-dep): $(test-extra-sources)
	@echo "compiling extra test classes"
	@mkdir -p $(test-build)
	files="$(shell $(MAKE) -s --no-print-directory build=$(build) $(test-extra-classes))"; \
	if test -n "$${files}"; then \
		$(javac) -d $(test-build) -bootclasspath $(boot-classpath) $${files}; \
	fi
	@touch $(@)

define compile-object
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(cflags) -c $$($(windows-path) $(<)) $(call output,$(@))
endef

define compile-asm-object
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(as) -I$(src) $(asmflags) -c $(<) -o $(@)
endef

$(vm-cpp-objects): $(build)/%.o: $(src)/%.cpp $(vm-depends)
	$(compile-object)

$(test-cpp-objects): $(test-build)/%.o: $(test)/%.cpp $(vm-depends)
	$(compile-object)

$(test-library): $(test-cpp-objects)
	@echo "linking $(@)"
ifdef msvc
	$(ld) $(shared) $(lflags) $(^) -out:$(@) -PDB:$(@).pdb \
		-IMPLIB:$(test-build)/$(name).lib -MANIFESTFILE:$(@).manifest
	$(mt) -manifest $(@).manifest -outputresource:"$(@);2"
else
	$(ld) $(^) $(shared) $(lflags) -o $(@)
endif

$(build)/%.o: $(lzma)/C/%.c
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(cflags) $(no-error) -c $$($(windows-path) $(<)) $(call output,$(@))

$(vm-asm-objects): $(build)/%-asm.o: $(src)/%.S
	$(compile-asm-object)

$(bootimage-generator-objects): $(build)/%.o: $(src)/%.cpp $(vm-depends)
	$(compile-object)

$(heapwalk-objects): $(build)/%.o: $(src)/%.cpp $(vm-depends)
	$(compile-object)

$(driver-object): $(driver-source)
	$(compile-object)

$(build)/main-dynamic.o: $(driver-source)
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(cflags) -DBOOT_LIBRARY=\"$(so-prefix)jvm$(so-suffix)\" \
		-c $(<) $(call output,$(@))

$(boot-object): $(boot-source)
	$(compile-object)

$(boot-javahome-object): $(src)/boot-javahome.cpp
	$(compile-object)

$(converter-objects) $(converter-tool-objects): $(build)/binaryToObject/%.o: $(src)/binaryToObject/%.cpp $(converter-depends)
	@mkdir -p $(dir $(@))
	$(build-cxx) $(converter-cflags) -c $(<) -o $(@)

$(converter): $(converter-objects) $(converter-tool-objects)
	$(build-cc) $(^) -g -o $(@)

$(lzma-encoder-objects): $(build)/lzma/%.o: $(src)/lzma/%.cpp
	@mkdir -p $(dir $(@))
	$(build-cxx) $(lzma-encoder-cflags) -c $(<) -o $(@)

$(lzma-encoder): $(lzma-encoder-objects) $(lzma-encoder-lzma-objects)
	$(build-cc) $(^) -g -o $(@)

$(lzma-loader): $(src)/lzma/load.cpp
	$(compile-object)

$(build)/classpath.jar: $(classpath-dep) $(classpath-jar-dep)
	@echo "creating $(@)"
	(wd=$$(pwd) && \
	 cd $(classpath-build) && \
	 $(jar) c0f "$$($(native-path) "$${wd}/$(@)")" .)

$(classpath-object): $(build)/classpath.jar $(converter)
	@echo "creating $(@)"
	$(converter) $(<) $(@) _binary_classpath_jar_start \
		_binary_classpath_jar_end $(target-format) $(arch)

$(build)/javahome.jar:
	@echo "creating $(@)"
	(wd=$$(pwd) && \
	 cd "$(build-javahome)" && \
	 $(jar) c0f "$$($(native-path) "$${wd}/$(@)")" $(javahome-files))

$(javahome-object): $(build)/javahome.jar $(converter)
	@echo "creating $(@)"
	$(converter) $(<) $(@) _binary_javahome_jar_start \
		_binary_javahome_jar_end $(target-format) $(arch)

define compile-generator-object
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(build-cxx) -DPOINTER_SIZE=$(pointer-size) -O0 -g3 $(build-cflags) \
		-c $(<) -o $(@)
endef

$(generator-objects): $(generator-depends)
$(generator-objects): $(build)/%-build.o: $(src)/%.cpp
	$(compile-generator-object)

$(build)/%-build.o: $(lzma)/C/%.c
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(build-cxx) -DPOINTER_SIZE=$(pointer-size) -O0 -g3 $(build-cflags) \
		$(no-error) -c $(<) -o $(@)

$(jni-objects): $(build)/%.o: $(classpath-src)/%.cpp
	$(compile-object)

$(static-library): $(vm-objects) $(classpath-objects) $(vm-heapwalk-objects) \
		$(javahome-object) $(boot-javahome-object) $(lzma-decode-objects)
	@echo "creating $(@)"
	rm -rf $(@)
	$(ar) cru $(@) $(^)
	$(ranlib) $(@)

$(bootimage-object) $(codeimage-object): $(bootimage-generator)
	$(<) -cp $(classpath-build) -bootimage $(bootimage-object) -codeimage $(codeimage-object) \
		-bootimage-symbols _binary_bootimage_bin_start:_binary_bootimage_bin_end \
		-codeimage-symbols _binary_codeimage_bin_start:_binary_codeimage_bin_end

executable-objects = $(vm-objects) $(classpath-objects) $(driver-object) \
	$(vm-heapwalk-objects) $(boot-object) $(vm-classpath-objects) \
	$(javahome-object) $(boot-javahome-object) $(lzma-decode-objects)

$(executable): $(executable-objects)
	@echo "linking $(@)"
ifeq ($(platform),windows)
ifdef msvc
	$(ld) $(lflags) $(executable-objects) -out:$(@) -PDB:$(@).pdb \
		-IMPLIB:$(@).lib -MANIFESTFILE:$(@).manifest
	$(mt) -manifest $(@).manifest -outputresource:"$(@);1"
else
	$(dlltool) -z $(@).def $(executable-objects)
	$(dlltool) -d $(@).def -e $(@).exp
	$(ld) $(@).exp $(executable-objects) $(lflags) -o $(@)
endif
else
	$(ld) $(executable-objects) $(rdynamic) $(lflags) $(bootimage-lflags) -o $(@)
endif
	$(strip) $(strip-all) $(@)

$(bootimage-generator): $(bootimage-generator-objects)
	echo arch=$(arch) platform=$(platform)
	$(MAKE) mode=$(mode) \
		arch=$(build-arch) \
		target-arch=$(arch) \
		platform=$(bootimage-platform) \
		target-format=$(target-format) \
		openjdk=$(openjdk) \
		openjdk-src=$(openjdk-src) \
		bootimage-generator= \
		build-bootimage-generator=$(bootimage-generator) \
		target-cflags="$(bootimage-cflags)" \
		target-asm=$(asm) \
		$(bootimage-generator)

$(build-bootimage-generator): \
		$(vm-objects) $(classpath-object) $(classpath-objects) \
		$(heapwalk-objects) $(bootimage-generator-objects) $(converter-objects) \
		$(lzma-decode-objects) $(lzma-encode-objects)
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

$(dynamic-library): $(vm-objects) $(dynamic-object) $(classpath-objects) \
		$(vm-heapwalk-objects) $(boot-object) $(vm-classpath-objects) \
		$(classpath-libraries) $(javahome-object) $(boot-javahome-object) \
		$(lzma-decode-objects)
	@echo "linking $(@)"
ifdef msvc
	$(ld) $(shared) $(lflags) $(^) -out:$(@) -PDB:$(@).pdb \
		-IMPLIB:$(build)/$(name).lib -MANIFESTFILE:$(@).manifest
	$(mt) -manifest $(@).manifest -outputresource:"$(@);2"
else
	$(ld) $(^) $(version-script-flag)	$(shared) $(lflags) $(bootimage-lflags) \
		-o $(@)
endif
	$(strip) $(strip-all) $(@)

# todo: the $(no-lto) flag below is due to odd undefined reference errors on
# Ubuntu 11.10 which may be fixable without disabling LTO.
$(executable-dynamic): $(driver-dynamic-objects) $(dynamic-library)
	@echo "linking $(@)"
ifdef msvc
	$(ld) $(lflags) -LIBPATH:$(build) -DEFAULTLIB:$(name) \
		-PDB:$(@).pdb -IMPLIB:$(@).lib $(driver-dynamic-objects) -out:$(@) \
		-MANIFESTFILE:$(@).manifest
	$(mt) -manifest $(@).manifest -outputresource:"$(@);1"
else
	$(ld) $(driver-dynamic-objects) -L$(build) -ljvm $(lflags) $(no-lto) -o $(@)
endif
	$(strip) $(strip-all) $(@)

$(generator): $(generator-objects) $(generator-lzma-objects)
	@echo "linking $(@)"
	$(build-ld) $(^) $(build-lflags) -o $(@)

$(openjdk-objects): $(build)/openjdk/%-openjdk.o: $(openjdk-src)/%.c \
		$(openjdk-headers-dep)
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	sed 's/^static jclass ia_class;//' < $(<) > $(build)/openjdk/$(notdir $(<))
ifeq ($(ios),true)
	sed \
		-e 's/^#ifndef __APPLE__/#if 1/' \
		-e 's/^#ifdef __APPLE__/#if 0/' \
		< "$(openjdk-src)/solaris/native/java/lang/ProcessEnvironment_md.c" \
		> $(build)/openjdk/ProcessEnvironment_md.c
	sed \
		-e 's/^#ifndef __APPLE__/#if 1/' \
		-e 's/^#ifdef __APPLE__/#if 0/' \
		< "$(openjdk-src)/solaris/native/java/lang/UNIXProcess_md.c" \
		> $(build)/openjdk/UNIXProcess_md.c
endif
	$(cc) -fPIC $(openjdk-extra-cflags) $(openjdk-cflags) \
		$(optimization-cflags) -w -c $(build)/openjdk/$(notdir $(<)) \
		$(call output,$(@))

$(openjdk-local-objects): $(build)/openjdk/%-openjdk.o: $(src)/openjdk/%.c \
		$(openjdk-headers-dep)
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cc) -fPIC $(openjdk-extra-cflags) $(openjdk-cflags) \
		$(optimization-cflags) -w -c $(<) $(call output,$(@))

$(openjdk-headers-dep):
	@echo "generating openjdk headers"
	@mkdir -p $(dir $(@))
	$(javah) -d $(build)/openjdk -bootclasspath $(boot-classpath) \
		$(openjdk-headers-classes)
ifeq ($(platform),windows)
	sed 's/^#ifdef _WIN64/#if 1/' \
		< "$(openjdk-src)/windows/native/java/net/net_util_md.h" \
		> $(build)/openjdk/net_util_md.h
	sed \
		-e 's/\(^#include "net_util.h"\)/\1\n#if (defined _INC_NLDEF) || (defined _WS2DEF_)\n#define HIDE(x) hide_##x\n#else\n#define HIDE(x) x\n#define _WINSOCK2API_\n#endif/' \
		-e 's/\(IpPrefix[a-zA-Z_]*\)/HIDE(\1)/' \
		-e 's/\(IpSuffix[a-zA-Z_]*\)/HIDE(\1)/' \
		-e 's/\(IpDad[a-zA-Z_]*\)/HIDE(\1)/' \
		-e 's/\(ScopeLevel[a-zA-Z_]*\)/HIDE(\1)/' \
		-e 's/\(SCOPE_LEVEL[a-zA-Z_]*\)/HIDE(\1)/' \
		< "$(openjdk-src)/windows/native/java/net/NetworkInterface.h" \
		> $(build)/openjdk/NetworkInterface.h
	echo 'static int getAddrsFromAdapter(IP_ADAPTER_ADDRESSES *ptr, netaddr **netaddrPP);' >> $(build)/openjdk/NetworkInterface.h
endif
ifeq ($(platform),darwin)
	mkdir -p $(build)/openjdk/netinet
	for file in \
		/usr/include/netinet/ip.h \
		/usr/include/netinet/in_systm.h \
		/usr/include/netinet/ip_icmp.h \
		/usr/include/netinet/in_var.h \
		/usr/include/netinet/icmp6.h \
		/usr/include/netinet/ip_var.h; do \
		if [ ! -f "$(build)/openjdk/netinet/$$(basename $${file})" ]; then \
			ln "$${file}" "$(build)/openjdk/netinet/$$(basename $${file})"; \
		fi; \
	done
	mkdir -p $(build)/openjdk/netinet6
	for file in \
		/usr/include/netinet6/in6_var.h; do \
		if [ ! -f "$(build)/openjdk/netinet6/$$(basename $${file})" ]; then \
			ln "$${file}" "$(build)/openjdk/netinet6/$$(basename $${file})"; \
		fi; \
	done
	mkdir -p $(build)/openjdk/net
	for file in \
		/usr/include/net/if_arp.h; do \
		if [ ! -f "$(build)/openjdk/net/$$(basename $${file})" ]; then \
			ln "$${file}" "$(build)/openjdk/net/$$(basename $${file})"; \
		fi; \
	done
	mkdir -p $(build)/openjdk/sys
	for file in \
		/usr/include/sys/kern_event.h \
		/usr/include/sys/sys_domain.h; do \
		if [ ! -f "$(build)/openjdk/sys/$$(basename $${file})" ]; then \
			ln "$${file}" "$(build)/openjdk/sys/$$(basename $${file})"; \
		fi; \
	done
endif
	@touch $(@)

$(openjdk-jar-dep):
	@echo "extracting openjdk classes"
	@mkdir -p $(dir $(@))
	@mkdir -p $(classpath-build)
	(cd $(classpath-build) && \
		$(jar) xf "$$($(native-path) "$(openjdk)/jre/lib/rt.jar")" && \
		$(jar) xf "$$($(native-path) "$(openjdk)/jre/lib/jsse.jar")" && \
		$(jar) xf "$$($(native-path) "$(openjdk)/jre/lib/jce.jar")" && \
		$(jar) xf "$$($(native-path) "$(openjdk)/jre/lib/charsets.jar")" && \
		$(jar) xf "$$($(native-path) "$(openjdk)/jre/lib/ext/sunjce_provider.jar")" && \
		$(jar) xf "$$($(native-path) "$(openjdk)/jre/lib/resources.jar")")
	@touch $(@)
