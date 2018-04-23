/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef SGX

#include <sys/types.h>
#include <sys/stat.h>
#include <string.h>
#include <stdlib.h>

#include "jni.h"
#include "jni-util.h"

#ifdef PLATFORM_WINDOWS
#include <winsock2.h>
#include <ws2tcpip.h>
#include <errno.h>
#ifdef _MSC_VER
#define snprintf sprintf_s
#else
#include <unistd.h>
#endif
#else
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <netdb.h>
#include <sys/select.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/tcp.h>
#include <sys/socket.h>
#endif

#define java_nio_channels_SelectionKey_OP_READ 1L
#define java_nio_channels_SelectionKey_OP_WRITE 4L
#define java_nio_channels_SelectionKey_OP_CONNECT 8L
#define java_nio_channels_SelectionKey_OP_ACCEPT 16L

#ifdef PLATFORM_WINDOWS
typedef int socklen_t;
#endif

inline void* operator new(size_t, void* p) throw()
{
  return p;
}

namespace {

inline jbyteArray charsToArray(JNIEnv* e, const char* s)
{
  unsigned length = strlen(s);
  jbyteArray a = e->NewByteArray(length + 1);
  e->SetByteArrayRegion(a, 0, length + 1, reinterpret_cast<const jbyte*>(s));
  return a;
}

inline void doClose(int socket)
{
#ifdef PLATFORM_WINDOWS
  closesocket(socket);
#else
  close(socket);
#endif
}

inline jbyteArray errorString(JNIEnv* e, int n)
{
#ifdef _MSC_VER
  const unsigned size = 128;
  char buffer[size];
  strerror_s(buffer, size, n);
  return charsToArray(e, buffer);
#else
  return charsToArray(e, strerror(n));
#endif
}

inline jbyteArray errorString(JNIEnv* e)
{
#ifdef PLATFORM_WINDOWS
  const unsigned size = 64;
  char buffer[size];
  snprintf(buffer, size, "wsa code: %d", WSAGetLastError());
  return charsToArray(e, buffer);
#else
  return errorString(e, errno);
#endif
}

void throwIOException(JNIEnv* e, const char* s)
{
  throwNew(e, "java/io/IOException", s);
}

void throwIOException(JNIEnv* e, jbyteArray a)
{
  size_t length = e->GetArrayLength(a);
  uint8_t* buf = static_cast<uint8_t*>(allocate(e, length));
  if (buf) {
    e->GetByteArrayRegion(a, 0, length, reinterpret_cast<jbyte*>(buf));
    throwIOException(e, reinterpret_cast<const char*>(buf));
    free(buf);
  } else {
    return;
  }
}

void throwIOException(JNIEnv* e)
{
  throwIOException(e, errorString(e));
}

inline bool einProgress(int error)
{
#ifdef PLATFORM_WINDOWS
  return error == WSAEINPROGRESS or error == WSAEWOULDBLOCK;
#else
  return error == EINPROGRESS;
#endif
}

bool setBlocking(JNIEnv* e, int d, bool blocking)
{
#ifdef PLATFORM_WINDOWS
  u_long a = (blocking ? 0 : 1);
  int r = ioctlsocket(d, FIONBIO, &a);
  if (r != 0) {
    throwIOException(e);
    return false;
  }
#else
  int r = fcntl(d,
                F_SETFL,
                (blocking ? (fcntl(d, F_GETFL) & (~O_NONBLOCK))
                          : (fcntl(d, F_GETFL) | O_NONBLOCK)));
  if (r < 0) {
    throwIOException(e);
    return false;
  }
#endif
  return true;
}

inline bool einProgress()
{
#ifdef PLATFORM_WINDOWS
  return WSAGetLastError() == WSAEINPROGRESS
         or WSAGetLastError() == WSAEWOULDBLOCK;
#else
  return errno == EINPROGRESS;
#endif
}

inline bool eagain()
{
#ifdef PLATFORM_WINDOWS
  return WSAGetLastError() == WSAEINPROGRESS
         or WSAGetLastError() == WSAEWOULDBLOCK;
#else
  return errno == EAGAIN;
#endif
}

}  // namespace <anonymous>

namespace {

class Pipe {
 public:
  Pipe(JNIEnv* e)
  {
    if (::pipe(pipe) != 0) {
      throwIOException(e);
      return;
    }

    if (setBlocking(e, pipe[0], false)) {
      setBlocking(e, pipe[1], false);
    }

    open_ = true;
  }

  void dispose()
  {
    ::doClose(pipe[0]);
    ::doClose(pipe[1]);
    open_ = false;
  }

  bool connected()
  {
    return open_;
  }

  int reader()
  {
    return pipe[0];
  }

  int writer()
  {
    return pipe[1];
  }

 private:
  int pipe[2];
  bool open_;
};

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
    Java_java_nio_ByteOrder_isNativeBigEndian(JNIEnv*, jclass)
{
  union {
    uint32_t i;
    char c[4];
  } u = {0x01020304};

  if (u.c[0] == 1)
    return JNI_TRUE;
  return JNI_FALSE;
}

#endif  // !SGX
