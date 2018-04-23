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
#include <unistd.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <stdlib.h>
#include <errno.h>
#include <IAERequest.h>
#include <IAEResponse.h>
#include <se_trace.h>
#include <UnixCommunicationSocket.h>


UnixCommunicationSocket::UnixCommunicationSocket(const char* socketbase)
:mSocketBase(NULL), mWasTimeout(false), mTimeoutMseconds(0)
{
    //doing all this manually to ensure <new> based memory allocation across the solution
    size_t size = strlen(socketbase) + 1;
    //ensure a fairly decend size for socketbase is not overflowed
    if (size > 255)
        return;     //a socketbase of more than 255 chars may break the system on connect

    mSocketBase = new char[size];
    memset(mSocketBase, 0, size);
    strncpy(mSocketBase, socketbase, size);

    mSocket = -1;

    memset(&mStartTime, 0, sizeof(struct timeval));
}

UnixCommunicationSocket::UnixCommunicationSocket(int socket)
:mSocketBase(NULL), mWasTimeout(false), mTimeoutMseconds(0)
{
    mSocket = socket;

    memset(&mStartTime, 0, sizeof(struct timeval));
}

UnixCommunicationSocket::~UnixCommunicationSocket()
{
    disconnect();
    if (mSocketBase != NULL)
    {
        delete [] mSocketBase;
        mSocketBase = NULL;
    }
}

void UnixCommunicationSocket::disconnect()
{
    if (mSocket != -1){
        close(mSocket);
        mSocket = -1;
    }
}

bool UnixCommunicationSocket::setTimeout(uint32_t timeout_milliseconds)
{
    struct timeval timeout;      

    mTimeoutMseconds = timeout_milliseconds;
    timeout.tv_sec = timeout_milliseconds / 1000;

    uint32_t millisecondsRemainder = timeout_milliseconds % 1000;
    timeout.tv_usec =  millisecondsRemainder * 1000;

    int rc = 0;
    rc = setsockopt (mSocket, SOL_SOCKET, SO_RCVTIMEO, (char *)&timeout, sizeof(timeout));
    if (rc < 0)
        return false;

    rc = setsockopt (mSocket, SOL_SOCKET, SO_SNDTIMEO, (char *)&timeout, sizeof(timeout));
    if (rc < 0)
        return false;

    return true;
}

void UnixCommunicationSocket::MarkStartTime()
{
    gettimeofday(&mStartTime,NULL);
}

bool UnixCommunicationSocket::CheckForTimeout()
{
    mWasTimeout = false;

    if (mTimeoutMseconds == 0)
        return false;

    struct timeval now;
    gettimeofday(&now, NULL);
    
    uint32_t sec = (uint32_t) (now.tv_sec - mStartTime.tv_sec);
    int32_t usec = (int32_t) (now.tv_usec - mStartTime.tv_usec);

    uint32_t delta_msec = (uint32_t) (sec * 1000 + usec / 1000);

    if (delta_msec >= mTimeoutMseconds)
    {
        mWasTimeout = true;
        return true;
    }

    return false;
}

ssize_t UnixCommunicationSocket::writeRaw(const char* data, ssize_t length)
{
    MarkStartTime();

    if (mSocket == -1)
        return -1;

    ssize_t written = 0;
    do {
        ssize_t step = write(mSocket, data+written, length-written);

        if(step == -1 && errno == EINTR && CheckForTimeout() == false){
            SE_TRACE_WARNING("write was interrupted by signal\n");
            continue;
        }
        if (step < 0 || CheckForTimeout())
        {
            //this connection is probably closed
            disconnect();
            break;
        }

        written+=step;
    } while (written < length);

    return written;
}

char* UnixCommunicationSocket::readRaw(ssize_t length)
{
    if (mSocket == -1)
        return NULL;

    MarkStartTime();

    ssize_t total_read = 0;
    char * recBuf = NULL;
    recBuf = new char[length];
    memset(recBuf, 0, length);

    do {
        ssize_t step = read(mSocket, recBuf + total_read, length - total_read);
        if(step == -1 && errno == EINTR && CheckForTimeout() == false){
            SE_TRACE_WARNING("read was interrupted by signal\n");
            continue;
        }
        //check connection closed by peer
        if (step <= 0 || CheckForTimeout())
        {
            //this connection is probably closed
            disconnect();
            delete[] recBuf;
            recBuf = NULL;
            break;
        }

        total_read += step;
    } while (total_read < length);

    return recBuf;
}

//this will connect to the AESM by opening an Unix Socket
bool UnixCommunicationSocket::init()
{
    //init will always return directly with success if object was created with pre-existent socket
    if (mSocket == -1)
    {
        struct sockaddr_un serv_addr;

        mSocket = socket(AF_UNIX, SOCK_STREAM, 0);
        if(mSocket < 0)
        {
            return false;
        }

        memset(&serv_addr, 0, sizeof(struct sockaddr_un));
        serv_addr.sun_family = AF_UNIX;
        memset(serv_addr.sun_path, 0, sizeof(serv_addr.sun_path));
        strncpy(serv_addr.sun_path, mSocketBase, sizeof(serv_addr.sun_path));

        if( connect(mSocket, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) != 0)
        {
            return false;
        }
        if (mSocket < 0)
            return false;
    }
    return true;
}

int UnixCommunicationSocket::getSockDescriptor() {
    return mSocket;
}
