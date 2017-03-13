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
#include <AESGXGetExtendedEpidGroupIdRequest.h>
#include <AESGXGetExtendedEpidGroupIdResponse.h>
#include <IAESMLogic.h>
#include <stdlib.h>
#include <sgx_uae_service.h>

AESGXGetExtendedEpidGroupIdRequest::AESGXGetExtendedEpidGroupIdRequest(uint32_t timeout) : IAERequest(timeout) {
}

AESGXGetExtendedEpidGroupIdRequest::AESGXGetExtendedEpidGroupIdRequest(const AESGXGetExtendedEpidGroupIdRequest& other)
: IAERequest(other)
{
    CopyFields(other.mTimeout);
}

AESGXGetExtendedEpidGroupIdRequest::~AESGXGetExtendedEpidGroupIdRequest()
{
    ReleaseMemory();
}

void AESGXGetExtendedEpidGroupIdRequest::ReleaseMemory()
{
    //empty for now
}


void AESGXGetExtendedEpidGroupIdRequest::CopyFields(uint32_t timeout)
{
    mTimeout = timeout;
}

AEMessage* AESGXGetExtendedEpidGroupIdRequest::serialize(ISerializer* serializer){
    return serializer->serialize(this);
}

IAERequest::RequestClass AESGXGetExtendedEpidGroupIdRequest::getRequestClass() {
    return PLATFORM_CLASS;
}

void AESGXGetExtendedEpidGroupIdRequest::inflateValues(uint32_t timeout)
{
    ReleaseMemory();

    CopyFields(timeout);
}

bool AESGXGetExtendedEpidGroupIdRequest::operator==(const AESGXGetExtendedEpidGroupIdRequest& other) const
{
    if (this == &other)
        return true;

    if (mTimeout != other.mTimeout)
        return false;

    return true;    //no members , default to true
}

AESGXGetExtendedEpidGroupIdRequest& AESGXGetExtendedEpidGroupIdRequest::operator=(const AESGXGetExtendedEpidGroupIdRequest& other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mTimeout);
 
    //do nothing - no members
    return *this;
}

void AESGXGetExtendedEpidGroupIdRequest::visit(IAERequestVisitor& visitor)
{
    visitor.visitSGXGetExtendedEpidGroupIdRequest(*this);
}


IAEResponse* AESGXGetExtendedEpidGroupIdRequest::execute(IAESMLogic* aesmLogic) 
{
    uint32_t extended_group_id;

    aesm_error_t result = aesmLogic->sgxGetExtendedEpidGroupId(&extended_group_id);

    AESGXGetExtendedEpidGroupIdResponse * response = new AESGXGetExtendedEpidGroupIdResponse((uint32_t)result, extended_group_id);

    return response;
}
