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
#include <AEReportAttestationResponse.h>

#include <string.h>
#include <stdlib.h>

AEReportAttestationResponse::AEReportAttestationResponse() :
    mUpdateInfoLength(0),
    mUpdateInfo(NULL)
{
}

AEReportAttestationResponse::AEReportAttestationResponse(int errorCode, uint32_t updateInfoLength, const uint8_t* updateInfo) : 
    mUpdateInfoLength(0),
    mUpdateInfo(NULL)
{
    CopyFields(errorCode, updateInfoLength, updateInfo);
}

AEReportAttestationResponse::AEReportAttestationResponse(const AEReportAttestationResponse& other) : 
    mUpdateInfoLength(0),
    mUpdateInfo(NULL)
{
    CopyFields(other.mErrorCode, other.mUpdateInfoLength, other.mUpdateInfo);
}

AEReportAttestationResponse::~AEReportAttestationResponse()
{
    ReleaseMemory();
}

void AEReportAttestationResponse::ReleaseMemory()
{
    if (mUpdateInfo != NULL)
        delete [] mUpdateInfo;
    mUpdateInfo = NULL;
    mUpdateInfoLength = 0;
    mErrorCode = SGX_ERROR_UNEXPECTED;
}

void AEReportAttestationResponse::CopyFields(int errorCode, uint32_t updateInfoLength,const uint8_t* updateInfo)
{
    if(updateInfoLength <= MAX_MEMORY_ALLOCATION )
    {
        mValidSizeCheck = true;
    }
    else
    {
        mValidSizeCheck = false;
        return;
    }

    mErrorCode = errorCode;
    mUpdateInfoLength = updateInfoLength;
    if (updateInfo != NULL && updateInfoLength > 0)
    {
        mUpdateInfo = new uint8_t[updateInfoLength];
        memcpy(mUpdateInfo, updateInfo, updateInfoLength);
    }
}

AEMessage* AEReportAttestationResponse::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

bool AEReportAttestationResponse::inflateWithMessage(AEMessage* message, ISerializer* serializer)
{
    return serializer->inflateResponse(message, this);
}

void AEReportAttestationResponse::inflateValues(int errorCode, uint32_t updateInfoLength, const uint8_t* updateInfo)
{
    ReleaseMemory();

    CopyFields(errorCode, updateInfoLength, updateInfo);
}

bool AEReportAttestationResponse::operator==(const AEReportAttestationResponse& other) const
{
    if (this == &other)
        return true;

    if (mUpdateInfoLength != other.mUpdateInfoLength)
        return false;

    if (mUpdateInfo == NULL && mUpdateInfo != other.mUpdateInfo)
        return false;

    if (mUpdateInfo != NULL && other.mUpdateInfo != NULL)
        if (memcmp(mUpdateInfo, other.mUpdateInfo, other.mUpdateInfoLength) != 0)
            return false;

    return true;
}

AEReportAttestationResponse& AEReportAttestationResponse::operator=(const AEReportAttestationResponse& other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mErrorCode, other.mUpdateInfoLength, other.mUpdateInfo);

    return *this;
}

//checks
bool AEReportAttestationResponse::check()
{
    if (mValidSizeCheck == false)
        return false;

    if (mUpdateInfo == NULL)
        return false;

    return true;
}

void AEReportAttestationResponse::visit(IAEResponseVisitor& visitor)
{
    visitor.visitReportAttestationResponse(*this);
}
