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
#include "CAESMServer.h"
#include "SocketTransporter.h"
#include "ProtobufSerializer.h"
#include "RequestData.h"
#include "AESMQueue.h"
#include "AESMWorkerThread.h"

#include <string>
#include <aesm_exception.h>
#include <unistd.h>



/*static*/
AESMQueueManager* CAESMServer::constructAESMQueueManager(IAESMLogic& aesmLogic, ITransporter& transporter)
{
  return new AESMQueueManager(
                new AESMWorkerThread(aesmLogic, transporter, new AESMQueue<RequestData>()),
                new AESMWorkerThread(aesmLogic, transporter, new AESMQueue<RequestData>()),
                new AESMWorkerThread(aesmLogic, transporter, new AESMQueue<RequestData>())
        );
}

CAESMServer::CAESMServer(IServerSocket* server_sock, CSelector* selector, IAESMLogic* aesmLogic):
    m_serverSocket(server_sock),
    m_selector(selector),
    m_aesmLogic(aesmLogic),
    m_shutDown(false)
{
  // Dependency injection
    ISerializer* serializer  = new ProtobufSerializer();
    m_transporter  = new SocketTransporter(NULL, serializer);

    m_queueManager = constructAESMQueueManager(*aesmLogic, *m_transporter);
}

CAESMServer::~CAESMServer()
{
    delete m_transporter;
    delete m_queueManager;
    delete m_serverSocket;
    delete m_selector;
    delete m_aesmLogic;
}

/*
 * Verify that the server was initialized with proper parameters
 * Create the worker thread for the server;
 * This function will throw whatever it is that the called functions throw.
 */
void CAESMServer::init()
{
    if(NULL == this->m_serverSocket ||
       NULL == this->m_selector)
    {
        std::string msg("null members");
        throw new aesm_exception(msg);
    }

    this->m_serverSocket->init();
}

void CAESMServer::doWork()
{
    // pipe is used to prevent select from blocking current thread
    if (pipe(m_fdPipe) < 0) {
        std::string msg("failed to create pipe");
        throw new aesm_exception(msg);
    }

    while(!m_shutDown) {

        if (!m_selector->select(m_fdPipe[0]))
            break;

        if(m_selector->canAcceptConnection())
        {
            ICommunicationSocket* commSock = this->m_serverSocket->accept();
            if (commSock == NULL)
                continue;

            m_selector->addSocket(commSock);
        }

        std::list<ICommunicationSocket*> socketsWithData    = m_selector->getSocsWithNewContent();
        std::list<ICommunicationSocket*>::const_iterator it = socketsWithData.begin();

        for (;it != socketsWithData.end(); ++it) {
            try {
                IAERequest  *request = m_transporter->receiveRequest(*it);  
                RequestData *requestData = new RequestData(*it, request);   //deleted by the AESMWorkerThread after response is sent

                m_queueManager->enqueue(requestData);

            } catch (SockDisconnectedException& e) {
                m_selector->removeSocket(*it);
            }
        }
    }

    close(m_fdPipe[0]);
    close(m_fdPipe[1]);
}

void CAESMServer::shutDown()
{
   m_shutDown = true;

   // notify CSelector to terminate
   if (write(m_fdPipe[1], &m_fdPipe[1], sizeof(int)) < 0)
   {
       // do nothing
   }

   m_aesmLogic->service_stop();
}
