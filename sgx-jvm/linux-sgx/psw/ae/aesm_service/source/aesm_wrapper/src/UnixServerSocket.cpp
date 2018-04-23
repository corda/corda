/*
 * Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *   * Neither the name of Intel Corporation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>
#include <errno.h>

#include "NonBlockingUnixCommunicationSocket.h"
#include "UnixServerSocket.h"

UnixServerSocket::UnixServerSocket(const char* socketbase, const unsigned int clientTimeout) :
    mSocketBase(socketbase),
    mSocket(-1),
    mClientTimeout(clientTimeout)
{
}

UnixServerSocket::~UnixServerSocket() {
    if (mSocket > 0) {
        unlink(mSocketBase);
        close(mSocket);
    }
}

void UnixServerSocket::init()
{
    /* init will always return directly with success if object was created with pre-existent socket */
    if (mSocket > 0)
        return;

    struct sockaddr_un server_address;

    mSocket = socket(AF_UNIX, SOCK_STREAM, 0);
    if (mSocket < 0) {
        throw("Failed to create socket");
    }

    server_address.sun_family = AF_UNIX;
    memset(server_address.sun_path, 0, sizeof(server_address.sun_path));
    strncpy(server_address.sun_path, mSocketBase, sizeof(server_address.sun_path));
    unlink(server_address.sun_path);

    socklen_t server_len = sizeof(server_address);
    int rc = bind(mSocket, (sockaddr*)&server_address, server_len);
    if (rc < 0) {
        close(mSocket);
        throw("Failed to create socket");
    }

    chmod(mSocketBase, 0777);

    rc = listen(mSocket, 32);
    if (rc < 0) {
        close(mSocket);
        throw("Error listening on socket"); 
    }
}

ICommunicationSocket* UnixServerSocket::accept()
{
    int client_sockfd = (int) TEMP_FAILURE_RETRY(::accept(mSocket, NULL, NULL));
    if (client_sockfd < 0)
        return NULL;

    NonBlockingUnixCommunicationSocket* sock = new NonBlockingUnixCommunicationSocket(client_sockfd);

    bool initializationSuccessfull = sock->init();

    if (initializationSuccessfull == false)
    {
        delete sock;
        sock = NULL;
    }
    else
    {
        //set AESM specific timeout for operations with client sockets
        sock->setTimeout(mClientTimeout);
    }

    return sock;
}
