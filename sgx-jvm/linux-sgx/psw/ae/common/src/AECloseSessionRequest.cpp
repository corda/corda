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
#include <AECloseSessionRequest.h>
#include <AECloseSessionResponse.h>
#include <IAESMLogic.h>

#include <string.h>
#include <stdlib.h>

AECloseSessionRequest::AECloseSessionRequest()
:mSessionId(0) 
{
}

AECloseSessionRequest::AECloseSessionRequest(uint32_t sessionId, uint32_t timeout)
:mSessionId(0)

{
    CopyFields(sessionId, timeout);
}

AECloseSessionRequest::AECloseSessionRequest(const AECloseSessionRequest& other)
:IAERequest(other), mSessionId(0)
{
    CopyFields(other.mSessionId, other.mTimeout);
}

AECloseSessionRequest::~AECloseSessionRequest()
{
    ReleaseMemory();
}

void AECloseSessionRequest::ReleaseMemory()
{
    mSessionId = 0;
}

void AECloseSessionRequest::CopyFields(uint32_t sessionId, uint32_t timeout)
{
    mSessionId = sessionId;
    mTimeout = timeout;
}

AEMessage* AECloseSessionRequest::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

void AECloseSessionRequest::inflateValues(uint32_t sessionId, uint32_t timeout)
{
    ReleaseMemory();
    
    CopyFields(sessionId, timeout);
}

bool AECloseSessionRequest::operator==(const AECloseSessionRequest& other) const
{
    if (this == &other)
        return true;

    if (mSessionId != other.mSessionId ||
        mTimeout != other.mTimeout)
        return false;

    return true;
}

AECloseSessionRequest& AECloseSessionRequest::operator=(const AECloseSessionRequest& other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mSessionId, other.mTimeout);

    return *this;
}

bool AECloseSessionRequest::check()
{
    return true;
}

IAERequest::RequestClass AECloseSessionRequest::getRequestClass()
{
    return PLATFORM_CLASS;
}

IAEResponse* AECloseSessionRequest::execute(IAESMLogic* aesmLogic)
{
    aesm_error_t result; 
    result = aesmLogic->closeSession(mSessionId);
    
    return new AECloseSessionResponse(result);
}

void AECloseSessionRequest::visit(IAERequestVisitor& visitor)
{
    visitor.visitCloseSessionRequest(*this);
}
