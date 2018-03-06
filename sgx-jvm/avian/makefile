MAKEFLAGS = -s

name = avian
version := $(shell grep version gradle.properties | cut -d'=' -f2)

java-version := $(shell "$(JAVA_HOME)/bin/java" -version 2>&1 \
	| grep 'version "1' \
	| sed 's/.*version "1.\([^.]*\).*/\1/')

build-arch := $(shell uname -m \
	| sed 's/^i.86$$/i386/' \
	| sed 's/^x86pc$$/i386/' \
	| sed 's/amd64/x86_64/' \
	| sed 's/^arm.*$$/arm/' \
	| sed 's/aarch64/arm64/')

build-platform := \
	$(shell uname -s | tr [:upper:] [:lower:] \
		| sed \
			-e 's/^mingw64.*$$/mingw32/' \
			-e 's/^mingw32.*$$/mingw32/' \
			-e 's/^cygwin.*$$/cygwin/' \
			-e 's/^darwin.*$$/macosx/')

arch = $(build-arch)
target-arch = $(arch)

bootimage-platform = \
	$(subst cygwin,windows,$(subst mingw32,windows,$(build-platform)))

platform = $(bootimage-platform)

codegen-targets = native

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
	ifeq ($(bootimage-test),true)
		# this option indicates that we should AOT-compile the test
		# classes as well as the class library
		options := $(options)-test
	endif
endif
ifeq ($(tails),true)
	options := $(options)-tails
endif
ifeq ($(continuations),true)
	options := $(options)-continuations
endif
ifeq ($(codegen-targets),all)
	options := $(options)-all
endif

ifeq ($(filter debug debug-fast fast stress stress-major small,$(mode)),)
	x := $(error "'$(mode)' is not a valid mode (choose one of: debug debug-fast fast stress stress-major small)")
endif

ifeq ($(filter compile interpret,$(process)),)
	x := $(error "'$(process)' is not a valid process (choose one of: compile interpret)")
endif

ifeq ($(filter x86_64 i386 arm arm64,$(arch)),)
	x := $(error "'$(arch)' is not a supported architecture (choose one of: x86_64 i386 arm arm64)")
endif

ifeq ($(platform),darwin)
	x := $(error "please use 'platform=macosx' or 'platform=ios' instead of 'platform=$(platform)'")
endif

ifneq ($(ios),)
	x := $(error "please use 'platform=ios' instead of 'ios=true'")
endif

ifeq ($(filter linux windows macosx ios freebsd,$(platform)),)
	x := $(error "'$(platform)' is not a supported platform (choose one of: linux windows macosx ios freebsd)")
endif

ifeq ($(platform),macosx)
	ifneq ($(filter arm arm64,$(arch)),)
		x := $(error "please use ('arch=arm' or 'arch=arm64') 'platform=ios' to build for ios-arm")
	endif
endif

ifeq ($(platform),ios)
	ifeq ($(filter i386 x86_64 arm arm64,$(arch)),)
		x := $(error "please specify 'arch=i386', 'arch=x86_64', 'arch=arm', or 'arch=arm64' with 'platform=ios'")
	endif
endif

ifeq ($(bootimage-test),true)
	ifneq ($(bootimage),true)
		x := $(error "bootimage-test=true only works when bootimage=true")
	endif
endif

aot-only = false
root := $(shell (cd .. && pwd))
build = build/$(platform)-$(arch)$(options)
host-build-root = $(build)/host
classpath-build = $(build)/classpath
test-build = $(build)/test
src = src
classpath-src = classpath
test = test
unittest = unittest
win32 ?= $(root)/win32
win64 ?= $(root)/win64
winrt ?= $(root)/winrt
wp8 ?= $(root)/wp8

classpath = avian

bootimage-classpath = $(classpath-build)

ifeq ($(bootimage-test),true)
	bootimage-classpath = $(classpath-build):$(test-build)
endif

ifeq ($(use-werror),true)
	werror = -Werror
endif

test-executable = $(shell pwd)/$(executable)
boot-classpath = $(classpath-build)
embed-prefix = /avian-embedded

native-path = echo

platform-kernel = $(subst macosx,darwin,$(subst ios,darwin,$1))

build-kernel = $(call platform-kernel,$(build-platform))
kernel = $(call platform-kernel,$(platform))

ifeq ($(build-platform),cygwin)
	native-path = cygpath -m
endif

windows-path = echo

path-separator = :

ifneq (,$(filter mingw32 cygwin,$(build-platform)))
	path-separator = ;
endif

target-path-separator = :

ifeq ($(platform),windows)
	target-path-separator = ;
endif

library-path-variable = LD_LIBRARY_PATH

ifeq ($(build-kernel),darwin)
	library-path-variable = DYLD_LIBRARY_PATH
endif

library-path = $(library-path-variable)=$(build)



ifneq ($(openjdk),)
	openjdk-version := $(shell $(openjdk)/bin/java -version 2>&1 \
		| grep 'version "1' \
		| sed 's/.*version "1.\([^.]*\).*/\1/')

	openjdk-arch = $(arch)
	ifeq ($(arch),x86_64)
		openjdk-arch = amd64
	endif

	ifneq ($(android),)
		x := $(error "android and openjdk are incompatible")
	endif

        ifeq ($(openjdk-image),)
                openjdk-image = $(openjdk)/jre
        endif

	ifneq ($(openjdk-src),)
		include openjdk-src.mk
		options := $(options)-openjdk-src
		classpath-objects = $(openjdk-objects) $(openjdk-local-objects)
		classpath-cflags = -DAVIAN_OPENJDK_SRC -DBOOT_JAVAHOME
		openjdk-jar-dep = $(build)/openjdk-jar.dep
		classpath-jar-dep = $(openjdk-jar-dep)
		javahome = $(embed-prefix)/javahomeJar
		javahome-files = lib/currency.data lib/security/java.security \
			lib/security/java.policy lib/security/cacerts

		ifneq (,$(wildcard $(openjdk)/jre/lib/zi))
			javahome-files += lib/zi
		endif

		ifneq (,$(wildcard $(openjdk)/jre/lib/tzdb.dat))
			javahome-files += lib/tzdb.dat
		endif

		local-policy = lib/security/local_policy.jar
		ifneq (,$(wildcard $(openjdk)/jre/$(local-policy)))
			javahome-files += $(local-policy)
		endif

		export-policy = lib/security/US_export_policy.jar
		ifneq (,$(wildcard $(openjdk)/jre/$(export-policy)))
			javahome-files += $(export-policy)
		endif

		ifeq ($(platform),windows)
			javahome-files += lib/tzmappings
		endif
		javahome-object = $(build)/javahome-jar.o
		boot-javahome-object = $(build)/boot-javahome.o
		stub-sources = $(src)/openjdk/stubs.cpp
		stub-objects = $(call cpp-objects,$(stub-sources),$(src),$(build))
	else
		soname-flag = -Wl,-soname -Wl,$(so-prefix)jvm$(so-suffix)
		version-script-flag = -Wl,--version-script=openjdk.ld
		options := $(options)-openjdk
		test-executable = $(shell pwd)/$(executable-dynamic)
		ifeq ($(build-kernel),darwin)
			library-path = \
				$(library-path-variable)=$(build):$(openjdk)/jre/lib
		else
			library-path = \
				$(library-path-variable)=$(build):$(openjdk)/jre/lib/$(openjdk-arch)
		endif
		javahome = "$$($(native-path) "$(openjdk)/jre")"
	endif

        ifeq ($(openjdk-image),)
            openjdk-image = $(openjdk)/jre
        endif

	classpath = openjdk
	boot-classpath := "$(boot-classpath)$(path-separator)$$($(native-path) "$(openjdk)/jre/lib/rt.jar")"
	build-javahome = $(openjdk)/jre
endif

