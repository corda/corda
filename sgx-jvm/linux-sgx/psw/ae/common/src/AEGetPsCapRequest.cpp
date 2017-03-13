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
#include <AEGetPsCapRequest.h>
#include <AEGetPsCapResponse.h>
#include <IAESMLogic.h>

#include <stdlib.h>

#include <sgx_uae_service.h>

AEGetPsCapRequest::AEGetPsCapRequest(uint32_t timeout) : IAERequest(timeout) {
}

AEGetPsCapRequest::AEGetPsCapRequest(const AEGetPsCapRequest& other)
: IAERequest(other)
{
    CopyFields(other.mTimeout);
}

AEGetPsCapRequest::~AEGetPsCapRequest()
{
    ReleaseMemory();
}

void AEGetPsCapRequest::ReleaseMemory()
{
    //empty for now
}


void AEGetPsCapRequest::CopyFields(uint32_t timeout)
{
    mTimeout = timeout;
}

AEMessage* AEGetPsCapRequest::serialize(ISerializer* serializer){
    return serializer->serialize(this);
}

IAERequest::RequestClass AEGetPsCapRequest::getRequestClass() {
    return PLATFORM_CLASS;
}

void AEGetPsCapRequest::inflateValues(uint32_t timeout)
{
    ReleaseMemory();

    CopyFields(timeout);
}

bool AEGetPsCapRequest::operator==(const AEGetPsCapRequest& other) const
{
    if (this == &other)
        return true;

    if (mTimeout != other.mTimeout)
        return false;

    return true;    //no members , default to true
}

AEGetPsCapRequest& AEGetPsCapRequest::operator=(const AEGetPsCapRequest& other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mTimeout);
 
    //do nothing - no members
    return *this;
}

void AEGetPsCapRequest::visit(IAERequestVisitor& visitor)
{
    visitor.visitGetPsCapRequest(*this);
}


IAEResponse* AEGetPsCapRequest::execute(IAESMLogic* aesmLogic) 
{
    uint64_t ps_cap;

    aesm_error_t result = aesmLogic->getPsCap(&ps_cap);

    AEGetPsCapResponse * response = new AEGetPsCapResponse((uint32_t)result, ps_cap);

    return response;
}
