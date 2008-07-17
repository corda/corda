Quick Start
-----------

on Linux:
 $ export JAVA_HOME=/usr/local/java # or wherever you have the JDK installed
 $ make
 $ build/linux-i386-compile-fast/avian -cp build/test Hello

on Mac OS X:
 $ export JAVA_HOME=/Library/Java/Home
 $ make
 $ build/darwin-i386-compile-fast/avian -cp build/test Hello


Introduction
------------

Avian is a lightweight virtual machine and class library designed to
provide a useful subset of Java's features, suitable for building
self-contained applications.  More information is available at the
project web site:

  http://oss.readytalk.com/avian

If you have any trouble building, running, or embedding Avian, please
post a message to our discussion group:

  http://groups.google.com/group/avian

That's also the place for any other questions, comments, or
suggestions you might have.


Supported Platforms
-------------------

Avian can currently target the following platforms:

  Linux (i386 and x86_64)
  Win32 (i386)
  Mac OS X (i386)

The Win32 port may be built on Linux using a MinGW cross compiler and
build environment.  Builds on MSYS or Cygwin are not yet supported,
but patches to enable them are welcome.


Building
--------

Build requirements include:

  * GNU make 3.80 or later
  * GCC 3.4 or later
  * JDK 1.5 or later
  * GNU binutils 2.17 or later (not needed on OS X)
  * MinGW 3.4 or later (only if cross-compiling for Windows)
  * zlib 1.2.3 or later

Earlier versions of some of these packages may also work but have not
been tested.

If you are cross-compiling for Windows, you may find it useful to use
our win32 repository: (run this from the directory containing the
avian directory)

  $ git clone git://oss.readytalk.com/win32.git

This gives you the Windows JNI headers, zlib headers and library, and
a few other useful libraries like OpenSSL and libjpeg.

The build is directed by a single makefile and may be influenced via
certain flags described below.

 $ make platform={linux,windows,darwin} arch={i386,x86_64} \
     process={compile,interpret} mode={debug,debug-fast,fast}

  * platform - the target platform
      default: output of $(uname -s | tr [:upper:] [:lower:])

  * arch - the target architecture
      default: output of $(uname -m)

  * mode - which set of compilation flags to use, which determine
    optimization level, debug symbols, and whether to enable
    assertions
      default: fast

  * process - choice between pure interpreter or JIT compiler
      default: compile


Installing
----------

 $ cp build/${platform}-${arch}-${process}-${mode}/avian ~/bin/


Embedding
---------

The following series of commands illustrates how to produce a
stand-alone executable out of a Java application using Avian.

Step 1: Build Avian, create a new directory, and populate it with the
VM object files and bootstrap classpath jar.

 $ make
 $ mkdir hello
 $ cd hello
 $ ar x ../build/${platform}-${arch}-${process}-${mode}/libavian.a
 $ cp ../build/classpath.jar boot.jar

Step 2: Build the Java code and add it to the jar.

 $ cat >Hello.java <<EOF
public class Hello {
  public static void main(String[] args) {
    System.out.println("hello, world!");
  }
}
EOF
 $ javac -bootclasspath boot.jar Hello.java
 $ jar u0f boot.jar Hello.class


Step 3: Make an object file out of the jar.

for linux-i386:

 $ objcopy -I binary boot.jar -O elf32-i386 -B i386 boot-jar.o

for linux-x86_64:

 $ objcopy -I binary boot.jar -O elf64-x86-64 -B i386:x86-64 boot-jar.o

for windows-i386:

 $ objcopy -I binary boot.jar -O pe-i386 -B i386 boot-jar.o

for darwin-i386: (objcopy is not currently supported on this platform,
so we use the binaryToMacho utility instead)

 $ ../build/darwin-i386-compile-fast/binaryToMacho boot.jar \
     __binary_boot_jar_start __binary_boot_jar_end > boot-jar.o


Step 4: Write a driver which starts the VM and runs the desired main
method.  Note the bootJar function, which will be called by the VM to
get a handle to the embedded jar.  We tell the VM about this jar by
setting the classpath to "[bootJar]".

 $ cat >main.cpp <<EOF
#include "stdint.h"
#include "jni.h"

#ifdef __MINGW32__
#  define EXPORT __declspec(dllexport)
#  define SYMBOL(x) binary_boot_jar_##x
#else
#  define EXPORT __attribute__ ((visibility("default")))
#  define SYMBOL(x) _binary_boot_jar_##x
#endif

extern "C" {

  extern const uint8_t SYMBOL(start)[];
  extern const uint8_t SYMBOL(end)[];

  EXPORT const uint8_t*
  bootJar(unsigned* size)
  {
    *size = SYMBOL(end) - SYMBOL(start);
    return SYMBOL(start);
  }

} // extern "C"

int
main(int ac, const char** av)
{
  JavaVMInitArgs vmArgs;
  vmArgs.version = JNI_VERSION_1_2;
  vmArgs.nOptions = 1;
  vmArgs.ignoreUnrecognized = JNI_TRUE;

  JavaVMOption options[vmArgs.nOptions];
  vmArgs.options = options;

  options[0].optionString = const_cast<char*>("-Djava.class.path=[bootJar]");

  JavaVM* vm;
  void* env;
  JNI_CreateJavaVM(&vm, &env, &vmArgs);
  JNIEnv* e = static_cast<JNIEnv*>(env);

  jclass c = e->FindClass("Hello");
  if (not e->ExceptionOccurred()) {
    jmethodID m = e->GetStaticMethodID(c, "main", "([Ljava/lang/String;)V");
    if (not e->ExceptionOccurred()) {
      jclass stringClass = e->FindClass("java/lang/String");
      if (not e->ExceptionOccurred()) {
        jobjectArray a = e->NewObjectArray(ac-1, stringClass, 0);
        if (not e->ExceptionOccurred()) {
          for (int i = 1; i < ac; ++i) {
            e->SetObjectArrayElement(a, i-1, e->NewStringUTF(av[i]));
          }
          
          e->CallStaticVoidMethod(c, m, a);
        }
      }
    }
  }

  int exitCode = 0;
  if (e->ExceptionOccurred()) {
    exitCode = -1;
    e->ExceptionDescribe();
  }

  vm->DestroyJavaVM();

  return exitCode;
}
EOF
 $ g++ -I$JAVA_HOME/include -c main.cpp -o main.o


Step 5: Link the objects produced above to produce the final
executable, and optionally strip its symbols.

on Linux:
 $ g++ -rdynamic *.o -ldl -lpthread -lz -o hello
 $ strip --strip-all hello

on Mac OS X:
 $ g++ -rdynamic *.o -ldl -lpthread -lz -o hello -framework CoreFoundation
 $ strip -S -x hello

