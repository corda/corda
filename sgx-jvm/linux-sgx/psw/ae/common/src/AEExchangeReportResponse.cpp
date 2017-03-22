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
#include <AEExchangeReportResponse.h>

#include <string.h>
#include <stdlib.h>

AEExchangeReportResponse::AEExchangeReportResponse()
:mDHMsg3Length(0), mDHMsg3(NULL)
{
}

AEExchangeReportResponse::AEExchangeReportResponse(int errorCode, uint32_t dhMsg3Length, const uint8_t* dhMsg3)
:mDHMsg3Length(0), mDHMsg3(NULL)
{
    CopyFields(errorCode, dhMsg3Length, dhMsg3);
}

AEExchangeReportResponse::AEExchangeReportResponse(const AEExchangeReportResponse& other)
:mDHMsg3Length(0), mDHMsg3(NULL)
{
    CopyFields(other.mErrorCode, other.mDHMsg3Length, other.mDHMsg3);
}

AEExchangeReportResponse::~AEExchangeReportResponse()
{
    ReleaseMemory();
}

void AEExchangeReportResponse::ReleaseMemory()
{
    if (mDHMsg3 != NULL)
    {
        if (mDHMsg3Length > 0)
            memset(mDHMsg3, 0, mDHMsg3Length);
        delete [] mDHMsg3;
        mDHMsg3 = NULL;
    }
    mDHMsg3Length = 0;
    mErrorCode = SGX_ERROR_UNEXPECTED;
}

void AEExchangeReportResponse::CopyFields(int errorCode, uint32_t dhMsg3Length,const uint8_t* dhMsg3)
{
    if(dhMsg3Length <= MAX_MEMORY_ALLOCATION)
    {
        mValidSizeCheck = true;
    }
    else
    {
        mValidSizeCheck = false;
        return;
    }

    mErrorCode = errorCode;
    mDHMsg3Length = dhMsg3Length;
    if (dhMsg3 != NULL && dhMsg3Length > 0) {
        mDHMsg3 = new uint8_t[dhMsg3Length];
        memcpy(mDHMsg3, dhMsg3, dhMsg3Length);
    }
}

AEMessage* AEExchangeReportResponse::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

bool AEExchangeReportResponse::inflateWithMessage(AEMessage* message, ISerializer* serializer)
{
    return serializer->inflateResponse(message, this);
}

void AEExchangeReportResponse::inflateValues(int errorCode, uint32_t dhMsg3Length,const uint8_t* dhMsg3)
{
    ReleaseMemory();

    CopyFields(errorCode, dhMsg3Length, dhMsg3);
}

bool AEExchangeReportResponse::operator==(const AEExchangeReportResponse& other) const
{
    if (this == &other)
        return true;

    if (mErrorCode != other.mErrorCode ||
        mDHMsg3Length != other.mDHMsg3Length)
        return false;

    if ((mDHMsg3 != other.mDHMsg3) &&
        (mDHMsg3 == NULL || other.mDHMsg3 == NULL))
        return false;

    if (mDHMsg3 != NULL && other.mDHMsg3 != NULL && memcmp(mDHMsg3, other.mDHMsg3, other.mDHMsg3Length) != 0)
        return false;

    return true;
}

AEExchangeReportResponse& AEExchangeReportResponse::operator=(const AEExchangeReportResponse& other)
{
    if (this == &other)
        return *this;

    ReleaseMemory();

    CopyFields(other.mErrorCode, other.mDHMsg3Length, other.mDHMsg3);

    return *this;
}

bool AEExchangeReportResponse::check()
{
    if (mErrorCode != SGX_SUCCESS)
        return false;

    if (mValidSizeCheck == false)
        return false;

    if (mDHMsg3 == NULL)
        return false;

    return true;
}

void AEExchangeReportResponse::visit(IAEResponseVisitor& visitor)
{
    visitor.visitExchangeReportResponse(*this);
}
