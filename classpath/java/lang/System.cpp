#include "stdio.h"
#include "jni.h"

extern "C" JNIEXPORT void JNICALL
Java_java_lang_System_Output_println(JNIEnv* e, jobject, jstring s)
{
  jboolean isCopy;
  const char* chars = e->GetStringUTFChars(s, &isCopy);
  if (chars) {
    printf("%s", chars);
  }
  e->ReleaseStringUTFChars(s, chars);
}
