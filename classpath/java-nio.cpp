/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <sys/types.h>
#include <sys/stat.h>
#include <string.h>
#include <stdlib.h>

#include "jni.h"
#include "jni-util.h"

#ifdef PLATFORM_WINDOWS
#  include <winsock2.h>
#  include <ws2tcpip.h>
#  include <errno.h>
#  ifdef _MSC_VER
#    define snprintf sprintf_s
#  else
#    include <unistd.h>
#  endif
#else
#  include <unistd.h>
#  include <fcntl.h>
#  include <errno.h>
#  include <netdb.h>
#  include <sys/select.h>
#  include <arpa/inet.h>
#  include <netinet/in.h>
#  include <netinet/ip.h>
#  include <netinet/tcp.h>
#  include <sys/socket.h>
#endif

#define java_nio_channels_SelectionKey_OP_READ 1L
#define java_nio_channels_SelectionKey_OP_WRITE 4L
#define java_nio_channels_SelectionKey_OP_CONNECT 8L
#define java_nio_channels_SelectionKey_OP_ACCEPT 16L

#ifdef PLATFORM_WINDOWS
typedef int socklen_t;
#endif

inline void* operator new(size_t, void* p) throw() { return p; }

namespace {

inline jbyteArray
charsToArray(JNIEnv* e, const char* s)
{
  unsigned length = strlen(s);
  jbyteArray a = e->NewByteArray(length + 1);
  e->SetByteArrayRegion(a, 0, length + 1, reinterpret_cast<const jbyte*>(s));
  return a;
}

inline void
doClose(int socket)
{
#ifdef PLATFORM_WINDOWS
  closesocket(socket);
#else
  close(socket);
#endif
}

inline jbyteArray
errorString(JNIEnv* e, int n)
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

inline jbyteArray
socketErrorString(JNIEnv* e, int n)
{
#ifdef PLATFORM_WINDOWS
  const unsigned size = 64;
  char buffer[size];
  snprintf(buffer, size, "wsa code: %d", n);
  return charsToArray(e, buffer);
#else
  return errorString(e, n);
#endif
}

inline jbyteArray
errorString(JNIEnv* e)
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

void
throwIOException(JNIEnv* e, const char* s)
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

void
throwIOException(JNIEnv* e)
{
  throwIOException(e, errorString(e));
}

void
throwSocketException(JNIEnv* e, const char* s)
{
  throwNew(e, "java/net/SocketException", s);
}

void
throwSocketException(JNIEnv* e, jbyteArray a)
{
  jbyte* s = static_cast<jbyte*>(e->GetPrimitiveArrayCritical(a, 0));
  throwSocketException(e, reinterpret_cast<const char*>(s));
  e->ReleasePrimitiveArrayCritical(a, s, 0);
}

void
throwSocketException(JNIEnv* e)
{
  throwSocketException(e, errorString(e));
}

void init(sockaddr_in* address, jint host, jint port)
{
  memset(address, 0, sizeof(sockaddr_in));
  address->sin_family = AF_INET;
  address->sin_port = htons(port);
  address->sin_addr.s_addr = htonl(host);
}

inline bool
einProgress(int error)
{
#ifdef PLATFORM_WINDOWS
  return error == WSAEINPROGRESS
    or error == WSAEWOULDBLOCK;
#else
  return error == EINPROGRESS;
#endif
}

inline bool
einProgress()
{
#ifdef PLATFORM_WINDOWS
  return WSAGetLastError() == WSAEINPROGRESS
    or WSAGetLastError() == WSAEWOULDBLOCK;
#else
  return errno == EINPROGRESS;
#endif
}

inline bool
eagain()
{
#ifdef PLATFORM_WINDOWS
  return WSAGetLastError() == WSAEINPROGRESS
    or WSAGetLastError() == WSAEWOULDBLOCK;
#else
  return errno == EAGAIN;
#endif
}

bool
setBlocking(JNIEnv* e, int d, bool blocking)
{
#ifdef PLATFORM_WINDOWS
  u_long a = (blocking ? 0 : 1);
  int r = ioctlsocket(d, FIONBIO, &a);
  if (r != 0) {
    throwIOException(e);
    return false;
  }
#else
  int r = fcntl(d, F_SETFL, (blocking
                             ? (fcntl(d, F_GETFL) & (~O_NONBLOCK))
                             : (fcntl(d, F_GETFL) | O_NONBLOCK)));
  if (r < 0) {
    throwIOException(e);
    return false;
  }
#endif
  return true;
}

bool
setTcpNoDelay(JNIEnv* e, int d, bool on)
{
  int flag = on;
  int r = setsockopt
    (d, IPPROTO_TCP, TCP_NODELAY, reinterpret_cast<char*>(&flag), sizeof(int));
  if (r < 0) {
    throwSocketException(e);
    return false;
  }
  return true;
}

void
doBind(JNIEnv* e, int s, sockaddr_in* address)
{
  { int opt = 1;
    int r = ::setsockopt(s, SOL_SOCKET, SO_REUSEADDR,
                         reinterpret_cast<char*>(&opt), sizeof(int));
    if (r != 0) {
      throwIOException(e);
      return;
    }
  }

#ifdef SO_NOSIGPIPE
  { int opt = 1;
    int r = ::setsockopt(s, SOL_SOCKET, SO_NOSIGPIPE,
                         reinterpret_cast<char*>(&opt), sizeof(int));
    if (r != 0) {
      throwIOException(e);
      return;
    }
  }
#endif

  { int r = ::bind
      (s, reinterpret_cast<sockaddr*>(address), sizeof(sockaddr_in));
    if (r != 0) {
      throwIOException(e);
      return;
    }
  }
}

void
doListen(JNIEnv* e, int s)
{
  int r = ::listen(s, 100);
  if (r != 0) {
    throwIOException(e);
  }
}

void
doFinishConnect(JNIEnv* e, int socket)
{
  int error;
  socklen_t size = sizeof(int);
  int r = getsockopt(socket, SOL_SOCKET, SO_ERROR,
                     reinterpret_cast<char*>(&error), &size);

  if (r != 0 or size != sizeof(int)) {
    throwIOException(e);
  } else if (error and not einProgress(error)) {
    throwIOException(e, socketErrorString(e, error));
  }
}

bool
doConnect(JNIEnv* e, int s, sockaddr_in* address)
{
  int r = ::connect(s, reinterpret_cast<sockaddr*>(address),
                    sizeof(sockaddr_in));
  if (r == 0) {
    return true;
  } else if (not einProgress()) {
    throwIOException(e);
    return false;
  } else {
    return false;
  }
}

int
doAccept(JNIEnv* e, int s)
{
  sockaddr address;
  socklen_t length = sizeof(address);
  int r = ::accept(s, &address, &length);
  if (r >= 0) {
    return r;
  } else if (errno != EINTR) {
    throwIOException(e);
  }
  return -1;
}

int
doRead(int fd, void* buffer, size_t count)
{
#ifdef PLATFORM_WINDOWS
  return recv(fd, static_cast<char*>(buffer), count, 0);
#else
  return read(fd, buffer, count);
#endif
}

int
doRecv(int fd, void* buffer, size_t count, int32_t* host, int32_t* port)
{
  sockaddr address;
  socklen_t length = sizeof(address);
  int r = recvfrom
    (fd, static_cast<char*>(buffer), count, 0, &address, &length);
  
  if (r > 0) {
    sockaddr_in a; memcpy(&a, &address, length);
    *host = ntohl(a.sin_addr.s_addr);
    *port = ntohs(a.sin_port);
  } else {
    *host = 0;
    *port = 0;
  }

  return r;
}

int
doWrite(int fd, const void* buffer, size_t count)
{
#ifdef PLATFORM_WINDOWS
  return send(fd, static_cast<const char*>(buffer), count, 0);
#else
  return write(fd, buffer, count);
#endif
}

int doSend(int fd, sockaddr_in* address, const void* buffer, size_t count)
{
  return sendto(fd,
                static_cast<const char*>(buffer),
                count,
                0,
                reinterpret_cast<sockaddr*>(address),
                sizeof(sockaddr_in));
}

int makeSocket(JNIEnv* e, int type = SOCK_STREAM, int protocol = IPPROTO_TCP)
{
  int s = ::socket(AF_INET, type, protocol);
  if (s < 0) {
    throwIOException(e);
    return s;
  }

  return s;
}

} // namespace <anonymous>


