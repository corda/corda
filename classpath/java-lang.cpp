#include "sys/time.h"
#include "time.h"
#include "time.h"
#include "string.h"
#include "jni.h"

#undef JNIEXPORT
#define JNIEXPORT __attribute__ ((visibility("default")))

extern "C" JNIEXPORT jstring JNICALL
Java_java_lang_System_getProperty(JNIEnv* e, jclass, jstring key)
{
  jstring value = 0;

  const char* chars = e->GetStringUTFChars(key, 0);
  if (chars) {
    if (strcmp(chars, "line.separator") == 0) {
      value = e->NewStringUTF("\n");
    } else if (strcmp(chars, "os.name") == 0) {
      value = e->NewStringUTF("posix");
    }
    e->ReleaseStringUTFChars(key, chars);
  }

  return value;
}

extern "C" JNIEXPORT jlong JNICALL
Java_java_lang_System_currentTimeMillis(JNIEnv*, jclass)
{
  timeval tv = { 0, 0 };
  gettimeofday(&tv, 0);
  return (static_cast<jlong>(tv.tv_sec) * 1000) +
    (static_cast<jlong>(tv.tv_usec) / 1000);
}
