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
#include "AESMQueueManager.h"
#include "IAERequest.h"

#include <oal/error_report.h>

AESMQueueManager::AESMQueueManager(
        AESMWorkerThread *quotingThread,
        AESMWorkerThread *launchThread,
        AESMWorkerThread *platformServiceThread
        ) :
    m_quotingThread(quotingThread),
    m_launchThread(launchThread),
    m_platformServiceThread(platformServiceThread)
{
    startQueueThreads();
}

AESMQueueManager::~AESMQueueManager()
{
    delete  m_quotingThread;
    delete  m_launchThread;
    delete  m_platformServiceThread;
}

void AESMQueueManager::startQueueThreads()
{
    m_launchThread->start();
    m_quotingThread->start();
    m_platformServiceThread->start();
}

void AESMQueueManager::enqueue(RequestData* requestData)
{
    if(requestData != NULL && requestData->getRequest() != NULL)
    {
        switch (requestData->getRequest()->getRequestClass()) {
            case IAERequest::QUOTING_CLASS:
                m_quotingThread->enqueue(requestData);
                break;
            case IAERequest::LAUNCH_CLASS:
                m_launchThread->enqueue(requestData);
                break;
            case IAERequest::PLATFORM_CLASS:
                m_platformServiceThread->enqueue(requestData);
                break;
            default:   //if we reach this point, this could only mean a corrupted or a forged message. In any case, close the connection
                       // Closing the connection will translate in an IPC error on the client side in case of corruption (and we would be correct), or in unexpected manner for forged messages (the case of an attacker client)
                delete requestData;     //this will delete the socket also. 
                AESM_LOG_ERROR("Malformed request received (May be forged for attack)");
        }

    }
}

void AESMQueueManager::shutDown()
{
    m_launchThread->shutDown();
    m_quotingThread->shutDown();
    m_platformServiceThread->shutDown();
}
