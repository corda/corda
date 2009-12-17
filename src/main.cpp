/* Copyright (c) 2008-2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "stdlib.h"
#include "stdio.h"
#include "string.h"
#include "jni.h"

#if (defined __MINGW32__) || (defined _MSC_VER)
#  define PATH_SEPARATOR ';'
#else
#  define PATH_SEPARATOR ':'
#endif

#ifdef _MSC_VER

#  define not !
#  define or ||
#  define and &&
#  define xor ^

template <class T>
class RuntimeArray {
 public:
  RuntimeArray(unsigned size):
    body(static_cast<T*>(malloc(size * sizeof(T))))
  { }

  ~RuntimeArray() {
    free(body);
  }

  T* body;
};

#  define RUNTIME_ARRAY(type, name, size) RuntimeArray<type> name(size);
#  define RUNTIME_ARRAY_BODY(name) name.body

#else // not _MSC_VER

#  define RUNTIME_ARRAY(type, name, size) type name[size];
#  define RUNTIME_ARRAY_BODY(name) name

#endif // not _MSC_VER

namespace {

void
usageAndExit(const char* name)
{
  fprintf
    (stderr, "usage: %s\n"
     "\t[{-cp|-classpath} <classpath>]\n"
     "\t[-Xmx<maximum heap size>]\n"
     "\t[-Xbootclasspath/p:<classpath to prepend to bootstrap classpath>]\n"
     "\t[-Xbootclasspath:<bootstrap classpath>]\n"
     "\t[-Xbootclasspath/a:<classpath to append to bootstrap classpath>]\n"
     "\t[-D<property name>=<property value> ...]\n"
     "\t<class name> [<argument> ...]\n", name);
  exit(-1);
}

} // namespace

int
main(int ac, const char** av)
{
  JavaVMInitArgs vmArgs;
  vmArgs.version = JNI_VERSION_1_2;
  vmArgs.nOptions = 1;
  vmArgs.ignoreUnrecognized = JNI_TRUE;

  const char* class_ = 0;
  int argc = 0;
  const char** argv = 0;
  const char* classpath = ".";

  for (int i = 1; i < ac; ++i) {
    if (strcmp(av[i], "-cp") == 0
        or strcmp(av[i], "-classpath") == 0)
    {
      classpath = av[++i];
    } else if (strncmp(av[i], "-X", 2) == 0
               or strncmp(av[i], "-D", 2) == 0)
    {
      ++ vmArgs.nOptions;
    } else {
      class_ = av[i++];
      if (i < ac) {
        argc = ac - i;
        argv = av + i;
        i = ac;
      }
    }
  }

#ifdef BOOT_LIBRARY
  ++ vmArgs.nOptions;
#endif

#ifdef BOOT_CLASSPATH
  ++ vmArgs.nOptions;
#endif

#ifdef BOOT_IMAGE
  ++ vmArgs.nOptions;
#endif

#ifdef BOOT_BUILTINS
  ++ vmArgs.nOptions;
#endif

  RUNTIME_ARRAY(JavaVMOption, options, vmArgs.nOptions);
  vmArgs.options = RUNTIME_ARRAY_BODY(options);

  unsigned optionIndex = 0;

#ifdef BOOT_IMAGE
  vmArgs.options[optionIndex++].optionString
    = const_cast<char*>("-Davian.bootimage=" BOOT_IMAGE);
#endif

#ifdef BOOT_CLASSPATH
  vmArgs.options[optionIndex++].optionString
    = const_cast<char*>("-Xbootclasspath:" BOOT_CLASSPATH);
#endif

#ifdef BOOT_LIBRARY
  vmArgs.options[optionIndex++].optionString
    = const_cast<char*>("-Davian.bootstrap=" BOOT_LIBRARY);
#endif

#ifdef BOOT_BUILTINS
  vmArgs.options[optionIndex++].optionString
    = const_cast<char*>("-Davian.builtins=" BOOT_BUILTINS);
#endif

#define CLASSPATH_PROPERTY "-Djava.class.path="

  unsigned classpathSize = strlen(classpath);
  unsigned classpathPropertyBufferSize
    = sizeof(CLASSPATH_PROPERTY) + classpathSize;

  RUNTIME_ARRAY(char, classpathPropertyBuffer, classpathPropertyBufferSize);
  memcpy(RUNTIME_ARRAY_BODY(classpathPropertyBuffer),
         CLASSPATH_PROPERTY,
         sizeof(CLASSPATH_PROPERTY) - 1);
  memcpy(RUNTIME_ARRAY_BODY(classpathPropertyBuffer)
         + sizeof(CLASSPATH_PROPERTY) - 1,
         classpath,
         classpathSize + 1);

  vmArgs.options[optionIndex++].optionString
    = RUNTIME_ARRAY_BODY(classpathPropertyBuffer);

  for (int i = 1; i < ac; ++i) {
    if (strncmp(av[i], "-X", 2) == 0
        or strncmp(av[i], "-D", 2) == 0)
    {
      vmArgs.options[optionIndex++].optionString = const_cast<char*>(av[i]);
    }
  }

  if (class_ == 0) {
    usageAndExit(av[0]);
  }

  JavaVM* vm;
  void* env;
  JNI_CreateJavaVM(&vm, &env, &vmArgs);
  JNIEnv* e = static_cast<JNIEnv*>(env);

  jclass c = e->FindClass(class_);
  if (not e->ExceptionCheck()) {
    jmethodID m = e->GetStaticMethodID(c, "main", "([Ljava/lang/String;)V");
    if (not e->ExceptionCheck()) {
      jclass stringClass = e->FindClass("java/lang/String");
      if (not e->ExceptionCheck()) {
        jobjectArray a = e->NewObjectArray(argc, stringClass, 0);
        if (not e->ExceptionCheck()) {
          for (int i = 0; i < argc; ++i) {
            e->SetObjectArrayElement(a, i, e->NewStringUTF(argv[i]));
          }
          
          e->CallStaticVoidMethod(c, m, a);
        }
      }
    }
  }

  int exitCode = 0;
  if (e->ExceptionCheck()) {
    exitCode = -1;
    e->ExceptionDescribe();
  }

  vm->DestroyJavaVM();

  return exitCode;
}
