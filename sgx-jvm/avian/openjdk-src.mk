openjdk-sources = \
	$(openjdk-src)/share/native/common/check_code.c \
	$(openjdk-src)/share/native/common/check_format.c \
	$(openjdk-src)/share/native/common/check_version.c \
	$(openjdk-src)/share/native/common/jdk_util.c \
	$(openjdk-src)/share/native/common/jio.c \
	$(openjdk-src)/share/native/common/jni_util.c \
	$(openjdk-src)/share/native/common/verify_stub.c \
	$(openjdk-src)/share/native/java/io/FileInputStream.c \
	$(openjdk-src)/share/native/java/io/io_util.c \
	$(openjdk-src)/share/native/java/lang/Class.c \
	$(openjdk-src)/share/native/java/lang/ClassLoader.c \
	$(openjdk-src)/share/native/java/lang/Double.c \
	$(openjdk-src)/share/native/java/lang/Float.c \
	$(openjdk-src)/share/native/java/lang/Object.c \
	$(openjdk-src)/share/native/java/lang/Package.c \
	$(wildcard $(openjdk-src)/share/native/java/lang/ref/Finalizer.c) \
	$(openjdk-src)/share/native/java/lang/reflect/Array.c \
	$(openjdk-src)/share/native/java/lang/reflect/Proxy.c \
	$(wildcard $(openjdk-src)/share/native/java/lang/ResourceBundle.c) \
	$(openjdk-src)/share/native/java/lang/Runtime.c \
	$(openjdk-src)/share/native/java/lang/SecurityManager.c \
	$(openjdk-src)/share/native/java/lang/Shutdown.c \
	$(openjdk-src)/share/native/java/lang/StrictMath.c \
	$(openjdk-src)/share/native/java/lang/String.c \
	$(openjdk-src)/share/native/java/lang/System.c \
	$(openjdk-src)/share/native/java/lang/Thread.c \
	$(openjdk-src)/share/native/java/lang/Throwable.c \
	$(wildcard $(openjdk-src)/share/native/java/lang/fdlibm/src/*.c) \
	$(openjdk-src)/share/native/java/net/InetAddress.c \
	$(openjdk-src)/share/native/java/nio/Bits.c \
	$(openjdk-src)/share/native/java/security/AccessController.c \
	$(openjdk-src)/share/native/java/util/concurrent/atomic/AtomicLong.c \
	$(openjdk-src)/share/native/java/util/TimeZone.c \
	$(openjdk-src)/share/native/java/util/zip/Adler32.c \
	$(openjdk-src)/share/native/java/util/zip/CRC32.c \
	$(openjdk-src)/share/native/java/util/zip/Deflater.c \
	$(openjdk-src)/share/native/java/util/zip/Inflater.c \
	$(openjdk-src)/share/native/java/util/zip/ZipFile.c \
	$(openjdk-src)/share/native/java/util/zip/zip_util.c \
	$(openjdk-src)/share/native/sun/misc/GC.c \
	$(openjdk-src)/share/native/sun/misc/MessageUtils.c \
	$(openjdk-src)/share/native/sun/misc/NativeSignalHandler.c \
	$(openjdk-src)/share/native/sun/misc/Signal.c \
	$(openjdk-src)/share/native/sun/misc/Version.c \
	$(openjdk-src)/share/native/sun/misc/VM.c \
	$(openjdk-src)/share/native/sun/misc/VMSupport.c \
	$(openjdk-src)/share/native/sun/reflect/ConstantPool.c \
	$(openjdk-src)/share/native/sun/reflect/NativeAccessors.c \
	$(openjdk-src)/share/native/sun/reflect/Reflection.c

openjdk-headers-classes = \
	java.io.FileDescriptor \
	java.io.FileInputStream \
	java.io.FileOutputStream \
	java.io.FileSystem \
	java.lang.Class \
	java.lang.ClassLoader \
	java.lang.Double \
	java.lang.Float \
	java.lang.Integer \
	java.lang.Long \
	java.lang.Object \
	java.lang.Package \
	java.lang.Runtime \
	java.lang.SecurityManager \
	java.lang.Shutdown \
	java.lang.StrictMath \
	java.lang.String \
	java.lang.System \
	java.lang.Thread \
	java.lang.Throwable \
	java.lang.ref.Finalizer \
	java.lang.reflect.Array \
	java.lang.reflect.Proxy \
	java.net.InetAddress \
	java.nio.MappedByteBuffer \
	java.security.AccessController \
	java.util.ResourceBundle \
	java.util.TimeZone \
	java.util.concurrent.atomic.AtomicLong \
	java.util.jar.JarFile \
	java.util.zip.Adler32 \
	java.util.zip.CRC32 \
	java.util.zip.Deflater \
	java.util.zip.Inflater \
	java.util.zip.ZipEntry \
	java.util.zip.ZipFile \
	sun.misc.GC \
	sun.misc.MessageUtils \
	sun.misc.NativeSignalHandler \
	sun.misc.Signal \
	sun.misc.VM \
	sun.misc.VMSupport \
	sun.misc.Version \
	sun.misc.URLClassPath \
	sun.nio.ch.IOStatus \
	sun.reflect.ConstantPool \
	sun.reflect.NativeConstructorAccessorImpl \
	sun.reflect.NativeMethodAccessorImpl \
	sun.reflect.Reflection \
	sun.security.provider.NativeSeedGenerator

ifneq (7,$(openjdk-version))
	openjdk-sources += \
		$(openjdk-src)/share/native/sun/misc/URLClassPath.c
endif

# todo: set properties according to architecture targeted and OpenJDK
# version used:
openjdk-cflags = \
	"-I$(src)/openjdk" \
	"-I$(build)/openjdk" \
	"-I$(openjdk-src)/share/javavm/export" \
	"-I$(openjdk-src)/share/native/common" \
	"-I$(openjdk-src)/share/native/java/io" \
	"-I$(openjdk-src)/share/native/java/lang" \
	"-I$(openjdk-src)/share/native/java/lang/fdlibm/include" \
	"-I$(openjdk-src)/share/native/java/net" \
	"-I$(openjdk-src)/share/native/java/util/zip" \
	"-I$(openjdk-src)/share/native/sun/nio/ch" \
	"-I$(openjdk-src)/share/javavm/include" \
	-D_LITTLE_ENDIAN \
	-DARCHPROPNAME=\"x86\" \
	-DRELEASE=\"1.6.0\" \
	-DJDK_MAJOR_VERSION=\"1\" \
	-DJDK_MINOR_VERSION=\"6\" \
	-DJDK_MICRO_VERSION=\"0\" \
	-DJDK_BUILD_NUMBER=\"0\" \
	-D_GNU_SOURCE

ifeq ($(kernel),darwin)
	openjdk-cflags += \
		-D_LFS_LARGEFILE=1 \
		-D_ALLBSD_SOURCE
endif

ifeq ($(platform),windows)
	openjdk-sources += \
		$(openjdk-src)/windows/native/common/jni_util_md.c \
		$(openjdk-src)/windows/native/java/io/canonicalize_md.c \
		$(openjdk-src)/windows/native/java/io/FileDescriptor_md.c \
		$(openjdk-src)/windows/native/java/io/FileInputStream_md.c \
		$(openjdk-src)/windows/native/java/io/FileOutputStream_md.c \
		$(openjdk-src)/windows/native/java/io/io_util_md.c \
		$(openjdk-src)/windows/native/java/io/WinNTFileSystem_md.c \
		$(openjdk-src)/windows/native/java/lang/java_props_md.c \
		$(openjdk-src)/windows/native/java/util/WindowsPreferences.c \
		$(openjdk-src)/windows/native/java/util/logging.c \
		$(openjdk-src)/windows/native/java/util/TimeZone_md.c \
		$(openjdk-src)/windows/native/sun/io/Win32ErrorMode.c \
		$(openjdk-src)/windows/native/sun/security/provider/WinCAPISeedGenerator.c

	ifeq (7,$(openjdk-version))
		openjdk-sources += \
			$(openjdk-src)/windows/native/java/io/FileSystem_md.c \
			$(openjdk-src)/windows/native/java/io/Win32FileSystem_md.c
	endif

	openjdk-headers-classes += \
		sun.io.Win32ErrorMode

	openjdk-cflags += \
		"-I$(openjdk-src)/windows/javavm/export" \
		"-I$(openjdk-src)/windows/native/common" \
		"-I$(openjdk-src)/windows/native/java/io" \
		"-I$(openjdk-src)/windows/native/java/net" \
		"-I$(openjdk-src)/windows/native/java/util" \
		"-I$(openjdk-src)/windows/native/sun/nio/ch" \
		"-I$(openjdk-src)/windows/javavm/include" \
		-DLOCALE_SNAME=0x0000005c \
		-DLOCALE_SISO3166CTRYNAME2=0x00000068 \
		-DLOCALE_SISO639LANGNAME2=0x00000067 \
		-D_JNI_IMPLEMENTATION_ \
		-D_JAVASOFT_WIN32_TYPEDEF_MD_H_ \
		-D_WIN32_WINNT=0x0600 \
		-Ds6_words=_s6_words \
		-Ds6_bytes=_s6_bytes

		ifeq ($(arch),x86_64)
			openjdk-cflags += "-I$(root)/win64/include"
		else
			openjdk-cflags += "-I$(root)/win32/include"
		endif
else
	openjdk-sources += \
		$(shell find $(openjdk-src)/solaris/native/common -name '*.c') \
		$(openjdk-src)/solaris/native/java/io/canonicalize_md.c \
		$(openjdk-src)/solaris/native/java/io/FileDescriptor_md.c \
		$(openjdk-src)/solaris/native/java/io/FileInputStream_md.c \
		$(openjdk-src)/solaris/native/java/io/FileOutputStream_md.c \
		$(wildcard $(openjdk-src)/solaris/native/java/io/FileSystem_md.c) \
		$(openjdk-src)/solaris/native/java/io/io_util_md.c \
		$(openjdk-src)/solaris/native/java/io/UnixFileSystem_md.c \
		$(openjdk-src)/solaris/native/java/lang/java_props_md.c \
		$(wildcard $(openjdk-src)/solaris/native/java/lang/childproc.c) \
		$(openjdk-src)/solaris/native/java/nio/MappedByteBuffer.c \
		$(openjdk-src)/solaris/native/java/util/FileSystemPreferences.c \
		$(openjdk-src)/solaris/native/java/util/logging.c \
		$(openjdk-src)/solaris/native/java/util/TimeZone_md.c

	openjdk-headers-classes += \
		java.io.UnixFileSystem

	ifneq (7,$(openjdk-version))
		openjdk-headers-classes +=
	endif

	openjdk-cflags += \
		"-I$(openjdk-src)/solaris/javavm/export" \
		"-I$(openjdk-src)/solaris/native/common" \
		"-I$(openjdk-src)/solaris/native/java/io" \
		"-I$(openjdk-src)/solaris/native/java/lang" \
		"-I$(openjdk-src)/solaris/native/java/net" \
		"-I$(openjdk-src)/solaris/native/java/util" \
		"-I$(openjdk-src)/solaris/native/sun/nio/ch" \
		"-I$(openjdk-src)/solaris/javavm/include" \
		"-I$(openjdk-src)/solaris/hpi/include" \
		"-I$(openjdk-src)/solaris/native/common/deps" \
		"-I$(openjdk-src)/solaris/native/common/deps/fontconfig2" \
		"-I$(openjdk-src)/solaris/native/common/deps/gconf2" \
		"-I$(openjdk-src)/solaris/native/common/deps/glib2" \
		"-I$(openjdk-src)/solaris/native/common/deps/gtk2" \
		"-DX11_PATH=\"/usr/X11R6\""

	ifeq ($(platform),linux)
		openjdk-sources += \
			$(openjdk-src)/solaris/native/java/net/linux_close.c

		openjdk-headers-classes +=

		openjdk-cflags += \
			"-I$(openjdk-src)/solaris/native/common/deps/glib2" \
			"-I$(openjdk-src)/solaris/native/common/deps/gconf2" \
			"-I$(openjdk-src)/solaris/native/common/deps/fontconfig2" \
			"-I$(openjdk-src)/solaris/native/common/deps/gtk2" \
			$(shell pkg-config --cflags glib-2.0) \
			$(shell pkg-config --cflags gconf-2.0)
	endif

	ifeq ($(kernel),darwin)
		openjdk-sources += \
			$(openjdk-src)/solaris/native/java/net/bsd_close.c \
			$(openjdk-src)/macosx/native/sun/nio/ch/KQueueArrayWrapper.c

		ifeq ($(platform),ios)
			openjdk-local-sources += \
				$(src)/openjdk/my_java_props_macosx.c
		else
			openjdk-sources += \
				$(openjdk-src)/solaris/native/java/lang/java_props_macosx.c
		endif

		openjdk-cflags += \
			-DMACOSX -x objective-c
	endif
endif

openjdk-local-sources +=

openjdk-c-objects = \
	$(foreach x,$(1),$(patsubst $(2)/%.c,$(3)/%-openjdk.o,$(x)))

openjdk-objects = \
	$(call openjdk-c-objects,$(openjdk-sources),$(openjdk-src),$(build)/openjdk)

openjdk-local-objects = \
	$(call openjdk-c-objects,$(openjdk-local-sources),$(src)/openjdk,$(build)/openjdk)

openjdk-headers-dep = $(build)/openjdk/headers.dep
