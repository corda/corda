#include "avian/machine.h"

using namespace vm;

extern "C" JNIEXPORT jint JNICALL
net_JNI_OnLoad(JavaVM*, void*)
{
  return 0;
}

extern "C" JNIEXPORT jint JNICALL
management_JNI_OnLoad(JavaVM*, void*)
{
  return 0;
}

extern "C" char* findJavaTZ_md(const char*, const char*)
{
  return 0;
}
