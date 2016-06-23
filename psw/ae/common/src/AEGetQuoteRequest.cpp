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
#include <AEGetQuoteRequest.h>
#include <AEGetQuoteResponse.h>
#include "IAESMLogic.h"

#include <stdlib.h>

#include <sgx_report.h>

    AEGetQuoteRequest::AEGetQuoteRequest()
:mReportLength(0), mReport(NULL), mQuoteType(0), mSPIDLength(0), mSPID(NULL),
    mNonceLength(0), mNonce(NULL), mSigRLLength(0), mSigRL(NULL), mBufferSize(0), mQEReport(false)
{
    mValidSizeCheck = false;
}

AEGetQuoteRequest::AEGetQuoteRequest(uint32_t reportLength, const uint8_t* report,
        uint32_t quoteType,
        uint32_t spidLength, const uint8_t* spid,
        uint32_t nonceLength, const uint8_t* nonce,
        uint32_t sig_rlLength, const uint8_t* sig_rl,
        uint32_t bufferSize,
        bool qe_report,
        uint32_t timeout)
:mReportLength(0), mReport(NULL), mQuoteType(0), mSPIDLength(0), mSPID(NULL),
    mNonceLength(0), mNonce(NULL), mSigRLLength(0), mSigRL(NULL), mBufferSize(0), mQEReport(false)
{
    CopyFields(reportLength, report, quoteType, spidLength, spid,
            nonceLength, nonce, sig_rlLength, sig_rl, bufferSize, qe_report, timeout);
}

AEGetQuoteRequest::AEGetQuoteRequest(const AEGetQuoteRequest& other)
: IAERequest(other)
{
    CopyFields(other.mReportLength, other.mReport,
            other.mQuoteType,
            other.mSPIDLength, other.mSPID,
            other.mNonceLength, other.mNonce,
            other.mSigRLLength, other.mSigRL,
            other.mBufferSize,
            other.mQEReport,
            other.mTimeout);
}

AEGetQuoteRequest::~AEGetQuoteRequest()
{
    ReleaseMemory();
}

AEMessage* AEGetQuoteRequest::serialize(ISerializer* serializer)
{
    return serializer->serialize(this);
}

void AEGetQuoteRequest::CopyFields(uint32_t reportLength, const uint8_t* report,
        uint32_t quoteType,
        uint32_t spidLength, const uint8_t* spid,
        uint32_t nonceLength, const uint8_t* nonce,
        uint32_t sig_rlLength, const uint8_t* sig_rl,
        uint32_t bufferSize,
        bool qe_report,
        uint32_t timeout)
{
    uint32_t totalAllocation = reportLength + spidLength + nonceLength + sig_rlLength;
    if(reportLength <= MAX_MEMORY_ALLOCATION &&  spidLength <= MAX_MEMORY_ALLOCATION &&
            nonceLength <= MAX_MEMORY_ALLOCATION && sig_rlLength <= MAX_MEMORY_ALLOCATION &&
            totalAllocation <= MAX_MEMORY_ALLOCATION)
    {
        mValidSizeCheck = true;
    }
    else
    {
        mReport = NULL;
        mReportLength = 0;

        mSigRL = NULL;
        mSigRLLength = 0;

        mNonce = NULL;
        mNonceLength = 0;

        mSPID = NULL;
        mSPIDLength = 0;
        
        mValidSizeCheck = false;
        return;
    }

    mReportLength = reportLength;
    if (reportLength > 0 && report != NULL)
    {
        mReport = new uint8_t[reportLength];
        memcpy(mReport, report, reportLength);
    }
    else
        mReport = NULL;

    mSigRLLength = sig_rlLength;
    if (sig_rl != NULL && sig_rlLength > 0)
    {
        mSigRL = new uint8_t[sig_rlLength];
        memcpy(mSigRL, sig_rl, sig_rlLength);
    }
    else
        mSigRL = NULL;

    mNonceLength = nonceLength;
    if (nonce != NULL && nonceLength > 0)
    {
        mNonce = new uint8_t[nonceLength];
        memcpy(mNonce, nonce, nonceLength);
    }
    else
        mNonce = NULL;

    mSPIDLength = spidLength;
    if (spid != NULL && spidLength > 0)
    {
        mSPID = new uint8_t[spidLength];
        memcpy(mSPID, spid, spidLength);
    }
    else
        mSPID = NULL;

    mQuoteType = quoteType;
    mBufferSize = bufferSize;
    mQEReport = qe_report;
    mTimeout = timeout;
}

void AEGetQuoteRequest::inflateValues(uint32_t reportLength, const uint8_t* report,
        uint32_t quoteType,
        uint32_t spidLength, const uint8_t* spid,
        uint32_t nonceLength, const uint8_t* nonce,
        uint32_t sig_rlLength, const uint8_t* sig_rl,
        uint32_t bufferSize,
        bool qe_report,
        uint32_t timeout)
{
    ReleaseMemory();

    CopyFields(reportLength, report, quoteType, spidLength, spid,
            nonceLength, nonce, sig_rlLength, sig_rl, bufferSize, qe_report, timeout);
}


