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
#include <AESGXSwitchExtendedEpidGroupRequest.h>
#include <AESGXSwitchExtendedEpidGroupResponse.h>
#include <IAESMLogic.h>

#include <string.h>
#include <stdlib.h>

AESGXSwitchExtendedEpidGroupRequest::AESGXSwitchExtendedEpidGroupRequest()
:mExtendedEpidGroupId(0) 
{
}

AESGXSwitchExtendedEpidGroupRequest::AESGXSwitchExtendedEpidGroupRequest(uint32_t extendedGroupId, uint32_t timeout)
:mExtendedEpidGroupId(0)

{
    CopyFields(extendedGroupId, timeout);
}

AESGXSwitchExtendedEpidGroupRequest::AESGXSwitchExtendedEpidGroupRequest(const AESGXSwitchExtendedEpidGroupRequest& other)
:IAERequest(other), mExtendedEpidGroupId(0)
{
    CopyFields(other.mExtendedEpidGroupId, other.mTimeout);
}

AESGXSwitchExtendedEpidGroupRequest::~AESGXSwitchExtendedEpidGroupRequest()
{
    ReleaseMemory();
}

void AESGXSwitchExtendedEpidGroupRequest::ReleaseMemory()
{
    mExtendedEpidGroupId = 0;
}

void AESGXSwitchExtendedEpidGroupRequest::CopyFields(uint32_t extendedGroupId, uint32_t timeout)
{
    mExtendedEpidGroupId = extendedGroupId;
    mTimeout = timeout;
}

AEMessage* AESGXSwitchExtendedEpidGroupRequest::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

void AESGXSwitchExtendedEpidGroupRequest::inflateValues(uint32_t extendedGroupId, uint32_t timeout)
{
    ReleaseMemory();
    
    CopyFields(extendedGroupId, timeout);
}

bool AESGXSwitchExtendedEpidGroupRequest::operator==(const AESGXSwitchExtendedEpidGroupRequest& other) const
{
    if (this == &other)
        return true;

    if (mExtendedEpidGroupId != other.mExtendedEpidGroupId ||
        mTimeout != other.mTimeout)
        return false;

    return true;
}

AESGXSwitchExtendedEpidGroupRequest& AESGXSwitchExtendedEpidGroupRequest::operator=(const AESGXSwitchExtendedEpidGroupRequest& other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mExtendedEpidGroupId, other.mTimeout);

    return *this;
}

bool AESGXSwitchExtendedEpidGroupRequest::check()
{
    return true;
}

IAERequest::RequestClass AESGXSwitchExtendedEpidGroupRequest::getRequestClass()
{
    return PLATFORM_CLASS;
}

IAEResponse* AESGXSwitchExtendedEpidGroupRequest::execute(IAESMLogic* aesmLogic)
{
    aesm_error_t result; 
    result = aesmLogic->sgxSwitchExtendedEpidGroup(mExtendedEpidGroupId);
    
    return new AESGXSwitchExtendedEpidGroupResponse(result);
}

void AESGXSwitchExtendedEpidGroupRequest::visit(IAERequestVisitor& visitor)
{
    visitor.visitSGXSwitchExtendedEpidGroupRequest(*this);
}
