/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

/*
 * This file implements a simple cross-platform JNI sockets API
 * It is used from different classes of the default Avian classpath
 */

#include "sockets.h"

namespace avian {
namespace classpath {
namespace sockets {

int last_socket_error() {
#ifdef PLATFORM_WINDOWS
		int error = WSAGetLastError();
#else
		int error = errno;
#endif
		return error;
}


void init(JNIEnv* ONLY_ON_WINDOWS(e)) {
#ifdef PLATFORM_WINDOWS
  static bool wsaInitialized = false;
  if (not wsaInitialized) {
	WSADATA data;
	int r = WSAStartup(MAKEWORD(2, 2), &data);
	if (r or LOBYTE(data.wVersion) != 2 or HIBYTE(data.wVersion) != 2) {
	  throwNew(e, "java/io/IOException", "WSAStartup failed");
	} else {
	  wsaInitialized = true;
	}
  }
#endif
}

SOCKET create(JNIEnv* e) {
	SOCKET sock;
	if (INVALID_SOCKET == (sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP))) {
		char buf[255];
		sprintf(buf, "Can't create a socket. System error: %d", last_socket_error());
		throwNew(e, "java/io/IOException", buf);
		return 0;	// This doesn't matter cause we have risen an exception
	}
	return sock;
}

void connect(JNIEnv* e, SOCKET sock, long addr, short port) {
	sockaddr_in adr;
	adr.sin_family = AF_INET;
#ifdef PLATFORM_WINDOWS
	adr.sin_addr.S_un.S_addr = htonl(addr);
#else
	adr.sin_addr.s_addr = htonl(addr);
#endif
	adr.sin_port = htons (port);

	if (SOCKET_ERROR == ::connect(sock, (sockaddr* )&adr, sizeof(adr)))
	{
		char buf[255];
		sprintf(buf, "Can't connect a socket. System error: %d", last_socket_error());
		throwNew(e, "java/io/IOException", buf);
		return;
	}
}

void bind(JNIEnv* e, SOCKET sock, long addr, short port) {
	sockaddr_in adr;
	adr.sin_family = AF_INET;
#ifdef PLATFORM_WINDOWS
	adr.sin_addr.S_un.S_addr = htonl(addr);
#else
	adr.sin_addr.s_addr = htonl(addr);
#endif
	adr.sin_port = htons (port);

	if (SOCKET_ERROR == ::bind(sock, (sockaddr* )&adr, sizeof(adr)))
	{
		char buf[255];
		sprintf(buf, "Can't bind a socket. System error: %d", last_socket_error());
		throwNew(e, "java/io/IOException", buf);
		return;
	}
}

SOCKET accept(JNIEnv* e, SOCKET sock, long* client_addr, short* client_port) {
	sockaddr_in adr;
	SOCKET client_socket = ::accept(sock, (sockaddr* )&adr, NULL);
	if (INVALID_SOCKET == client_socket) {
		char buf[255];
		sprintf(buf, "Can't accept the incoming connection. System error: %d", last_socket_error());
		throwNew(e, "java/io/IOException", buf);
		return INVALID_SOCKET;
	}

	if (client_addr != NULL) {
	#ifdef PLATFORM_WINDOWS
		*client_addr = ntohl(adr.sin_addr.S_un.S_addr);
	#else
		*client_addr = ntohl(adr.sin_addr.s_addr);
	#endif
	}

	if (client_port != NULL) {
		*client_port = ntohs (adr.sin_port);
	}

	return client_socket;
}

void send(JNIEnv* e, SOCKET sock, const char* buff_ptr, int buff_size) {
	if (SOCKET_ERROR == ::send(sock, buff_ptr, buff_size, 0)) {
		char buf[255];
		sprintf(buf, "Can't send data through the socket. System error: %d", last_socket_error());
		throwNew(e, "java/io/IOException", buf);
		return;
	}
}

int recv(JNIEnv* e, SOCKET sock, char* buff_ptr, int buff_size) {
	int length = ::recv(sock, buff_ptr, buff_size, 0);
	if (SOCKET_ERROR == length) {
		char buf[255];
		sprintf(buf, "Can't receive data through the socket. System error: %d", last_socket_error());
		throwNew(e, "java/io/IOException", buf);
		return 0;	// This doesn't matter cause we have risen an exception
	}
	return length;
}

void abort(JNIEnv* e, SOCKET sock) {
	if (SOCKET_ERROR == ::closesocket(sock)) {
		char buf[255];
		sprintf(buf, "Can't close the socket. System error: %d", last_socket_error());
		throwNew(e, "java/io/IOException", buf);
	}
}

void close(JNIEnv* e, SOCKET sock) {
	if (SOCKET_ERROR == ::shutdown(sock, SD_BOTH)) {
		int errcode = last_socket_error();
		if (errcode != ENOTCONN) {
			char buf[255];
			sprintf(buf, "Can't shutdown the socket. System error: %d", errcode);
			throwNew(e, "java/io/IOException", buf);
		}
	}
}

void close_input(JNIEnv* e, SOCKET sock) {
	if (SOCKET_ERROR == ::shutdown(sock, SD_RECEIVE)) {
		int errcode = last_socket_error();
		if (errcode != ENOTCONN) {
			char buf[255];
			sprintf(buf, "Can't shutdown the socket. System error: %d", errcode);
			throwNew(e, "java/io/IOException", buf);
		}
	}
}

void close_output(JNIEnv* e, SOCKET sock) {
	if (SOCKET_ERROR == ::shutdown(sock, SD_SEND)) {
		int errcode = last_socket_error();
		if (errcode != ENOTCONN) {
			char buf[255];
			sprintf(buf, "Can't shutdown the socket. System error: %d", errcode);
			throwNew(e, "java/io/IOException", buf);
		}
	}
}

}
}
}
