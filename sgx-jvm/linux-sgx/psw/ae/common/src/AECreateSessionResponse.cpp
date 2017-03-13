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
#include <AECreateSessionResponse.h>

#include <stdlib.h>
#include <string.h>

AECreateSessionResponse::AECreateSessionResponse()
:mSessionId(0), mDHMsg1Length(0), mDHMsg1(NULL)
{
}

AECreateSessionResponse::AECreateSessionResponse(int errorCode, uint32_t sessionId, uint32_t dhMsg1Length, const uint8_t* dhMsg1)
:mSessionId(0), mDHMsg1Length(0), mDHMsg1(NULL)
{
    CopyFields(errorCode, sessionId, dhMsg1Length, dhMsg1);
}

AECreateSessionResponse::AECreateSessionResponse(const AECreateSessionResponse& other)
:mSessionId(0), mDHMsg1Length(0), mDHMsg1(NULL)
{
    CopyFields(other.mErrorCode, other.mSessionId, other.mDHMsg1Length, other.mDHMsg1);
}

AECreateSessionResponse::~AECreateSessionResponse()
{
    ReleaseMemory();
}

void AECreateSessionResponse::ReleaseMemory()
{
    if (mDHMsg1 != NULL)
    {
        if (mDHMsg1Length > 0)
            memset(mDHMsg1, 0, mDHMsg1Length);
        delete [] mDHMsg1;
        mDHMsg1 = NULL;
    }
    mErrorCode = SGX_ERROR_UNEXPECTED;
    mDHMsg1Length = 0;
    mSessionId = 0;
}

void AECreateSessionResponse::CopyFields(int errorCode, uint32_t sessionId, uint32_t dhMsg1Length, const uint8_t* dhMsg1)
{
    if(dhMsg1Length <= MAX_MEMORY_ALLOCATION)
    {
        mValidSizeCheck = true;
    }
    else
    {
        mValidSizeCheck = false;
        return;
    }


    mErrorCode = errorCode;
    mSessionId = sessionId;
    mDHMsg1Length = dhMsg1Length;

    if (dhMsg1 != NULL && dhMsg1Length > 0) {
        mDHMsg1 = new uint8_t[dhMsg1Length];
        memcpy(mDHMsg1, dhMsg1, dhMsg1Length);
    }
}

AEMessage* AECreateSessionResponse::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

bool AECreateSessionResponse::inflateWithMessage(AEMessage* message, ISerializer* serializer)
{
    return serializer->inflateResponse(message, this);
}

void AECreateSessionResponse::inflateValues(int errorCode, uint32_t sessionId, uint32_t dhMsg1Length, const uint8_t* dhMsg1)
{
    ReleaseMemory();
    
    CopyFields(errorCode, sessionId, dhMsg1Length, dhMsg1);
}

bool AECreateSessionResponse::operator==(const AECreateSessionResponse& other) const
{
    if (this == &other)
        return true;

    if (mErrorCode != other.mErrorCode ||
            mSessionId != other.mSessionId ||
            mDHMsg1Length != other.mDHMsg1Length)
        return false;

    if ((mDHMsg1 != other.mDHMsg1) &&
            (mDHMsg1 == NULL || other.mDHMsg1 == NULL))
        return false;

    if (mDHMsg1 != NULL && other.mDHMsg1 != NULL && memcmp(mDHMsg1, other.mDHMsg1, other.mDHMsg1Length) != 0)
        return false;



    return true;
}

AECreateSessionResponse& AECreateSessionResponse::operator=(const AECreateSessionResponse& other)
{
    if (this == & other)
        return *this;

    inflateValues(other.mErrorCode, other.mSessionId, other.mDHMsg1Length, other.mDHMsg1);

    return *this;
}

bool AECreateSessionResponse::check()
{
    if (mErrorCode != SGX_SUCCESS )
        return false;

    if (mValidSizeCheck == false)
        return false;
    
    if (mDHMsg1 == NULL)
        return false;

    return true;
}

void AECreateSessionResponse::visit(IAEResponseVisitor& visitor)
{
    visitor.visitCreateSessionResponse(*this);
}