void AEGetQuoteRequest::ReleaseMemory()
{
    if (mReport != NULL)
    {
        if (mReportLength > 0)
            memset(mReport,0,mReportLength);
        delete [] mReport;
        mReport = NULL;
    }
    if (mSigRL != NULL)
    {
        if (mSigRLLength > 0)
            memset(mSigRL, 0, mSigRLLength);
        delete [] mSigRL;
        mSigRL = NULL;
    }
    if (mNonce != NULL)
    {
        if (mNonceLength > 0)
            memset(mNonce, 0, mNonceLength);
        delete [] mNonce;
        mNonce = NULL;
    }
    if (mSPID != NULL)
    {
        if (mSPIDLength > 0)
            memset(mSPID, 0, mSPIDLength);
        delete [] mSPID;
        mSPID = NULL;
    }

    mReportLength = 0;
    mSigRLLength  = 0;
    mNonceLength  = 0;
    mSPIDLength   = 0;
    mBufferSize   = 0;
    mQuoteType    = 0;
    mQEReport     = false;
    mTimeout      = 0;
}

bool AEGetQuoteRequest::operator==(const AEGetQuoteRequest& other) const
{
    if (&other == this)
        return true;

    if (mReportLength != other.mReportLength ||
            mSigRLLength  != other.mSigRLLength  ||
            mNonceLength  != other.mNonceLength  ||
            mSPIDLength   != other.mSPIDLength   ||
            mQuoteType    != other.mQuoteType    ||
            mBufferSize   != other.mBufferSize   ||
            mQEReport     != other.mQEReport     ||
            mTimeout      != other.mTimeout)
        return false;

    if ((mReport != other.mReport) &&
            (mReport == NULL || other.mReport == NULL))
        return false;

    if ((mSigRL != other.mSigRL) &&
            (mSigRL == NULL || other.mSigRL == NULL))
        return false;

    if ((mNonce != other.mNonce) &&
            (mNonce == NULL || other.mNonce == NULL))
        return false;

    if ((mSPID != other.mSPID) &&
            (mSPID == NULL || other.mSPID == NULL))
        return false;

    if ((mReport  != NULL && other.mReport != NULL  && memcmp(mReport, other.mReport, mReportLength) != 0) ||
            (mSigRL  != NULL && other.mSigRL  != NULL  && memcmp(mSigRL,  other.mSigRL,  mSigRLLength)  != 0) ||
            (mNonce  != NULL && other.mNonce  != NULL  && memcmp(mNonce, other.mNonce,   mNonceLength)  != 0) ||
            (mSPID   != NULL && other.mSPID   != NULL  && memcmp(mSPID,  other.mSPID,    mSPIDLength)   !=0 ))
        return false;
    return true;
}

AEGetQuoteRequest& AEGetQuoteRequest::operator=(const AEGetQuoteRequest& other)
{
    if (this == &other)
        return *this;

    inflateValues(other.mReportLength, other.mReport,
            other.mQuoteType,
            other.mSPIDLength, other.mSPID,
            other.mNonceLength, other.mNonce,
            other.mSigRLLength, other.mSigRL,
            other.mBufferSize,
            other.mQEReport,
            other.mTimeout);

    return *this;
}

bool AEGetQuoteRequest::check()
{
    //maybe TODO - add stronger checks

    if(mValidSizeCheck == false)
        return false;

    //allocations - only non optional fields
    if (mReport == NULL || mSPID == NULL)
        return false;

    return true;
}

IAERequest::RequestClass AEGetQuoteRequest::getRequestClass() {
    return QUOTING_CLASS;
}

void AEGetQuoteRequest::visit(IAERequestVisitor& visitor) 
{
    visitor.visitGetQuoteRequest(*this);
}

IAEResponse* AEGetQuoteRequest::execute(IAESMLogic* aesmLogic) {
    aesm_error_t result;

    int32_t qe_report_length = 0;
    uint8_t* qe_report = NULL;
    if (mQEReport == true)
    {
        qe_report_length = sizeof(sgx_report_t);
        qe_report = new uint8_t[sizeof(sgx_report_t)];
    }
    uint8_t* quote = new uint8_t[mBufferSize];

    result = aesmLogic->getQuote(mReportLength,mReport,
            mQuoteType,
            mSPIDLength, mSPID,
            mNonceLength, mNonce,
            mSigRLLength, mSigRL,
            mBufferSize, quote,
            qe_report_length, qe_report);

    AEGetQuoteResponse* response = new AEGetQuoteResponse(result, mBufferSize, quote, qe_report_length, qe_report);

    //free memory
    delete [] quote;
    delete [] qe_report;

    return response;
}