extern "C" JNIEXPORT jint JNICALL
Java_java_nio_channels_ServerSocketChannel_natDoAccept(JNIEnv *e, jclass, jint socket)
{
  return ::doAccept(e, socket);
}

extern "C" JNIEXPORT void JNICALL
    Java_java_nio_channels_ServerSocketChannel_natDoListen(JNIEnv* e,
                                                           jclass,
                                                           jint socket,
                                                           jint host,
                                                           jint port)
{
  sockaddr_in address;
  init(&address, host, port);

  ::doBind(e, socket, &address);
  if (e->ExceptionCheck())
    return;

  ::doListen(e, socket);
}

extern "C" JNIEXPORT void JNICALL
    Java_java_nio_channels_SocketChannel_bind(JNIEnv* e,
                                              jclass,
                                              jint socket,
                                              jint host,
                                              jint port)
{
  sockaddr_in address;
  init(&address, host, port);

  ::doBind(e, socket, &address);
}

extern "C" JNIEXPORT void JNICALL
    Java_java_nio_channels_DatagramChannel_bind(JNIEnv* e,
                                                jclass c,
                                                jint socket,
                                                jint host,
                                                jint port)
{
  Java_java_nio_channels_SocketChannel_bind(e, c, socket, host, port);
}

extern "C" JNIEXPORT void JNICALL
Java_java_nio_channels_SocketChannel_configureBlocking(JNIEnv *e,
                                                       jclass,
                                                       jint socket,
                                                       jboolean blocking)
{
  setBlocking(e, socket, blocking);
}