ifneq ($(android),)
	options := $(options)-android
	classpath-jar-dep = $(build)/android.dep
	luni-native = $(android)/libcore/luni/src/main/native
	classpath-cflags = -DBOOT_JAVAHOME
	android-cflags = -I$(luni-native) \
		-I$(android)/libnativehelper/include/nativehelper \
		-I$(android)/libnativehelper \
		-I$(android)/system/core/include \
		-I$(android)/external/zlib \
		-I$(android)/external/icu4c/i18n \
		-I$(android)/external/icu4c/common \
		-I$(android)/external/expat \
		-I$(android)/external/openssl/include \
		-I$(android)/external/openssl \
		-I$(android)/libcore/include \
		-I$(build)/android-src/external/fdlibm \
		-I$(build)/android-src \
		-fno-exceptions \
		-D_FILE_OFFSET_BITS=64 \
		-DOS_SHARED_LIB_FORMAT_STR="\"$(so-prefix)%s$(so-suffix)\"" \
		-DJNI_JARJAR_PREFIX= \
		-D__DARWIN_UNIX03=1 \
		-D__PROVIDE_FIXMES \
		-DSTATIC_LIB \
		-D__STDC_FORMAT_MACROS=1 \
		-g3 \
		-Wno-shift-count-overflow

	# on Windows (in MinGW-based build) there are neither __BEGIN_DECLS nor __END_DECLS
	# defines; we don't want to patch every file that uses them, so we stub them in
	# using CFLAGS mechanism
	# Also we have off64_t defined in mingw-w64 headers, so let's tell that
	ifeq ($(platform),windows)
		android-cflags += "-D__BEGIN_DECLS=extern \"C\" {" "-D__END_DECLS=}" "-DHAVE_OFF64_T"
	endif

	luni-cpps := $(shell find $(luni-native) -name '*.cpp')

	libziparchive-native := $(android)/system/core/libziparchive
	libziparchive-ccs := $(libziparchive-native)/zip_archive.cc

	libutils-native := $(android)/system/core/libutils
	libutils-cpps := $(libutils-native)/FileMap.cpp

	libnativehelper-native := $(android)/libnativehelper
	libnativehelper-cpps := $(libnativehelper-native)/JniConstants.cpp \
		$(libnativehelper-native)/toStringArray.cpp

	crypto-native := $(android)/external/conscrypt/src/main/native
	crypto-cpps := $(crypto-native)/org_conscrypt_NativeCrypto.cpp

	ifeq ($(platform),windows)
		android-cflags += -D__STDC_CONSTANT_MACROS -DHAVE_WIN32_FILEMAP -D__STDC_FORMAT_MACROS
		blacklist = $(luni-native)/java_io_Console.cpp \
			$(luni-native)/java_lang_ProcessManager.cpp

		icu-libs := $(android)/external/icu4c/lib/libsicuin.a \
			$(android)/external/icu4c/lib/libsicuuc.a \
			$(android)/external/icu4c/lib/sicudt.a
		platform-lflags := -lgdi32 -lshlwapi -lwsock32 -static-libgcc -static-libstdc++ -Wl,-Bstatic -lstdc++ -lpthread -Wl,-Bdynamic
	else
		android-cflags += -fPIC -DHAVE_SYS_UIO_H -DHAVE_POSIX_FILEMAP
		blacklist =

		icu-libs := $(android)/external/icu4c/lib/libicui18n.a \
			$(android)/external/icu4c/lib/libicuuc.a \
			$(android)/external/icu4c/lib/libicudata.a
	endif
	luni-cpps := $(filter-out $(blacklist),$(luni-cpps))

	classpath-lflags := \
		$(icu-libs) \
		$(android)/external/fdlibm/libfdm.a \
		$(android)/external/expat/.libs/libexpat.a \
		$(android)/openssl-upstream/libssl.a \
		$(android)/openssl-upstream/libcrypto.a \
		$(platform-lflags) \
		-lstdc++

	ifeq ($(platform),linux)
		android-cflags += -DHAVE_OFF64_T
		classpath-lflags += -lrt
	endif

	classpath-objects = \
		$(call cpp-objects,$(luni-cpps),$(luni-native),$(build)) \
		$(call cpp-objects,$(crypto-cpps),$(crypto-native),$(build)) \
		$(call cpp-objects,$(libnativehelper-cpps),$(libnativehelper-native),$(build)) \
		$(call cc-objects,$(libziparchive-ccs),$(libziparchive-native),$(build)) \
		$(call cpp-objects,$(libutils-cpps),$(libutils-native),$(build))
	luni-java = $(android)/libcore/luni/src/main/java

	luni-blacklist = \
		$(luni-java)/libcore/reflect/AnnotationAccess.java

	luni-javas := \
		$(filter-out $(luni-blacklist),$(shell find $(luni-java) -name '*.java'))

	luni-nonjavas := $(shell find $(luni-java) -not -type d -not -name '*.java')
	luni-copied-nonjavas = $(call noop-files,$(luni-nonjavas),$(luni-java),)

	crypto-java = $(android)/external/conscrypt/src/main/java
	crypto-javas := $(shell find $(crypto-java) -name '*.java')

	crypto-platform-java = $(android)/external/conscrypt/src/platform/java
	crypto-platform-javas := $(shell find $(crypto-platform-java) -name '*.java')

	dalvik-java = $(android)/libcore/dalvik/src/main/java
	dalvik-javas := \
		$(dalvik-java)/dalvik/system/DalvikLogHandler.java \
		$(dalvik-java)/dalvik/system/CloseGuard.java \
		$(dalvik-java)/dalvik/system/VMDebug.java \
		$(dalvik-java)/dalvik/system/BlockGuard.java \
		$(dalvik-java)/dalvik/system/SocketTagger.java \
		$(dalvik-java)/dalvik/system/DalvikLogging.java \

	libart-java = $(android)/libcore/libart/src/main/java
	libart-javas := \
		$(libart-java)/dalvik/system/VMRuntime.java \
		$(libart-java)/dalvik/system/VMStack.java \
		$(libart-java)/java/lang/Thread.java \
		$(libart-java)/java/lang/ThreadGroup.java \
		$(libart-java)/java/lang/Enum.java \
		$(libart-java)/java/lang/String.java \
		$(libart-java)/java/lang/ref/Reference.java \
		$(libart-java)/java/lang/reflect/AccessibleObject.java \

	xml-java = $(android)/libcore/xml/src/main/java
	xml-javas := $(shell find $(xml-java) -name '*.java')

	okhttp-android-java = $(android)/external/okhttp/android/main/java
	okhttp-android-javas := $(shell find $(okhttp-android-java) -name '*.java')

	okhttp-java = $(android)/external/okhttp/okhttp/src/main/java
	okhttp-javas := $(shell find $(okhttp-java) -name '*.java')

	okio-java = $(android)/external/okhttp/okio/src/main/java
	okio-javas := $(shell find $(okio-java) -name '*.java')

	bcpkix-java = $(android)/external/bouncycastle/bcpkix/src/main/java
	bcpkix-javas := $(shell find $(bcpkix-java) -name '*.java')

	bcprov-java = $(android)/external/bouncycastle/bcprov/src/main/java
	bcprov-javas := $(shell find $(bcprov-java) -name '*.java')

	android-classes = \
		$(call java-classes,$(luni-javas),$(luni-java),$(build)/android) \
		$(call java-classes,$(crypto-javas),$(crypto-java),$(build)/android) \
		$(call java-classes,$(crypto-platform-javas),$(crypto-platform-java),$(build)/android) \
		$(call java-classes,$(dalvik-javas),$(dalvik-java),$(build)/android) \
		$(call java-classes,$(libart-javas),$(libart-java),$(build)/android) \
		$(call java-classes,$(xml-javas),$(xml-java),$(build)/android) \
		$(call java-classes,$(okhttp-javas),$(okhttp-java),$(build)/android) \
		$(call java-classes,$(okhttp-android-javas),$(okhttp-android-java),$(build)/android) \
		$(call java-classes,$(okio-javas),$(okio-java),$(build)/android) \
		$(call java-classes,$(bcpkix-javas),$(bcpkix-java),$(build)/android) \
		$(call java-classes,$(bcprov-javas),$(bcprov-java),$(build)/android)

	classpath = android

	javahome-files = tzdata
	javahome-object = $(build)/javahome-jar.o
	boot-javahome-object = $(build)/boot-javahome.o
	build-javahome = $(android)/bionic/libc/zoneinfo
	stub-sources = $(src)/android/stubs.cpp
	stub-objects = $(call cpp-objects,$(stub-sources),$(src),$(build))
endif

ifeq ($(classpath),avian)
	jni-sources := $(shell find $(classpath-src) -name '*.cpp')
	jni-objects = $(call cpp-objects,$(jni-sources),$(classpath-src),$(build))
	classpath-objects = $(jni-objects)
endif

input = List

ifeq ($(use-clang),true)
	build-cxx = clang++ -std=c++11
	build-cc = clang
else
	build-cxx = g++
	build-cc = gcc
endif

mflag =
ifneq ($(kernel),darwin)
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
javac = "$(JAVA_HOME)/bin/javac" -encoding UTF-8
javah = "$(JAVA_HOME)/bin/javah"
jar = "$(JAVA_HOME)/bin/jar"
strip = strip
strip-all = --strip-all

rdynamic = -rdynamic

cflags_debug = -O0 -g3
cflags_debug_fast = -O0 -g3
cflags_stress = -O0 -g3
cflags_stress_major = -O0 -g3
ifeq ($(use-clang),true)
	cflags_fast = -O3 -g3
	cflags_small = -Oz -g3
else
	cflags_fast = -O3 -g3
	cflags_small = -Os -g3
endif

# note that we suppress the non-virtual-dtor warning because we never
# use the delete operator, which means we don't need virtual
# destructors:
warnings = -Wall -Wextra $(werror) -Wunused-parameter -Winit-self \
	-Wno-non-virtual-dtor

target-cflags = -DTARGET_BYTES_PER_WORD=$(pointer-size)

common-cflags = $(warnings) -std=c++0x -fno-rtti -fno-exceptions -I$(classpath-src) \
	"-I$(JAVA_HOME)/include" -I$(src) -I$(build) -Iinclude $(classpath-cflags) \
	-D__STDC_LIMIT_MACROS -D_JNI_IMPLEMENTATION_ -DAVIAN_VERSION=\"$(version)\" \
	-DAVIAN_INFO="\"$(info)\"" \
	-DUSE_ATOMIC_OPERATIONS -DAVIAN_JAVA_HOME=\"$(javahome)\" \
	-DAVIAN_EMBED_PREFIX=\"$(embed-prefix)\" $(target-cflags)

asmflags = $(target-cflags) -I$(src)

ifneq (,$(filter i386 x86_64,$(arch)))
	ifeq ($(use-frame-pointer),true)
		common-cflags += -fno-omit-frame-pointer -DAVIAN_USE_FRAME_POINTER
		asmflags += -DAVIAN_USE_FRAME_POINTER
	endif
endif

build-cflags = $(common-cflags) -fPIC -fvisibility=hidden \
	"-I$(JAVA_HOME)/include/linux" $(extra-build-cflags) -I$(src) -pthread

converter-cflags = -D__STDC_CONSTANT_MACROS -std=c++0x -Iinclude/ -Isrc/ \
	-fno-rtti -fno-exceptions \
	-DAVIAN_TARGET_ARCH=AVIAN_ARCH_UNKNOWN \
	-DAVIAN_TARGET_FORMAT=AVIAN_FORMAT_UNKNOWN \
	-Wall -Wextra $(werror) -Wunused-parameter -Winit-self -Wno-non-virtual-dtor

cflags = $(build-cflags)

common-lflags = -lm -lz

ifeq ($(use-clang),true)
	ifeq ($(build-kernel),darwin)
		common-lflags += -Wl,-export_dynamic
	else
		ifneq ($(platform),windows)
			common-lflags += -Wl,-E
		else
			common-lflags += -Wl,--export-all-symbols
		endif
	endif
endif

build-lflags = -lz -lpthread -ldl

lflags = $(common-lflags) -lpthread -ldl

build-system = posix

system = posix
asm = x86

ifeq ($(system),sgx)
    cflags += -DSGX -I../linux-sgx/common/inc -I../jvm-enclave/common
    lflags += $(shared)
endif

pointer-size = 8

so-prefix = lib
so-suffix = .so

static-prefix = lib
static-suffix = .a

output = -o $(1)
asm-output = -o $(1)
asm-input = -c $(1)
asm-format = S
as = $(cc)
ld = $(cc)
build-ld = $(build-cc)
build-ld-cpp = $(build-cxx)

default-remote-test-host = localhost
default-remote-test-port = 22
ifeq ($(remote-test-host),)
	remote-test-host = $(default-remote-test-host)
else
	remote-test = true
endif
ifeq ($(remote-test-port),)
	remote-test-port = $(default-remote-test-port)
else
	remote-test = true
endif
remote-test-user = ${USER}
remote-test-dir = /tmp/avian-test-${USER}

static = -static
shared = -shared

rpath = -Wl,-rpath=\$$ORIGIN -Wl,-z,origin

openjdk-extra-cflags = -fvisibility=hidden

bootimage-cflags = -DTARGET_BYTES_PER_WORD=$(pointer-size)
bootimage-symbols = _binary_bootimage_bin_start:_binary_bootimage_bin_end
codeimage-symbols = _binary_codeimage_bin_start:_binary_codeimage_bin_end

developer-dir := $(shell if test -d /Developer/Platforms/$(target).platform/Developer/SDKs; then echo /Developer; \
	else echo /Applications/Xcode.app/Contents/Developer; fi)

ifneq (,$(filter i386 arm,$(arch)))
	pointer-size = 4
endif

