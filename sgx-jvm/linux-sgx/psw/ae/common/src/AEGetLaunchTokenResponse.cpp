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
#include <AEGetLaunchTokenResponse.h>

#include <string.h>
#include <stdlib.h>

AEGetLaunchTokenResponse::AEGetLaunchTokenResponse() :
    mTokenLength(0),
    mToken(NULL)
{
}

AEGetLaunchTokenResponse::AEGetLaunchTokenResponse(int errorCode, uint32_t tokenLength, const uint8_t* token) : 
    mTokenLength(0),
    mToken(NULL)
{
    CopyFields(errorCode, tokenLength, token);
}

AEGetLaunchTokenResponse::AEGetLaunchTokenResponse(const AEGetLaunchTokenResponse& other) : 
    mTokenLength(0),
    mToken(NULL)
{
    CopyFields(other.mErrorCode, other.mTokenLength, other.mToken);
}

AEGetLaunchTokenResponse::~AEGetLaunchTokenResponse()
{
    ReleaseMemory();
}

AEMessage* AEGetLaunchTokenResponse::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

bool AEGetLaunchTokenResponse::inflateWithMessage(AEMessage* message, ISerializer* serializer)
{
    return serializer->inflateResponse(message, this);
}

void AEGetLaunchTokenResponse::inflateValues(int errorCode, uint32_t tokenLength,const uint8_t* token)
{
    ReleaseMemory();

    CopyFields(errorCode, tokenLength, token);
}

        //operators
bool AEGetLaunchTokenResponse::operator==(const AEGetLaunchTokenResponse& other) const
{
    if (this == &other)
        return true;

    if (mTokenLength != other.mTokenLength)
        return false;

    if (mToken == NULL && mToken != other.mToken)
        return false;

    if (mToken != NULL && other.mToken != NULL)
        if (memcmp(mToken, other.mToken, other.mTokenLength) != 0)
            return false;

    return true;
}

AEGetLaunchTokenResponse& AEGetLaunchTokenResponse::operator=(const AEGetLaunchTokenResponse& other)
{
    if (this == &other)
        return *this;

    ReleaseMemory();

    CopyFields(other.mErrorCode, other.mTokenLength, other.mToken);

    return *this;
}

        //checks
bool AEGetLaunchTokenResponse::check()
{
    if (mErrorCode != SGX_SUCCESS)
        return false;

    //simple size check
    if (mValidSizeCheck == false)
        return false;

    if (mToken == NULL)
        return false;

    return true;
}

void AEGetLaunchTokenResponse::ReleaseMemory()
{
    if (mToken != NULL)
        delete [] mToken;
    mToken = NULL;
    mTokenLength = 0;
    mErrorCode = SGX_ERROR_UNEXPECTED;
}

void AEGetLaunchTokenResponse::CopyFields(int errorCode, uint32_t tokenLength,const uint8_t* token)
{   
    if(tokenLength <= MAX_MEMORY_ALLOCATION)
    {
        mValidSizeCheck = true;
    }
    else
    {
        mValidSizeCheck = false;
        return;
    }

    mErrorCode = errorCode;
    mTokenLength = tokenLength;

    if (token != NULL && tokenLength > 0)
    {
        mToken = new uint8_t[tokenLength];
        memcpy(mToken, token, tokenLength);
    }
}

void AEGetLaunchTokenResponse::visit(IAEResponseVisitor& visitor)
{
    visitor.visitGetLaunchTokenResponse(*this);
}
