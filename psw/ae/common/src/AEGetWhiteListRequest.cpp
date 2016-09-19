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
#include <AEGetWhiteListRequest.h>
#include <AEGetWhiteListResponse.h>
#include <IAESMLogic.h>

#include <string.h>
#include <stdlib.h>

AEGetWhiteListRequest::AEGetWhiteListRequest()
:mWhiteListSize(0) 
{
}

AEGetWhiteListRequest::AEGetWhiteListRequest(uint32_t whiteListSize, uint32_t timeout)
:mWhiteListSize(0)

{
    CopyFields(whiteListSize, timeout);
}

AEGetWhiteListRequest::AEGetWhiteListRequest(const AEGetWhiteListRequest& other)
:IAERequest(other), mWhiteListSize(0)
{
    CopyFields(other.mWhiteListSize, other.mTimeout);
}

AEGetWhiteListRequest::~AEGetWhiteListRequest()
{
    ReleaseMemory();
}

void AEGetWhiteListRequest::ReleaseMemory()
{
    mWhiteListSize = 0;
}

void AEGetWhiteListRequest::CopyFields(uint32_t whiteListSize, uint32_t timeout)
{
    mWhiteListSize = whiteListSize;
    mTimeout = timeout;
}

AEMessage* AEGetWhiteListRequest::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

void AEGetWhiteListRequest::inflateValues(uint32_t whiteListSize, uint32_t timeout)
{
    ReleaseMemory();
    
    CopyFields(whiteListSize, timeout);
}

bool AEGetWhiteListRequest::operator==(const AEGetWhiteListRequest& other) const
{
    if (this == &other)
        return true;

    if (mWhiteListSize != other.mWhiteListSize ||
        mTimeout != other.mTimeout)
        return false;

    return true;
}

AEGetWhiteListRequest& AEGetWhiteListRequest::operator=(const AEGetWhiteListRequest& other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mWhiteListSize, other.mTimeout);

    return *this;
}

bool AEGetWhiteListRequest::check()
{
    return true;
}

IAERequest::RequestClass AEGetWhiteListRequest::getRequestClass()
{
    return PLATFORM_CLASS;
}

IAEResponse* AEGetWhiteListRequest::execute(IAESMLogic* aesmLogic)
{
    uint8_t* white_list = new uint8_t[mWhiteListSize];
        
    aesm_error_t result = aesmLogic->getWhiteList(white_list, mWhiteListSize);

    AEGetWhiteListResponse* getWhiteListResponse = new AEGetWhiteListResponse(result, mWhiteListSize, white_list);
    delete [] white_list;
    return getWhiteListResponse;
}

void AEGetWhiteListRequest::visit(IAERequestVisitor& visitor)
{
    visitor.visitGetWhiteListRequest(*this);
}
