#include "stdio.h"
#include "jni.h"

#undef JNIEXPORT
#define JNIEXPORT __attribute__ ((visibility("default")))

extern "C" JNIEXPORT void JNICALL
Java_java_lang_System_00024Output_println(JNIEnv* e, jobject, jstring s)
{
  jboolean isCopy;
  const char* chars = e->GetStringUTFChars(s, &isCopy);
  if (chars) {
    printf("%s\n", chars);
  }
  e->ReleaseStringUTFChars(s, chars);
}
