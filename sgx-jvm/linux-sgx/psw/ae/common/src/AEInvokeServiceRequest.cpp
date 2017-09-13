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

#include <AEInvokeServiceRequest.h>
#include <AEInvokeServiceResponse.h>
#include <IAESMLogic.h>

#include <stdlib.h>
#include <limits.h>
#include <IAEMessage.h>

AEInvokeServiceRequest::AEInvokeServiceRequest(const aesm::message::Request::InvokeServiceRequest& request) :
    m_request(NULL)
{
    m_request = new aesm::message::Request::InvokeServiceRequest();
    m_request->CopyFrom(request);
}

AEInvokeServiceRequest::AEInvokeServiceRequest(uint32_t pseMessageLength, const uint8_t* pseMessage, uint32_t pseResponseSize, uint32_t timeout)
    :m_request(NULL)
{
    m_request = new aesm::message::Request::InvokeServiceRequest();

    if (pseMessageLength !=0 && pseMessage != NULL)
        m_request->set_pse_message(pseMessage, pseMessageLength);
    m_request->set_pse_resp_size(pseResponseSize);
    m_request->set_timeout(timeout);
}

AEInvokeServiceRequest::AEInvokeServiceRequest(const AEInvokeServiceRequest& other)
    : m_request(NULL)
{
    if (other.m_request != NULL)
        m_request = new aesm::message::Request::InvokeServiceRequest(*other.m_request);
}

AEInvokeServiceRequest::~AEInvokeServiceRequest()
{
    if (m_request != NULL)
        delete m_request;
}

AEMessage* AEInvokeServiceRequest::serialize()
{
    AEMessage *ae_msg = NULL;
    aesm::message::Request msg;
    if (check())
    {
        aesm::message::Request::InvokeServiceRequest* mutableReq = msg.mutable_invokeservicereq();
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


AEInvokeServiceRequest& AEInvokeServiceRequest::operator=(const AEInvokeServiceRequest& other)
{
    if (this == &other)
        return *this;
    if (m_request != NULL)
    {
        delete m_request;
        m_request = NULL;
    }
    if (other.m_request != NULL)
        m_request = new aesm::message::Request::InvokeServiceRequest(*other.m_request);
    return *this;
}

bool AEInvokeServiceRequest::check()
{
    if (m_request == NULL)
        return false;
    return m_request->IsInitialized();
}

IAERequest::RequestClass AEInvokeServiceRequest::getRequestClass() {
    return PLATFORM_CLASS;
}

IAEResponse* AEInvokeServiceRequest::execute(IAESMLogic* aesmLogic) {
    aesm_error_t ret = AESM_UNEXPECTED_ERROR;
    uint8_t* response = NULL;
    uint32_t response_size = 0;

    if (check())
    {

        uint32_t pse_message_length = 0;
        uint8_t* pse_message = NULL;

        if (m_request->has_pse_message())
        {
            pse_message_length = (unsigned int)m_request->pse_message().size();
            pse_message = (uint8_t*)const_cast<char *>(m_request->pse_message().data());
        }

        response_size = m_request->pse_resp_size();
        ret = aesmLogic->invokeService(pse_message, pse_message_length, &response, response_size);
    }

    IAEResponse* ae_res = new AEInvokeServiceResponse(ret, response_size, response);
    if (response)
        delete[] response;

    return ae_res;
}
