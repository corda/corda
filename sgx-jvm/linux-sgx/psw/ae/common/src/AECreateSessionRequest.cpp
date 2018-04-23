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

#include <AECreateSessionRequest.h>
#include <AECreateSessionResponse.h>
#include <IAESMLogic.h>

#include <stdlib.h>
#include <limits.h>
#include <IAEMessage.h>

AECreateSessionRequest::AECreateSessionRequest(const aesm::message::Request::CreateSessionRequest& request)
    :m_request(NULL)
{
    m_request = new aesm::message::Request::CreateSessionRequest();
    m_request->CopyFrom(request);
}

AECreateSessionRequest::AECreateSessionRequest(uint32_t dhMsg1Size, uint32_t timeout)
    :m_request(NULL)
{
    m_request = new aesm::message::Request::CreateSessionRequest();
    m_request->set_dh_msg1_size(dhMsg1Size);
    m_request->set_timeout(timeout);
}

AECreateSessionRequest::AECreateSessionRequest(const AECreateSessionRequest& other)
    :m_request(NULL)
{
    if (other.m_request != NULL)
        m_request = new aesm::message::Request::CreateSessionRequest(*other.m_request);
}

AECreateSessionRequest::~AECreateSessionRequest()
{
    if (m_request != NULL)
        delete m_request;
}



AEMessage* AECreateSessionRequest::serialize()
{
    AEMessage *ae_msg = NULL;
    aesm::message::Request msg;
    if (check())
    {
        aesm::message::Request::CreateSessionRequest* mutableReq = msg.mutable_createsessionreq();
        mutableReq->CopyFrom(*m_request);

        if (msg.ByteSize() <= INT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int)msg.ByteSize();
            ae_msg->data = new char[ae_msg->size];
            msg.SerializeToArray(ae_msg->data, ae_msg->size);
        }
    }
    return ae_msg;
}

AECreateSessionRequest& AECreateSessionRequest::operator=(const AECreateSessionRequest& other)
{
    if (this == &other)
        return *this;
    if (m_request != NULL)
    {
        delete m_request;
        m_request = NULL;
    }
    if (other.m_request != NULL)
        m_request = new aesm::message::Request::CreateSessionRequest(*other.m_request);
    return *this;
}

bool AECreateSessionRequest::check()
{
    if (m_request == NULL)
        return false;
    return m_request->IsInitialized();
}

IAERequest::RequestClass AECreateSessionRequest::getRequestClass() {
    return PLATFORM_CLASS;
}

IAEResponse* AECreateSessionRequest::execute(IAESMLogic* aesmLogic) {
    aesm_error_t result = AESM_UNEXPECTED_ERROR;
    uint32_t sid = 0;
    uint8_t* dh_msg1 = NULL;
    uint32_t dh_msg1_size = 0;
    if (check())
    {
        dh_msg1_size = m_request->dh_msg1_size();
        result = aesmLogic->createSession(&sid, &dh_msg1, dh_msg1_size);

    }
    AECreateSessionResponse* response = new AECreateSessionResponse(result, sid, dh_msg1_size, dh_msg1);

    //free the buffer before send
    if (dh_msg1)
        delete[] dh_msg1;
    return response;
}
