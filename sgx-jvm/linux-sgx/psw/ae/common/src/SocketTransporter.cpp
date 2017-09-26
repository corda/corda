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
#include <SocketTransporter.h>

#include <IAERequest.h>
#include <IAEResponse.h>
#include <ISerializer.h>
#include <ICommunicationSocket.h>
#include <IAEMessage.h>

#include <string.h>
#include <stdlib.h>

SocketTransporter::SocketTransporter(ISocketFactory* socketFactory, ISerializer* serializer)
:mSocketFactory(socketFactory), mSerializer(serializer)
{
}

SocketTransporter::~SocketTransporter()
{
    if (mSocketFactory != NULL)
    {
        delete mSocketFactory;
        mSocketFactory = NULL;
    }
    if (mSerializer != NULL)
    {
        delete mSerializer;
        mSerializer = NULL;
    }
}

AEMessage* SocketTransporter::receiveMessage(ICommunicationSocket* sock) {
    AEMessage * message = new AEMessage();
    char* msgSize = NULL;
    msgSize = sock->readRaw(sizeof(AEMessage::size));
    if (msgSize != NULL)
    {
        memcpy((char*)&message->size, msgSize, sizeof(message->size));
        message->data = sock->readRaw(message->size);
        delete [] msgSize;
    }
    return message;
}

uae_oal_status_t SocketTransporter::sendMessage(AEMessage *message, ICommunicationSocket* sock) {
    if ((sock->writeRaw((char*)&message->size, sizeof(message->size))) == -1)
        return UAE_OAL_ERROR_UNEXPECTED;
    if ((sock->writeRaw(message->data, message->size)) == -1)
        return UAE_OAL_ERROR_UNEXPECTED;
    return UAE_OAL_SUCCESS;
}

uae_oal_status_t SocketTransporter::transact(IAERequest* request, IAEResponse* response, uint32_t timeout)
{
    if (request == NULL || response == NULL)
        return UAE_OAL_ERROR_INVALID;


    ICommunicationSocket* communicationSocket = mSocketFactory->NewCommunicationSocket();

    if (communicationSocket == NULL)
        return UAE_OAL_ERROR_AESM_UNAVAILABLE;

    uae_oal_status_t ret = UAE_OAL_ERROR_UNEXPECTED;

    //set the timeout value
    if (timeout > 0)
        communicationSocket->setTimeout(timeout);

    AEMessage * reqMsg = request->serialize();
    
    if (reqMsg != NULL)
    {
        //send the request
        ret = sendMessage(reqMsg, communicationSocket);
        if (ret == UAE_OAL_SUCCESS)
        {

            //read the response
            AEMessage * resMsg = receiveMessage(communicationSocket);

            if (communicationSocket->wasTimeoutDetected() == false)
            {
                if (resMsg != NULL && resMsg->data != NULL)
                    response->inflateWithMessage(resMsg);
                else
                    ret = UAE_OAL_ERROR_UNEXPECTED;
            }

            delete resMsg;
        }
    }

    if (communicationSocket->wasTimeoutDetected() == true)
        ret = UAE_OAL_ERROR_TIMEOUT;

    //free stuff
    delete reqMsg;
    delete communicationSocket;

    return ret;
}

IAERequest* SocketTransporter::receiveRequest(ICommunicationSocket* sock) {
    AEMessage * msg = receiveMessage(sock);
    IAERequest* request = mSerializer->inflateRequest(msg);
    delete msg;
    return request;
}

uae_oal_status_t SocketTransporter::sendResponse(IAEResponse* response, ICommunicationSocket* sock) {
    AEMessage * message = response->serialize();
    uae_oal_status_t retVal = sendMessage(message, sock);
    delete message;
    return retVal;
}
