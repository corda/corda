#include "math.h"
#include "stdlib.h"
#include "sys/time.h"
#include "time.h"
#include "time.h"
#include "string.h"
#include "stdio.h"
#include "stdint.h"
#include "jni.h"
#include "jni-util.h"

#ifdef WIN32
#  include "windows.h"
#  define SO_PREFIX ""
#else
#  define SO_PREFIX "lib"
#endif

#ifdef __APPLE__
#  define SO_SUFFIX ".jnilib"
#elif defined WIN32
#  define SO_SUFFIX ".dll"
#else
#  define SO_SUFFIX ".so"
#endif

extern "C" JNIEXPORT jstring JNICALL
Java_java_lang_System_getProperty(JNIEnv* e, jclass, jstring name,
                                  jbooleanArray found)
{
  jstring r = 0;
  const char* chars = e->GetStringUTFChars(name, 0);
  if (chars) {
#ifdef WIN32 
    if (strcmp(chars, "line.separator") == 0) {
      r = e->NewStringUTF("\r\n");
    } else if (strcmp(chars, "file.separator") == 0) {
      r = e->NewStringUTF("\\");
    } else if (strcmp(chars, "os.name") == 0) {
      r = e->NewStringUTF("windows");
    } else if (strcmp(chars, "java.io.tmpdir") == 0) {
      TCHAR buffer[MAX_PATH];
      GetTempPath(MAX_PATH, buffer);
      r = e->NewStringUTF(buffer);
    } else if (strcmp(chars, "user.home") == 0) {
      LPWSTR home = _wgetenv(L"USERPROFILE");
      r = e->NewString(reinterpret_cast<jchar*>(home), lstrlenW(home));
    }
#else
    if (strcmp(chars, "line.separator") == 0) {
      r = e->NewStringUTF("\n");
    } else if (strcmp(chars, "file.separator") == 0) {
      r = e->NewStringUTF("/");
    } else if (strcmp(chars, "os.name") == 0) {
      r = e->NewStringUTF("posix");
    } else if (strcmp(chars, "java.io.tmpdir") == 0) {
      r = e->NewStringUTF("/tmp");
    } else if (strcmp(chars, "user.home") == 0) {
      r = e->NewStringUTF(getenv("HOME"));      
    }
#endif

    e->ReleaseStringUTFChars(name, chars);
  }

  if (r) {
    jboolean v = true;
    e->SetBooleanArrayRegion(found, 0, 1, &v);
  }

  return r;
}

extern "C" JNIEXPORT jlong JNICALL
Java_java_lang_System_currentTimeMillis(JNIEnv*, jclass)
{
#ifdef WIN32
  static LARGE_INTEGER frequency;
  static LARGE_INTEGER time;
  static bool init = true;

  if (init) {
    QueryPerformanceFrequency(&frequency);

    if (frequency.QuadPart == 0) {
      return 0;      
    }

    init = false;
  }

  QueryPerformanceCounter(&time);
  return static_cast<int64_t>
    (((static_cast<double>(time.QuadPart)) * 1000.0) /
     (static_cast<double>(frequency.QuadPart)));
#else
  timeval tv = { 0, 0 };
  gettimeofday(&tv, 0);
  return (static_cast<jlong>(tv.tv_sec) * 1000) +
    (static_cast<jlong>(tv.tv_usec) / 1000);
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_java_lang_System_doMapLibraryName(JNIEnv* e, jclass, jstring name)
{
  jstring r = 0;
  const char* chars = e->GetStringUTFChars(name, 0);
  if (chars) {
    unsigned nameLength = strlen(chars);
    unsigned size = sizeof(SO_PREFIX) + nameLength + sizeof(SO_SUFFIX);
    char buffer[size];
    snprintf(buffer, size, SO_PREFIX "%s" SO_SUFFIX, chars);
    r = e->NewStringUTF(buffer);

    e->ReleaseStringUTFChars(name, chars);
  }
  return r;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_java_lang_Math_sin(JNIEnv*, jclass, jdouble val)
{
  return sin(val);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_java_lang_Math_sqrt(JNIEnv*, jclass, jdouble val)
{
  return sqrt(val);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_java_lang_Math_pow(JNIEnv*, jclass, jdouble val, jdouble exp)
{
  return pow(val, exp);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_java_lang_Math_floor(JNIEnv*, jclass, jdouble val)
{
  return floor(val);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_java_lang_Math_ceil(JNIEnv*, jclass, jdouble val)
{
  return ceil(val);
}

extern "C" JNIEXPORT jint JNICALL
Java_java_lang_Double_fillBufferWithDouble(JNIEnv* e, jclass, jdouble val,
					   jbyteArray buffer, jint bufferSize) {
  jboolean isCopy;
  jbyte* buf = e->GetByteArrayElements(buffer, &isCopy);
  jint count = snprintf(reinterpret_cast<char*>(buf), bufferSize, "%g", val);
  e->ReleaseByteArrayElements(buffer, buf, 0);
  return count;
}
