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
#include <AEInvokeServiceResponse.h>

#include <stdlib.h>
#include <string.h>

    AEInvokeServiceResponse::AEInvokeServiceResponse()
:mPSEMessageLength(0), mPSEMessage(NULL)
{
}

    AEInvokeServiceResponse::AEInvokeServiceResponse(int errorCode, uint32_t pseMessageLength, const uint8_t* pseMessage)
:mPSEMessageLength(0), mPSEMessage(NULL)
{
    CopyFields(errorCode, pseMessageLength, pseMessage);
}

    AEInvokeServiceResponse::AEInvokeServiceResponse(const AEInvokeServiceResponse& other)
:mPSEMessageLength(0), mPSEMessage(NULL)
{
    CopyFields(other.mErrorCode, other.mPSEMessageLength, other.mPSEMessage);
}

AEInvokeServiceResponse::~AEInvokeServiceResponse()
{
    ReleaseMemory();
}

void AEInvokeServiceResponse::ReleaseMemory()
{
    if (mPSEMessage != NULL)
    {
        if (mPSEMessageLength > 0)
            memset(mPSEMessage, 0, mPSEMessageLength);
        delete [] mPSEMessage;
        mPSEMessage = NULL;
    }
    mErrorCode = SGX_ERROR_UNEXPECTED;
    mPSEMessageLength = 0;
}

void AEInvokeServiceResponse::CopyFields(int errorCode, uint32_t pseMessageLength,const uint8_t* pseMessage)
{
    if(pseMessageLength <= MAX_MEMORY_ALLOCATION )
    {
        mValidSizeCheck = true;
    }
    else
    {
        mValidSizeCheck = false;
        return;
    }

    mErrorCode = errorCode;
    mPSEMessageLength = pseMessageLength;
    if (pseMessage != NULL && pseMessageLength > 0) {
        mPSEMessage = new uint8_t[pseMessageLength];
        memcpy(mPSEMessage, pseMessage, pseMessageLength);
    }
}

AEMessage* AEInvokeServiceResponse::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

bool AEInvokeServiceResponse::inflateWithMessage(AEMessage* message, ISerializer* serializer)
{
    return serializer->inflateResponse(message, this);
}

void AEInvokeServiceResponse::inflateValues(int errorCode, uint32_t pseMessageLength,const uint8_t* pseMessage)
{
    ReleaseMemory();

    CopyFields(errorCode, pseMessageLength, pseMessage);
}

bool AEInvokeServiceResponse::operator==(const AEInvokeServiceResponse& other) const
{
    if (this == &other)
        return true;

    if (mErrorCode != other.mErrorCode ||
            mPSEMessageLength != other.mPSEMessageLength)
        return false;

    if ((mPSEMessage != other.mPSEMessage) &&
            (mPSEMessage == NULL || other.mPSEMessage == NULL))
        return false;

    if (mPSEMessage != NULL && other.mPSEMessage != NULL &&
            memcmp(mPSEMessage, other.mPSEMessage, other.mPSEMessageLength) != 0)
        return false;

    return true;
}

AEInvokeServiceResponse& AEInvokeServiceResponse::operator=(const AEInvokeServiceResponse& other)
{
    if (this == &other)
        return * this;

    inflateValues(other.mErrorCode, other.mPSEMessageLength, other.mPSEMessage);

    return *this;
}

bool AEInvokeServiceResponse::check()
{
    if (mErrorCode != SGX_SUCCESS)
        return false;

    if (mValidSizeCheck == false)
        return false;

    if (mPSEMessage == NULL)
        return false;

    return true;
}

void AEInvokeServiceResponse::visit(IAEResponseVisitor& visitor)
{
    visitor.visitInvokeServiceResponse(*this);
}
