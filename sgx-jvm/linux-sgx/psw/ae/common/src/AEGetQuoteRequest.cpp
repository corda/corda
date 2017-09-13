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

#include <AEGetQuoteRequest.h>
#include <AEGetQuoteResponse.h>
#include "IAESMLogic.h"

#include <stdlib.h>
#include <limits.h>
#include <IAEMessage.h>


    AEGetQuoteRequest::AEGetQuoteRequest(const aesm::message::Request::GetQuoteRequest& request) :
    m_request(NULL)
{
    m_request = new aesm::message::Request::GetQuoteRequest();
    m_request->CopyFrom(request);
}

AEGetQuoteRequest::AEGetQuoteRequest(uint32_t reportLength, const uint8_t* report,
        uint32_t quoteType,
        uint32_t spidLength, const uint8_t* spid,
        uint32_t nonceLength, const uint8_t* nonce,
        uint32_t sig_rlLength, const uint8_t* sig_rl,
        uint32_t bufferSize,
        bool qe_report,
        uint32_t timeout)
        : m_request(NULL)
{
    m_request = new aesm::message::Request::GetQuoteRequest();
    if (reportLength !=0 && report != NULL)
        m_request->set_report(report, reportLength);
    if (spidLength != 0 && spid != NULL)
        m_request->set_spid(spid, spidLength);
    if (nonceLength != 0 && nonce != NULL)
        m_request->set_nonce(nonce, nonceLength);
    if (sig_rlLength != 0 && sig_rl != NULL)
        m_request->set_sig_rl(sig_rl, sig_rlLength);
    m_request->set_quote_type(quoteType);
    m_request->set_buf_size(bufferSize);
    m_request->set_qe_report(qe_report);
    m_request->set_timeout(timeout);
}

AEGetQuoteRequest::AEGetQuoteRequest(const AEGetQuoteRequest& other)
    : m_request(NULL)
{
    if (other.m_request != NULL)
        m_request = new aesm::message::Request::GetQuoteRequest(*other.m_request);
}

AEGetQuoteRequest::~AEGetQuoteRequest()
{
    if (m_request != NULL)
        delete m_request;}

AEMessage* AEGetQuoteRequest::serialize()
{
    AEMessage *ae_msg = NULL;
    aesm::message::Request msg;
    if (check())
    {
        aesm::message::Request::GetQuoteRequest* mutableReq = msg.mutable_getquotereq();
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

AEGetQuoteRequest& AEGetQuoteRequest::operator=(const AEGetQuoteRequest& other)
{
    if (this == &other)
        return *this;
    if (m_request != NULL)
    {
        delete m_request;
        m_request = NULL;
    }
    if (other.m_request != NULL)
        m_request = new aesm::message::Request::GetQuoteRequest(*other.m_request);
    return *this;
}

bool AEGetQuoteRequest::check()
{
    if (m_request == NULL)
        return false;
    return m_request->IsInitialized();
}

IAERequest::RequestClass AEGetQuoteRequest::getRequestClass() {
    return QUOTING_CLASS;
}

IAEResponse* AEGetQuoteRequest::execute(IAESMLogic* aesmLogic) {
    aesm_error_t result = AESM_UNEXPECTED_ERROR;

    uint32_t qe_report_length = 0;
    uint8_t* qe_report = NULL;
    uint32_t quote_length = 0;
    uint8_t* quote = NULL;

    if (check())
    {
        uint32_t report_length = 0;
        uint8_t* report = NULL;
        uint32_t spid_length = 0;
        uint8_t* spid = NULL;
        uint32_t nonce_length = 0;
        uint8_t* nonce = NULL;
        uint32_t sig_rl_length = 0;
        uint8_t* sig_rl = NULL;

        if (m_request->has_report())
        {
            report_length = (unsigned int)m_request->report().size();
            report = (uint8_t*)const_cast<char *>(m_request->report().data());
        }
        if (m_request->has_spid())
        {
            spid_length = (unsigned int)m_request->spid().size();
            spid = (uint8_t*)const_cast<char *>(m_request->spid().data());
        }
        if (m_request->has_nonce())
        {
            nonce_length = (unsigned int)m_request->nonce().size();
            nonce = (uint8_t*)const_cast<char *>(m_request->nonce().data());
        }
        if (m_request->has_sig_rl())
        {
            sig_rl_length = (unsigned int)m_request->sig_rl().size();
            sig_rl = (uint8_t*)const_cast<char *>(m_request->sig_rl().data());
        }
        quote_length = (uint32_t)m_request->buf_size();

        result = aesmLogic->getQuote(report_length, report,
                (uint32_t)m_request->quote_type(),
                spid_length, spid,
                nonce_length, nonce,
                sig_rl_length, sig_rl,
                quote_length, &quote,
                m_request->qe_report(), &qe_report_length, &qe_report);

    }
    AEGetQuoteResponse* response = new AEGetQuoteResponse(result, quote_length, quote, qe_report_length, qe_report);

    //free the buffer before send
    if (quote)
        delete[] quote;
    if (qe_report)
        delete[] qe_report;
    return response;
}
