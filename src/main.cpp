/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "stdlib.h"
#include "stdio.h"
#include "string.h"
#include "stdint.h"
#include "jni.h"

#ifdef __MINGW32__
#  define PATH_SEPARATOR ';'
#else
#  define PATH_SEPARATOR ':'
#endif

#ifdef JNI_VERSION_1_6
// todo: use JavaVMInitArgs instead
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

namespace {

void
usageAndExit(const char* name)
{
  fprintf(stderr, "usage: %s [-cp <classpath>] [-Xmx<maximum heap size>] "
          "<class name> [<argument> ...]\n", name);
  exit(-1);
}

} // namespace

int
main(int ac, const char** av)
{
  JDK1_1InitArgs vmArgs;
  vmArgs.version = 0x00010001;
  JNI_GetDefaultJavaVMInitArgs(&vmArgs);

  const char* class_ = 0;
  int argc = 0;
  const char** argv = 0;
  int propertyCount = 0;

  for (int i = 1; i < ac; ++i) {
    if (strcmp(av[i], "-cp") == 0) {
      vmArgs.classpath = const_cast<char*>(av[++i]);
    } else if (strncmp(av[i], "-Xmx", 4) == 0) {
      vmArgs.maxHeapSize = atoi(av[i] + 4);
    } else if (strncmp(av[i], "-D", 2) == 0) {
      ++ propertyCount;
    } else {
      class_ = av[i++];
      if (i < ac) {
        argc = ac - i;
        argv = av + i;
        i = ac;
      }
    }
  }

#ifdef BOOT_CLASSPATH
  unsigned size = sizeof(BOOT_CLASSPATH) + 1 + strlen(vmArgs.classpath);
  char classpath[size];
  snprintf(classpath, size, "%s%c%s",
           BOOT_CLASSPATH, PATH_SEPARATOR, vmArgs.classpath);
  vmArgs.classpath = classpath;
#endif

#ifdef BOOT_LIBRARY
  const int BootPropertyCount = 1;
#else
  const int BootPropertyCount = 0;
#endif

  const char* properties[propertyCount + BootPropertyCount + 1];
  properties[propertyCount + BootPropertyCount] = 0;
  for (int i = 1; i < ac; ++i) {
    if (strncmp(av[i], "-D", 2) == 0) {
      properties[--propertyCount] = av[i] + 2;
    }
  }

#ifdef BOOT_LIBRARY
  properties[propertyCount + BootPropertyCount - 1]
    = "avian.bootstrap=" BOOT_LIBRARY;
#endif

  vmArgs.properties = const_cast<char**>(properties);

  if (class_ == 0) {
    usageAndExit(av[0]);
  }

  JavaVM* vm;
  void* env;
  JNI_CreateJavaVM(&vm, &env, &vmArgs);
  JNIEnv* e = static_cast<JNIEnv*>(env);

  jclass c = e->FindClass(class_);
  if (not e->ExceptionOccurred()) {
    jmethodID m = e->GetStaticMethodID(c, "main", "([Ljava/lang/String;)V");
    if (not e->ExceptionOccurred()) {
      jclass stringClass = e->FindClass("java/lang/String");
      if (not e->ExceptionOccurred()) {
        jobjectArray a = e->NewObjectArray(argc, stringClass, 0);
        if (not e->ExceptionOccurred()) {
          for (int i = 0; i < argc; ++i) {
            e->SetObjectArrayElement(a, i, e->NewStringUTF(argv[i]));
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
