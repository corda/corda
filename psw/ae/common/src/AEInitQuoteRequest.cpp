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
#include <AEInitQuoteRequest.h>
#include <AEInitQuoteResponse.h>
#include <IAESMLogic.h>

#include <stdlib.h>

#include <sgx_uae_service.h>

AEInitQuoteRequest::AEInitQuoteRequest(uint32_t timeout)
{
    inflateValues(timeout);
}

AEInitQuoteRequest::AEInitQuoteRequest(const AEInitQuoteRequest& other)
:IAERequest(other)
{
    CopyFields(other.mTimeout);
}

AEInitQuoteRequest::~AEInitQuoteRequest()
{
}

void AEInitQuoteRequest::ReleaseMemory()
{
    //empty for now
}

void AEInitQuoteRequest::CopyFields(uint32_t timeout)
{
    mTimeout = timeout;
}

AEMessage* AEInitQuoteRequest::serialize(ISerializer* serializer){
    return serializer->serialize(this);
}

IAERequest::RequestClass AEInitQuoteRequest::getRequestClass() {
    return QUOTING_CLASS;
}

void AEInitQuoteRequest::inflateValues(uint32_t timeout)
{
    ReleaseMemory();

    CopyFields(timeout);
}

bool AEInitQuoteRequest::operator==(const AEInitQuoteRequest& other) const
{
    if (this == &other)
        return true;

    if (mTimeout != other.mTimeout)
        return false;

    return true;    //no members , default to true
}

AEInitQuoteRequest& AEInitQuoteRequest::operator=(const AEInitQuoteRequest& other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mTimeout); 

    //do nothing - no members
    return *this;
}

void AEInitQuoteRequest::visit(IAERequestVisitor& visitor)
{
    visitor.visitInitQuoteRequest(*this);
}


IAEResponse* AEInitQuoteRequest::execute(IAESMLogic* aesmLogic) {
    uint8_t* target_info = new uint8_t[sizeof(sgx_target_info_t)];
    uint8_t* gid = new uint8_t[sizeof(sgx_epid_group_id_t)];
    uint32_t target_info_length=sizeof(sgx_target_info_t);
    uint32_t gid_length=sizeof(sgx_epid_group_id_t);

    aesm_error_t result= aesmLogic->initQuote(target_info,target_info_length,gid, gid_length);

    AEInitQuoteResponse * response = new AEInitQuoteResponse((uint32_t)result, gid_length, gid, target_info_length, target_info);

    delete [] target_info;
    delete [] gid;

    return response;
}
