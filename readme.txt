Quick Start
-----------

On Linux:
 $ export JAVA_HOME=/usr/local/java
 $ make
 $ build/linux-i386-compile-fast/avian -cp build/test Hello

On Mac OS X:
 $ export JAVA_HOME=/Library/Java/Home
 $ make
 $ build/darwin-i386-compile-fast/avian -cp build/test Hello

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
    assertions.
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
 $ jar c0f boot.jar Hello.class


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
     __binary_boot_jar_start __binary_boot_jar_size > boot-jar.o


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
  extern const uint8_t SYMBOL(size)[];

  EXPORT const uint8_t*
  bootJar(unsigned* size)
  {
    *size = reinterpret_cast<uintptr_t>(SYMBOL(size));
    return SYMBOL(start);
  }

} // extern "C"

#ifdef JNI_VERSION_1_6
typedef struct JDK1_1InitArgs {
    jint version;

    char **properties;
    jint checkSource;
    jint nativeStackSize;
    jint javaStackSize;
    jint minHeapSize;
    jint maxHeapSize;
    jint verifyMode;
    char *classpath;

    jint (JNICALL *vfprintf)(FILE *fp, const char *format, va_list args);
    void (JNICALL *exit)(jint code);
    void (JNICALL *abort)(void);

    jint enableClassGC;
    jint enableVerboseGC;
    jint disableAsyncGC;
    jint verbose;
    jboolean debugging;
    jint debugPort;
} JDK1_1InitArgs;
#endif

int
main(int ac, const char** av)
{
  JDK1_1InitArgs vmArgs;
  vmArgs.version = 0x00010001;
  JNI_GetDefaultJavaVMInitArgs(&vmArgs);

  vmArgs.classpath = const_cast<char*>("[bootJar]");

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

On linux:
 $ g++ -rdynamic *.o -ldl -lpthread -lz -o hello
 $ strip --strip-all hello
On Mac OS X:
 $ g++ -rdynamic *.o -ldl -lpthread -lz -o hello -framework CoreFoundation
 $ strip -S -x hello

