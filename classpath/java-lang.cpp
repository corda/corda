#include "sys/time.h"
#include "time.h"
#include "time.h"
#include "string.h"
#include "jni.h"
#include "jni-util.h"

#undef JNIEXPORT
#define JNIEXPORT __attribute__ ((visibility("default")))

extern "C" JNIEXPORT jstring JNICALL
Java_java_lang_System_getProperty(JNIEnv* e, jclass, jint code)
{
  enum {
    LineSeparator = 100,
    OsName = 101
  };

  switch (code) {
  case LineSeparator:
    return e->NewStringUTF("\n");
    
  case OsName:
    return e->NewStringUTF("posix");

  default:
    throwNew(e, "java/lang/RuntimeException", 0);
    return 0;
  }
}

extern "C" JNIEXPORT jlong JNICALL
Java_java_lang_System_currentTimeMillis(JNIEnv*, jclass)
{
  timeval tv = { 0, 0 };
  gettimeofday(&tv, 0);
  return (static_cast<jlong>(tv.tv_sec) * 1000) +
    (static_cast<jlong>(tv.tv_usec) / 1000);
}
