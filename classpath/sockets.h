/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */


/*
 * This file represents a simple cross-platform JNI sockets API
 * It is used from different classes of the default Avian classpath
 */

#ifndef SOCKETS_H_
#define SOCKETS_H_

#include "avian/common.h"
#include "jni.h"
#include "jni-util.h"

#ifdef PLATFORM_WINDOWS
#  include <winsock2.h>

#  define ONLY_ON_WINDOWS(x)	x

#  ifndef ENOTCONN
#    define ENOTCONN WSAENOTCONN
#  endif
#else
#  include <netdb.h>
#  include <sys/socket.h>
#  include <netinet/in.h>
#  include <unistd.h>

#  define ONLY_ON_WINDOWS(x)
#  define SOCKET				int
#  define INVALID_SOCKET		-1
#  define SOCKET_ERROR			-1
#  define closesocket(x)		close(x)

#  define SD_RECEIVE			SHUT_RD
#  define SD_SEND				SHUT_WR
#  define SD_BOTH				SHUT_RDWR

#endif

namespace avian {
namespace classpath {
namespace sockets {

// Library initialization
void init(JNIEnv* ONLY_ON_WINDOWS(e));

// Socket initialization
SOCKET create(JNIEnv* e);
void connect(JNIEnv* e, SOCKET sock, long addr, short port);
void bind(JNIEnv* e, SOCKET sock, long addr, short port);
SOCKET accept(JNIEnv* e, SOCKET sock, long* client_addr, short* client_port);

// I/O
void send(JNIEnv* e, SOCKET sock, const char* buff_ptr, int buff_size);
int recv(JNIEnv* e, SOCKET sock, char* buff_ptr, int buff_size);

// Socket closing
void abort(JNIEnv* e, SOCKET sock);
void close(JNIEnv* e, SOCKET sock);
void close_input(JNIEnv* e, SOCKET sock);
void close_output(JNIEnv* e, SOCKET sock);

}
}
}
#endif /* SOCKETS_H_ */
