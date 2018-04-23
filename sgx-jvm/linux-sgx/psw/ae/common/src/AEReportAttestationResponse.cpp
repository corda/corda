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

#include <AEReportAttestationResponse.h>

#include <string.h>
#include <stdlib.h>
#include <limits.h>
#include <IAEMessage.h>

AEReportAttestationResponse::AEReportAttestationResponse()
    :m_response(NULL)
{
}

AEReportAttestationResponse::AEReportAttestationResponse(aesm::message::Response::ReportAttestationErrorResponse& response)
    :m_response(NULL)
{
    m_response = new aesm::message::Response::ReportAttestationErrorResponse(response);
}

AEReportAttestationResponse::AEReportAttestationResponse(uint32_t errorCode, uint32_t updateInfoLength, const uint8_t* updateInfo)
    :m_response(NULL)
{
    m_response = new aesm::message::Response::ReportAttestationErrorResponse();
    m_response->set_errorcode(errorCode);
    if (updateInfoLength!= 0 && updateInfo != NULL)
        m_response->set_platform_update_info(updateInfo, updateInfoLength);
}

AEReportAttestationResponse::AEReportAttestationResponse(const AEReportAttestationResponse& other)
    :m_response(NULL)
{
    if (other.m_response != NULL)
        m_response = new aesm::message::Response::ReportAttestationErrorResponse(*other.m_response);
}

AEReportAttestationResponse::~AEReportAttestationResponse()
{
    ReleaseMemory();
}

void AEReportAttestationResponse::ReleaseMemory()
{
    if (m_response != NULL)
    {
        delete m_response;
        m_response = NULL;
    }
}

AEMessage* AEReportAttestationResponse::serialize()
{
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    if (check())
    {
        aesm::message::Response::ReportAttestationErrorResponse* mutableRes = msg.mutable_reporterrres();
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

bool AEReportAttestationResponse::inflateWithMessage(AEMessage* message)
{
    aesm::message::Response msg;
    msg.ParseFromArray(message->data, message->size);
    if (msg.has_reporterrres() == false)
        return false;

    //this is an AEReportAttestationResponse
    ReleaseMemory();
    m_response = new aesm::message::Response::ReportAttestationErrorResponse(msg.reporterrres());
    return true;
}

bool AEReportAttestationResponse::GetValues(uint32_t* errorCode, uint32_t updateInfoLength, uint8_t* updateInfo) const
{
    if (m_response->has_platform_update_info() && updateInfo != NULL)
    {
        if (m_response->platform_update_info().size() <= updateInfoLength)
            memcpy(updateInfo, m_response->platform_update_info().c_str(), m_response->platform_update_info().size());
        else
            return false;
    }
    *errorCode = m_response->errorcode();
    return true;
}

AEReportAttestationResponse& AEReportAttestationResponse::operator=(const AEReportAttestationResponse& other)
{
    if (this == &other)
        return *this;

    ReleaseMemory();
    if (other.m_response != NULL)
    {
        m_response = new aesm::message::Response::ReportAttestationErrorResponse(*other.m_response);
    }
    return *this;
}

//checks
bool AEReportAttestationResponse::check()
{
    if (m_response == NULL)
        return false;
    return m_response->IsInitialized();
}
