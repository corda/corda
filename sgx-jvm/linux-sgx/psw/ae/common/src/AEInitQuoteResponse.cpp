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

#include <AEInitQuoteResponse.h>

#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <IAEMessage.h>

AEInitQuoteResponse::AEInitQuoteResponse() :
    m_response(NULL)
{
}

AEInitQuoteResponse::AEInitQuoteResponse(aesm::message::Response::InitQuoteResponse& response) :
    m_response(NULL)
{
    m_response = new aesm::message::Response::InitQuoteResponse(response);
}

AEInitQuoteResponse::AEInitQuoteResponse(uint32_t errorCode, uint32_t gidLength, const uint8_t* gid,
                            uint32_t targetInfoLength, const uint8_t* targetInfo) :
    m_response(NULL)
{

    m_response = new aesm::message::Response::InitQuoteResponse();
    m_response->set_errorcode(errorCode);
    if (gidLength!= 0 && gid != NULL)
        m_response->set_gid(gid, gidLength);
    if (targetInfoLength!= 0 && targetInfo != NULL)
        m_response->set_targetinfo(targetInfo, targetInfoLength);
}

AEInitQuoteResponse::AEInitQuoteResponse(const AEInitQuoteResponse& other) :
    m_response(NULL)
{
    if (other.m_response != NULL)
        m_response = new aesm::message::Response::InitQuoteResponse(*other.m_response);
}
AEInitQuoteResponse::~AEInitQuoteResponse()
{
    ReleaseMemory();
}

void AEInitQuoteResponse::ReleaseMemory()
{
    if (m_response != NULL)
    {
        delete m_response;
        m_response = NULL;
    }
}


AEMessage* AEInitQuoteResponse::serialize()
{
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    if (check())
    {
        aesm::message::Response::InitQuoteResponse* mutableRes = msg.mutable_initquoteres();
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

bool AEInitQuoteResponse::inflateWithMessage(AEMessage* message)
{
    aesm::message::Response msg;
    msg.ParseFromArray(message->data, message->size);
    if (msg.has_initquoteres() == false)
        return false;

    //this is an AEGetLaunchTokenResponse
    ReleaseMemory();
    m_response = new aesm::message::Response::InitQuoteResponse(msg.initquoteres());
    return true;
}

bool AEInitQuoteResponse::GetValues(uint32_t* errorCode, uint32_t gidLength, uint8_t* gid,
                            uint32_t targetInfoLength, uint8_t* targetInfo) const
{
    if (m_response->has_gid() && gid != NULL)
    {
        if (m_response->gid().size() <= gidLength)
            memcpy(gid, m_response->gid().c_str(), m_response->gid().size());
        else
            return false;
    }
    if (m_response->has_targetinfo() && targetInfo != NULL)
    {
        if (m_response->targetinfo().size() <= targetInfoLength)
            memcpy(targetInfo, m_response->targetinfo().c_str(), m_response->targetinfo().size());
        else
            return false;
    }
    *errorCode = m_response->errorcode(); 
    return true;
}

AEInitQuoteResponse & AEInitQuoteResponse::operator=(const AEInitQuoteResponse &other)
{
    if (this == &other)
        return *this;

    ReleaseMemory();
    if (other.m_response != NULL)
    {
        m_response = new aesm::message::Response::InitQuoteResponse(*other.m_response);
    }
    return *this;
}

bool AEInitQuoteResponse::check()
{
    if (m_response == NULL)
        return false;
    return m_response->IsInitialized();
}
