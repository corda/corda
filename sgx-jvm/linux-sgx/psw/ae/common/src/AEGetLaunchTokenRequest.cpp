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

#include <AEGetLaunchTokenRequest.h>
#include <AEGetLaunchTokenResponse.h>
#include <IAESMLogic.h>

#include <stdlib.h>
#include <limits.h>
#include <IAEMessage.h>

AEGetLaunchTokenRequest::AEGetLaunchTokenRequest(const aesm::message::Request::GetLaunchTokenRequest& request) :
    m_request(NULL)
{
    m_request = new aesm::message::Request::GetLaunchTokenRequest();
    m_request->CopyFrom(request);
}

AEGetLaunchTokenRequest::AEGetLaunchTokenRequest(uint32_t measurementLength, const uint8_t* measurement,
        uint32_t pubkeyLength, const uint8_t* pubkey,
        uint32_t attributesLength, const uint8_t* attributes,
        uint32_t timeout) :
        m_request(NULL)
{
    m_request = new aesm::message::Request::GetLaunchTokenRequest();
    if (measurementLength != 0 && measurement != NULL)
        m_request->set_mr_enclave(measurement, measurementLength);
    if (pubkeyLength!= 0 && pubkey != NULL)
        m_request->set_mr_signer(pubkey, pubkeyLength);
    if (attributesLength != 0 && attributes != NULL)
        m_request->set_se_attributes(attributes, attributesLength);
    m_request->set_timeout(timeout);
}

AEGetLaunchTokenRequest::AEGetLaunchTokenRequest(const AEGetLaunchTokenRequest& other) :
    m_request(NULL)
{
    if (other.m_request != NULL)
        m_request = new aesm::message::Request::GetLaunchTokenRequest(*other.m_request);
}

AEGetLaunchTokenRequest::~AEGetLaunchTokenRequest()
{
    if (m_request != NULL)
        delete m_request;
}

AEMessage* AEGetLaunchTokenRequest::serialize()
{
    AEMessage *ae_msg = NULL;
    aesm::message::Request msg;
    if (check())
    {
        aesm::message::Request::GetLaunchTokenRequest* mutableReq = msg.mutable_getlictokenreq();
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


bool AEGetLaunchTokenRequest::check()
{
    if (m_request == NULL)
        return false;
    return m_request->IsInitialized();
}
AEGetLaunchTokenRequest& AEGetLaunchTokenRequest::operator=(const AEGetLaunchTokenRequest& other)
{
    if (this == &other)
        return *this;
    if (m_request != NULL)
    {
        delete m_request;
        m_request = NULL;
    }
    if (other.m_request != NULL)
        m_request = new aesm::message::Request::GetLaunchTokenRequest(*other.m_request);
    return *this;
}
IAERequest::RequestClass AEGetLaunchTokenRequest::getRequestClass() {
    return LAUNCH_CLASS;
}


IAEResponse* AEGetLaunchTokenRequest::execute(IAESMLogic* aesmLogic) {

    aesm_error_t result = AESM_UNEXPECTED_ERROR;
    uint8_t* token = NULL;
    uint32_t tokenSize = 0;

    if (check())
    {
        uint32_t mr_enclave_length = 0;
        uint8_t* mr_enclave = NULL;
        uint32_t mr_signer_length = 0;
        uint8_t* mr_signer = NULL;
        uint32_t se_attributes_length = 0;
        uint8_t* se_attributes = NULL;


        if (m_request->has_mr_enclave())
        {
            mr_enclave_length = (unsigned int)m_request->mr_enclave().size();
            mr_enclave = (uint8_t*)const_cast<char *>(m_request->mr_enclave().data());
        }
        if (m_request->has_mr_signer())
        {
            mr_signer_length = (unsigned int)m_request->mr_signer().size();
            mr_signer = (uint8_t*)const_cast<char *>(m_request->mr_signer().data());
        }
        if (m_request->has_se_attributes())
        {
            se_attributes_length = (unsigned int)m_request->se_attributes().size();
            se_attributes = (uint8_t*)const_cast<char *>(m_request->se_attributes().data());
        }

        result = aesmLogic->getLaunchToken(mr_enclave, mr_enclave_length,
            mr_signer, mr_signer_length,
            se_attributes, se_attributes_length,
            &token, &tokenSize);

    }
    IAEResponse* response = new AEGetLaunchTokenResponse(result, tokenSize, token);

    //free the buffer before send
    if (token)
        delete [] token;
    return response;
}
