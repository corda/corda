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


#include <AEGetPsCapResponse.h>


#include <string.h>
#include <stdlib.h>
#include <limits.h>
#include <IAEMessage.h>

AEGetPsCapResponse::AEGetPsCapResponse()
    :m_response(NULL)
{
}

AEGetPsCapResponse::AEGetPsCapResponse(aesm::message::Response::GetPsCapResponse& response) :
    m_response(NULL)
{
    m_response = new aesm::message::Response::GetPsCapResponse(response);
}

AEGetPsCapResponse::AEGetPsCapResponse(uint32_t errorCode, uint64_t ps_cap) :
    m_response(NULL)
{
    m_response = new aesm::message::Response::GetPsCapResponse();
    m_response->set_errorcode(errorCode);
    m_response->set_ps_cap(ps_cap);}

AEGetPsCapResponse::AEGetPsCapResponse(const AEGetPsCapResponse& other)
    :m_response(NULL)
{
    if (other.m_response != NULL)
        m_response = new aesm::message::Response::GetPsCapResponse(*other.m_response);
}

AEGetPsCapResponse::~AEGetPsCapResponse()
{
    ReleaseMemory();
}

void AEGetPsCapResponse::ReleaseMemory()
{
   if (m_response != NULL)
    {
        delete m_response;
        m_response = NULL;
    }
}

AEMessage* AEGetPsCapResponse::serialize()
{
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    if (check())
    {
        aesm::message::Response::GetPsCapResponse* mutableRes = msg.mutable_getpscapres();
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

bool AEGetPsCapResponse::inflateWithMessage(AEMessage* message)
{
    aesm::message::Response msg;
    if (!msg.ParseFromArray(message->data, message->size))
        return false;
    if (msg.has_getpscapres() == false)
        return false;

    //this is an AEGetPsCapResponse
    ReleaseMemory();
    m_response = new aesm::message::Response::GetPsCapResponse(msg.getpscapres());
    return true;
}

bool AEGetPsCapResponse::GetValues(uint32_t* errorCode, uint64_t* ps_cap) const
{
    *ps_cap = m_response->ps_cap();
    *errorCode = m_response->errorcode();
    return true;
}

AEGetPsCapResponse & AEGetPsCapResponse::operator=(const AEGetPsCapResponse &other)
{
    if (this == &other)
        return *this;

    ReleaseMemory();
    if (other.m_response != NULL)
    {
        m_response = new aesm::message::Response::GetPsCapResponse(*other.m_response);
    }
    return *this;
}

bool AEGetPsCapResponse::check()
{
    if (m_response == NULL)
        return false;
    return m_response->IsInitialized();
}