extern "C" JNIEXPORT void JNICALL
Java_java_nio_channels_DatagramChannel_configureBlocking(JNIEnv* e,
                                                         jclass c,
                                                         jint socket,
                                                         jboolean blocking)
{
  return Java_java_nio_channels_SocketChannel_configureBlocking
    (e, c, socket, blocking);
}

extern "C" JNIEXPORT void JNICALL
Java_java_nio_channels_SocketChannel_natSetTcpNoDelay(JNIEnv *e,
                                                      jclass,
                                                      jint socket,
                                                      jboolean on)
{
  setTcpNoDelay(e, socket, on);
}

extern "C" JNIEXPORT jboolean JNICALL
    Java_java_nio_channels_SocketChannel_natDoConnect(JNIEnv* e,
                                                      jclass,
                                                      jint socket,
                                                      jint host,
                                                      jint port)
{
  sockaddr_in address;
  init(&address, host, port);

  return ::doConnect(e, socket, &address);
}

extern "C" JNIEXPORT jint JNICALL
    Java_java_nio_channels_SocketChannel_makeSocket(JNIEnv* e, jclass)
{
  return makeSocket(e);
}

extern "C" JNIEXPORT jint JNICALL
    Java_java_nio_channels_DatagramChannel_makeSocket(JNIEnv* e, jclass)
{
  return makeSocket(e, SOCK_DGRAM, IPPROTO_UDP);
}

extern "C" JNIEXPORT jboolean JNICALL
    Java_java_nio_channels_DatagramChannel_connect(JNIEnv* e,
                                                   jclass,
                                                   jint socket,
                                                   jint host,
                                                   jint port)
{
  sockaddr_in address;
  init(&address, host, port);

  return ::doConnect(e, socket, &address);
}

extern "C" JNIEXPORT void JNICALL
Java_java_nio_channels_SocketChannel_natFinishConnect(JNIEnv *e,
                                                      jclass,
                                                      jint socket)
{
  doFinishConnect(e, socket);
}

