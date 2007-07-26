#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>

#include "jni.h"

#undef JNIEXPORT
#define JNIEXPORT __attribute__ ((visibility("default")))

#ifdef WIN32
#  include <io.h>
#  define CLOSE _close
#  define WRITE _write
#else
#  include <unistd.h>
#  define CLOSE close
#  define WRITE write
#endif

namespace {

void
doWrite(JNIEnv* e, jint fd, const jbyte* data, jint length)
{
  int r = WRITE(fd, data, length);
  if (r != length) {
    e->ThrowNew(e->FindClass("java/lang/IOException"), strerror(errno));
  }  
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_java_io_FileOutputStream_write__II(JNIEnv* e, jclass, jint fd, jint c)
{
  jbyte data = c;
  doWrite(e, fd, &data, 1);
}

extern "C" JNIEXPORT void JNICALL
Java_java_io_FileOutputStream_write__I_3BII
(JNIEnv* e, jclass, jint fd, jbyteArray b, jint offset, jint length)
{
  jbyte* data = static_cast<jbyte*>(malloc(length));
  if (data == 0) {
    e->ThrowNew(e->FindClass("java/lang/OutOfMemoryError"), 0);
    return;    
  }

  e->GetByteArrayRegion(b, offset, length, data);

  if (not e->ExceptionCheck()) {
    doWrite(e, fd, data, length);
  }

  free(data);
}

extern "C" JNIEXPORT void JNICALL
Java_java_io_FileOutputStream_close(JNIEnv* e, jclass, jint fd)
{
  int r = CLOSE(fd);
  if (r == -1) {
    e->ThrowNew(e->FindClass("java/lang/IOException"), strerror(errno));
  }
}
