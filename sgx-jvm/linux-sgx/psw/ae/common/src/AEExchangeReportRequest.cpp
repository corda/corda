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

#include <AEExchangeReportRequest.h>
#include <AEExchangeReportResponse.h>
#include <IAESMLogic.h>

#include <stdlib.h>
#include <limits.h>
#include <IAEMessage.h>


AEExchangeReportRequest::AEExchangeReportRequest(const aesm::message::Request::ExchangeReportRequest& request)
    :m_request(NULL)
{
    m_request = new aesm::message::Request::ExchangeReportRequest();
    m_request->CopyFrom(request);
}

AEExchangeReportRequest::AEExchangeReportRequest(uint32_t sessionId, uint32_t dhMsg2Length, const uint8_t* dhMsg2, uint32_t dhMsg3Length, uint32_t timeout)
:m_request(NULL)
{
    m_request = new aesm::message::Request::ExchangeReportRequest();
    if (dhMsg2Length != 0 && dhMsg2 != NULL)
    {
        m_request->set_se_dh_msg2(dhMsg2, dhMsg2Length);
    }

    m_request->set_session_id(sessionId);
    m_request->set_se_dh_msg3_size(dhMsg3Length);
    m_request->set_timeout(timeout);
}

AEExchangeReportRequest::AEExchangeReportRequest(const AEExchangeReportRequest& other)
    :m_request(NULL)
{
    if (other.m_request != NULL)
        m_request = new aesm::message::Request::ExchangeReportRequest(*other.m_request);
}

AEExchangeReportRequest::~AEExchangeReportRequest()
{
    if (m_request != NULL)
        delete m_request;
}


AEMessage* AEExchangeReportRequest::serialize()
{
    AEMessage *ae_msg = NULL;
    aesm::message::Request msg;
    if (check())
    {
        aesm::message::Request::ExchangeReportRequest* mutableReq = msg.mutable_exchangereportreq();
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

AEExchangeReportRequest& AEExchangeReportRequest::operator=(const AEExchangeReportRequest& other)
{
    if (this == &other)
        return *this;
    if (m_request != NULL)
    {
        delete m_request;
        m_request = NULL;
    }
    if (other.m_request != NULL)
        m_request = new aesm::message::Request::ExchangeReportRequest(*other.m_request);
    return *this;
}

bool AEExchangeReportRequest::check()
{
    if (m_request == NULL)
        return false;
    return m_request->IsInitialized();
}

IAERequest::RequestClass AEExchangeReportRequest::getRequestClass() {
    return PLATFORM_CLASS;
}

IAEResponse* AEExchangeReportRequest::execute(IAESMLogic* aesmLogic) {

    aesm_error_t ret = AESM_UNEXPECTED_ERROR;
    uint32_t dh_msg3_size = 0;
    uint8_t* dh_msg3 = NULL;
    if (check())
    {
        uint32_t dh_msg2_length = 0;
        uint8_t* dh_msg2 = NULL;

        if (m_request->has_se_dh_msg2())
        {
            dh_msg2_length = (unsigned int)m_request->se_dh_msg2().size();
            dh_msg2 = (uint8_t*)const_cast<char *>(m_request->se_dh_msg2().data());
        }

        dh_msg3_size = m_request->se_dh_msg3_size();
        ret = aesmLogic->exchangeReport(m_request->session_id(), dh_msg2, dh_msg2_length, &dh_msg3, dh_msg3_size);
    }

    IAEResponse* response = new AEExchangeReportResponse(ret, dh_msg3_size, dh_msg3);

    if (dh_msg3)
    {
        delete[] dh_msg3;
    }

    return response;
}
