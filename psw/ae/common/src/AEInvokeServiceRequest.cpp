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
#include <AEInvokeServiceRequest.h>
#include <AEInvokeServiceResponse.h>
#include <IAESMLogic.h>

#include <stdlib.h>
#include <string.h>

    AEInvokeServiceRequest::AEInvokeServiceRequest()
:mPSEMessageLength(0), mPSEMessage(NULL), mResponseSize(0)
{
}

    AEInvokeServiceRequest::AEInvokeServiceRequest(uint32_t pseMessageLength, const uint8_t* pseMessage, uint32_t pseResponseSize, uint32_t timeout)
:mPSEMessageLength(0), mPSEMessage(NULL), mResponseSize(0)
{
    CopyFields(pseMessageLength, pseMessage, pseResponseSize, timeout);
}

    AEInvokeServiceRequest::AEInvokeServiceRequest(const AEInvokeServiceRequest& other)
:IAERequest(other), mPSEMessageLength(0), mPSEMessage(NULL), mResponseSize(0)
{
    CopyFields(other.mPSEMessageLength, other.mPSEMessage, other.mResponseSize, other.mTimeout);
}

AEInvokeServiceRequest::~AEInvokeServiceRequest()
{
    ReleaseMemory();
}

void AEInvokeServiceRequest::ReleaseMemory()
{
    if (mPSEMessage != NULL)
    {
        if (mPSEMessageLength > 0)
            memset(mPSEMessage, 0, mPSEMessageLength);
        delete [] mPSEMessage;
        mPSEMessage = NULL;
    }
    mPSEMessageLength = 0;
    mResponseSize = 0;
    mTimeout = 0;
}

void AEInvokeServiceRequest::CopyFields(uint32_t pseMessageLength, const uint8_t* pseMessage, uint32_t pseResponseSize, uint32_t timeout)
{
    if(pseMessageLength <= MAX_MEMORY_ALLOCATION && pseResponseSize <= MAX_MEMORY_ALLOCATION)
    {
        mValidSizeCheck = true;
    }
    else
    {
        mValidSizeCheck = false;
        return;
    }

    mPSEMessageLength = pseMessageLength;
    if (pseMessage != NULL && pseMessageLength > 0)
    {
        mPSEMessage = new uint8_t[pseMessageLength];
        memcpy(mPSEMessage, pseMessage, pseMessageLength);
    }

    mTimeout = timeout;
    mResponseSize = pseResponseSize;
}

AEMessage* AEInvokeServiceRequest::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

void AEInvokeServiceRequest::inflateValues(uint32_t pseMessageLength, const uint8_t* pseMessage, uint32_t pseResponseSize, uint32_t timeout)
{
    ReleaseMemory();

    CopyFields(pseMessageLength, pseMessage, pseResponseSize, timeout);
}

bool AEInvokeServiceRequest::operator==(const AEInvokeServiceRequest& other) const
{
    if (this == & other)
        return true;

    if (mPSEMessageLength != other.mPSEMessageLength ||
            mResponseSize != other.mResponseSize ||
            mTimeout != other.mTimeout)
        return false;

    if ((mPSEMessage != other.mPSEMessage) &&
            (mPSEMessage == NULL || other.mPSEMessage == NULL))
        return false;

    if (mPSEMessage != NULL && other.mPSEMessage != NULL && memcmp(mPSEMessage, other.mPSEMessage, other.mPSEMessageLength) != 0)
        return false;
    //NOTE: timeout discarded from test
    return true;
}

AEInvokeServiceRequest& AEInvokeServiceRequest::operator=(const AEInvokeServiceRequest& other)
{
    if (this == &other)
        return * this;

    CopyFields(other.mPSEMessageLength, other.mPSEMessage, other.mResponseSize, other.mTimeout);

    return *this;
}

bool AEInvokeServiceRequest::check()
{
    if(mValidSizeCheck == false)
        return false;

    if (mPSEMessage == NULL)
        return false;

    return true;
}

IAERequest::RequestClass AEInvokeServiceRequest::getRequestClass() {
    return PLATFORM_CLASS;
}

IAEResponse* AEInvokeServiceRequest::execute(IAESMLogic* aesmLogic) {
    aesm_error_t ret = AESM_UNEXPECTED_ERROR;
    uint8_t* response = NULL;

    if (check() == false)   
    {
        ret = AESM_PARAMETER_ERROR;
    }
    else
    {
        response = new uint8_t[mResponseSize];
    }
    if (response != NULL)
    {
        ret = aesmLogic->invokeService(mPSEMessage, mPSEMessageLength, response, mResponseSize);
    }

    IAEResponse* ae_res= new AEInvokeServiceResponse(ret, mResponseSize, response);

    delete [] response;

    return ae_res;
}

void AEInvokeServiceRequest::visit(IAERequestVisitor& visitor)
{
    visitor.visitInvokeServiceRequest(*this);
}
