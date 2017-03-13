/*
 * Copyright (C) 2011-2016 Intel Corporation. All rights reserved.
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
#include <ISerializer.h>
#include <AEExchangeReportRequest.h>
#include <AEExchangeReportResponse.h>
#include <IAESMLogic.h>

#include <string.h>
#include <stdlib.h>


AEExchangeReportRequest::AEExchangeReportRequest()
:mSessionId(0), mDHMsg2Length(0), mDHMsg2(NULL), mDHMsg3Length(0)
{
}

AEExchangeReportRequest::AEExchangeReportRequest(uint32_t sessionId, uint32_t dhMsg2Length, const uint8_t* dhMsg2, uint32_t dhMsg3Length, uint32_t timeout)
:mSessionId(0), mDHMsg2Length(0), mDHMsg2(NULL), mDHMsg3Length(0)
{
    CopyFields(sessionId, dhMsg2Length, dhMsg2, dhMsg3Length, timeout);
}

AEExchangeReportRequest::AEExchangeReportRequest(const AEExchangeReportRequest& other)
:IAERequest(other), mSessionId(0), mDHMsg2Length(0), mDHMsg2(NULL), mDHMsg3Length(0)
{
    CopyFields(other.mSessionId, other.mDHMsg2Length, other.mDHMsg2, other.mDHMsg3Length, other.mTimeout);
}

AEExchangeReportRequest::~AEExchangeReportRequest()
{
    ReleaseMemory();
}

void AEExchangeReportRequest::ReleaseMemory()
{
    if (mDHMsg2 != NULL)
    {
        if (mDHMsg2Length > 0)
            memset(mDHMsg2, 0, mDHMsg2Length);
        delete [] mDHMsg2;
        mDHMsg2 = NULL;
    }
    mDHMsg2Length = 0;
    mDHMsg3Length = 0;
    mSessionId = 0;
    mTimeout = 0;
}

void AEExchangeReportRequest::CopyFields(uint32_t sessionId, uint32_t dhMsg2Length, const uint8_t* dhMsg2, uint32_t dhMsg3Length, uint32_t timeout)
{
    if(dhMsg2Length <= MAX_MEMORY_ALLOCATION && mDHMsg3Length <= MAX_MEMORY_ALLOCATION)
    {
        mValidSizeCheck = true;
    }
    else
    {
        mValidSizeCheck = false;
        return;
    }

    mSessionId = sessionId;
    mDHMsg2Length = dhMsg2Length;
    mDHMsg3Length = dhMsg3Length;

    mTimeout = timeout;

    if (dhMsg2 != NULL && dhMsg2Length > 0) {
        mDHMsg2 = new uint8_t[dhMsg2Length];
        memcpy(mDHMsg2, dhMsg2, dhMsg2Length);
    }

}

AEMessage* AEExchangeReportRequest::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

void AEExchangeReportRequest::inflateValues(uint32_t sessionId, uint32_t dhMsg2Length, const uint8_t* dhMsg2, uint32_t dhMsg3Length, uint32_t timeout)
{
    ReleaseMemory();

    CopyFields(sessionId, dhMsg2Length, dhMsg2, dhMsg3Length, timeout);
}

bool AEExchangeReportRequest::operator==(const AEExchangeReportRequest& other) const
{
    if (this == &other)
        return true;

    if (mSessionId != other.mSessionId ||
        mDHMsg2Length != other.mDHMsg2Length ||
        mDHMsg3Length != other.mDHMsg3Length ||
        mTimeout != other.mTimeout)
        return false;

    if ((mDHMsg2 != other.mDHMsg2) &&
            (mDHMsg2 == NULL || other.mDHMsg2 == NULL))
        return false;

    if (mDHMsg2 != NULL && other.mDHMsg2 != NULL && memcmp(mDHMsg2, other.mDHMsg2, other.mDHMsg2Length) != 0)
        return false;

    return true;
}

AEExchangeReportRequest& AEExchangeReportRequest::operator=(const AEExchangeReportRequest& other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mSessionId, other.mDHMsg2Length, other.mDHMsg2, other.mDHMsg3Length, other.mTimeout);

    return *this;
}

bool AEExchangeReportRequest::check()
{
    if(mValidSizeCheck == false)
        return false;

    if (mDHMsg2 == NULL)
        return false;

    return true;
}

IAERequest::RequestClass AEExchangeReportRequest::getRequestClass() {
    return PLATFORM_CLASS;
}

IAEResponse* AEExchangeReportRequest::execute(IAESMLogic* aesmLogic) {
    aesm_error_t ret = AESM_UNEXPECTED_ERROR;
    uint8_t* dh_msg3 = NULL;

    if (check() == false)
    {
        ret = AESM_PARAMETER_ERROR;
    }
    else
    {
        dh_msg3 = new uint8_t[mDHMsg3Length];
        ret = aesmLogic->exchangeReport(mSessionId, mDHMsg2, mDHMsg2Length, dh_msg3, mDHMsg3Length);
    }

    IAEResponse* ae_res = new AEExchangeReportResponse(ret, mDHMsg3Length, dh_msg3);

    if (dh_msg3) 
    {
        delete [] dh_msg3;
    }

    return ae_res;
}

void AEExchangeReportRequest::visit(IAERequestVisitor& visitor)
{
    visitor.visitExchangeReportRequest(*this);
}