ifneq (,$(filter arm arm64,$(arch)))
	asm = arm

	ifneq ($(platform),ios)
		ifneq ($(arch),arm64)
			no-psabi = -Wno-psabi
			cflags += -marm $(no-psabi)

			# By default, assume we can't use armv7-specific instructions on
			# non-iOS platforms.  Ideally, we'd detect this at runtime.
			armv6=true
		endif
	endif

	ifneq ($(arch),$(build-arch))
		ifneq ($(kernel),darwin)
			ifeq ($(arch),arm64)
				cxx = aarch64-linux-gnu-g++
				cc = aarch64-linux-gnu-gcc
				ar = aarch64-linux-gnu-ar
				ranlib = aarch64-linux-gnu-ranlib
				strip = aarch64-linux-gnu-strip
			else
				cxx = arm-linux-gnueabi-g++
				cc = arm-linux-gnueabi-gcc
				ar = arm-linux-gnueabi-ar
				ranlib = arm-linux-gnueabi-ranlib
				strip = arm-linux-gnueabi-strip
			endif
		endif
	endif
endif

ifeq ($(armv6),true)
	cflags += -DAVIAN_ASSUME_ARMV6
endif

ifeq ($(platform),ios)
	cflags += -DAVIAN_IOS
	use-lto = false
endif

ifeq ($(build-kernel),darwin)
	build-cflags = $(common-cflags) -fPIC -fvisibility=hidden -I$(src)
	cflags += -Wno-deprecated-declarations
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
ifeq ($(platform),android)
	ifeq ($(build-platform),cygwin)
		ndk = "$$(cygpath -u "$(ANDROID_NDK)")"
	else
		ndk = $(ANDROID_NDK)
	endif

	ifeq ($(android-version),)
		android-version = 5
	endif

	ifeq ($(android-toolchain),)
		android-toolchain = 4.7
	endif

	ifeq ($(arch),arm)
		android-toolchain-name = arm-linux-androideabi
		android-toolchain-prefix = arm-linux-androideabi-
	endif
	ifeq ($(arch),i386)
		android-toolchain-name = x86
		android-toolchain-prefix = i686-linux-android-
	endif

	ifeq ($(android-arm-arch),)
		android-arm-arch = armv5
	endif

	options := $(options)-api$(android-version)-$(android-toolchain)-$(android-arm-arch)

	build-cflags = $(common-cflags) -I$(src)
	build-lflags = -lz -lpthread
	ifeq ($(subst cygwin,windows,$(subst mingw32,windows,$(build-platform))),windows)
		toolchain-host-platform = $(subst cygwin,windows,$(subst mingw32,windows,$(build-platform)))
		build-system = windows
		build-cxx = i686-w64-mingw32-g++
		build-cc = i686-w64-mingw32-gcc
		sysroot = "$$(cygpath -w "$(ndk)/platforms/android-$(android-version)/arch-arm")"
		build-cflags += "-I$(JAVA_HOME)/include/win32"
	else
		toolchain-host-platform = $(subst cygwin,windows,$(subst mingw32,windows,$(build-platform)))-*
		sysroot = $(ndk)/platforms/android-$(android-version)/arch-arm
		build-cflags += "-I$(JAVA_HOME)/include/linux"
		build-lflags += -ldl
	endif
	toolchain = $(ndk)/toolchains/$(android-toolchain-name)-$(android-toolchain)/prebuilt/$(toolchain-host-platform)
	cflags = "-I$(sysroot)/usr/include" "-I$(JAVA_HOME)/include/linux" $(common-cflags) "-I$(src)" -std=c++11 $(no-psabi)
	lflags = "-L$(sysroot)/usr/lib" $(common-lflags) -llog
	target-format = elf
	use-lto = false

	ifeq ($(arch),arm)
		cflags += -marm -march=$(android-arm-arch) -ftree-vectorize -ffast-math -mfloat-abi=softfp
	endif
	ifeq ($(arch),i386)
	endif

	cxx = $(toolchain)/bin/$(android-toolchain-prefix)g++ --sysroot="$(sysroot)"
	cc = $(toolchain)/bin/$(android-toolchain-prefix)gcc --sysroot="$(sysroot)"
	as = $(cxx)
	ar = $(toolchain)/bin/$(android-toolchain-prefix)ar
	ranlib = $(toolchain)/bin/$(android-toolchain-prefix)ranlib
	strip = $(toolchain)/bin/$(android-toolchain-prefix)strip
endif

ifeq ($(kernel),darwin)
	target-format = macho
	ifeq (${OSX_SDK_SYSROOT},)
		OSX_SDK_SYSROOT = 10.6u
	endif
	ifeq (${OSX_SDK_VERSION},)
		OSX_SDK_VERSION = 10.6
	endif
	ifneq ($(build-kernel),darwin)
		cxx = i686-apple-darwin8-g++ $(mflag)
		cc = i686-apple-darwin8-gcc $(mflag)
		ar = i686-apple-darwin8-ar
		ranlib = i686-apple-darwin8-ranlib
		strip = i686-apple-darwin8-strip
		sysroot = /opt/mac/SDKs/MacOSX${OSX_SDK_SYSROOT}.sdk
		cflags = -I$(sysroot)/System/Library/Frameworks/JavaVM.framework/Versions/1.5.0/Headers/ \
			$(common-cflags) -fPIC -fvisibility=hidden -I$(src)
	endif

	ifneq ($(platform),ios)
		platform-dir = $(developer-dir)/Platforms/MacOSX.platform
		sdk-dir = $(platform-dir)/Developer/SDKs

		mac-version := $(shell \
				if test -d $(sdk-dir)/MacOSX10.11.sdk; then echo 10.11; \
			elif test -d $(sdk-dir)/MacOSX10.10.sdk; then echo 10.10; \
			elif test -d $(sdk-dir)/MacOSX10.9.sdk; then echo 10.9; \
			elif test -d $(sdk-dir)/MacOSX10.8.sdk; then echo 10.8; \
			elif test -d $(sdk-dir)/MacOSX10.7.sdk; then echo 10.7; \
			elif test -d $(sdk-dir)/MacOSX10.6.sdk; then echo 10.6; \
			else echo; fi)

		sysroot = $(sdk-dir)/MacOSX$(mac-version).sdk
	endif

	soname-flag =
	version-script-flag =
	lflags = $(common-lflags) -ldl -framework CoreFoundation -framework Foundation

	ifeq (,$(shell ld -v 2>&1 | grep cctools))
		lflags += -Wl,-compatibility_version,1.0.0
	endif

	ifneq ($(platform),ios)
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
	rpath =

	ifeq ($(platform),ios)
		ifeq ($(sim),true)
			target = iPhoneSimulator
			sdk = iphonesimulator$(ios-version)
			ifeq ($(arch),i386)
				arch-flag = -arch i386
			else
				arch-flag = -arch x86_64
				arch = x86_64
			endif
			release = Release-iphonesimulator
		else
			target = iPhoneOS
			sdk = iphoneos$(ios-version)
			ifeq ($(arch),arm)
				arch-flag = -arch armv7
			else
				arch-flag = -arch arm64
				arch = arm64
			endif
			release = Release-iphoneos
		endif

		platform-dir = $(developer-dir)/Platforms/$(target).platform
		sdk-dir = $(platform-dir)/Developer/SDKs

		ios-version := $(shell for x in 10.3 10.2 10.1 10.0 9.3 9.2 9.1 9.0 8.3 8.2 8.1 8.0; \
			do if test -d $(sdk-dir)/$(target)$$x.sdk \
				-o -L $(sdk-dir)/$(target)$$x.sdk; \
			then echo $$x; break; fi; done)

		ifeq ($(ios-version),)
			x := $(error "couldn't find SDK in $(sdk-dir)")
		endif

		sysroot = $(sdk-dir)/$(target)$(ios-version).sdk

# apparently, the header files we need are part of the simulator SDK
# but not the device SDK, so we copy them from the former even if
# we're targeting the latter

		header-sysroot := $(subst iPhoneOS,iPhoneSimulator,$(sysroot))

		ios-bin = $(platform-dir)/Developer/usr/bin

		found-gcc = $(shell if test -f $(ios-bin)/gcc; then echo true; else echo false; fi)

		ifeq ($(found-gcc),false)
			use-clang = true
		endif

		ifeq ($(use-clang),true)
			cxx = clang -std=c++11
			cc = clang
		else
			cxx = $(ios-bin)/g++
			cc = $(ios-bin)/gcc
		endif

		flags = -isysroot $(sdk-dir)/$(target)$(ios-version).sdk \
			$(arch-flag)

		classpath-extra-cflags += $(flags)
		cflags += $(flags)
		asmflags += $(flags)
		lflags += $(flags)

		ios-version-min=$(ios-version)
		ifdef ios_deployment_target
			ios-version-min=ios_deployment_target
		endif

		ifeq ($(sim),true)
			ifeq ($(arch),x86_64)
				classpath-extra-cflags += \
					-arch x86_64 -miphoneos-version-min=$(ios-version-min)
				cflags += -arch x86_64 -miphoneos-version-min=$(ios-version-min)
				asmflags += -arch x86_64 -miphoneos-version-min=$(ios-version-min)
				lflags += -arch x86_64 -miphoneos-version-min=$(ios-version-min)
			else
				classpath-extra-cflags += \
					-arch i386 -miphoneos-version-min=$(ios-version-min)
				cflags += -arch i386 -miphoneos-version-min=$(ios-version-min)
				asmflags += -arch i386 -miphoneos-version-min=$(ios-version-min)
				lflags += -arch i386 -miphoneos-version-min=$(ios-version-min)
			endif
		else
			ifeq ($(arch),arm64)
				classpath-extra-cflags += \
					-arch arm64 -miphoneos-version-min=$(ios-version-min)
				cflags += -arch arm64 -miphoneos-version-min=$(ios-version-min)
				asmflags += -arch arm64 -miphoneos-version-min=$(ios-version-min)
				lflags += -arch arm64 -miphoneos-version-min=$(ios-version-min)
			else
				classpath-extra-cflags += \
					-arch armv7 -miphoneos-version-min=$(ios-version-min)
				cflags += -arch armv7 -miphoneos-version-min=$(ios-version-min)
				asmflags += -arch armv7 -miphoneos-version-min=$(ios-version-min)
				lflags += -arch armv7 -miphoneos-version-min=$(ios-version-min)
			endif
		endif
	else # not ios
		ifeq ($(arch),i386)
				classpath-extra-cflags += \
					-arch i386 -mmacosx-version-min=${OSX_SDK_VERSION}
				cflags += -arch i386 -mmacosx-version-min=${OSX_SDK_VERSION}
				asmflags += -arch i386 -mmacosx-version-min=${OSX_SDK_VERSION}
				lflags += -arch i386 -mmacosx-version-min=${OSX_SDK_VERSION}
		endif

		ifeq ($(arch),x86_64)
				classpath-extra-cflags += -arch x86_64
				cflags += -arch x86_64
				asmflags += -arch x86_64
				lflags += -arch x86_64
		endif
	endif
	cflags += -I$(JAVA_HOME)/include/darwin
