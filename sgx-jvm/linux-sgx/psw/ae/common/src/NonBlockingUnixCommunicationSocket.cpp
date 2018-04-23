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

#ifndef __STDC_LIMIT_MACROS
#define __STDC_LIMIT_MACROS
#endif
#include <stdint.h>
#include <NonBlockingUnixCommunicationSocket.h>
#include <arch.h>
#include <sys/types.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <sys/epoll.h>
#include <string.h>
#include <se_trace.h>
NonBlockingUnixCommunicationSocket::~NonBlockingUnixCommunicationSocket()
{
   if (mEvents != NULL)
        delete [] mEvents;

   close(mEpoll);
   close(mCommandPipe[0]);
   close(mCommandPipe[1]);

}

se_static_assert(MAX_EVENTS<=UINT32_MAX/sizeof(struct epoll_event));
bool  NonBlockingUnixCommunicationSocket::init()
{
    //create the epoll structure
    mEpoll = epoll_create(1); 
    if (mEpoll < 0)
        return false;
  
    //create the command pipe
    int ret;
    ret = pipe(mCommandPipe);
    if (ret != 0)
    {
        close(mEpoll);
        return false;        
    }

    //place one end of the pipe in the epoll list
    struct epoll_event event;
    event.data.fd = mCommandPipe[0];
    event.events = EPOLLIN | EPOLLET;
    int registerCommand = epoll_ctl (mEpoll, EPOLL_CTL_ADD, mCommandPipe[0], &event);

    //connect to the AESM - blocking connect    
    bool connectInit = UnixCommunicationSocket::init();

    //register the event
    event.data.fd = mSocket;
    event.events = EPOLLET;
    int registerSocket = epoll_ctl (mEpoll, EPOLL_CTL_ADD, mSocket, &event);

    if (registerCommand != 0 || registerSocket != 0 || connectInit == false)
    {
        close(mEpoll);
        close(mCommandPipe[0]);
        close(mCommandPipe[1]);
        
        return false;
    }

    //create events buffer
    mEvents = new  struct epoll_event[MAX_EVENTS];
    memset((char*)mEvents, 0, MAX_EVENTS * sizeof(struct epoll_event));

    return MakeNonBlocking();
}

char* NonBlockingUnixCommunicationSocket::readRaw(ssize_t length)
{
    if (mSocket == -1)
        return NULL;

    // Add read event
    struct epoll_event event;
    event.data.fd = mSocket;
    event.events = EPOLLIN | EPOLLET;
    int registerSocket = epoll_ctl (mEpoll, EPOLL_CTL_MOD, mSocket, &event);
    if (registerSocket != 0)
    {
        return NULL;
    }

    ssize_t total_read = 0;
    ssize_t step = 0;
    char * recBuf = NULL;
    recBuf = new char[length];
    memset(recBuf, 0, length);

    int32_t epollTimeout = (mTimeoutMseconds > 0 ? mTimeoutMseconds : -1);
    int eventNum = 0;
    int i = 0;
    bool errorDetected = false;
    bool cancellationDetected = false;
    bool peerSocketClosed = false;


    MarkStartTime();
    
    do{
        //try a direct read (maybe all data is available already)
        step = read(mSocket, recBuf, length);
        if(step == -1 && errno == EINTR && CheckForTimeout() == false){
             SE_TRACE_WARNING("read is interrupted by signal\n");
             continue;
        }
        if (step == -1 && errno != EAGAIN)
        {
            errorDetected = true;
        }
        else
        {   
            if (step != -1)
            {
                total_read += step;
            }

            if (total_read == length)
            {
                break;  //we are finished here
            }
        }

        //wait for events
        do {
            eventNum = epoll_wait(mEpoll, mEvents, MAX_EVENTS, epollTimeout);
        } while (eventNum == -1 && errno == EINTR && CheckForTimeout() == false);

        if (eventNum == -1)
        {
            errorDetected = true;
        }
   
        for (i = 0; 
                CheckForTimeout() == false      &&      //need to be sure to check this first  
                errorDetected == false          && 
                cancellationDetected == false   &&
                peerSocketClosed == false       &&
                i < eventNum; 
             i++)
            {
                if (mEvents[i].events & EPOLLHUP) 
                {
                    peerSocketClosed = true;
                    //peer closed socket. one more reading all remaining data.
                }
                if ((mEvents[i].events & EPOLLERR) ||
                    (!(mEvents[i].events & EPOLLIN)))
                {
                    //error
                    errorDetected = true;
                }
                else
                {
                    if (mEvents[i].data.fd == mCommandPipe[0])
                    {
                        //cancellation -- in the case this logic would complicate by needing more commands, we will detach this into
                        //a CommandManager of some sort
                        cancellationDetected = true;    
                    }
                    else
                    {
                        //read data
                        step = partialRead(recBuf + total_read, length - total_read);
                        if (step == -1)
                        {
                            errorDetected = true;
                        }
                        if (step == 0)  //peer closed socket
                        {
                            //did this happen before getting the entire data ?
                            if (total_read != length)
                                errorDetected = true;   
                        }
                        total_read += step;
                    }
                }

        }
    
        if (total_read != length)
        {
            if (errorDetected || cancellationDetected || wasTimeoutDetected())
            {
                disconnect();
                delete [] recBuf;
                recBuf = NULL;
                break;
            }
        }

        //clear events
        memset((char*)mEvents, 0, MAX_EVENTS * sizeof(struct epoll_event));

    }while (total_read < length);

    event.data.fd = mSocket;
    event.events = EPOLLET;
    registerSocket = epoll_ctl (mEpoll, EPOLL_CTL_MOD, mSocket, &event);
    if (registerSocket != 0)
    {
        disconnect();

        if (NULL != recBuf)
            delete [] recBuf;
        return NULL;
    }

    return recBuf;
}

