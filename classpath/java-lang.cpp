#include "sys/time.h"
#include "time.h"
#include "time.h"
#include "string.h"
#include "stdio.h"
#include "jni.h"
#include "jni-util.h"

#ifdef __APPLE__
#  define SO_SUFFIX ".jnilib"
#else
#  define SO_SUFFIX ".so"
#endif

#undef JNIEXPORT
#define JNIEXPORT __attribute__ ((visibility("default")))

extern "C" JNIEXPORT jstring JNICALL
Java_java_lang_System_getProperty(JNIEnv* e, jclass, jint code)
{
  enum {
    LineSeparator = 100,
    FileSeparator = 101,
    OsName = 102,
    JavaIoTmpdir = 103
  };

  switch (code) {
  case LineSeparator:
    return e->NewStringUTF("\n");
    
  case FileSeparator:
    return e->NewStringUTF("/");
    
  case OsName:
    return e->NewStringUTF("posix");

  case JavaIoTmpdir:
    return e->NewStringUTF("/tmp");

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

extern "C" JNIEXPORT jstring JNICALL
Java_java_lang_System_doMapLibraryName(JNIEnv* e, jclass, jstring name)
{
  jstring r = 0;
  const char* chars = e->GetStringUTFChars(name, 0);
  if (chars) {
    unsigned nameLength = strlen(chars);
    unsigned size = nameLength + 3 + sizeof(SO_SUFFIX);
    char buffer[size];
    snprintf(buffer, size, "lib%s" SO_SUFFIX, chars);
    r = e->NewStringUTF(buffer);

    e->ReleaseStringUTFChars(name, chars);
  }
  return r;
}

extern "C" JNIEXPORT jint JNICALL
Java_java_lang_Double_fillBufferWithDouble(JNIEnv *e, jclass, jdouble val,
					   jbyteArray buffer, jint bufferSize) {
  jboolean isCopy;
  jbyte* buf = e->GetByteArrayElements(buffer, &isCopy);
  jint count = snprintf(reinterpret_cast<char*>(buf), bufferSize, "%g", val);
  e->ReleaseByteArrayElements(buffer, buf, 0);
  return count;
}
