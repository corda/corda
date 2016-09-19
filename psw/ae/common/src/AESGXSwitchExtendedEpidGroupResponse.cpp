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
#include <AESGXSwitchExtendedEpidGroupResponse.h>

#include <string.h>
#include <stdlib.h>

AESGXSwitchExtendedEpidGroupResponse::AESGXSwitchExtendedEpidGroupResponse()
{
}

AESGXSwitchExtendedEpidGroupResponse::AESGXSwitchExtendedEpidGroupResponse(int errorCode)
{
    CopyFields(errorCode);
}

AESGXSwitchExtendedEpidGroupResponse::AESGXSwitchExtendedEpidGroupResponse(const AESGXSwitchExtendedEpidGroupResponse& other)
{
    CopyFields(other.mErrorCode);
}

AESGXSwitchExtendedEpidGroupResponse::~AESGXSwitchExtendedEpidGroupResponse()
{
    ReleaseMemory();
}

void AESGXSwitchExtendedEpidGroupResponse::ReleaseMemory()
{
}

void AESGXSwitchExtendedEpidGroupResponse::CopyFields(int errorCode)
{
    mErrorCode = errorCode;
}

AEMessage* AESGXSwitchExtendedEpidGroupResponse::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

bool AESGXSwitchExtendedEpidGroupResponse::inflateWithMessage(AEMessage* message, ISerializer* serializer)
{
    return serializer->inflateResponse(message, this);
}

void AESGXSwitchExtendedEpidGroupResponse::inflateValues(int errorCode)
{
    ReleaseMemory();

    CopyFields(errorCode);
}

bool AESGXSwitchExtendedEpidGroupResponse::operator==(const AESGXSwitchExtendedEpidGroupResponse& other) const
{
    if (this == &other)
        return true;

    if (mErrorCode != other.mErrorCode)
        return false;

    return true;
}

AESGXSwitchExtendedEpidGroupResponse& AESGXSwitchExtendedEpidGroupResponse::operator=(const AESGXSwitchExtendedEpidGroupResponse& other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mErrorCode);

    return *this;
}

bool AESGXSwitchExtendedEpidGroupResponse::check()
{
    if (mErrorCode != SGX_SUCCESS)
        return false;
    return true;
}

void AESGXSwitchExtendedEpidGroupResponse::visit(IAEResponseVisitor& visitor)
{
    visitor.visitSGXSwitchExtendedEpidGroupResponse(*this);
}