extern "C" JNIEXPORT jint JNICALL
Java_java_nio_channels_SocketChannel_natRead(JNIEnv *e,
					     jclass,
					     jint socket,
					     jbyteArray buffer,
					     jint offset,
					     jint length,
                                             jboolean blocking)
{
  int r;
  if (blocking) {
    uint8_t* buf = static_cast<uint8_t*>(allocate(e, length));
    if (buf) {
      r = ::doRead(socket, buf, length);
      if (r > 0) {
        e->SetByteArrayRegion
          (buffer, offset, r, reinterpret_cast<jbyte*>(buf));
      }
      free(buf);
    } else {
      return 0;
    }
  } else {
    jboolean isCopy;
    uint8_t* buf = static_cast<uint8_t*>
      (e->GetPrimitiveArrayCritical(buffer, &isCopy));

    r = ::doRead(socket, buf + offset, length);

    e->ReleasePrimitiveArrayCritical(buffer, buf, 0);
  }

  if (r < 0) {
    if (eagain()) {
      return 0;
    } else {
      throwIOException(e);
    }
  } else if (r == 0) {
    return -1;
  }
  return r;
}

extern "C" JNIEXPORT jint JNICALL
Java_java_nio_channels_DatagramChannel_receive(JNIEnv* e,
                                               jclass,
                                               jint socket,
                                               jbyteArray buffer,
                                               jint offset,
                                               jint length,
                                               jboolean blocking,
                                               jintArray address)
{
  int r;
  int32_t host;
  int32_t port;
  if (blocking) {
    uint8_t* buf = static_cast<uint8_t*>(allocate(e, length));
    if (buf) {
      r = ::doRecv(socket, buf, length, &host, &port);
      if (r > 0) {
        e->SetByteArrayRegion
          (buffer, offset, r, reinterpret_cast<jbyte*>(buf));
      }
      free(buf);
    } else {
      return 0;
    }
  } else {
    jboolean isCopy;
    uint8_t* buf = static_cast<uint8_t*>
      (e->GetPrimitiveArrayCritical(buffer, &isCopy));

    r = ::doRecv(socket, buf + offset, length, &host, &port);

    e->ReleasePrimitiveArrayCritical(buffer, buf, 0);
  }

  if (r < 0) {
    if (eagain()) {
      return 0;
    } else {
      throwIOException(e);
    }
  } else if (r == 0) {
    return -1;
  } else {
    jint jhost = host; e->SetIntArrayRegion(address, 0, 1, &jhost);
    jint jport = port; e->SetIntArrayRegion(address, 1, 1, &jport);
  }

  return r;
}

extern "C" JNIEXPORT jint JNICALL
Java_java_nio_channels_SocketChannel_natWrite(JNIEnv *e,
					      jclass,
					      jint socket,
					      jbyteArray buffer,
					      jint offset,
					      jint length,
                                              jboolean blocking)
{
  int r;
  if (blocking) {
    uint8_t* buf = static_cast<uint8_t*>(allocate(e, length));
    if (buf) {
      e->GetByteArrayRegion
        (buffer, offset, length, reinterpret_cast<jbyte*>(buf));
      r = ::doWrite(socket, buf, length);
      free(buf);
    } else {
      return 0;
    }
  } else {
    jboolean isCopy;
    uint8_t* buf = static_cast<uint8_t*>
      (e->GetPrimitiveArrayCritical(buffer, &isCopy));

    r = ::doWrite(socket, buf + offset, length);

    e->ReleasePrimitiveArrayCritical(buffer, buf, 0);
  }

  if (r < 0) {
    if (eagain()) {
      return 0;
    } else {
      throwIOException(e);
    }
  }
  return r;
}

extern "C" JNIEXPORT jint JNICALL
Java_java_nio_channels_DatagramChannel_write(JNIEnv* e,
                                             jclass c,
                                             jint socket,
                                             jbyteArray buffer,
                                             jint offset,
                                             jint length,
                                             jboolean blocking)
{
  return Java_java_nio_channels_SocketChannel_natWrite
    (e, c, socket, buffer, offset, length, blocking);
}

