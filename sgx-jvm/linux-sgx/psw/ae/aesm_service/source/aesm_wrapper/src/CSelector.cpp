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
#include "CSelector.h"
#include "ICommunicationSocket.h"
#include <sys/epoll.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>

CSelector::CSelector(IServerSocket* serverSock) :
    m_serverSock(serverSock)
{
    m_connectedSockets.clear();
    FD_ZERO(&m_workingSet);
}


CSelector::~CSelector()
{
}

void CSelector::addSocket(ICommunicationSocket* socket)
{
    m_connectedSockets.push_back(socket);
}

void CSelector::removeSocket(ICommunicationSocket* socket)
{
    m_connectedSockets.remove(socket);
}

bool CSelector::select(int fd_term)
{
    int max_sd;
    std::list<ICommunicationSocket*>::const_iterator it = m_connectedSockets.begin();

    FD_ZERO(&m_workingSet);
    FD_SET(m_serverSock->getSockDescriptor(), &m_workingSet);
    max_sd = m_serverSock->getSockDescriptor();

    if (fd_term != -1) {
        // a pipe is setup to prevent select from blocking current thread
        FD_SET(fd_term, &m_workingSet);
        if (fd_term > max_sd)
            max_sd = fd_term;
    }

    for(; it!=m_connectedSockets.end(); ++it) {
        int sock_fd = (*it)->getSockDescriptor();
        if (sock_fd > max_sd)
            max_sd = sock_fd;

        FD_SET(sock_fd, &m_workingSet);
    }

    int rc = (int) TEMP_FAILURE_RETRY(::select(max_sd + 1, &m_workingSet, NULL, NULL, NULL));
    if (rc < 0) {
        throw "Select failed"; 
    }

    if (fd_term != -1 && FD_ISSET(fd_term, &m_workingSet))
        return false;

    return true;
}

bool CSelector::canAcceptConnection()
{
    if (FD_ISSET(m_serverSock->getSockDescriptor(), &m_workingSet)) {
        return true;
    } else {
        return false;
    }
}

std::list<ICommunicationSocket*> CSelector::getSocsWithNewContent()
{
    std::list<ICommunicationSocket*> socketswithContent;
    std::list<ICommunicationSocket*>::iterator it = m_connectedSockets.begin();

    while( it!=m_connectedSockets.end())
    {
        if (FD_ISSET((*it)->getSockDescriptor(), &m_workingSet)) {
            socketswithContent.push_back(*it);
            m_connectedSockets.erase(it++);
        } else {
            ++it;
        }
    }

    return socketswithContent;
}
