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

#include <AEInvokeServiceResponse.h>

#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <IAEMessage.h>

    AEInvokeServiceResponse::AEInvokeServiceResponse()
    :m_response(NULL)
{
}

AEInvokeServiceResponse::AEInvokeServiceResponse(aesm::message::Response::InvokeServiceResponse& response)
    :m_response(NULL)
{
    m_response = new aesm::message::Response::InvokeServiceResponse(response);
}

AEInvokeServiceResponse::AEInvokeServiceResponse(uint32_t errorCode, uint32_t pseMessageLength, const uint8_t* pseMessage)
    :m_response(NULL)
{
    m_response = new aesm::message::Response::InvokeServiceResponse();
    m_response->set_errorcode(errorCode);
    if (pseMessageLength!= 0 && pseMessage != NULL)
        m_response->set_pse_message(pseMessage, pseMessageLength);
}

AEInvokeServiceResponse::AEInvokeServiceResponse(const AEInvokeServiceResponse& other)
    :m_response(NULL)
{
    if (other.m_response != NULL)
        m_response = new aesm::message::Response::InvokeServiceResponse(*other.m_response);
}

AEInvokeServiceResponse::~AEInvokeServiceResponse()
{
    ReleaseMemory();
}

void AEInvokeServiceResponse::ReleaseMemory()
{
    if (m_response != NULL)
    {
        delete m_response;
        m_response = NULL;
    }
}

AEMessage* AEInvokeServiceResponse::serialize()
{
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    if (check())
    {
        aesm::message::Response::InvokeServiceResponse* mutableRes = msg.mutable_invokeserviceres();
        mutableRes->CopyFrom(*m_response);

        if (msg.ByteSize() <= INT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int)msg.ByteSize();
            ae_msg->data = new char[ae_msg->size];
            msg.SerializeToArray(ae_msg->data, ae_msg->size);
        }
    }
    return ae_msg;
}

bool AEInvokeServiceResponse::inflateWithMessage(AEMessage* message)
{
    aesm::message::Response msg;
    msg.ParseFromArray(message->data, message->size);
    if (msg.has_invokeserviceres() == false)
        return false;

    //this is an AEInvokeServiceResponse
    ReleaseMemory();
    m_response = new aesm::message::Response::InvokeServiceResponse(msg.invokeserviceres());
    return true;
}

bool AEInvokeServiceResponse::GetValues(uint32_t* errorCode, uint32_t pseMessageLength,uint8_t* pseMessage) const
{
    if (m_response->has_pse_message() && pseMessage != NULL)
    {
        if (m_response->pse_message().size() <= pseMessageLength)
            memcpy(pseMessage, m_response->pse_message().c_str(), m_response->pse_message().size());
        else
            return false;
    }
    *errorCode = m_response->errorcode(); 
    return true;
}

AEInvokeServiceResponse& AEInvokeServiceResponse::operator=(const AEInvokeServiceResponse& other)
{
    if (this == &other)
        return * this;

    ReleaseMemory();
    if (other.m_response != NULL)
    {
        m_response = new aesm::message::Response::InvokeServiceResponse(*other.m_response);
    }
    return *this;
}

bool AEInvokeServiceResponse::check()
{
    if (m_response == NULL)
        return false;
    return m_response->IsInitialized();
}
