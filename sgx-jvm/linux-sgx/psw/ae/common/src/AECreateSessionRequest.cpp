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
#include <AECreateSessionRequest.h>
#include <AECreateSessionResponse.h>
#include <IAESMLogic.h>

#include <stdlib.h>

AECreateSessionRequest::AECreateSessionRequest()
:mDHMsg1Size(0)
{
}

AECreateSessionRequest::AECreateSessionRequest(uint32_t dhMsg1Size, uint32_t timeout)
:mDHMsg1Size(0)
{
    CopyFields(dhMsg1Size, timeout);
}

AECreateSessionRequest::AECreateSessionRequest(const AECreateSessionRequest& other)
:IAERequest(other), mDHMsg1Size(0)
{
    CopyFields(other.mDHMsg1Size, other.mTimeout);
}

AECreateSessionRequest::~AECreateSessionRequest()
{
    ReleaseMemory();
}

void AECreateSessionRequest::ReleaseMemory()
{
    //none for now
}

void AECreateSessionRequest::CopyFields(uint32_t dhMsg1Size, uint32_t timeout)
{
    if(dhMsg1Size <= MAX_MEMORY_ALLOCATION)
    {
        mValidSizeCheck = true;
    }
    else
    {
        mValidSizeCheck = false;
        return;
    }

    mDHMsg1Size = dhMsg1Size;
    mTimeout = timeout;
}

AEMessage* AECreateSessionRequest::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

void AECreateSessionRequest::inflateValues(uint32_t dhMsg1Size, uint32_t timeout)
{
    ReleaseMemory();

    CopyFields(dhMsg1Size, timeout);
}

bool AECreateSessionRequest::operator==(const AECreateSessionRequest& other) const
{
    if (this == &other)
        return true;
    
    if (mDHMsg1Size != other.mDHMsg1Size ||
        mTimeout != other.mTimeout)
        return false;

    return true;
}

AECreateSessionRequest& AECreateSessionRequest::operator=(const AECreateSessionRequest& other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mDHMsg1Size, mTimeout);

    return *this;
}

bool AECreateSessionRequest::check()
{
    if (mValidSizeCheck == false)
        return false; 
 
    return true;
}

IAERequest::RequestClass AECreateSessionRequest::getRequestClass() {
    return PLATFORM_CLASS;
}

IAEResponse* AECreateSessionRequest::execute(IAESMLogic* aesmLogic) {
    uint8_t* dh_msg1 = new uint8_t[mDHMsg1Size];
    uint32_t sid = 0;
    
    aesm_error_t result = aesmLogic->createSession(&sid, dh_msg1, mDHMsg1Size);

    AECreateSessionResponse* sessionResponse = new AECreateSessionResponse(result, sid, mDHMsg1Size, dh_msg1);
    delete [] dh_msg1;
    return sessionResponse;
}

void AECreateSessionRequest::visit(IAERequestVisitor& visitor) {
    visitor.visitCreateSessionRequest(*this);
}
