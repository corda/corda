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


#include <AEGetWhiteListSizeResponse.h>

#include <stdlib.h>
#include <string.h>

#include <limits.h>
#include <IAEMessage.h>

AEGetWhiteListSizeResponse::AEGetWhiteListSizeResponse()
    :m_response(NULL)
{
}

AEGetWhiteListSizeResponse::AEGetWhiteListSizeResponse(aesm::message::Response::GetWhiteListSizeResponse& response)
    :m_response(NULL)
{
    m_response = new aesm::message::Response::GetWhiteListSizeResponse(response);
}

AEGetWhiteListSizeResponse::AEGetWhiteListSizeResponse(uint32_t errorCode, uint32_t white_list_size)
    :m_response(NULL)
{
    m_response = new aesm::message::Response::GetWhiteListSizeResponse();
    m_response->set_errorcode(errorCode);
    m_response->set_white_list_size(white_list_size);
}

AEGetWhiteListSizeResponse::AEGetWhiteListSizeResponse(const AEGetWhiteListSizeResponse& other)
    :m_response(NULL)
{
    if (other.m_response != NULL)
        m_response = new aesm::message::Response::GetWhiteListSizeResponse(*other.m_response);
}

AEGetWhiteListSizeResponse::~AEGetWhiteListSizeResponse()
{
    ReleaseMemory();
}

void AEGetWhiteListSizeResponse::ReleaseMemory()
{
   if (m_response != NULL)
    {
        delete m_response;
        m_response = NULL;
    }
}


AEMessage* AEGetWhiteListSizeResponse::serialize()
{
    AEMessage *ae_msg = NULL;
    aesm::message::Response msg;
    if (check())
    {
        aesm::message::Response::GetWhiteListSizeResponse* mutableResp = msg.mutable_getwhitelistsizeres();
        mutableResp->CopyFrom(*m_response);

        if (msg.ByteSize() <= INT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int)msg.ByteSize();
            ae_msg->data = new char[ae_msg->size];
            msg.SerializeToArray(ae_msg->data, ae_msg->size);
        }
    }
    return ae_msg;
}

bool AEGetWhiteListSizeResponse::inflateWithMessage(AEMessage* message)
{
    aesm::message::Response msg;
    msg.ParseFromArray(message->data, message->size);
    if (msg.has_getwhitelistsizeres() == false)
        return false;

    ReleaseMemory();
    m_response = new aesm::message::Response::GetWhiteListSizeResponse(msg.getwhitelistsizeres());
    return true;
}

bool AEGetWhiteListSizeResponse::GetValues(uint32_t* errorCode, uint32_t* white_list_size) const
{
    *white_list_size = m_response->white_list_size();
    *errorCode = m_response->errorcode();
    return true;
}

AEGetWhiteListSizeResponse & AEGetWhiteListSizeResponse::operator=(const AEGetWhiteListSizeResponse &other)
{
    if (this == &other)
        return *this;

    ReleaseMemory();
    if (other.m_response != NULL)
    {
        m_response = new aesm::message::Response::GetWhiteListSizeResponse(*other.m_response);
    }    return *this;
}

bool AEGetWhiteListSizeResponse::check()
{
    if (m_response == NULL)
        return false;
    return m_response->IsInitialized();
}
