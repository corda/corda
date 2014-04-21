/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "jni.h"
#include "avian/machine.h"
#include "sockets.h"
#include "jni-util.h"

using namespace avian::classpath::sockets;

extern "C" JNIEXPORT void JNICALL
Java_java_net_Socket_init(JNIEnv* e, jclass) {
	init(e);
}

extern "C" JNIEXPORT SOCKET JNICALL
Java_java_net_Socket_create(JNIEnv* e, jclass) {
	return create(e);
}

extern "C" JNIEXPORT void JNICALL
Java_java_net_Socket_connect(JNIEnv* e, jclass, SOCKET sock, long addr, short port) {
	connect(e, sock, addr, port);
}

extern "C" JNIEXPORT void JNICALL
Java_java_net_Socket_bind(JNIEnv* e, jclass, SOCKET sock, long addr, short port) {
	bind(e, sock, addr, port);
}

extern "C" JNIEXPORT void JNICALL
Java_java_net_Socket_abort(JNIEnv* e, jclass, SOCKET sock) {
	abort(e, sock);
}

extern "C" JNIEXPORT void JNICALL
Java_java_net_Socket_close(JNIEnv* e, jclass, SOCKET sock) {
	close(e, sock);
}

extern "C" JNIEXPORT void JNICALL
Java_java_net_Socket_closeOutput(JNIEnv* e, jclass, SOCKET sock) {
	close_output(e, sock);
}

extern "C" JNIEXPORT void JNICALL
Java_java_net_Socket_closeInput(JNIEnv* e, jclass, SOCKET sock) {
	close_input(e, sock);
}

extern "C" JNIEXPORT void JNICALL
Avian_java_net_Socket_send(vm::Thread* t, vm::object, uintptr_t* arguments) {		/* SOCKET s, object buffer_obj, int start_pos, int count  */
	SOCKET& s = *(reinterpret_cast<SOCKET*>(&arguments[0]));
	vm::object buffer_obj = reinterpret_cast<vm::object>(arguments[2]);
	int32_t& start_pos = *(reinterpret_cast<int32_t*>(&arguments[3]));
	int32_t& count = *(reinterpret_cast<int32_t*>(&arguments[4]));
	char* buffer = reinterpret_cast<char*>(&vm::byteArrayBody(t, buffer_obj, start_pos));
	avian::classpath::sockets::send((JNIEnv*)t, s, buffer, count);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_net_Socket_recv(vm::Thread* t, vm::object, uintptr_t* arguments) {		/* SOCKET s, object buffer_obj, int start_pos, int count  */
	SOCKET& s = *(reinterpret_cast<SOCKET*>(&arguments[0]));
	vm::object buffer_obj = reinterpret_cast<vm::object>(arguments[2]);
	int32_t& start_pos = *(reinterpret_cast<int32_t*>(&arguments[3]));
	int32_t& count = *(reinterpret_cast<int32_t*>(&arguments[4]));
	char* buffer = reinterpret_cast<char*>(&vm::byteArrayBody(t, buffer_obj, start_pos));
	return avian::classpath::sockets::recv((JNIEnv*)t, s, buffer, count);
}

extern "C" JNIEXPORT jint JNICALL
Java_java_net_InetAddress_ipv4AddressForName(JNIEnv* e,
                                             jclass,
                                             jstring name)
{
  const char* chars = e->GetStringUTFChars(name, 0);
  if (chars) {
#ifdef PLATFORM_WINDOWS
    hostent* host = gethostbyname(chars);
    e->ReleaseStringUTFChars(name, chars);
    if (host) {
      return ntohl(reinterpret_cast<in_addr*>(host->h_addr_list[0])->s_addr);
    } else {
      throwNew(e, "java/net/UnknownHostException", 0);
      return 0;
    }
#else
    addrinfo hints;
    memset(&hints, 0, sizeof(addrinfo));
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;

    addrinfo* result;
    int r = getaddrinfo(chars, 0, &hints, &result);
    e->ReleaseStringUTFChars(name, chars);

    if (r != 0) {
      throwNew(e, "java/net/UnknownHostException", 0);
      return 0;
    } else {
      int address = ntohl
        (reinterpret_cast<sockaddr_in*>(result->ai_addr)->sin_addr.s_addr);

      freeaddrinfo(result);
      return address;
    }
#endif
  } else {
    throwNew(e, "java/lang/OutOfMemoryError", 0);
    return 0;
  }
}

