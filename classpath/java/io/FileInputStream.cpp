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
#  define READ _read
#else
#  include <unistd.h>
#  define CLOSE close
#  define READ read
#endif

namespace {

int
doRead(JNIEnv* e, jint fd, jbyte* data, jint length)
{
  int r = READ(fd, data, length);
  if (r > 0) {
    return r;
  } else if (r == 0) {
    return -1;
  } else {
    e->ThrowNew(e->FindClass("java/lang/IOException"), strerror(errno));
    return 0;
  }  
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_java_io_FileInputStream_read__I(JNIEnv* e, jclass, jint fd)
{
  jbyte data;
  int r = doRead(e, fd, &data, 1);
  if (r <= 0) {
    return -1;
  } else {
    return data;
  }
}

extern "C" JNIEXPORT jint JNICALL
Java_java_io_FileInputStream_read__I_3BII
(JNIEnv* e, jclass, jint fd, jbyteArray b, jint offset, jint length)
{
  jbyte* data = static_cast<jbyte*>(malloc(length));
  if (data == 0) {
    e->ThrowNew(e->FindClass("java/lang/OutOfMemoryError"), 0);
    return 0;    
  }

  int r = doRead(e, fd, data, length);

  e->SetByteArrayRegion(b, offset, length, data);

  free(data);

  return r;
}

extern "C" JNIEXPORT void JNICALL
Java_java_io_FileInputStream_close(JNIEnv* e, jclass, jint fd)
{
  int r = CLOSE(fd);
  if (r == -1) {
    e->ThrowNew(e->FindClass("java/lang/IOException"), strerror(errno));
  }
}
