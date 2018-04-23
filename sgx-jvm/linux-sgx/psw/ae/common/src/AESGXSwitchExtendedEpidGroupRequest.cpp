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
#include <AESGXSwitchExtendedEpidGroupRequest.h>
#include <AESGXSwitchExtendedEpidGroupResponse.h>
#include <IAESMLogic.h>

#include <string.h>
#include <stdlib.h>
#include <limits.h>
#include <IAEMessage.h>

AESGXSwitchExtendedEpidGroupRequest::AESGXSwitchExtendedEpidGroupRequest(const aesm::message::Request::SGXSwitchExtendedEpidGroupRequest& request) :
    m_request(NULL)
{
    m_request = new aesm::message::Request::SGXSwitchExtendedEpidGroupRequest();
    m_request->CopyFrom(request);
}


AESGXSwitchExtendedEpidGroupRequest::AESGXSwitchExtendedEpidGroupRequest(uint32_t extendedGroupId, uint32_t timeout)
    :m_request(NULL)

{
    m_request = new aesm::message::Request::SGXSwitchExtendedEpidGroupRequest();
    m_request->set_x_group_id(extendedGroupId);
    m_request->set_timeout(timeout);
}

AESGXSwitchExtendedEpidGroupRequest::AESGXSwitchExtendedEpidGroupRequest(const AESGXSwitchExtendedEpidGroupRequest& other)
    : m_request(NULL)
{
    if (other.m_request != NULL)
        m_request = new aesm::message::Request::SGXSwitchExtendedEpidGroupRequest(*other.m_request);
}

AESGXSwitchExtendedEpidGroupRequest::~AESGXSwitchExtendedEpidGroupRequest()
{
    if (m_request != NULL)
        delete m_request;
}

AEMessage* AESGXSwitchExtendedEpidGroupRequest::serialize()
{
    AEMessage *ae_msg = NULL;
    aesm::message::Request msg;
    if (check())
    {
        aesm::message::Request::SGXSwitchExtendedEpidGroupRequest* mutableReq = msg.mutable_sgxswitchextendedepidgroupreq();
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

AESGXSwitchExtendedEpidGroupRequest& AESGXSwitchExtendedEpidGroupRequest::operator=(const AESGXSwitchExtendedEpidGroupRequest& other)
{
    if (this == &other)
        return *this;
    if (m_request != NULL)
    {
        delete m_request;
        m_request = NULL;
    }
    if (other.m_request != NULL)
        m_request = new aesm::message::Request::SGXSwitchExtendedEpidGroupRequest(*other.m_request);
    return *this;
}

bool AESGXSwitchExtendedEpidGroupRequest::check()
{
    if (m_request == NULL)
        return false;
    return m_request->IsInitialized();
}

IAERequest::RequestClass AESGXSwitchExtendedEpidGroupRequest::getRequestClass()
{
    return QUOTING_CLASS;
}

IAEResponse* AESGXSwitchExtendedEpidGroupRequest::execute(IAESMLogic* aesmLogic)
{
    aesm_error_t result = AESM_UNEXPECTED_ERROR;
    if (check())
    {
        uint32_t extended_group_id = m_request->x_group_id();

        result = aesmLogic->sgxSwitchExtendedEpidGroup(extended_group_id);
    }
    return new AESGXSwitchExtendedEpidGroupResponse(result);
}