endif

openjdk-extra-cflags += $(classpath-extra-cflags)

find-tool = $(shell if ( command -v "$(1)$(2)" >/dev/null ); then (echo "$(1)$(2)") else (echo "$(2)"); fi)

ifeq ($(platform),windows)
	target-format = pe

	inc = "$(win32)/include"
	lib = "$(win32)/lib"

	embed-prefix = c:/avian-embedded
	system = windows

	so-prefix =
	so-suffix = .dll
	exe-suffix = .exe
	rpath =

	lflags = -L$(lib) $(common-lflags) -lws2_32 -lversion -luuid -liphlpapi \
		-lmswsock -mconsole

	cflags = -I$(inc) $(common-cflags) -DWINVER=0x0500 -U__STRICT_ANSI__

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
		static-on-windows = -static
		common-cflags += "-I$(JAVA_HOME)/include/win32"
		build-cflags = $(common-cflags) -I$(src) -I$(inc) -mthreads \
			-D_WIN32_WINNT=0x0500
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
		dlltool = $(call find-tool,x86_64-w64-mingw32-,dlltool)
		ar = $(call find-tool,x86_64-w64-mingw32-,ar)
		ranlib = $(call find-tool,x86_64-w64-mingw32-,ranlib)
		strip = $(call find-tool,x86_64-w64-mingw32-,strip)
		inc = "$(win64)/include"
		lib = "$(win64)/lib"
	else
		shared += -Wl,--add-stdcall-alias
	endif

	embed = $(build-embed)/embed$(exe-suffix)
	embed-loader = $(build-embed-loader)/embed-loader$(exe-suffix)
	embed-loader-o = $(build-embed)/embed-loader.o
endif

ifeq ($(platform),wp8)
	ifeq ($(shell uname -s | grep -i -c WOW64),1)
		programFiles = Program Files (x86)
	else
		programFiles = Program Files
	endif
	ifeq ($(MSVS_ROOT),)
		# Environment variable MSVS_ROOT not found. It should be something like
		# "C:\$(programFiles)\Microsoft Visual Studio 11.0"
		MSVS_ROOT = C:\$(programFiles)\Microsoft Visual Studio 11.0
	endif
	ifeq ($(MSVC_ROOT),)
		# Environment variable MSVC_ROOT not found. It should be something like
		# "C:\$(programFiles)\Microsoft Visual Studio 11.0\VC"
		MSVC_ROOT = $(MSVS_ROOT)\VC
	endif
	ifeq ($(WP80_SDK),)
		# Environment variable WP8_SDK not found. It should be something like
		# "C:\Program Files[ (x86)]\Microsoft Visual Studio 11.0\VC\WPSDK\WP80"
		# TODO: Lookup in SOFTWARE\Microsoft\Microsoft SDKs\WindowsPhone\v8.0
		WP80_SDK = $(MSVS_ROOT)\VC\WPSDK\WP80
	endif
	ifeq ($(WP80_KIT),)
		# Environment variable WP8_KIT not found. It should be something like
		# "c:\Program Files[ (x86)]\Windows Phone Kits\8.0"
		# TODO: Lookup in SOFTWARE\Microsoft\Microsoft SDKs\WindowsPhone\v8.0
		WP80_KIT = C:\$(programFiles)\Windows Phone Kits\8.0
	endif
	ifeq ($(WIN8_KIT),)
		# Environment variable WIN8_KIT not found. It should be something like
		# "c:\Program Files[ (x86)]\Windows Kits\8.0"
		WIN8_KIT = C:\$(programFiles)\Windows Kits\8.0
	endif
	ifeq ($(build-platform),cygwin)
		windows-path = cygpath -w
	else
		windows-path = $(native-path)
	endif
	windows-java-home := $(shell $(windows-path) "$(JAVA_HOME)")
	target-format = pe
	ms_cl_compiler = wp8
	use-lto = false
	supports_avian_executable = false
	aot-only = true
	ifneq ($(bootimage),true)
		x := $(error Windows Phone 8 target requires bootimage=true)
	endif
	system = windows
	build-system = windows
	static-prefix =
	static-suffix = .lib
	so-prefix =
	so-suffix = .dll
	exe-suffix = .exe
	manifest-flags = -MANIFEST:NO

	ifeq ($(arch),arm)
		wp8_arch = \x86_arm
		vc_arch = \arm
		w8kit_arch = arm
		deps_arch = ARM
		as = "$$(cygpath -u "$(WP80_SDK)\bin\x86_arm\armasm.exe")"
		cxx = "$$(cygpath -u "$(WP80_SDK)\bin\x86_arm\cl.exe")"
		ld = "$$(cygpath -u "$(WP80_SDK)\bin\x86_arm\link.exe")"
		asmflags = -machine ARM -32
		asm-output = -o $(1)
		asm-input = $(1)
		machine_type = ARM
		bootimage-symbols = binary_bootimage_bin_start:binary_bootimage_bin_end
		codeimage-symbols = binary_codeimage_bin_start:binary_codeimage_bin_end
	endif
	ifeq ($(arch),i386)
		wp8_arch =
		vc_arch =
		w8kit_arch = x86
		deps_arch = x86
		asmflags = $(target-cflags) -safeseh -nologo -Gd
		as = "$$(cygpath -u "$(WP80_SDK)\bin\ml.exe")"
		cxx = "$$(cygpath -u "$(WP80_SDK)\bin\cl.exe")"
		ld = "$$(cygpath -u "$(WP80_SDK)\bin\link.exe")"
		ifeq ($(mode),debug)
			asmflags += -Zd
		endif
		ifeq ($(mode),debug-fast)
			asmflags += -Zd
		endif
		asm-output = $(output)
		machine_type = X86
	endif

	PATH := $(shell cygpath -u "$(MSVS_ROOT)\Common7\IDE"):$(shell cygpath -u "$(WP80_SDK)\bin$(wp8_arch)"):$(shell cygpath -u "$(WP80_SDK)\bin"):${PATH}

	build-cflags = $(common-cflags) -I$(src) -I$(inc) -mthreads
	build-lflags = -lz -lpthread

	cflags = -nologo \
		-AI"$(WP80_KIT)\Windows Metadata" \
		-I"$(WP80_SDK)\include" -I"$(WP80_KIT)\Include" -I"$(WP80_KIT)\Include\minwin" -I"$(WP80_KIT)\Include\mincore" \
		-DWINAPI_FAMILY=WINAPI_FAMILY_PHONE_APP -D_USRDLL -D_WINDLL \
		-DAVIAN_VERSION=\"$(version)\" -D_JNI_IMPLEMENTATION_ \
		-DUSE_ATOMIC_OPERATIONS -DAVIAN_JAVA_HOME=\"$(javahome)\" \
		-DAVIAN_EMBED_PREFIX=\"$(embed-prefix)\" \
		-I"$(shell $(windows-path) "$(wp8)/zlib/upstream")" -I"$(shell $(windows-path) "$(wp8)/interop/avian-interop-client")" \
		-I"$(shell $(windows-path) "$(wp8)/include")" -I$(src) -I$(classpath-src) \
		-I"$(build)" \
		-I"$(windows-java-home)/include" -I"$(windows-java-home)/include/win32" \
		-DTARGET_BYTES_PER_WORD=$(pointer-size) \
		-Gd -EHsc

	common-lflags =

	ifeq ($(mode),debug)
		build-type = Debug
	endif
	ifeq ($(mode),debug-fast)
		build-type = Debug
	endif
	ifeq ($(mode),stress-major)
		build-type = Release
	endif
	ifeq ($(mode),fast)
		build-type = Release
	endif
	ifeq ($(mode),fast)
		build-type = Release
	endif
	ifeq ($(mode),small)
		build-type = Release
	endif

	arflags = -MACHINE:$(machine_type)
	lflags = $(common-lflags) -nologo \
		-MACHINE:$(machine_type) \
		-LIBPATH:"$(WP80_KIT)\lib\$(w8kit_arch)" -LIBPATH:"$(WP80_SDK)\lib$(vc_arch)" -LIBPATH:"$(WIN8_KIT)\Lib\win8\um\$(w8kit_arch)" \
		ws2_32.lib \
		"$(shell $(windows-path) "$(wp8)\lib\$(deps_arch)\$(build-type)\zlib.lib")" "$(shell $(windows-path) "$(wp8)\lib\$(deps_arch)\$(build-type)\ThreadEmulation.lib")" \
		"$(shell $(windows-path) "$(wp8)\lib\$(deps_arch)\$(build-type)\AvianInteropClient.lib")"
	lflags += -NXCOMPAT -DYNAMICBASE -SUBSYSTEM:CONSOLE -TLBID:1
	lflags += -NODEFAULTLIB:"ole32.lib" -NODEFAULTLIB:"kernel32.lib"
	lflags += PhoneAppModelHost.lib WindowsPhoneCore.lib -WINMD -WINMDFILE:$(subst $(so-suffix),.winmd,$(@))

	cc = $(cxx)
	asm-format = masm
	shared = -dll
	ar = "$$(cygpath -u "$(WP80_SDK)\bin\lib.exe")"
	arflags += -nologo
	ifeq ($(build-platform),cygwin)
		build-cxx = i686-w64-mingw32-g++
		build-cc = i686-w64-mingw32-gcc
		dlltool = i686-w64-mingw32-dlltool
		ranlib =
		strip =
	endif
	output = -Fo$(1)

	#TODO: -MT or -ZW?
	cflags_debug = -Od -Zi -MDd
	cflags_debug_fast = -Od -Zi -MDd
	cflags_stress = -O0 -g3 -MD
	cflags_stress_major = -O0 -g3 -MD
	cflags_fast = -O2 -Zi -MD
	cflags_small = -O1s -Zi -MD
	# -GL [whole program optimization] in 'fast' and 'small' breaks compilation for some reason

	ifeq ($(mode),debug)
		cflags +=
		lflags +=
	endif
	ifeq ($(mode),debug-fast)
		cflags += -DNDEBUG
		lflags +=
	endif
	ifeq ($(mode),stress-major)
		cflags +=
		lflags +=
	endif
	ifeq ($(mode),fast)
		cflags +=
		lflags +=
	endif
	# -LTCG is needed only if -GL is used
	ifeq ($(mode),fast)
		cflags += -DNDEBUG
		lflags += -LTCG
		arflags +=
	endif
	ifeq ($(mode),small)
		cflags += -DNDEBUG
		lflags += -LTCG
		arflags +=
	endif

	strip = :
endif

