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
#ifndef _AE_EXCHANGE_REPORT_RESPONSE_H
#define _AE_EXCHANGE_REPORT_RESPONSE_H

#include <IAEResponse.h>
#include <stdint.h>

class AEExchangeReportResponse : public IAEResponse
{
    public:
        AEExchangeReportResponse();
        AEExchangeReportResponse(int errorCode, uint32_t dhMsg3Length, const uint8_t* dhMsg3);
        AEExchangeReportResponse(const AEExchangeReportResponse& other);
        ~AEExchangeReportResponse();

        AEMessage* serialize(ISerializer* serializer);
        bool inflateWithMessage(AEMessage* message, ISerializer* serializer);
        void inflateValues(int errorCode, uint32_t dhMsg3Length, const uint8_t* dhMsg3);

        //getters
        uint32_t       GetDHMsg3Length() const { return mDHMsg3Length; }
        const uint8_t* GetDHMsg3()       const { return mDHMsg3; }

        //operators
        bool operator==(const AEExchangeReportResponse& other) const;
        AEExchangeReportResponse& operator=(const AEExchangeReportResponse& other);

        //checkers
        bool check();
        virtual void visit(IAEResponseVisitor& visitor);

    protected:
        void ReleaseMemory();
        void CopyFields(int errorCode, uint32_t dhMsg3Length, const uint8_t* dhMsg3);

        uint32_t    mDHMsg3Length;
        uint8_t*    mDHMsg3;
};

#endif
