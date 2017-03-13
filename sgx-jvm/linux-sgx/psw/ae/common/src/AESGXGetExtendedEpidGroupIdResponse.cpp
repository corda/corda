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
#include <AESGXGetExtendedEpidGroupIdResponse.h>

#include <stdlib.h>
#include <string.h>

AESGXGetExtendedEpidGroupIdResponse::AESGXGetExtendedEpidGroupIdResponse() :
    mExtendedEpidGroupId(-1)
{
}

AESGXGetExtendedEpidGroupIdResponse::AESGXGetExtendedEpidGroupIdResponse(int errorCode, uint32_t extendedGroupId) :
    mExtendedEpidGroupId(-1)
{
    CopyFields(errorCode, extendedGroupId);
}

AESGXGetExtendedEpidGroupIdResponse::AESGXGetExtendedEpidGroupIdResponse(const AESGXGetExtendedEpidGroupIdResponse& other) :
    mExtendedEpidGroupId(-1)
{
    CopyFields(other.mErrorCode, other.mExtendedEpidGroupId);
}

AESGXGetExtendedEpidGroupIdResponse::~AESGXGetExtendedEpidGroupIdResponse()
{
    ReleaseMemory();
}

void AESGXGetExtendedEpidGroupIdResponse::ReleaseMemory()
{
    mErrorCode = SGX_ERROR_UNEXPECTED;
}

void AESGXGetExtendedEpidGroupIdResponse::CopyFields(int errorCode, uint32_t extendedGroupId)
{
    mErrorCode = errorCode;
    mExtendedEpidGroupId = extendedGroupId;
}


AEMessage* AESGXGetExtendedEpidGroupIdResponse::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

bool AESGXGetExtendedEpidGroupIdResponse::inflateWithMessage(AEMessage* message, ISerializer *serializer)
{
    return serializer->inflateResponse(message, this);
}

void AESGXGetExtendedEpidGroupIdResponse::inflateValues(int errorCode, uint32_t extendedGroupId)
{
    ReleaseMemory();

    CopyFields(errorCode, extendedGroupId);
}

bool AESGXGetExtendedEpidGroupIdResponse::operator==(const AESGXGetExtendedEpidGroupIdResponse &other) const
{
    if (this == &other)
            return true;

    if (mExtendedEpidGroupId!= other.mExtendedEpidGroupId)
            return false;
    return true;
}

AESGXGetExtendedEpidGroupIdResponse & AESGXGetExtendedEpidGroupIdResponse::operator=(const AESGXGetExtendedEpidGroupIdResponse &other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mErrorCode, other.mExtendedEpidGroupId);

    return *this;
}

bool AESGXGetExtendedEpidGroupIdResponse::check()
{
    // no MAC to check at this point, but do some generic parameter check

    //impose a limit of 1MB for these messages. If larger then a transmission, or unmarshalling error may have occured
    //also a big value here might be an attack

    //first, fail if errorCode is not 0
    if (mErrorCode != SGX_SUCCESS)
        return false;

    return true;
}

void AESGXGetExtendedEpidGroupIdResponse::visit(IAEResponseVisitor& visitor)
{
    visitor.visitSGXGetExtendedEpidGroupIdResponse(*this);
}

