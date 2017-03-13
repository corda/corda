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
#include <AEGetQuoteResponse.h>

#include <stdlib.h>
#include <string.h>

AEGetQuoteResponse::AEGetQuoteResponse() :
    mQuoteLength(0),
    mQuote(NULL),
    mQEReportLength(0),
    mQEReport(NULL)
{
}

AEGetQuoteResponse::AEGetQuoteResponse(int errorCode, uint32_t quoteLength, const uint8_t* quote,
                                                      uint32_t qeReportLength, const uint8_t* qeReport) :
    mQuoteLength(0),
    mQuote(NULL),
    mQEReportLength(0),
    mQEReport(NULL)
{
    CopyFields(errorCode, quoteLength, quote,qeReportLength, qeReport);
}

//copy constructor
AEGetQuoteResponse::AEGetQuoteResponse(const AEGetQuoteResponse& other) :
    mQuoteLength(0),
    mQuote(NULL),
    mQEReportLength(0),
    mQEReport(NULL)
{
    CopyFields(other.mErrorCode, other.mQuoteLength, other.mQuote, other.mQEReportLength, other.mQEReport);
}

AEGetQuoteResponse::~AEGetQuoteResponse()
{
    ReleaseMemory();
}

void AEGetQuoteResponse::ReleaseMemory()
{
    if (mQuote != NULL)
        delete [] mQuote;
    if (mQEReport != NULL)
        delete [] mQEReport;
    mQuote = NULL;
    mQEReport = NULL;
    mQuoteLength = 0;
    mQEReportLength = 0;
    mErrorCode = SGX_ERROR_UNEXPECTED;
}

void AEGetQuoteResponse::CopyFields(int errorCode, uint32_t quoteLength,const uint8_t* quote,
                                                   uint32_t qeReportLength, const uint8_t* qeReport)
{
    uint32_t totalSize = qeReportLength + quoteLength;
    
    if(quoteLength <= MAX_MEMORY_ALLOCATION && qeReportLength <= MAX_MEMORY_ALLOCATION
        && totalSize <= MAX_MEMORY_ALLOCATION )
    {
        mValidSizeCheck = true;
    }
    else
    {
        mValidSizeCheck = false;
        return;
    }

    mErrorCode = errorCode;
    mQuoteLength = quoteLength;
    mQEReportLength = qeReportLength;

    if (quote != NULL && quoteLength > 0)
    {
        mQuote = new uint8_t[quoteLength];
        memcpy(mQuote, quote, quoteLength);
    }
    
    if (qeReport != NULL&& qeReportLength > 0)
    {
        mQEReport = new uint8_t[qeReportLength];
        memcpy(mQEReport, qeReport, qeReportLength);
    }
}

AEMessage* AEGetQuoteResponse::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

bool AEGetQuoteResponse::inflateWithMessage(AEMessage* message, ISerializer* serializer)
{
    return serializer->inflateResponse(message, this);
}

void AEGetQuoteResponse::inflateValues(int errorCode, uint32_t quoteLength, const uint8_t* quote,
                                                      uint32_t qeReportLength, const uint8_t* qeReport)
{
    ReleaseMemory();

    CopyFields(errorCode, quoteLength, quote, qeReportLength, qeReport);
}

bool AEGetQuoteResponse::operator==(const AEGetQuoteResponse& other) const
{
    if (this == &other)
        return true;

    if (mErrorCode != other.mErrorCode      ||
        mQuoteLength != other.mQuoteLength  ||
        mQEReportLength != other.mQEReportLength)
            return false;

    if (mQuote != NULL && other.mQuote != NULL)
        if (memcmp(mQuote, other.mQuote, other.mQuoteLength) != 0)
            return false;
    if (mQEReport != NULL && other.mQEReport != NULL)
        if (memcmp(mQEReport, other.mQEReport, other.mQEReportLength) != 0)
            return false;

    return true;
}

AEGetQuoteResponse& AEGetQuoteResponse::operator=(const AEGetQuoteResponse& other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mErrorCode, other.mQuoteLength, other.mQuote, other.mQEReportLength, other.mQEReport);

    return *this;
}

bool AEGetQuoteResponse::check()
{
    if (mValidSizeCheck == false)
        return false;

    return true;
}

void AEGetQuoteResponse::visit(IAEResponseVisitor& visitor)
{
    visitor.visitGetQuoteResponse(*this);
}
