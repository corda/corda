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
#include <AEGetLaunchTokenRequest.h>
#include <AEGetLaunchTokenResponse.h>
#include <IAESMLogic.h>

#include <stdlib.h>

AEGetLaunchTokenRequest::AEGetLaunchTokenRequest() :
    mEnclaveMeasurementLength(0),
    mEnclaveMeasurement(NULL),
    mSigstructLength(0),
    mSigstruct(NULL),
    mSEAttributesLength(0),
    mSEAttributes(NULL)
{
}

AEGetLaunchTokenRequest::AEGetLaunchTokenRequest(uint32_t measurementLength, const uint8_t* measurement,
        uint32_t sigstructLength, const uint8_t* sigstruct,
        uint32_t attributesLength, const uint8_t* attributes,
        uint32_t timeout) :
    mEnclaveMeasurementLength(0),
    mEnclaveMeasurement(NULL),
    mSigstructLength(0),
    mSigstruct(NULL),
    mSEAttributesLength(0),
    mSEAttributes(NULL)
{
    CopyFields(measurementLength, measurement, sigstructLength, sigstruct, attributesLength, attributes, timeout);
}

AEGetLaunchTokenRequest::AEGetLaunchTokenRequest(const AEGetLaunchTokenRequest& other) :
    IAERequest(other),
    mEnclaveMeasurementLength(0),
    mEnclaveMeasurement(NULL),
    mSigstructLength(0),
    mSigstruct(NULL),
    mSEAttributesLength(0),
    mSEAttributes(NULL)
{
    CopyFields(other.mEnclaveMeasurementLength, other.mEnclaveMeasurement, other.mSigstructLength, other.mSigstruct, other.mSEAttributesLength, other.mSEAttributes, other.mTimeout);
}

AEGetLaunchTokenRequest::~AEGetLaunchTokenRequest()
{
    ReleaseMemory();
}

void AEGetLaunchTokenRequest::CopyFields(uint32_t measurementLength,const uint8_t* measurement,
        uint32_t sigstructLength,const uint8_t* sigstruct,
        uint32_t attributesLength,const uint8_t* attributes,
        uint32_t timeout)
{
    uint32_t totalSize = measurementLength + sigstructLength + attributesLength;
    if (measurementLength <= MAX_MEMORY_ALLOCATION && sigstructLength <= MAX_MEMORY_ALLOCATION &&
            attributesLength <= MAX_MEMORY_ALLOCATION && totalSize <= MAX_MEMORY_ALLOCATION)
    {
         mValidSizeCheck = true;
    }
    else
    {
         mValidSizeCheck = false;
        return;
    }

    mTimeout = timeout;

    if (measurement != NULL && measurementLength > 0)
    {
        mEnclaveMeasurement = new uint8_t[measurementLength];
        mEnclaveMeasurementLength = measurementLength;
        memcpy(mEnclaveMeasurement, measurement, measurementLength);
    }

    if (sigstruct != NULL && sigstructLength > 0)
    {
        mSigstruct = new uint8_t[sigstructLength];
        mSigstructLength = sigstructLength;
        memcpy(mSigstruct, sigstruct, sigstructLength);
    }

    if (attributes != NULL && attributesLength > 0)
    {
        mSEAttributes = new uint8_t[attributesLength];
        mSEAttributesLength = attributesLength;
        memcpy(mSEAttributes, attributes, attributesLength);
    }
}

void AEGetLaunchTokenRequest::ReleaseMemory()
{
    if (mEnclaveMeasurement != NULL)
    {
        delete [] mEnclaveMeasurement;
        mEnclaveMeasurement = NULL;
    }

    if (mSigstruct != NULL)
    {
        if (mSigstructLength > 0)
        {
            memset(mSigstruct, 0, mSigstructLength);
        }
        delete [] mSigstruct;
        mSigstruct = NULL;

    }

    if (mSEAttributes != NULL)
    {
        if (mSEAttributesLength > 0)
        {
            memset(mSEAttributes, 0, mSEAttributesLength);
        }
        delete [] mSEAttributes;
        mSEAttributes = NULL;
    }
}

AEMessage* AEGetLaunchTokenRequest::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

void AEGetLaunchTokenRequest::inflateValues(uint32_t measurementLength,const uint8_t* measurement,
        uint32_t sigstructLength,const uint8_t* sigstruct,
        uint32_t attributesLength,const uint8_t* attributes,
        uint32_t timeout)
{

    ReleaseMemory();

    CopyFields(measurementLength, measurement, sigstructLength, sigstruct, attributesLength, attributes, timeout);
}

bool AEGetLaunchTokenRequest::check()
{
    if (mValidSizeCheck == false)
        return false;

    //memory
    if (mEnclaveMeasurement == NULL || mSigstruct == NULL || mSEAttributes == NULL)
        return false;
    //sizes
    if (mEnclaveMeasurementLength == 0 || mSigstructLength == 0 || mSEAttributesLength == 0)
        return false;

    return true;
}

bool AEGetLaunchTokenRequest::operator==(const AEGetLaunchTokenRequest& other) const
{
    if (this == &other)
        return true;

    if (mEnclaveMeasurementLength != other.mEnclaveMeasurementLength ||
            mSigstructLength != other.mSigstructLength ||
            mSEAttributesLength != other.mSEAttributesLength ||
            mTimeout != other.mTimeout)
        return false;

    if ((mEnclaveMeasurement != other.mEnclaveMeasurement) &&
            (mEnclaveMeasurement == NULL || other.mEnclaveMeasurement == NULL))
        return false;

    if ((mSigstruct != other.mSigstruct) &&
            (mSigstruct == NULL || other.mSigstruct == NULL))
        return false;

    if ((mSEAttributes != other.mSEAttributes) &&
            (mSEAttributes == NULL || other.mSEAttributes == NULL))
        return false;

    if (mEnclaveMeasurement != NULL && memcmp(mEnclaveMeasurement, other.mEnclaveMeasurement, other.mEnclaveMeasurementLength) != 0)
        return false;

    if (mSigstruct != NULL && memcmp(mSigstruct, other.mSigstruct, other.mSigstructLength) != 0)
        return false;

    if (mSEAttributes != NULL && memcmp(mSEAttributes, other.mSEAttributes, other.mSEAttributesLength) != 0)
        return false;

    return true;

}

AEGetLaunchTokenRequest& AEGetLaunchTokenRequest::operator=(const AEGetLaunchTokenRequest& other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mEnclaveMeasurementLength, other.mEnclaveMeasurement, other.mSigstructLength, other.mSigstruct, other.mSEAttributesLength, other.mSEAttributes, other.mTimeout);

    return *this;
}

IAERequest::RequestClass AEGetLaunchTokenRequest::getRequestClass() {
    return LAUNCH_CLASS;
}

IAEResponse* AEGetLaunchTokenRequest::execute(IAESMLogic* aesmLogic) {
    uint8_t* token;
    uint32_t tokenSize;

    aesm_error_t result = aesmLogic->getLaunchToken(mEnclaveMeasurement, mEnclaveMeasurementLength, mSigstruct, mSigstructLength, mSEAttributes, mSEAttributesLength, &token, &tokenSize);

    IAEResponse* response = new AEGetLaunchTokenResponse(result, tokenSize, token);
    
    //free the buffer before send
    delete [] token;

    return response;
}

void AEGetLaunchTokenRequest::visit(IAERequestVisitor& visitor) 
{
    visitor.visitGetLaunchTokenRequest(*this);
}
