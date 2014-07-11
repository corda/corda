#include "avian/machine.h"

using namespace vm;

extern "C" AVIAN_EXPORT jint JNICALL net_JNI_OnLoad(JavaVM*, void*)
{
  return 0;
}

extern "C" AVIAN_EXPORT jint JNICALL management_JNI_OnLoad(JavaVM*, void*)
{
  return 0;
}

extern "C" char* findJavaTZ_md(const char*, const char*)
{
  return 0;
}
