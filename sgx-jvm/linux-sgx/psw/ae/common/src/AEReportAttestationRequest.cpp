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
#include <AEReportAttestationRequest.h>
#include <AEReportAttestationResponse.h>
#include <IAESMLogic.h>

#include <string.h>
#include <stdlib.h>

AEReportAttestationRequest::AEReportAttestationRequest()
:mAttestationErrorCode(-1),
mPlatformInfoLength(0),
mPlatformInfo(NULL),
mUpdateInfoLength(0)
{
}

AEReportAttestationRequest::AEReportAttestationRequest(uint32_t platformInfoLength, const uint8_t* platformInfo, uint32_t attestation_error_code, uint32_t updateInfoLength, uint32_t timeout)
:mAttestationErrorCode(-1),
mPlatformInfoLength(0),
mPlatformInfo(NULL),
mUpdateInfoLength(0)

{
    CopyFields(platformInfoLength, platformInfo, attestation_error_code, updateInfoLength, timeout);
}

AEReportAttestationRequest::AEReportAttestationRequest(const AEReportAttestationRequest& other)
:IAERequest(other),
mAttestationErrorCode(-1),
mPlatformInfoLength(0),
mPlatformInfo(NULL),
mUpdateInfoLength(0)
{
    CopyFields(other.mPlatformInfoLength, other.mPlatformInfo, other.mAttestationErrorCode, other.mUpdateInfoLength, other.mTimeout);
}

AEReportAttestationRequest::~AEReportAttestationRequest()
{
    ReleaseMemory();
}

void AEReportAttestationRequest::ReleaseMemory()
{
    if (mPlatformInfo != NULL)
    {
        if (mPlatformInfoLength > 0)
        {
            memset(mPlatformInfo, 0, mPlatformInfoLength);
        }
        delete [] mPlatformInfo;
        mPlatformInfo = NULL;
    }
}

void AEReportAttestationRequest::CopyFields(uint32_t platformInfoLength, const uint8_t* platformInfo, uint32_t attestation_error_code, uint32_t updateInfoLength, uint32_t timeout)
{
    if (platformInfoLength <= MAX_MEMORY_ALLOCATION )
    {
        mValidSizeCheck = true;
    }
    else
    {
        mValidSizeCheck = false;
        return;
    }
    
    if (platformInfo != NULL && platformInfoLength > 0)
    {
        mPlatformInfo = new uint8_t[platformInfoLength];
        mPlatformInfoLength = platformInfoLength;
        memcpy(mPlatformInfo, platformInfo, platformInfoLength);
    }
    mAttestationErrorCode = attestation_error_code;
    mUpdateInfoLength = updateInfoLength;
    mTimeout = timeout;
}

AEMessage* AEReportAttestationRequest::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

void AEReportAttestationRequest::inflateValues(uint32_t platformInfoLength, const uint8_t* platformInfo, uint32_t attestation_error_code, uint32_t updateInfoLength, uint32_t timeout)
{
    ReleaseMemory();

    CopyFields(platformInfoLength, platformInfo, attestation_error_code, updateInfoLength, timeout);
}

bool AEReportAttestationRequest::operator==(const AEReportAttestationRequest& other) const
{
    if (this == &other)
        return true;

    if (mPlatformInfoLength != other.mPlatformInfoLength ||
            mTimeout != other.mTimeout)
        return false;

    if (mAttestationErrorCode != other.mAttestationErrorCode)
        return false;

    if (mUpdateInfoLength!= other.mUpdateInfoLength)
        return false;


    if (mPlatformInfo == other.mPlatformInfo)
        return true;

    if (mPlatformInfo == NULL)
        return false; //only mPlatformInfo in NULL, because mPlatformInfo != other.mPlatformInfo

    if (other.mPlatformInfo == NULL)
        return false; //only other.mPlatformInfo in NULL, because mPlatformInfo != other.mPlatformInfo

    if (memcmp(mPlatformInfo, other.mPlatformInfo, other.mPlatformInfoLength) != 0)
        return false;

    return true;
}

AEReportAttestationRequest& AEReportAttestationRequest::operator=(const AEReportAttestationRequest& other)
{
    if (this == &other)
        return *this;

    ReleaseMemory();

    CopyFields(other.mPlatformInfoLength, other.mPlatformInfo, other.mAttestationErrorCode, other.mUpdateInfoLength, other.mTimeout);

    return *this;
}

bool AEReportAttestationRequest::check()
{
    if (mValidSizeCheck == false)
        return false;        
    return true;
}

IAERequest::RequestClass AEReportAttestationRequest::getRequestClass()
{
    return QUOTING_CLASS;
}

IAEResponse* AEReportAttestationRequest::execute(IAESMLogic* aesmLogic)
{
    aesm_error_t result; 
    uint8_t* update_info = new uint8_t[mUpdateInfoLength];
    memset(update_info,0, mUpdateInfoLength);
    result = aesmLogic->reportAttestationStatus(mPlatformInfo, mPlatformInfoLength,
            mAttestationErrorCode,
            update_info, mUpdateInfoLength);
    
    IAEResponse* response = new AEReportAttestationResponse(result, mUpdateInfoLength, update_info);
    delete []update_info;
    return response;
}

void AEReportAttestationRequest::visit(IAERequestVisitor& visitor) 
{
    visitor.visitReportAttestationRequest(*this);
}

