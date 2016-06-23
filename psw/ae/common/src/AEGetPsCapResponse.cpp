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
#include <AEGetPsCapResponse.h>

#include <stdlib.h>
#include <string.h>

AEGetPsCapResponse::AEGetPsCapResponse() :
    mPsCap(-1)
{
}

AEGetPsCapResponse::AEGetPsCapResponse(int errorCode, uint64_t ps_cap) :
    mPsCap(-1)
{
    CopyFields(errorCode, ps_cap);
}

AEGetPsCapResponse::AEGetPsCapResponse(const AEGetPsCapResponse& other) :
    mPsCap(-1)
{
    CopyFields(other.mErrorCode, other.mPsCap);
}

AEGetPsCapResponse::~AEGetPsCapResponse()
{
    ReleaseMemory();
}

void AEGetPsCapResponse::ReleaseMemory()
{
    mErrorCode = SGX_ERROR_UNEXPECTED;
}

void AEGetPsCapResponse::CopyFields(int errorCode, uint64_t ps_cap)
{
    mErrorCode = errorCode;
    mPsCap = ps_cap;
}


AEMessage* AEGetPsCapResponse::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

bool AEGetPsCapResponse::inflateWithMessage(AEMessage* message, ISerializer *serializer)
{
    return serializer->inflateResponse(message, this);
}

void AEGetPsCapResponse::inflateValues(int errorCode, uint64_t ps_cap)
{
    ReleaseMemory();

    CopyFields(errorCode, ps_cap);
}

bool AEGetPsCapResponse::operator==(const AEGetPsCapResponse &other) const
{
    if (this == &other)
            return true;

    if (mPsCap!= other.mPsCap)
            return false;
    return true;
}

AEGetPsCapResponse & AEGetPsCapResponse::operator=(const AEGetPsCapResponse &other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mErrorCode, other.mPsCap);

    return *this;
}

bool AEGetPsCapResponse::check()
{
    // no MAC to check at this point, but do some generic parameter check

    //impose a limit of 1MB for these messages. If larger then a transmission, or unmarshalling error may have occured
    //also a big value here might be an attack

    //first, fail if errorCode is not 0
    if (mErrorCode != SGX_SUCCESS)
        return false;

    return true;
}

void AEGetPsCapResponse::visit(IAEResponseVisitor& visitor)
{
    visitor.visitGetPsCapResponse(*this);
}