ifdef msvc
	target-format = pe
	windows-path = $(native-path)
	windows-java-home := $(shell $(windows-path) "$(JAVA_HOME)")
	zlib := $(shell $(windows-path) "$(win32)/msvc")
	ms_cl_compiler = regular
	as = $(build-cc)
	cxx = "$(msvc)/BIN/cl.exe"
	cc = $(cxx)
	ld = "$(msvc)/BIN/link.exe"
	mt = "mt.exe"
	ar = "$(msvc)/BIN/lib.exe"
	manifest-flags = -MANIFEST -MANIFESTFILE:$(@).manifest
	cflags = -nologo -DAVIAN_VERSION=\"$(version)\" -D_JNI_IMPLEMENTATION_ \
		-DUSE_ATOMIC_OPERATIONS -DAVIAN_JAVA_HOME=\"$(javahome)\" \
		-DAVIAN_EMBED_PREFIX=\"$(embed-prefix)\" \
		-Fd$(build)/$(name).pdb -I"$(zlib)/include" -I$(src) -I$(classpath-src) \
		-I"$(build)" -Iinclude \
		-I"$(windows-java-home)/include" -I"$(windows-java-home)/include/win32" \
		-DTARGET_BYTES_PER_WORD=$(pointer-size)

	ifneq ($(lzma),)
		cflags += -I$(shell $(windows-path) "$(lzma)")
	endif

	shared = -dll
	lflags = -nologo -LIBPATH:"$(zlib)/lib" -DEFAULTLIB:ws2_32 \
		-DEFAULTLIB:zlib -DEFAULTLIB:user32 -MANIFEST -debug
	output = -Fo$(1)

	cflags_debug = -Od -Zi -MDd
	cflags_debug_fast = -Od -Zi -DNDEBUG
	cflags_fast = -O2 -GL -Zi -DNDEBUG
	cflags_small = -O1s -Zi -GL -DNDEBUG
	ifeq ($(mode),fast)
		lflags += -LTCG
	endif
	ifeq ($(mode),small)
		lflags += -LTCG
	endif

	use-lto = false
	strip = :
endif

ifeq ($(mode),debug)
	optimization-cflags = $(cflags_debug)
	converter-cflags += $(cflags_debug)
	strip = :
endif
ifeq ($(mode),debug-fast)
	optimization-cflags = $(cflags_debug_fast) -DNDEBUG
	strip = :
endif
ifeq ($(mode),stress)
	optimization-cflags = $(cflags_stress) -DVM_STRESS
	strip = :
endif
ifeq ($(mode),stress-major)
	optimization-cflags = $(cflags_stress_major) -DVM_STRESS -DVM_STRESS_MAJOR
	strip = :
endif
ifeq ($(mode),fast)
	optimization-cflags = $(cflags_fast) -DNDEBUG
endif
ifeq ($(mode),small)
	optimization-cflags = $(cflags_small) -DNDEBUG
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

ifndef ms_cl_compiler
ifneq ($(kernel),darwin)
ifeq ($(arch),i386)
# this is necessary to support __sync_bool_compare_and_swap:
	cflags += -march=i586
	lflags += -march=i586
endif
endif
endif

c-objects = $(foreach x,$(1),$(patsubst $(2)/%.c,$(3)/%.o,$(x)))
cpp-objects = $(foreach x,$(1),$(patsubst $(2)/%.cpp,$(3)/%.o,$(x)))
cc-objects = $(foreach x,$(1),$(patsubst $(2)/%.cc,$(3)/%.o,$(x)))
asm-objects = $(foreach x,$(1),$(patsubst $(2)/%.$(asm-format),$(3)/%-asm.o,$(x)))
java-classes = $(foreach x,$(1),$(patsubst $(2)/%.java,$(3)/%.class,$(x)))
noop-files = $(foreach x,$(1),$(patsubst $(2)/%,$(3)/%,$(x)))

generated-code = \
	$(build)/type-enums.cpp \
	$(build)/type-declarations.cpp \
	$(build)/type-constructors.cpp \
	$(build)/type-initializations.cpp \
	$(build)/type-java-initializations.cpp \
	$(build)/type-name-initializations.cpp \
	$(build)/type-maps.cpp

vm-depends := $(generated-code) \
	$(shell find src include -name '*.h' -or -name '*.inc.cpp')