extern "C" JNIEXPORT jint JNICALL
    Java_java_nio_channels_DatagramChannel_send(JNIEnv* e,
                                                jclass,
                                                jint socket,
                                                jint host,
                                                jint port,
                                                jbyteArray buffer,
                                                jint offset,
                                                jint length,
                                                jboolean blocking)
{
  sockaddr_in address;
  init(&address, host, port);

  int r;
  if (blocking) {
    uint8_t* buf = static_cast<uint8_t*>(allocate(e, length));
    if (buf) {
      e->GetByteArrayRegion(
          buffer, offset, length, reinterpret_cast<jbyte*>(buf));
      r = ::doSend(socket, &address, buf, length);
      free(buf);
    } else {
      return 0;
    }
  } else {
    jboolean isCopy;
    uint8_t* buf
        = static_cast<uint8_t*>(e->GetPrimitiveArrayCritical(buffer, &isCopy));

    r = ::doSend(socket, &address, buf + offset, length);

    e->ReleasePrimitiveArrayCritical(buffer, buf, 0);
  }

  if (r < 0) {
    if (eagain()) {
      return 0;
    } else {
      throwIOException(e);
    }
  }
  return r;
}

extern "C" JNIEXPORT void JNICALL
Java_java_nio_channels_SocketChannel_natThrowWriteError(JNIEnv *e,
							jclass,
							jint socket)
{
  int error;
  socklen_t size = sizeof(int);
  int r = getsockopt(socket, SOL_SOCKET, SO_ERROR,
		     reinterpret_cast<char*>(&error), &size);
  if (r != 0 or size != sizeof(int)) {
    throwIOException(e);
  } else if (error != 0) {
    throwIOException(e, socketErrorString(e, error));
  }
}

extern "C" JNIEXPORT void JNICALL
Java_java_nio_channels_SocketChannel_natCloseSocket(JNIEnv *,
						    jclass,
						    jint socket)
{
  doClose(socket);
}

extern "C" JNIEXPORT void JNICALL
Java_java_nio_channels_DatagramChannel_close(JNIEnv *,
                                             jclass,
                                             jint socket)
{
  doClose(socket);
}

namespace {

class Pipe {
 public:
#ifdef PLATFORM_WINDOWS
  // The Windows socket API only accepts socket file descriptors, not
  // pipe descriptors or others.  Thus, to implement
  // Selector.wakeup(), we make a socket connection via the loopback
  // interface and use it as a pipe.
  Pipe(JNIEnv* e): connected_(false), listener_(-1), reader_(-1), writer_(-1) {
    sockaddr_in address;
    address.sin_family = AF_INET;
    address.sin_port = 0;
    address.sin_addr.s_addr = inet_addr("127.0.0.1"); //INADDR_LOOPBACK;

    listener_ = makeSocket(e);
    if (e->ExceptionCheck()) return;

    setBlocking(e, listener_, false);

    ::doBind(e, listener_, &address);
    if (e->ExceptionCheck()) return;

    ::doListen(e, listener_);
    if (e->ExceptionCheck()) return;

    socklen_t length = sizeof(sockaddr_in);
    int r = getsockname(listener_, reinterpret_cast<sockaddr*>(&address),
                        &length);
    if (r) {
      throwIOException(e);
      return;
    }

    writer_ = makeSocket(e);
    if (e->ExceptionCheck()) return;

    setBlocking(e, writer_, true);
    connected_ = ::doConnect(e, writer_, &address);
  }

  void dispose() {
    if (listener_ >= 0) ::doClose(listener_);
    if (reader_ >= 0) ::doClose(reader_);
    if (writer_ >= 0) ::doClose(writer_);
  }

  bool connected() {
    return connected_;
  }

  void setConnected(bool v) {
    connected_ = v;
  }

  int listener() {
    return listener_;
  }

  void setListener(int v) {
    listener_ = v;
  }

  int reader() {
    return reader_;
  }

  void setReader(int v) {
    reader_ = v;
  }

  int writer() {
    return writer_;
  }