/**
* Read no more than maxLength bytes
*/
ssize_t NonBlockingUnixCommunicationSocket::partialRead(char* buffer, ssize_t maxLength)
{
    ssize_t step = 0;
    ssize_t chunkSize = (maxLength < 512 ? maxLength : 512);
    ssize_t totalRead = 0;
    ssize_t remaining = maxLength;

    while (totalRead < maxLength)
    {
        remaining = maxLength - totalRead;        

        step = read(mSocket, buffer + totalRead,  (remaining > chunkSize ? chunkSize : remaining));

        if(step == -1 && errno == EINTR && CheckForTimeout() == false){
             SE_TRACE_WARNING("read was interrupted by signal\n");
             continue;
        }

        if (step == -1)
        {
            if (errno != EAGAIN)
                return -1;
            break;
        }
        
        totalRead += step;
        if (step == 0)
            break;
    } 
    return totalRead;
}

ssize_t  NonBlockingUnixCommunicationSocket::writeRaw(const char* data, ssize_t length)
{
    if (mSocket == -1)
        return -1; 

    ssize_t total_write = 0;
    ssize_t step = 0;

    int32_t epollTimeout = (mTimeoutMseconds > 0 ? mTimeoutMseconds : -1);
    int eventNum = 0;
    int i = 0;
    bool errorDetected = false;
    bool cancellationDetected = false;
    bool peerSocketClosed = false;

    bool lastWriteSuccessful = false;
    struct epoll_event event;
    int registerSocket;

    MarkStartTime();

    do
    {
        step = write(mSocket, data + total_write, length - total_write);
        if(step == -1 && errno == EINTR && CheckForTimeout() == false){
             SE_TRACE_WARNING("write was interrupted by signal\n");
             continue;
        }

        if (step == -1 && errno != EAGAIN)
        {
            // an error occured
            errorDetected = true;
        }
        else
        {
            if (step == -1 && errno == EAGAIN)
            {
                // the internal buffer is full

                // EPOLLOUT is added so that an event is generated when there is
                // empty space in the buffer
                lastWriteSuccessful = false;

                event.data.fd = mSocket;
		event.events = EPOLLET | EPOLLOUT;
		registerSocket = epoll_ctl (mEpoll, EPOLL_CTL_MOD, mSocket, &event);
		if (registerSocket != 0)
                {
                    return -1;
                }
	    }
            else
            {
                // the write was successful

                if (!lastWriteSuccessful)
                {
                    // remove EPOLLOUT
                    lastWriteSuccessful = true;

                    event.data.fd = mSocket;
                    event.events = EPOLLET;
                    registerSocket = epoll_ctl (mEpoll, EPOLL_CTL_MOD, mSocket, &event);
                    if (registerSocket != 0)
                    {
                        return -1;
                    }
                }

                total_write += step;

                if (total_write == length)
                {
                    break;
                }

                continue;
            }
        }
        do {
            eventNum = epoll_wait(mEpoll, mEvents, MAX_EVENTS, epollTimeout);
        } while (eventNum == -1 && errno == EINTR && CheckForTimeout() == false);
        if (eventNum == -1)
        {
            errorDetected = true;
        }

        for (i = 0;
                CheckForTimeout() == false    &&
                errorDetected == false        &&
                cancellationDetected == false &&
                peerSocketClosed == false     &&
                i < eventNum;
             i++)
        {
           if (mEvents[i].events & EPOLLHUP)
           {
               // the socket or that pipe have been closed
               peerSocketClosed = true;
               continue;
           }
           if ((mEvents[i].events & EPOLLERR) ||
               (!(mEvents[i].events & EPOLLOUT)))
           {
               // received an event other than EPOLLOUT
               errorDetected = true;
           }
           else
           {
               if (mEvents[i].data.fd == mCommandPipe[0])
               {
                   cancellationDetected = true;
               }
           }
        }

        if (errorDetected || cancellationDetected || wasTimeoutDetected() || peerSocketClosed)
        {
            disconnect();
            break;
        }

        memset((char*)mEvents, 0, MAX_EVENTS * sizeof(struct epoll_event));
    }
    while(total_write < length);

    event.data.fd = mSocket;
    event.events = EPOLLET;
    registerSocket = epoll_ctl (mEpoll, EPOLL_CTL_MOD, mSocket, &event);
    if (registerSocket != 0)
    {
        return -1;
    }

    return total_write;
}

int   NonBlockingUnixCommunicationSocket::getSockDescriptor()
{
    return UnixCommunicationSocket::getSockDescriptor();
}

bool  NonBlockingUnixCommunicationSocket::wasTimeoutDetected()
{
    return UnixCommunicationSocket::wasTimeoutDetected();
}

bool  NonBlockingUnixCommunicationSocket::setTimeout(uint32_t milliseconds)
{
    mTimeoutMseconds = milliseconds;
    return true; 
}

bool  NonBlockingUnixCommunicationSocket::MakeNonBlocking()
{
    int flags, ret;

    flags = fcntl (mSocket, F_GETFL, 0);
    if (flags == -1)
    {
        return false;
    }

    flags |= (int)O_NONBLOCK;
    ret = fcntl (mSocket, F_SETFL, flags);
    if (ret == -1)
    {
        return false;
    }

    return true;
}

void  NonBlockingUnixCommunicationSocket::Cancel() const
{
    //write anything on the pipe
    char cmd = '1';
    if (write(mCommandPipe[0],&cmd,1) < 0)
    {
        // do nothing
    }
}
