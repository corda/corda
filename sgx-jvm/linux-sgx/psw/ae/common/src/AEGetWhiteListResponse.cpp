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
#include <AEGetWhiteListResponse.h>
#include <stdlib.h>
#include <string.h>

AEGetWhiteListResponse::AEGetWhiteListResponse()
:mWhiteListLength(0), mWhiteList(NULL)
{
}

AEGetWhiteListResponse::AEGetWhiteListResponse(int errorCode, uint32_t whiteListLength, const uint8_t* whiteList)
:mWhiteListLength(0), mWhiteList(NULL)
{
    CopyFields(errorCode, whiteListLength, whiteList);
}

AEGetWhiteListResponse::AEGetWhiteListResponse(const AEGetWhiteListResponse& other)
:mWhiteListLength(0), mWhiteList(NULL)
{
    CopyFields(other.mErrorCode, other.mWhiteListLength, other.mWhiteList);
}

AEGetWhiteListResponse::~AEGetWhiteListResponse()
{
    ReleaseMemory();
}

void AEGetWhiteListResponse::ReleaseMemory()
{
    if (mWhiteList != NULL)
    {
        if (mWhiteListLength > 0)
            memset(mWhiteList, 0, mWhiteListLength);
        delete [] mWhiteList;
        mWhiteList = NULL;
    }
    mErrorCode = SGX_ERROR_UNEXPECTED;
    mWhiteListLength = 0;
}

void AEGetWhiteListResponse::CopyFields(int errorCode, uint32_t whiteListLength,const uint8_t* whiteList)
{
    if(whiteListLength <= MAX_MEMORY_ALLOCATION )
    {
        mValidSizeCheck = true;
    }
    else
    {
        mValidSizeCheck = false;
        return;
    }

    mErrorCode = errorCode;
    mWhiteListLength = whiteListLength;
    if (whiteList != NULL && whiteListLength > 0) {
        mWhiteList = new uint8_t[whiteListLength];
        memcpy(mWhiteList, whiteList, whiteListLength);
    }
}

AEMessage* AEGetWhiteListResponse::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

bool AEGetWhiteListResponse::inflateWithMessage(AEMessage* message, ISerializer* serializer)
{
    return serializer->inflateResponse(message, this);
}

void AEGetWhiteListResponse::inflateValues(int errorCode, uint32_t whiteListLength,const uint8_t* whiteList)
{
    ReleaseMemory();

    CopyFields(errorCode, whiteListLength, whiteList);
}

bool AEGetWhiteListResponse::operator==(const AEGetWhiteListResponse& other) const
{
    if (this == &other)
        return true;

    if (mErrorCode != other.mErrorCode ||
            mWhiteListLength != other.mWhiteListLength)
        return false;

    if ((mWhiteList != other.mWhiteList) &&
            (mWhiteList == NULL || other.mWhiteList == NULL))
        return false;

    if (mWhiteList != NULL && other.mWhiteList != NULL &&
            memcmp(mWhiteList, other.mWhiteList, other.mWhiteListLength) != 0)
        return false;

    return true;
}

AEGetWhiteListResponse& AEGetWhiteListResponse::operator=(const AEGetWhiteListResponse& other)
{
    if (this == &other)
        return * this;

    inflateValues(other.mErrorCode, other.mWhiteListLength, other.mWhiteList);

    return *this;
}

bool AEGetWhiteListResponse::check()
{
    if (mErrorCode != SGX_SUCCESS)
        return false;

    if (mValidSizeCheck == false)
        return false;

    if (mWhiteList == NULL)
        return false;

    return true;
}

void AEGetWhiteListResponse::visit(IAEResponseVisitor& visitor)
{
    visitor.visitGetWhiteListResponse(*this);
}
