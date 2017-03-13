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
#include <AEGetWhiteListSizeResponse.h>

#include <stdlib.h>
#include <string.h>

AEGetWhiteListSizeResponse::AEGetWhiteListSizeResponse() :
    mWhiteListSize(-1)
{
}

AEGetWhiteListSizeResponse::AEGetWhiteListSizeResponse(int errorCode, uint32_t white_list_size) :
    mWhiteListSize(-1)
{
    CopyFields(errorCode, white_list_size);
}

AEGetWhiteListSizeResponse::AEGetWhiteListSizeResponse(const AEGetWhiteListSizeResponse& other) :
    mWhiteListSize(-1)
{
    CopyFields(other.mErrorCode, other.mWhiteListSize);
}

AEGetWhiteListSizeResponse::~AEGetWhiteListSizeResponse()
{
    ReleaseMemory();
}

void AEGetWhiteListSizeResponse::ReleaseMemory()
{
    mErrorCode = SGX_ERROR_UNEXPECTED;
}

void AEGetWhiteListSizeResponse::CopyFields(int errorCode, uint32_t white_list_size)
{
    mErrorCode = errorCode;
    mWhiteListSize = white_list_size;
}


AEMessage* AEGetWhiteListSizeResponse::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

bool AEGetWhiteListSizeResponse::inflateWithMessage(AEMessage* message, ISerializer *serializer)
{
    return serializer->inflateResponse(message, this);
}

void AEGetWhiteListSizeResponse::inflateValues(int errorCode, uint32_t white_list_size)
{
    ReleaseMemory();

    CopyFields(errorCode, white_list_size);
}

bool AEGetWhiteListSizeResponse::operator==(const AEGetWhiteListSizeResponse &other) const
{
    if (this == &other)
            return true;

    if (mWhiteListSize!= other.mWhiteListSize)
            return false;
    return true;
}

AEGetWhiteListSizeResponse & AEGetWhiteListSizeResponse::operator=(const AEGetWhiteListSizeResponse &other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mErrorCode, other.mWhiteListSize);

    return *this;
}

bool AEGetWhiteListSizeResponse::check()
{
    // no MAC to check at this point, but do some generic parameter check

    //impose a limit of 1MB for these messages. If larger then a transmission, or unmarshalling error may have occured
    //also a big value here might be an attack

    //first, fail if errorCode is not 0
    if (mErrorCode != SGX_SUCCESS)
        return false;

    return true;
}

void AEGetWhiteListSizeResponse::visit(IAEResponseVisitor& visitor)
{
    visitor.visitGetWhiteListSizeResponse(*this);
}

