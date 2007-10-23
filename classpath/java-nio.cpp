#include <sys/types.h>
#include <sys/stat.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>

#ifdef WIN32
#  include <winsock2.h>
#  include <errno.h>
#else
#  include <fcntl.h>
#  include <errno.h>
#  include <netdb.h>
#  include <sys/select.h>
#endif

#include "jni.h"
#include "jni-util.h"

#undef JNIEXPORT
#define JNIEXPORT __attribute__ ((visibility("default")))

#define java_nio_channels_SelectionKey_OP_READ 1L
#define java_nio_channels_SelectionKey_OP_WRITE 4L
#define java_nio_channels_SelectionKey_OP_ACCEPT 16L

#ifdef WIN32
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

inline jbyteArray
errorString(JNIEnv* e, int n)
{
  return charsToArray(e, strerror(n));
}

inline jbyteArray
errorString(JNIEnv* e)
{
#ifdef WIN32
  const unsigned size = 64;
  char buffer[size];
  snprintf(buffer, size, "wsa code: %d", WSAGetLastError());
  return charsToArray(e, buffer);
#else
  return errorString(errno);
#endif
}

void
throwIOException(JNIEnv* e, const char* s)
{
  throwNew(e, "java/io/IOException", s);
}

void
throwIOException(JNIEnv* e, jbyteArray a)
{
  jbyte* s = static_cast<jbyte*>(e->GetPrimitiveArrayCritical(a, 0));
  throwIOException(e, reinterpret_cast<const char*>(s));
  e->ReleasePrimitiveArrayCritical(a, s, 0);
}

void
throwIOException(JNIEnv* e)
{
  throwIOException(e, errorString(e));
}

void
init(JNIEnv* e, sockaddr_in* address, jstring hostString, jint port)
{
  const char* chars = e->GetStringUTFChars(hostString, 0);
  if (chars) {
    hostent* host = gethostbyname(chars);
    e->ReleaseStringUTFChars(hostString, chars);
    if (host == 0) {
      //     herror("init: gethostbyname");
      throwIOException(e);
      return;
    }
    memset(address, 0, sizeof(sockaddr_in));
    address->sin_family = AF_INET;
    address->sin_port = htons(port);
    address->sin_addr = *reinterpret_cast<in_addr*>(host->h_addr_list[0]);
  }
}

inline bool
einProgress()
{
#ifdef WIN32
  return WSAGetLastError() == WSAEINPROGRESS
    or WSAGetLastError() == WSAEWOULDBLOCK;
#else
  return errno == EINPROGRESS;
#endif
}

inline bool
eagain()
{
#ifdef WIN32
  return WSAGetLastError() == WSAEINPROGRESS
    or WSAGetLastError() == WSAEWOULDBLOCK;
#else
  return errno == EAGAIN;
#endif
}

bool
makeNonblocking(JNIEnv* e, int d)
{
#ifdef WIN32
  u_long a = 1;
  int r = ioctlsocket(d, FIONBIO, &a);
  if (r != 0) {
    throwIOException(e);
    return false;
  }
#else
  int r = fcntl(d, F_SETFL, fcntl(d, F_GETFL) | O_NONBLOCK);
  if (r < 0) {
    throwIOException(e);
    return false;
  }
#endif
  return true;
}

void
doListen(JNIEnv* e, int s, sockaddr_in* address)
{
  int opt = 1;
  int r = ::setsockopt(s, SOL_SOCKET, SO_REUSEADDR,
                       reinterpret_cast<char*>(&opt), sizeof(int));
  if (r != 0) {
    throwIOException(e);
    return;
  }

  r = ::bind(s, reinterpret_cast<sockaddr*>(address), sizeof(sockaddr_in));
  if (r != 0) {
    throwIOException(e);
    return;
  }

  r = ::listen(s, 100);
  if (r != 0) {
    throwIOException(e);
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
    makeNonblocking(e, r);
    return r;
  } else {
    throwIOException(e);
  }
  return -1;
}

int
doRead(int fd, void* buffer, size_t count)
{
#ifdef WIN32
  return recv(fd, static_cast<char*>(buffer), count, 0);
#else
  return read(fd, buffer, count);
#endif
}

int
doWrite(int fd, const void* buffer, size_t count)
{
#ifdef WIN32
  return send(fd, static_cast<const char*>(buffer), count, 0);
#else
  return write(fd, buffer, count);
#endif
}

int
makeSocket(JNIEnv* e, bool blocking = false)
{
#ifdef WIN32
  static bool wsaInitialized = false;
  if (not wsaInitialized) {
    WSADATA data;
    int r = WSAStartup(MAKEWORD(2, 2), &data);
    if (r or LOBYTE(data.wVersion) != 2 or HIBYTE(data.wVersion) != 2) {
      throwIOException(e, "WSAStartup failed");
    }
  }
#endif

  int s = ::socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
  if (s < 0) { throwIOException(e); return s; }

  if (not blocking) makeNonblocking(e, s);

  return s;
}

} // namespace <anonymous>