 private:
  bool connected_;
  int listener_;
  int reader_;
  int writer_;
#else
  Pipe(JNIEnv* e) {
    if (::pipe(pipe) != 0) {
      throwIOException(e);
      return;
    }

    if (setBlocking(e, pipe[0], false)) {
      setBlocking(e, pipe[1], false);
    }

    open_ = true;
  }

  void dispose() {
    ::doClose(pipe[0]);
    ::doClose(pipe[1]);
    open_ = false;
  }

  bool connected() {
    return open_;
  }

  int reader() {
    return pipe[0];
  }

  int writer() {
    return pipe[1];
  }

 private:
  int pipe[2];
  bool open_;
#endif
};

struct SelectorState {
  fd_set read;
  fd_set write;
  fd_set except;
  Pipe control;
  SelectorState(JNIEnv* e) : control(e) { }
};

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_java_nio_channels_SocketSelector_natInit(JNIEnv* e, jclass)
{
  void *mem = malloc(sizeof(SelectorState));
  if (mem) {
    SelectorState *s = new (mem) SelectorState(e);
    if (e->ExceptionCheck()) return 0;

    if (s) {
      FD_ZERO(&(s->read));
      FD_ZERO(&(s->write));
      FD_ZERO(&(s->except));
      return reinterpret_cast<jlong>(s);
    }
  }
  throwNew(e, "java/lang/OutOfMemoryError", 0);
  return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_java_nio_channels_SocketSelector_natWakeup(JNIEnv *e, jclass, jlong state)
{
  SelectorState* s = reinterpret_cast<SelectorState*>(state);
  if (s->control.connected()) {
    const char c = 1;
    int r = ::doWrite(s->control.writer(), &c, 1);
    if (r != 1) {
      throwIOException(e);
    }
  }
}

extern "C" JNIEXPORT void JNICALL
Java_java_nio_channels_SocketSelector_natClose(JNIEnv *, jclass, jlong state)
{
  SelectorState* s = reinterpret_cast<SelectorState*>(state);
  s->control.dispose();
  free(s);
}

extern "C" JNIEXPORT void JNICALL
Java_java_nio_channels_SocketSelector_natSelectClearAll(JNIEnv *, jclass,
							jint socket,
							jlong state)
{
  SelectorState* s = reinterpret_cast<SelectorState*>(state);
  FD_CLR(static_cast<unsigned>(socket), &(s->read));
  FD_CLR(static_cast<unsigned>(socket), &(s->write));
  FD_CLR(static_cast<unsigned>(socket), &(s->except));
}

extern "C" JNIEXPORT jint JNICALL
Java_java_nio_channels_SocketSelector_natSelectUpdateInterestSet(JNIEnv *,
								 jclass,
								 jint socket,
								 jint interest,
								 jlong state,
								 jint max)
{
  SelectorState* s = reinterpret_cast<SelectorState*>(state);
  if (interest & (java_nio_channels_SelectionKey_OP_READ |
		  java_nio_channels_SelectionKey_OP_ACCEPT)) {
    FD_SET(static_cast<unsigned>(socket), &(s->read));
    if (max < socket) max = socket;
  } else {
    FD_CLR(static_cast<unsigned>(socket), &(s->read));
  }
  
  if (interest & (java_nio_channels_SelectionKey_OP_WRITE |
		  java_nio_channels_SelectionKey_OP_CONNECT)) {
    FD_SET(static_cast<unsigned>(socket), &(s->write));
    FD_SET(static_cast<unsigned>(socket), &(s->except));
    if (max < socket) max = socket;
  } else {
    FD_CLR(static_cast<unsigned>(socket), &(s->write));
  }
  return max;
}

extern "C" JNIEXPORT jint JNICALL
Java_java_nio_channels_SocketSelector_natDoSocketSelect(JNIEnv *e, jclass,
							jlong state,
							jint max,
							jlong interval)
{
  SelectorState* s = reinterpret_cast<SelectorState*>(state);
  if (s->control.reader() >= 0) {
    int socket = s->control.reader();
    FD_SET(static_cast<unsigned>(socket), &(s->read));
    if (max < socket) max = socket;
  }

#ifdef PLATFORM_WINDOWS
  if (s->control.listener() >= 0) {
    int socket = s->control.listener();
    FD_SET(static_cast<unsigned>(socket), &(s->read));
    if (max < socket) max = socket;
  }

  if (not s->control.connected()) {
    int socket = s->control.writer();
    FD_SET(static_cast<unsigned>(socket), &(s->write));
    FD_SET(static_cast<unsigned>(socket), &(s->except));
    if (max < socket) max = socket;
  }
#endif

  timeval time;
  if (interval > 0) {
    time.tv_sec = interval / 1000;
    time.tv_usec = (interval % 1000) * 1000;
  } else if (interval < 0) {
    time.tv_sec = 0;
    time.tv_usec = 0;
  } else {
    time.tv_sec = 24 * 60 * 60 * 1000;
    time.tv_usec = 0;
  }
  int r = ::select(max + 1, &(s->read), &(s->write), &(s->except), &time);

  if (r < 0) {
    if (errno != EINTR) {
      throwIOException(e);
      return 0;
    }
  }

#ifdef PLATFORM_WINDOWS
  if (FD_ISSET(s->control.writer(), &(s->write)) or
      FD_ISSET(s->control.writer(), &(s->except)))
  {
    int socket = s->control.writer();
    FD_CLR(static_cast<unsigned>(socket), &(s->write));
    FD_CLR(static_cast<unsigned>(socket), &(s->except));

    int error;
    socklen_t size = sizeof(int);
    int r = getsockopt(socket, SOL_SOCKET, SO_ERROR,
                       reinterpret_cast<char*>(&error), &size);
    if (r != 0 or size != sizeof(int)) {
      throwIOException(e);
    } else if (error != 0) {
      throwIOException(e, socketErrorString(e, error));
    }
    s->control.setConnected(true);
  }

  if (s->control.listener() >= 0 and
      FD_ISSET(s->control.listener(), &(s->read)))
  {
    FD_CLR(static_cast<unsigned>(s->control.listener()), &(s->read));

    s->control.setReader(::doAccept(e, s->control.listener()));
    s->control.setListener(-1);
  }
#endif

  if (s->control.reader() >= 0 and
      FD_ISSET(s->control.reader(), &(s->read)))
  {
    FD_CLR(static_cast<unsigned>(s->control.reader()), &(s->read));

    char c;
    int r = 1;
    while (r == 1) {
      r = ::doRead(s->control.reader(), &c, 1);
    }
    if (r < 0 and not eagain()) {
      throwIOException(e);
    }
  }

  return r;
}

extern "C" JNIEXPORT jint JNICALL
Java_java_nio_channels_SocketSelector_natUpdateReadySet(JNIEnv *, jclass,
							jint socket,
							jint interest,
							jlong state)
{
  SelectorState* s = reinterpret_cast<SelectorState*>(state);
  jint ready = 0;
        
  if (FD_ISSET(socket, &(s->read))) {
    if (interest & java_nio_channels_SelectionKey_OP_READ) {
      ready |= java_nio_channels_SelectionKey_OP_READ;
    }
    
    if (interest & java_nio_channels_SelectionKey_OP_ACCEPT) {
      ready |= java_nio_channels_SelectionKey_OP_ACCEPT;
    }
  }
  
  if (FD_ISSET(socket, &(s->write)) or FD_ISSET(socket, &(s->except))) {
    if (interest & java_nio_channels_SelectionKey_OP_WRITE) {
      ready |= java_nio_channels_SelectionKey_OP_WRITE;
    }

    if (interest & java_nio_channels_SelectionKey_OP_CONNECT) {
      ready |= java_nio_channels_SelectionKey_OP_CONNECT;
    }    
  }

  return ready;
}


extern "C" JNIEXPORT jboolean JNICALL
Java_java_nio_ByteOrder_isNativeBigEndian(JNIEnv *, jclass)
{
  union {
    uint32_t i;
    char c[4];
  } u = {0x01020304};

  if (u.c[0] == 1)
    return JNI_TRUE;
  return JNI_FALSE;
}
