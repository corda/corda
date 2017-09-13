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
#include <UnixSocketFactory.h>
#include <UnixCommunicationSocket.h>

#include <stdlib.h>
#include <string.h>

#define MAX_SIZE 255

UnixSocketFactory::UnixSocketFactory(const char* socketbase)
:mSocketBase(NULL)
{
    //doing all this manually to ensure <new> based memory allocation across the solution
    ssize_t sizeInBytes = strnlen(socketbase, MAX_SIZE) + 1;
    
    //ensure a fairly decend size for socketbase is not overflowed
    if (sizeInBytes > MAX_SIZE)
        return;     //a socketbase of more than 255 chars may break the system on connect

    mSocketBase = new char[sizeInBytes];
    strncpy(mSocketBase, socketbase, sizeInBytes);
}

UnixSocketFactory::~UnixSocketFactory()
{
    if (mSocketBase != NULL)
        delete [] mSocketBase;
    mSocketBase = NULL;
}

ICommunicationSocket* UnixSocketFactory::NewCommunicationSocket()
{
    UnixCommunicationSocket* sock = new UnixCommunicationSocket(mSocketBase);
    bool initializationSuccessfull = sock->init();

    if (initializationSuccessfull == false)
    {
        delete sock;
        sock = NULL;
    }
    
    return sock;
}