vm-sources = \
	$(src)/system/$(system).cpp \
	$(wildcard $(src)/system/$(system)/*.cpp) \
	$(src)/finder.cpp \
	$(src)/machine.cpp \
	$(src)/util.cpp \
	$(src)/heap/heap.cpp \
	$(src)/$(process).cpp \
	$(src)/classpath-$(classpath).cpp \
	$(src)/builtin.cpp \
	$(src)/jnienv.cpp \
	$(src)/process.cpp \
	$(src)/heapdump.cpp

vm-asm-sources = $(src)/$(arch).$(asm-format)

target-asm = $(asm)

build-embed = $(build)/embed
build-embed-loader = $(build)/embed-loader

embed-loader-sources = $(src)/embedded-loader.cpp
embed-loader-objects = $(call cpp-objects,$(embed-loader-sources),$(src),$(build-embed-loader))

embed-sources = $(src)/embed.cpp
embed-objects = $(call cpp-objects,$(embed-sources),$(src),$(build-embed))

compiler-sources = \
	$(src)/codegen/compiler.cpp \
	$(wildcard $(src)/codegen/compiler/*.cpp) \
	$(src)/debug-util.cpp \
	$(src)/codegen/runtime.cpp \
	$(src)/codegen/targets.cpp \
	$(src)/util/fixed-allocator.cpp

x86-assembler-sources = $(wildcard $(src)/codegen/target/x86/*.cpp)

arm-assembler-sources = $(wildcard $(src)/codegen/target/arm/*.cpp)

all-assembler-sources = \
	$(x86-assembler-sources) \
	$(arm-assembler-sources)

native-assembler-sources = $($(target-asm)-assembler-sources)

all-codegen-target-sources = \
	$(compiler-sources) \
	$(native-assembler-sources)

ifeq ($(process),compile)
	vm-sources += $(compiler-sources)

	ifeq ($(codegen-targets),native)
		vm-sources += $(native-assembler-sources)
	endif
	ifeq ($(codegen-targets),all)
		ifneq (,$(filter arm arm64,$(arch)))
			# The x86 jit has a dependency on the x86 assembly code,
			# and thus can't be successfully built on non-x86 platforms.
			vm-sources += $(native-assembler-sources)
		else
			vm-sources += $(all-assembler-sources)
		endif
	endif

	vm-asm-sources += $(src)/compile-$(arch).$(asm-format)
endif
ifeq ($(aot-only),true)
	cflags += -DAVIAN_AOT_ONLY
endif

vm-cpp-objects = $(call cpp-objects,$(vm-sources),$(src),$(build))
all-codegen-target-objects = $(call cpp-objects,$(all-codegen-target-sources),$(src),$(build))
vm-asm-objects = $(call asm-objects,$(vm-asm-sources),$(src),$(build))
vm-objects = $(vm-cpp-objects) $(vm-asm-objects)

heapwalk-sources = $(src)/heapwalk.cpp
heapwalk-objects = \
	$(call cpp-objects,$(heapwalk-sources),$(src),$(build))

unittest-objects = $(call cpp-objects,$(unittest-sources),$(unittest),$(build)/unittest)

vm-heapwalk-objects = $(heapwalk-objects)

ifeq ($(tails),true)
	cflags += -DAVIAN_TAILS
endif

ifeq ($(continuations),true)
	cflags += -DAVIAN_CONTINUATIONS
	asmflags += -DAVIAN_CONTINUATIONS
endif

bootimage-generator-sources = $(src)/tools/bootimage-generator/main.cpp $(src)/util/arg-parser.cpp $(stub-sources)

ifneq ($(lzma),)
	bootimage-generator-sources += $(src)/lzma-encode.cpp
endif

bootimage-generator-objects = \
	$(call cpp-objects,$(bootimage-generator-sources),$(src),$(build))
bootimage-generator = $(build)/bootimage-generator

ifneq ($(mode),fast)
	host-vm-options := -$(mode)
endif

host-vm = build/$(build-platform)-$(build-arch)-interpret$(host-vm-options)/$(so-prefix)jvm$(so-suffix)

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
	$(src)/tools/type-generator/main.cpp \
	$(src)/system/$(build-system).cpp \
	$(wildcard $(src)/system/$(build-system)/*.cpp) \
	$(src)/finder.cpp \
	$(src)/util/arg-parser.cpp

ifneq ($(lzma),)
	common-cflags += -I$(lzma) -DAVIAN_USE_LZMA

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

	lzma-build-cflags = -D_7ZIP_ST -D__STDC_CONSTANT_MACROS \
		-fno-exceptions -fPIC -I$(lzma)/C

	lzma-cflags = $(lzma-build-cflags) $(classpath-extra-cflags)

	lzma-encoder-sources = \
		$(src)/lzma/main.cpp

	lzma-encoder-objects = \
		$(call cpp-objects,$(lzma-encoder-sources),$(src),$(build))

	lzma-encoder-lzma-sources = $(lzma-encode-sources) $(lzma-decode-sources)

	lzma-encoder-lzma-objects = \
		$(call generator-c-objects,$(lzma-encoder-lzma-sources),$(lzma)/C,$(build))

	lzma-loader = $(build)/lzma/load.o

	lzma-library = $(build)/libavian-lzma.a
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

all-depends = $(shell find include -name '*.h')

object-writer-depends = $(shell find $(src)/tools/object-writer -name '*.h')
object-writer-sources = $(shell find $(src)/tools/object-writer -name '*.cpp')
object-writer-objects = $(call cpp-objects,$(object-writer-sources),$(src),$(build))

binary-to-object-depends = $(shell find $(src)/tools/binary-to-object/ -name '*.h')
binary-to-object-sources = $(shell find $(src)/tools/binary-to-object/ -name '*.cpp')
binary-to-object-objects = $(call cpp-objects,$(binary-to-object-sources),$(src),$(build))

converter-sources = $(object-writer-sources)

converter-tool-depends = $(binary-to-object-depends) $(all-depends)
converter-tool-sources = $(binary-to-object-sources)

converter-objects = $(call cpp-objects,$(converter-sources),$(src),$(build))
converter-tool-objects = $(call cpp-objects,$(converter-tool-sources),$(src),$(build))
converter = $(build)/binaryToObject/binaryToObject

static-library = $(build)/$(static-prefix)$(name)$(static-suffix)
executable = $(build)/$(name)${exe-suffix}
dynamic-library = $(build)/$(so-prefix)jvm$(so-suffix)
executable-dynamic = $(build)/$(name)-dynamic$(exe-suffix)

unittest-executable = $(build)/$(name)-unittest${exe-suffix}

ifneq ($(classpath),avian)
# Assembler, ConstantPool, and Stream are not technically needed for a
# working build, but we include them since our Subroutine test uses
# them to synthesize a class:
	classpath-sources := \
		$(classpath-src)/avian/Addendum.java \
		$(classpath-src)/avian/AnnotationInvocationHandler.java \
		$(classpath-src)/avian/Assembler.java \
		$(classpath-src)/avian/Callback.java \
		$(classpath-src)/avian/Cell.java \
		$(classpath-src)/avian/ClassAddendum.java \
		$(classpath-src)/avian/Classes.java \
		$(classpath-src)/avian/Code.java \
		$(classpath-src)/avian/ConstantPool.java \
		$(classpath-src)/avian/Continuations.java \
		$(classpath-src)/avian/FieldAddendum.java \
		$(classpath-src)/avian/Function.java \
		$(classpath-src)/avian/IncompatibleContinuationException.java \
		$(classpath-src)/avian/InnerClassReference.java \
		$(classpath-src)/avian/Machine.java \
		$(classpath-src)/avian/MethodAddendum.java \
		$(classpath-src)/avian/Pair.java \
		$(classpath-src)/avian/Singleton.java \
		$(classpath-src)/avian/Stream.java \
		$(classpath-src)/avian/SystemClassLoader.java \
		$(classpath-src)/avian/VMClass.java \
		$(classpath-src)/avian/VMField.java \
		$(classpath-src)/avian/VMMethod.java \
		$(classpath-src)/avian/avianvmresource/Handler.java \
		$(classpath-src)/avian/file/Handler.java \
		$(classpath-src)/java/lang/invoke/MethodHandle.java \
		$(classpath-src)/java/lang/invoke/MethodHandles.java \
		$(classpath-src)/java/lang/invoke/MethodType.java \
		$(classpath-src)/java/lang/invoke/LambdaMetafactory.java \
		$(classpath-src)/java/lang/invoke/LambdaConversionException.java \
		$(classpath-src)/java/lang/invoke/CallSite.java

	ifeq ($(openjdk),)
		classpath-sources := $(classpath-sources) \
			$(classpath-src)/dalvik/system/BaseDexClassLoader.java \
			$(classpath-src)/libcore/reflect/AnnotationAccess.java \
			$(classpath-src)/sun/reflect/ConstantPool.java \
			$(classpath-src)/java/net/ProtocolFamily.java \
			$(classpath-src)/java/net/StandardProtocolFamily.java \
			$(classpath-src)/sun/misc/Cleaner.java \
			$(classpath-src)/sun/misc/Unsafe.java \
			$(classpath-src)/java/lang/Object.java \
			$(classpath-src)/java/lang/Class.java \
			$(classpath-src)/java/lang/ClassLoader.java \
			$(classpath-src)/java/lang/Package.java \
			$(classpath-src)/java/lang/reflect/Proxy.java \
			$(classpath-src)/java/lang/reflect/Field.java \
			$(classpath-src)/java/lang/reflect/SignatureParser.java \
			$(classpath-src)/java/lang/reflect/Constructor.java \
			$(classpath-src)/java/lang/reflect/AccessibleObject.java \
			$(classpath-src)/java/lang/reflect/Method.java
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
test-sources := $(wildcard $(test)/*.java)

# HACK ALERT!!
# This test fails regularly on travis, but nowhere else.  We have yet to spend the time to investigate that test, so we disable it on PR builds.
# Note: travis set TRAVIS_PULL_REQUEST environment variable to either the PR number or "false", as appropriate
ifeq (false,$(TRAVIS_PULL_REQUEST))
else
ifeq (,$(TRAVIS_PULL_REQUEST))
else
	test-sources := $(subst $(test)/Trace.java,,$(test-sources))
endif
endif

ifeq (7,$(java-version))
	test-sources := $(subst $(test)/InvokeDynamic.java,,$(test-sources))
	test-sources := $(subst $(test)/Interfaces.java,,$(test-sources))
endif

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

unittest-sources = \
	$(wildcard $(unittest)/*.cpp) \
	$(wildcard $(unittest)/util/*.cpp) \
	$(wildcard $(unittest)/codegen/*.cpp)

unittest-depends = \
	$(wildcard $(unittest)/*.h)

ifeq ($(continuations),true)
	continuation-tests = \
		extra.ComposableContinuations \
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

ifeq ($(target-arch),arm)
	cflags += -DAVIAN_TARGET_ARCH=AVIAN_ARCH_ARM
endif

ifeq ($(target-arch),arm64)
	cflags += -DAVIAN_TARGET_ARCH=AVIAN_ARCH_ARM64
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

test-flags = -Djava.library.path=$(build) \
	-cp '$(build)/test$(target-path-separator)$(build)/extra-dir'

test-args = $(test-flags) $(input)

ifneq ($(filter linux windows macosx,$(platform)),)
eclipse-exec-env = eclipse-ee
eclipse-jdk-dir = $(build)/eclipse/jdk
eclipse-ee-file = $(eclipse-jdk-dir)/avian.ee
eclipse-bin-dir = $(eclipse-jdk-dir)/bin
eclipse-lib-dir = $(eclipse-jdk-dir)/jre/lib
eclipse-src-dir = $(eclipse-jdk-dir)/src
define eclipse-ee-descriptor
# An Eclipse execution environment for the Avian JVM\
\n-Dee.executable=bin/java${exe-suffix}\
\n-Dee.bootclasspath=jre/lib/rt.jar\
\n-Dee.language.level=1.7\
\n-Dee.name=$(name)-$(version)-$(platform)-$(arch)$(options)\
\n-Dee.src=src\
\n-Dee.javadoc=file://$${ee.home}/doc\
\n-Djava.home=$${ee.home}\n
endef
else
eclipse-exec-env =
endif

.PHONY: build
ifneq ($(supports_avian_executable),false)
build: $(static-library) $(executable) $(dynamic-library) $(lzma-library) \
	$(lzma-encoder) $(executable-dynamic) $(classpath-dep) $(test-dep) \
	$(test-extra-dep) $(embed) $(build)/classpath.jar $(eclipse-exec-env)
else
build: $(static-library) $(dynamic-library) $(lzma-library) \
	$(lzma-encoder) $(classpath-dep) $(test-dep) \
	$(test-extra-dep) $(embed) $(build)/classpath.jar
endif

$(test-dep): $(classpath-dep)

$(test-extra-dep): $(classpath-dep)

.PHONY: run
run: build
	$(library-path) $(test-executable) $(test-args)

.PHONY: debug
debug: build
	$(library-path) $(db) $(test-executable) $(test-args)

.PHONY: vg
vg: build
	$(library-path) $(vg) $(test-executable) $(test-args)

.PHONY: test
test: build-test run-test

.PHONY: build-test
build-test: build $(build)/run-tests.sh $(build)/test.sh $(unittest-executable)

.PHONY: run-test
run-test:
ifneq ($(remote-test),true)
	/bin/sh $(build)/run-tests.sh
else
	@echo "running tests on $(remote-test-user)@$(remote-test-host):$(remote-test-port), in $(remote-test-dir)"
	rsync $(build) -rav --exclude '*.o' --rsh="ssh -p$(remote-test-port)" $(remote-test-user)@$(remote-test-host):$(remote-test-dir)
	ssh -p$(remote-test-port) $(remote-test-user)@$(remote-test-host) sh "$(remote-test-dir)/$(platform)-$(arch)$(options)/run-tests.sh"
endif

.PHONY: jdk-test
jdk-test: $(test-dep) $(build)/classpath.jar $(build)/jdk-run-tests.sh $(build)/test.sh
	/bin/sh $(build)/jdk-run-tests.sh

.PHONY: tarball
tarball:
	@echo "creating build/avian-$(version).tar.bz2"
	@mkdir -p build
	(cd .. && tar --exclude=build --exclude=cmake-build --exclude=distrib \
		--exclude=lib --exclude='.*' --exclude='*~' \
		-cjf avian/build/avian-$(version).tar.bz2 avian)

.PHONY: clean-current
clean-current:
	@echo "removing $(build)"
	rm -rf $(build)

.PHONY: clean
clean:
	@echo "removing build directories"
	rm -rf build cmake-build distrib lib

.PHONY: eclipse-ee
ifneq ($(strip $(eclipse-exec-env)),)
eclipse-ee: $(eclipse-ee-file) $(eclipse-lib-dir)/rt.jar $(eclipse-bin-dir)/java${exe-suffix} $(eclipse-src-dir)

$(eclipse-bin-dir):
	@mkdir -p $(@)

$(eclipse-lib-dir):
	@mkdir -p $(@)

$(eclipse-jdk-dir):
	@mkdir -p $(@)

$(eclipse-ee-file): $(eclipse-jdk-dir)
	@echo "writing eclipse execution environment descriptor to $(@)"
	@printf '${eclipse-ee-descriptor}' > $(@)

$(eclipse-src-dir): $(eclipse-jdk-dir)
	@echo "symlinking classpath for $(@)"
	@ln -sf ../../../../classpath $(@)

$(eclipse-bin-dir)/java$(exe-suffix): $(eclipse-bin-dir) $(executable)
	@echo "symlinking $(executable) for $(@)"
	@ln -sf ../../../$(name)${exe-suffix} $(@)

$(eclipse-lib-dir)/rt.jar: $(eclipse-lib-dir) $(build)/classpath.jar
	@echo "symlinking $(build)/classpath.jar for $(@)"
	@ln -sf ../../../../classpath.jar $(@)
else
eclipse-ee:
	$(error "Eclipse execution environment for platform '$(platform)' is not supported")
endif

ifeq ($(continuations),true)
$(build)/compile-x86-asm.o: $(src)/continuations-x86.$(asm-format)
endif

$(build)/run-tests.sh: $(test-classes) makefile $(build)/extra-dir/multi-classpath-test.txt $(build)/test/multi-classpath-test.txt
	echo 'cd $$(dirname $$0)' > $(@)
	echo "sh ./test.sh 2>/dev/null \\" >> $(@)
	echo "$(shell echo $(library-path) | sed 's|$(build)|\.|g') ./$(name)-unittest${exe-suffix} ./$(notdir $(test-executable)) $(mode) \"-Djava.library.path=. -cp test$(target-path-separator)extra-dir\" \\" >> $(@)
	echo "$(call class-names,$(test-build),$(filter-out $(test-support-classes), $(test-classes))) \\" >> $(@)
	echo "$(continuation-tests) $(tail-tests)" >> $(@)

$(build)/jdk-run-tests.sh: $(test-classes) makefile $(build)/extra-dir/multi-classpath-test.txt $(build)/test/multi-classpath-test.txt
	echo 'cd $$(dirname $$0)' > $(@)
	echo "sh ./test.sh 2>/dev/null \\" >> $(@)
	echo "'' true $(JAVA_HOME)/bin/java $(mode) \"-Xmx128m -Djava.library.path=. -cp test$(path-separator)extra-dir$(path-separator)classpath\" \\" >> $(@)
	echo "$(call class-names,$(test-build),$(filter-out $(test-support-classes), $(test-classes))) \\" >> $(@)
	echo "$(continuation-tests) $(tail-tests)" >> $(@)

$(build)/extra-dir/multi-classpath-test.txt:
	mkdir -p $(build)/extra-dir
	echo "$@" > $@

$(build)/test/multi-classpath-test.txt:
	echo "$@" > $@

$(build)/test.sh: $(test)/test.sh
	cp $(<) $(@)

gen-arg = $(shell echo $(1) | sed -e 's:$(build)/type-\(.*\)\.cpp:\1:')
$(generated-code): %.cpp: $(src)/types.def $(generator) $(classpath-dep)
	@echo "generating $(@)"
	@mkdir -p $(dir $(@))
	$(generator) -cp $(boot-classpath) -i $(<) -o $(@) -t $(call gen-arg,$(@))

$(classpath-dep): $(classpath-sources) $(classpath-jar-dep)
	@echo "compiling classpath classes"
	@mkdir -p $(classpath-build)
	$(javac) -source 1.$(java-version) -target 1.$(java-version) \
		-d $(classpath-build) -bootclasspath $(boot-classpath) \
		$(classpath-sources)
	@touch $(@)

$(build)/android-src/%.cpp: $(luni-native)/%.cpp
	cp $(<) $(@)

$(build)/android-src/%.cpp: $(libnativehelper-native)/%.cpp
	cp $(<) $(@)

$(build)/android-src/%.cpp: $(crypto-native)/%.cpp
	cp $(<) $(@)

$(build)/android-src/%.cpp: $(libziparchive-native)/%.cc
	cp $(<) $(@)

$(build)/android-src/%.cpp: $(libutils-native)/%.cpp
	cp $(<) $(@)

$(build)/%.o: $(build)/android-src/%.cpp $(build)/android.dep
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(android-cflags) $(classpath-extra-cflags) -c \
		$$($(windows-path) $(<)) $(call output,$(@))

$(build)/android.dep: $(luni-javas) $(dalvik-javas) $(libart-javas) \
		$(xml-javas) $(okhttp-android-javas) $(okhttp-javas) $(okio-javas) \
		$(bcpkix-javas) $(bcprov-javas) $(luni-nonjavas) $(crypto-javas) $(crypto-platform-javas)
	@echo "compiling luni classes"
	@mkdir -p $(classpath-build)
	@mkdir -p $(build)/android
	@mkdir -p $(build)/android-src/external/fdlibm
	@mkdir -p $(build)/android-src/libexpat
	cp $(android)/external/fdlibm/fdlibm.h $(build)/android-src/external/fdlibm/
	cp $(android)/external/expat/lib/expat*.h $(build)/android-src/libexpat/
	cp -a $(luni-java)/* $(xml-java)/* $(okhttp-android-java)/* $(okhttp-java)/* $(okio-java)/* $(bcpkix-java)/* $(bcprov-java)/* $(build)/android-src/
	rm $(call noop-files,$(luni-blacklist),$(luni-java),$(build)/android-src)
	(cd $(dalvik-java) && \
			$(jar) c $(call noop-files,$(dalvik-javas),$(dalvik-java),.)) \
		| (cd $(build)/android-src && $(jar) x)
	(cd $(libart-java) && \
			$(jar) c $(call noop-files,$(libart-javas),$(libart-java),.)) \
		| (cd $(build)/android-src && $(jar) x)
	(cd $(crypto-java) && \
			$(jar) c $(call noop-files,$(crypto-javas),$(crypto-java),.)) \
		| (cd $(build)/android-src && $(jar) x)
	(cd $(crypto-platform-java) && \
			$(jar) c $(call noop-files,$(crypto-platform-javas),$(crypto-platform-java),.)) \
		| (cd $(build)/android-src && $(jar) x)
	(cd $(classpath-src) && \
			$(jar) c $(call noop-files,$(classpath-sources),$(classpath-src),.)) \
		| (cd $(build)/android-src && $(jar) x)
#	(cd android && $(jar) c *)	| (cd $(build)/android-src && $(jar) x)
	find $(build)/android-src -name '*.java' > $(build)/android.txt
	$(javac) -Xmaxerrs 1000 -d $(build)/android @$(build)/android.txt
	rm $(build)/android/sun/misc/Unsafe* \
		$(build)/android/java/lang/reflect/Proxy*
	for x in $(luni-copied-nonjavas); \
		do cp $(luni-java)$${x} $(build)/android$${x} ; \
	done
	# fix security.properties - get rid of "com.android" in front of classes starting with "org"
	sed -i -e 's/\(.*=\)com\.android\.\(org\..*\)/\1\2/g' \
		$(build)/android/java/security/security.properties
	chmod +w $(build)/android/java/security/security.properties
	cp -r $(build)/android/* $(classpath-build)
	@touch $(@)

$(test-build)/%.class: $(test)/%.java
	@echo $(<)

$(test-dep): $(test-sources) $(test-library)
	@echo "compiling test classes"
	@mkdir -p $(test-build)
	files="$(shell $(MAKE) -s --no-print-directory build=$(build) $(test-classes))"; \
	if test -n "$${files}"; then \
		$(javac) -source 1.$(java-version) -target 1.$(java-version) \
			-classpath $(test-build) -d $(test-build) -bootclasspath $(boot-classpath) $${files}; \
	fi
	$(javac) -source 1.2 -target 1.1 -XDjsrlimit=0 -d $(test-build) \
		-bootclasspath $(boot-classpath) test/Subroutine.java
	@touch $(@)

$(test-extra-dep): $(test-extra-sources)
	@echo "compiling extra test classes"
	@mkdir -p $(test-build)
	files="$(shell $(MAKE) -s --no-print-directory build=$(build) $(test-extra-classes))"; \
	if test -n "$${files}"; then \
		$(javac) -source 1.$(java-version) -target 1.$(java-version) \
			-d $(test-build) -bootclasspath $(boot-classpath) $${files}; \
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
	$(as) $(asmflags) $(call asm-output,$(@)) $(call asm-input,$(<))
endef

define compile-unittest-object
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(cflags) -c $$($(windows-path) $(<)) -I$(unittest) $(call output,$(@))
endef

$(vm-cpp-objects): $(build)/%.o: $(src)/%.cpp $(vm-depends)
	$(compile-object)

ifeq ($(process),interpret)
$(all-codegen-target-objects): $(build)/%.o: $(src)/%.cpp $(vm-depends)
	$(compile-object)
endif

$(unittest-objects): $(build)/unittest/%.o: $(unittest)/%.cpp $(vm-depends) $(unittest-depends)
	$(compile-unittest-object)

$(test-cpp-objects): $(test-build)/%.o: $(test)/%.cpp $(vm-depends)
	$(compile-object)

$(test-library): $(test-cpp-objects)
	@echo "linking $(@)"
ifdef ms_cl_compiler
	$(ld) $(shared) $(lflags) $(^) -out:$(@) \
		-debug -PDB:$(subst $(so-suffix),.pdb,$(@)) \
		-IMPLIB:$(test-build)/$(name).lib $(manifest-flags)
ifdef mt
	$(mt) -nologo -manifest $(@).manifest -outputresource:"$(@);2"
endif
else
	$(ld) $(^) $(shared) $(lflags) -o $(@)
endif

ifdef embed
$(embed): $(embed-objects) $(embed-loader-o)
	@echo "building $(embed)"
ifdef ms_cl_compiler
	$(ld) $(lflags) $(^) -out:$(@) \
		-debug -PDB:$(subst $(exe-suffix),.pdb,$(@)) $(manifest-flags)
ifdef mt
	$(mt) -nologo -manifest $(@).manifest -outputresource:"$(@);1"
endif
else
	$(cxx) $(^) $(lflags) $(static) $(call output,$(@))
endif

$(build-embed)/%.o: $(src)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(cflags) -c $(<) $(call output,$(@))

$(embed-loader-o): $(embed-loader) $(converter)
	@mkdir -p $(dir $(@))
	$(converter) $(<) $(@) _binary_loader_start \
		_binary_loader_end $(target-format) $(arch)

$(embed-loader): $(embed-loader-objects) $(vm-objects) $(classpath-objects) \
		$(heapwalk-objects) $(lzma-decode-objects)
ifdef ms_cl_compiler
	$(ld) $(lflags) $(^) -out:$(@) \
		-debug -PDB:$(subst $(exe-suffix),.pdb,$(@)) $(manifest-flags)
ifdef mt
	$(mt) -nologo -manifest $(@).manifest -outputresource:"$(@);1"
endif
else
	$(dlltool) -z $(addsuffix .def,$(basename $(@))) $(^)
	$(dlltool) -d $(addsuffix .def,$(basename $(@))) -e $(addsuffix .exp,$(basename $(@)))
	$(ld) $(addsuffix .exp,$(basename $(@))) $(^) \
		$(lflags) $(classpath-lflags) $(bootimage-lflags) -o $(@)
endif
	$(strip) $(strip-all) $(@)

$(build-embed-loader)/%.o: $(src)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(cflags) -c $(<) $(call output,$(@))
endif

$(build)/%.o: $(lzma)/C/%.c
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cc) $(lzma-cflags) -c $$($(windows-path) $(<)) $(call output,$(@))

$(vm-asm-objects): $(build)/%-asm.o: $(src)/%.$(asm-format)
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

$(object-writer-objects) $(binary-to-object-objects): $(build)/%.o: $(src)/%.cpp $(binary-to-object-depends) $(object-writer-depends) $(all-depends)
	@mkdir -p $(dir $(@))
	$(build-cxx) $(converter-cflags) -c $(<) -o $(@)

$(converter): $(converter-objects) $(converter-tool-objects)
	@mkdir -p $(dir $(@))
	$(build-cc) $(^) -g -o $(@)

$(lzma-encoder-objects): $(build)/lzma/%.o: $(src)/lzma/%.cpp
	@mkdir -p $(dir $(@))
	$(build-cxx) $(lzma-build-cflags) -c $(<) -o $(@)

$(lzma-encoder): $(lzma-encoder-objects) $(lzma-encoder-lzma-objects)
	$(build-cc) $(^) -g -o $(@)

$(lzma-library): $(lzma-loader) $(lzma-decode-objects)
	@echo "creating $(@)"
	@rm -rf $(build)/libavian-lzma
	@mkdir -p $(build)/libavian-lzma
	rm -rf $(@)
	for x in $(^); \
		do cp $${x} $(build)/libavian-lzma/$$(echo $${x} | sed s:/:_:g); \
	done
ifdef ms_cl_compiler
	$(ar) $(arflags) $(build)/libavian-lzma/*.o -out:$(@)
else
	$(ar) cru $(@) $(build)/libavian-lzma/*.o
	$(ranlib) $(@)
endif

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
	$(build-cc) -DPOINTER_SIZE=$(pointer-size) -O0 -g3 $(lzma-build-cflags) \
		-c $(<) -o $(@)

$(jni-objects): $(build)/%.o: $(classpath-src)/%.cpp $(vm-depends)
	$(compile-object)

$(static-library): $(vm-objects) $(classpath-objects) $(vm-heapwalk-objects) \
		$(javahome-object) $(boot-javahome-object) $(lzma-decode-objects)
	@echo "creating $(@)"
	@rm -rf $(build)/libavian
	@mkdir -p $(build)/libavian
	rm -rf $(@)
	for x in $(^); \
		do cp $${x} $(build)/libavian/$$(echo $${x} | sed s:/:_:g); \
	done
ifdef ms_cl_compiler
	$(ar) $(arflags) $(build)/libavian/*.o -out:$(@)
else
	$(ar) cru $(@) $(build)/libavian/*.o
	$(ranlib) $(@)
endif

$(bootimage-object) $(codeimage-object): $(bootimage-generator) \
		$(classpath-jar-dep) $(test-dep)
	@echo "generating bootimage and codeimage binaries from $(classpath-build) using $(<)"
	$(<) -cp $(bootimage-classpath) -bootimage $(bootimage-object) -codeimage $(codeimage-object) \
		-bootimage-symbols $(bootimage-symbols) \
		-codeimage-symbols $(codeimage-symbols) \
		-hostvm $(host-vm)

executable-objects = $(vm-objects) $(classpath-objects) $(driver-object) \
	$(vm-heapwalk-objects) $(boot-object) $(vm-classpath-objects) \
	$(javahome-object) $(boot-javahome-object) $(lzma-decode-objects)

unittest-executable-objects = $(unittest-objects) $(vm-objects) \
	$(vm-heapwalk-objects) $(build)/util/arg-parser.o $(stub-objects) \
	$(lzma-decode-objects)

ifeq ($(process),interpret)
	unittest-executable-objects += $(all-codegen-target-objects)
endif

# apparently, make does poorly with ifs inside of defines, and indented defines.
# I suggest re-indenting the following before making edits (and unindenting afterwards):
ifneq ($(platform),windows)
define link-executable
	@echo linking $(@)
	$(ld) $(^) $(rdynamic) $(lflags) $(classpath-lflags) $(bootimage-lflags) \
		-o $(@)
endef
else
ifdef ms_cl_compiler
ifdef mt
define link-executable
	@echo linking $(@)
	$(ld) $(lflags) $(^) -out:$(@) \
		-debug -PDB:$(subst $(exe-suffix),.pdb,$(@)) $(manifest-flags)
	$(mt) -nologo -manifest $(@).manifest -outputresource:"$(@);1"
endef
else
define link-executable
	@echo linking $(@)
	$(mt) -nologo -manifest $(@).manifest -outputresource:"$(@);1"
endef
endif
else
define link-executable
	@echo linking $(@)
	$(dlltool) -z $(@).def $(^)
	$(dlltool) -d $(@).def -e $(@).exp
	$(ld) $(@).exp $(^) $(lflags) $(classpath-lflags) -o $(@)
endef
endif
endif

$(executable): $(executable-objects)
	$(link-executable)

$(unittest-executable): $(unittest-executable-objects)
	$(link-executable)

$(bootimage-generator): $(bootimage-generator-objects) $(vm-objects)
	echo building $(bootimage-generator) arch=$(build-arch) platform=$(bootimage-platform)
	$(MAKE) process=interpret \
		bootimage= \
		bootimage-test= \
		mode=$(mode) \
		platform=$(bootimage-platform) \
		arch=$(build-arch)
	$(MAKE) mode=$(mode) \
		build=$(host-build-root) \
		arch=$(build-arch) \
		aot-only=false \
		target-arch=$(arch) \
		armv6=$(armv6) \
		platform=$(bootimage-platform) \
		target-format=$(target-format) \
		android=$(android) \
		openjdk=$(openjdk) \
		openjdk-src=$(openjdk-src) \
		bootimage-generator= \
		build-bootimage-generator=$(bootimage-generator) \
		target-cflags="$(bootimage-cflags)" \
		target-asm=$(asm) \
		$(bootimage-generator)

$(build-bootimage-generator): \
		$(vm-objects) $(classpath-object) \
		$(heapwalk-objects) $(bootimage-generator-objects) $(converter-objects) \
		$(lzma-decode-objects) $(lzma-encode-objects)
	@echo "linking $(@)"
ifeq ($(platform),windows)
ifdef ms_cl_compiler
	$(ld) $(bootimage-generator-lflags) $(lflags) $(^) -out:$(@) \
		-debug -PDB:$(subst $(exe-suffix),.pdb,$(@)) $(manifest-flags)
ifdef mt
	$(mt) -nologo -manifest $(@).manifest -outputresource:"$(@);1"
endif
else
	$(dlltool) -z $(@).def $(^)
	$(dlltool) -d $(@).def -e $(@).exp
	$(ld) $(@).exp $(^) $(bootimage-generator-lflags) $(lflags) -o $(@)
endif
else
	$(ld) $(^) $(rdynamic) $(bootimage-generator-lflags) $(lflags) -o $(@)
endif

$(dynamic-library): $(vm-objects) $(dynamic-object) $(classpath-objects) \
		$(vm-heapwalk-objects) $(boot-object) $(vm-classpath-objects) \
		$(classpath-libraries) $(javahome-object) $(boot-javahome-object) \
		$(lzma-decode-objects)
	@echo "linking $(@)"
ifdef ms_cl_compiler
	$(ld) $(shared) $(lflags) $(^) -out:$(@) \
		-debug -PDB:$(subst $(so-suffix),.pdb,$(@)) \
		-IMPLIB:$(subst $(so-suffix),.lib,$(@)) $(manifest-flags)
ifdef mt
	$(mt) -nologo -manifest $(@).manifest -outputresource:"$(@);2"
endif
else
	$(ld) $(^) $(version-script-flag) $(soname-flag) \
		$(shared) $(lflags) $(classpath-lflags) $(bootimage-lflags) \
		-o $(@)
endif
	$(strip) $(strip-all) $(@)

# todo: the $(no-lto) flag below is due to odd undefined reference errors on
# Ubuntu 11.10 which may be fixable without disabling LTO.
$(executable-dynamic): $(driver-dynamic-objects) $(dynamic-library)
	@echo "linking $(@)"
ifdef ms_cl_compiler
	$(ld) $(lflags) -LIBPATH:$(build) -DEFAULTLIB:$(name) \
		-debug -PDB:$(subst $(exe-suffix),.pdb,$(@)) \
		$(driver-dynamic-objects) -out:$(@) $(manifest-flags)
ifdef mt
	$(mt) -nologo -manifest $(@).manifest -outputresource:"$(@);1"
endif
else
	$(ld) $(driver-dynamic-objects) -L$(build) -ljvm $(lflags) $(no-lto) $(rpath) -o $(@)
endif
	$(strip) $(strip-all) $(@)

$(generator): $(generator-objects) $(generator-lzma-objects)
	@echo "linking $(@)"
	$(build-ld-cpp) $(^) $(build-lflags) $(static-on-windows) -o $(@)

$(openjdk-objects): $(build)/openjdk/%-openjdk.o: $(openjdk-src)/%.c \
		$(openjdk-headers-dep)
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	sed 's/^static jclass ia_class;//' < $(<) > $(build)/openjdk/$(notdir $(<))
ifeq ($(platform),ios)
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
	if [ -e "$(openjdk-src)/solaris/native/java/lang/childproc.h" ]; then \
		sed \
			-e 's/^#ifndef __APPLE__/#if 1/' \
			-e 's/^#ifdef __APPLE__/#if 0/' \
			< "$(openjdk-src)/solaris/native/java/lang/childproc.h" \
			> $(build)/openjdk/childproc.h; \
	fi
endif
ifneq (7,$(openjdk-version))
	if [ -f openjdk-patches/$(notdir $(<)).8.patch ]; then \
		( cd $(build) && patch -p0 ) < openjdk-patches/$(notdir $(<)).8.patch; \
	fi
	if [ -f openjdk-patches/$(notdir $(<)).8.$(platform).patch ]; then \
		( cd $(build) && patch -p0 ) < openjdk-patches/$(notdir $(<)).8.$(platform).patch; \
	fi
endif
	if [ -f openjdk-patches/$(notdir $(<)).patch ]; then \
		( cd $(build) && patch -p0 ) < openjdk-patches/$(notdir $(<)).patch; \
	fi
	$(cc) -fPIC $(openjdk-extra-cflags) $(openjdk-cflags) \
		$(optimization-cflags) -w -c $(build)/openjdk/$(notdir $(<)) \
		$(call output,$(@)) -Wno-return-type

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

ifeq ($(kernel),darwin)
	mkdir -p $(build)/openjdk/netinet
	for file in \
		$(header-sysroot)/usr/include/netinet/ip.h \
		$(header-sysroot)/usr/include/netinet/in_systm.h \
		$(header-sysroot)/usr/include/netinet/ip_icmp.h \
		$(header-sysroot)/usr/include/netinet/in_var.h \
		$(header-sysroot)/usr/include/netinet/icmp6.h \
		$(header-sysroot)/usr/include/netinet/ip_var.h; do \
		if [ ! -f "$(build)/openjdk/netinet/$$(basename $${file})" ]; then \
			ln -s "$${file}" "$(build)/openjdk/netinet/$$(basename $${file})"; \
		fi; \
	done
	mkdir -p $(build)/openjdk/netinet6
	for file in \
		$(header-sysroot)/usr/include/netinet6/in6_var.h; do \
		if [ ! -f "$(build)/openjdk/netinet6/$$(basename $${file})" ]; then \
			ln -s "$${file}" "$(build)/openjdk/netinet6/$$(basename $${file})"; \
		fi; \
	done
	mkdir -p $(build)/openjdk/net
	for file in \
		$(header-sysroot)/usr/include/net/if_arp.h; do \
		if [ ! -f "$(build)/openjdk/net/$$(basename $${file})" ]; then \
			ln -s "$${file}" "$(build)/openjdk/net/$$(basename $${file})"; \
		fi; \
	done
	mkdir -p $(build)/openjdk/sys
	for file in \
		$(header-sysroot)/usr/include/sys/kern_event.h \
		$(header-sysroot)/usr/include/sys/sys_domain.h; do \
		if [ ! -f "$(build)/openjdk/sys/$$(basename $${file})" ]; then \
			ln -s "$${file}" "$(build)/openjdk/sys/$$(basename $${file})"; \
		fi; \
	done
endif
	@touch $(@)

$(openjdk-jar-dep):
	@echo "extracting openjdk classes"
	@mkdir -p $(dir $(@))
	@mkdir -p $(classpath-build)
	(cd $(classpath-build) && \
		$(jar) xf "$$($(native-path) "$(openjdk-image)/lib/rt.jar")" && \
		$(jar) xf "$$($(native-path) "$(openjdk-image)/lib/charsets.jar")" && \
		$(jar) xf "$$($(native-path) "$(openjdk-image)/lib/resources.jar")" && \
		$(jar) xf "$$($(native-path) "$(openjdk-image)/lib/ext/sunec.jar")")
	@touch $(@)
