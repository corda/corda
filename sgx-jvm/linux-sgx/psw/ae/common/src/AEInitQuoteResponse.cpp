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
#include <AEInitQuoteResponse.h>

#include <stdlib.h>
#include <string.h>

AEInitQuoteResponse::AEInitQuoteResponse() :
    mGIDLength(0),
    mTargetInfoLength(0),
    mTargetInfo(NULL),
    mGID(NULL)
{
}

AEInitQuoteResponse::AEInitQuoteResponse(int errorCode, uint32_t gidLength, const uint8_t* gid,
                            uint32_t targetInfoLength, const uint8_t* targetInfo) :
    mGIDLength(0),
    mTargetInfoLength(0),
    mTargetInfo(NULL),
    mGID(NULL)
{
    CopyFields(errorCode, gidLength, gid, targetInfoLength, targetInfo);
}

AEInitQuoteResponse::AEInitQuoteResponse(const AEInitQuoteResponse& other) :
    mGIDLength(0),
    mTargetInfoLength(0),
    mTargetInfo(NULL),
    mGID(NULL)
{
    CopyFields(other.mErrorCode, other.mGIDLength, other.mGID, other.mTargetInfoLength, other.mTargetInfo);
}

AEInitQuoteResponse::~AEInitQuoteResponse()
{
    ReleaseMemory();
}

void AEInitQuoteResponse::ReleaseMemory()
{
    if (mGID != NULL){
        delete [] mGID;
        mGID = NULL;
        mGIDLength = 0;
    }

    if (mTargetInfo != NULL)
    {
        delete [] mTargetInfo;
        mTargetInfo = NULL;
        mTargetInfoLength = 0;
    }

    mErrorCode = SGX_ERROR_UNEXPECTED;
}

void AEInitQuoteResponse::CopyFields(int errorCode, uint32_t gidLength, const uint8_t* gid,
                            uint32_t targetInfoLength, const uint8_t* targetInfo)
{
    uint32_t totalSize = gidLength + targetInfoLength;
    
    if(gidLength <= MAX_MEMORY_ALLOCATION && targetInfoLength <= MAX_MEMORY_ALLOCATION
        && totalSize <= MAX_MEMORY_ALLOCATION )
    {
        mValidSizeCheck = true;
    }
    else
    {
        mValidSizeCheck = false;
        return;
    }

    mErrorCode = errorCode;

    mGIDLength = gidLength;

    if (gid == NULL)
        mGID = NULL;
    else
    {
        mGID = new uint8_t[mGIDLength];
        memcpy(mGID, gid, mGIDLength);
    }

    mTargetInfoLength = targetInfoLength;
    mTargetInfo = new uint8_t[mTargetInfoLength];
    memcpy(mTargetInfo, targetInfo, mTargetInfoLength);
}


AEMessage* AEInitQuoteResponse::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

bool AEInitQuoteResponse::inflateWithMessage(AEMessage* message, ISerializer *serializer)
{
    return serializer->inflateResponse(message, this);
}

void AEInitQuoteResponse::inflateValues(int errorCode, uint32_t gidLength, const uint8_t* gid,
                            uint32_t targetInfoLength, const uint8_t* targetInfo)
{
    ReleaseMemory();

    CopyFields(errorCode, gidLength, gid, targetInfoLength, targetInfo);
}

bool AEInitQuoteResponse::operator==(const AEInitQuoteResponse &other) const
{
    if (this == &other)
            return true;

    if (mTargetInfoLength != other.mTargetInfoLength ||
        mGIDLength        != other.mGIDLength)
            return false;

    if (memcmp(mGID, other.mGID, mGIDLength) != 0)
        return false;

    if (memcmp(mTargetInfo, other.mTargetInfo, mTargetInfoLength) != 0)
        return false;

    return true;
}

AEInitQuoteResponse & AEInitQuoteResponse::operator=(const AEInitQuoteResponse &other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mErrorCode, other.mGIDLength, other.mGID, other.mTargetInfoLength, other.mTargetInfo);

    return *this;
}

bool AEInitQuoteResponse::check()
{
    // no MAC to check at this point, but do some generic parameter check

    //first, fail if errorCode is not 0
    if (mErrorCode != SGX_SUCCESS)
        return false;

    if (mValidSizeCheck == false)
        return false;
    
     //check memory allocation fail in this object
    if (mTargetInfoLength > 0 && mTargetInfo == NULL)
        return false;

    if (mGIDLength > 0 && mGID == NULL)
        return false;

    return true;
}

void AEInitQuoteResponse::visit(IAEResponseVisitor& visitor)
{
    visitor.visitInitQuoteResponse(*this);
}
