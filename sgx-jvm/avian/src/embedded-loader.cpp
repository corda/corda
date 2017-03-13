/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <windows.h>
#include <tchar.h>
#include <stdint.h>

#include "avian/embed.h"
#include "avian/common.h"
#include "jni.h"

extern "C" {
// since we aren't linking against libstdc++, we must implement this
// ourselves:
void __cxa_pure_virtual(void)
{
  abort();
}

AVIAN_EXPORT const uint8_t* bootJar(unsigned* size)
{
  if (HRSRC hResInfo = FindResourceW(
          NULL, RESID_BOOT_JAR, reinterpret_cast<LPCWSTR>(RT_RCDATA))) {
    if (HGLOBAL hRes = LoadResource(NULL, hResInfo)) {
      *size = SizeofResource(NULL, hResInfo);
      return (const uint8_t*)LockResource(hRes);
    }
  }

  fprintf(stderr, "boot.jar resource not found\n");

  *size = 0;
  return NULL;
}
}  // extern "C"

static void getMainClass(char* pName, int maxLen)
{
  if (0 == LoadString(NULL, RESID_MAIN_CLASS, pName, maxLen)) {
    fprintf(stderr, "Main class not specified\n");
    strcpy(pName, "Main");
  }
}

int main(int ac, const char** av)
{
  JavaVMInitArgs vmArgs;
  vmArgs.version = JNI_VERSION_1_2;
  vmArgs.nOptions = 1;
  vmArgs.ignoreUnrecognized = JNI_TRUE;

  JavaVMOption options[1];
  vmArgs.options = options;

  options[0].optionString = const_cast<char*>("-Xbootclasspath:[bootJar]");

  JavaVM* vm;
  void* env;
  JNI_CreateJavaVM(&vm, &env, &vmArgs);
  JNIEnv* e = static_cast<JNIEnv*>(env);

  char mainClass[256];
  getMainClass(mainClass, sizeof(mainClass));

  jclass c = e->FindClass(mainClass);
  if (not e->ExceptionCheck()) {
    jmethodID m = e->GetStaticMethodID(c, "main", "([Ljava/lang/String;)V");
    if (not e->ExceptionCheck()) {
      jclass stringClass = e->FindClass("java/lang/String");
      if (not e->ExceptionCheck()) {
        jobjectArray a = e->NewObjectArray(ac - 1, stringClass, 0);
        if (not e->ExceptionCheck()) {
          for (int i = 1; i < ac; ++i) {
            e->SetObjectArrayElement(a, i - 1, e->NewStringUTF(av[i]));
          }

          e->CallStaticVoidMethod(c, m, a);
        } else
          fprintf(stderr, "Couldn't create array\n");
      } else
        fprintf(stderr, "java.lang.String not found\n");
    } else
      fprintf(stderr, "main method not found\n");
  } else
    fprintf(stderr, "Main class not found\n");

  int exitCode = 0;
  if (e->ExceptionCheck()) {
    exitCode = -1;
    e->ExceptionDescribe();
    e->ExceptionClear();
  }

  vm->DestroyJavaVM();

  return exitCode;
}