extern "C" JNIEXPORT jint JNICALL
Java_java_nio_channels_ServerSocketChannel_natDoAccept(JNIEnv *e, jclass, jint socket)
{
  return ::doAccept(e, socket);
}

extern "C" JNIEXPORT jint JNICALL
Java_java_nio_channels_ServerSocketChannel_natDoListen(JNIEnv *e,
						       jclass,
						       jstring host,
						       jint port)
{
  int s = makeSocket(e);
  if (s < 0) return s;
  
  sockaddr_in address;
  init(e, &address, host, port);

  ::doListen(e, s, &address);
  return s;
}

extern "C" JNIEXPORT jint JNICALL
Java_java_nio_channels_SocketChannel_natDoConnect(JNIEnv *e,
						  jclass,
						  jstring host,
						  jint port,
						  jbooleanArray retVal)
{
  int s = makeSocket(e);
  
  sockaddr_in address;
  init(e, &address, host, port);
  
  jboolean connected = ::doConnect(e, s, &address);
  e->SetBooleanArrayRegion(retVal, 0, 1, &connected);
  
  return s;
}

extern "C" JNIEXPORT jint JNICALL
Java_java_nio_channels_SocketChannel_natRead(JNIEnv *e,
					     jclass,
					     jint socket,
					     jbyteArray buffer,
					     jint offset,
					     jint length)
{
  jboolean isCopy;
  uint8_t *buf = static_cast<uint8_t*>
    (e->GetPrimitiveArrayCritical(buffer, &isCopy));
  int r = ::doRead(socket, buf + offset, length);
  e->ReleasePrimitiveArrayCritical(buffer, buf, 0);
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
Java_java_nio_channels_SocketChannel_natWrite(JNIEnv *e,
					      jclass,
					      jint socket,
					      jbyteArray buffer,
					      jint offset,
					      jint length)
{
  jboolean isCopy;
  uint8_t *buf = static_cast<uint8_t*>
    (e->GetPrimitiveArrayCritical(buffer, &isCopy));
  int r = ::doWrite(socket, buf + offset, length);
  e->ReleasePrimitiveArrayCritical(buffer, buf, 0);
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
  if (r != 0 or size != sizeof(int) or error != 0) {
    throwIOException(e, errorString(e, error));
  }
}

extern "C" JNIEXPORT void JNICALL
Java_java_nio_channels_SocketChannel_natCloseSocket(JNIEnv *,
						    jclass,
						    jint socket)
{
  close(socket);
}

namespace {

class Pipe {
 public:
#ifdef WIN32
  // The Windows socket API only accepts socket file descriptors, not
  // pipe descriptors or others.  Thus, to implement
  // Selector.wakeup(), we make a socket connection via the loopback
  // interface and use it as a pipe.
  Pipe(JNIEnv* e): connected_(false), listener_(-1), reader_(-1), writer_(-1) {
    sockaddr_in address;
    address.sin_family = AF_INET;
    address.sin_port = 0;
    address.sin_addr.s_addr = inet_addr("127.0.0.1"); //INADDR_LOOPBACK;
    listener_ = makeSocket(e, false);
    ::doListen(e, listener_, &address);

    socklen_t length = sizeof(sockaddr_in);
    int r = getsockname(listener_, reinterpret_cast<sockaddr*>(&address),
                        &length);
    if (r) {
      throwIOException(e);
    }

    writer_ = makeSocket(e, true);
    connected_ = ::doConnect(e, writer_, &address);
  }

  void dispose() {
    if (listener_ >= 0) ::close(listener_);
    if (reader_ >= 0) ::close(reader_);
    if (writer_ >= 0) ::close(writer_);
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

    if (makeNonblocking(e, pipe[0])) {
      makeNonblocking(e, pipe[1]);
    }
  }

  void dispose() {
    ::close(pipe[0]);
    ::close(pipe[1]);
  }

  bool connected() {
    return true;
  }

  int reader() {
    return pipe[0];
  }

  int writer() {
    return pipe[1];
  }

 private:
  int pipe[2];
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
Java_java_nio_channels_SocketSelector_natClearWoken(JNIEnv *e, jclass, jlong state)
{
  SelectorState* s = reinterpret_cast<SelectorState*>(state);
  if (s->control.connected() and s->control.reader() >= 0) {
    char c;
    int r = ::doRead(s->control.reader(), &c, 1);
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
  
  if (interest & java_nio_channels_SelectionKey_OP_WRITE) {
    FD_SET(static_cast<unsigned>(socket), &(s->write));
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
  timeval time = { interval / 1000, (interval % 1000) * 1000 };
  int r = ::select(max + 1, &(s->read), &(s->write), &(s->except), &time);
  if (r < 0) {
    if (errno != EINTR) {
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
  
  if ((interest & java_nio_channels_SelectionKey_OP_WRITE)
      and FD_ISSET(socket, &(s->write))) {
    ready |= java_nio_channels_SelectionKey_OP_WRITE;
  }
  return ready;
}


