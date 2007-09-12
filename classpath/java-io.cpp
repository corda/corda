#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>

#include "jni.h"
#include "jni-util.h"

#undef JNIEXPORT
#define JNIEXPORT __attribute__ ((visibility("default")))

#ifdef WIN32
#  include <io.h>
#  define OPEN _open
#  define CLOSE _close
#  define READ _read
#  define WRITE _write
#  define STAT _stat
#  define STRUCT_STAT struct _stat
#  define MKDIR(path, mode) _mkdir(path)
#  define CREAT _creat
#  define UNLINK _unlink
#  define OPEN_MASK O_BINARY
#else
#  include <unistd.h>
#  define OPEN open
#  define CLOSE close
#  define READ read
#  define WRITE write
#  define STAT stat
#  define STRUCT_STAT struct stat
#  define MKDIR mkdir
#  define CREAT creat
#  define UNLINK unlink
#  define OPEN_MASK 0
#endif

namespace {

inline bool
exists(const char* path)
{
  STRUCT_STAT s;
  return STAT(path, &s) == 0;
}

inline int
doOpen(JNIEnv* e, const char* path, int mask)
{
  int fd = OPEN(path, mask | OPEN_MASK, S_IRUSR | S_IWUSR);
  if (fd == -1) {
    throwNew(e, "java/io/IOException", strerror(errno));
  }
  return fd;
}

inline void
doClose(JNIEnv* e, jint fd)
{
  int r = CLOSE(fd);
  if (r == -1) {
    throwNew(e, "java/io/IOException", strerror(errno));
  }
}

inline int
doRead(JNIEnv* e, jint fd, jbyte* data, jint length)
{
  int r = READ(fd, data, length);
  if (r > 0) {
    return r;
  } else if (r == 0) {
    return -1;
  } else {
    throwNew(e, "java/io/IOException", strerror(errno));
    return 0;
  }  
}

inline void
doWrite(JNIEnv* e, jint fd, const jbyte* data, jint length)
{
  int r = WRITE(fd, data, length);
  if (r != length) {
    throwNew(e, "java/io/IOException", strerror(errno));
  }  
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_java_io_File_toCanonicalPath(JNIEnv* /*e*/, jclass, jstring path)
{
  // todo
  return path;
}

extern "C" JNIEXPORT jstring JNICALL
Java_java_io_File_toAbsolutePath(JNIEnv* /*e*/, jclass, jstring path)
{
  // todo
  return path;
}

extern "C" JNIEXPORT jlong JNICALL
Java_java_io_File_length(JNIEnv* e, jclass, jstring path)
{
  const char* chars = e->GetStringUTFChars(path, 0);
  if (chars) {
    STRUCT_STAT s;
    int r = STAT(chars, &s);
    if (r == 0) {
      return s.st_size;
    }
    e->ReleaseStringUTFChars(path, chars);
  }

  return -1;
}

extern "C" JNIEXPORT void JNICALL
Java_java_io_File_mkdir(JNIEnv* e, jclass, jstring path)
{
  const char* chars = e->GetStringUTFChars(path, 0);
  if (chars) {
    if (not exists(chars)) {
      int r = ::MKDIR(chars, 0700);
      if (r != 0) {
        throwNew(e, "java/io/IOException", strerror(errno));
      }
    }
    e->ReleaseStringUTFChars(path, chars);
  }
}

extern "C" JNIEXPORT void JNICALL
Java_java_io_File_createNewFile(JNIEnv* e, jclass, jstring path)
{
  const char* chars = e->GetStringUTFChars(path, 0);
  if (chars) {
    if (not exists(chars)) {
      int fd = CREAT(chars, 0600);
      if (fd == -1) {
        throwNew(e, "java/io/IOException", strerror(errno));
      } else {
        doClose(e, fd);
      }
    }
    e->ReleaseStringUTFChars(path, chars);
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_java_io_File_delete(JNIEnv* e, jclass, jstring path)
{
  const char* chars = e->GetStringUTFChars(path, 0);
  int r = -1;
  if (chars) {
    r = UNLINK(chars);
    e->ReleaseStringUTFChars(path, chars);
  }
  return r == 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_java_io_File_exists(JNIEnv* e, jclass, jstring path)
{
  const char* chars = e->GetStringUTFChars(path, 0);
  if (chars) {
    bool v = exists(chars);
    e->ReleaseStringUTFChars(path, chars);
    return v;
  } else {
    return false;
  }
}

extern "C" JNIEXPORT jint JNICALL
Java_java_io_FileInputStream_open(JNIEnv* e, jclass, jstring path)
{
  const char* chars = e->GetStringUTFChars(path, 0);
  if (chars) {
    int fd = doOpen(e, chars, O_RDONLY);
    e->ReleaseStringUTFChars(path, chars);
    return fd;
  } else {
    return -1;
  }
}

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
    throwNew(e, "java/lang/OutOfMemoryError", 0);
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
  doClose(e, fd);
}

extern "C" JNIEXPORT jint JNICALL
Java_java_io_FileOutputStream_open(JNIEnv* e, jclass, jstring path)
{
  const char* chars = e->GetStringUTFChars(path, 0);
  if (chars) {
    int fd = doOpen(e, chars, O_WRONLY | O_CREAT);
    e->ReleaseStringUTFChars(path, chars);
    return fd;
  } else {
    return -1;
  }
}

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
    throwNew(e, "java/lang/OutOfMemoryError", 0);
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
  doClose(e, fd);
}
