/*
 * Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
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
#ifndef _AE_EXCHANGE_REPORT_REQUEST_H
#define _AE_EXCHANGE_REPORT_REQUEST_H

#include <IAERequest.h>
#include <stdint.h>

namespace aesm
{
    namespace message
    {
        class Request_ExchangeReportRequest;
    };
};

class AEExchangeReportRequest : public IAERequest
{
    public:
        AEExchangeReportRequest(const aesm::message::Request_ExchangeReportRequest& request);
        AEExchangeReportRequest(uint32_t sessionId, uint32_t dhMsg2Length, const uint8_t* dhMsg2, uint32_t dhMsg3Length, uint32_t timeout=0);
        AEExchangeReportRequest(const AEExchangeReportRequest& other);
        ~AEExchangeReportRequest();

        AEMessage* serialize();

        //operators
        AEExchangeReportRequest& operator=(const AEExchangeReportRequest& other);

        //checkers
        bool check();
        IAEResponse* execute(IAESMLogic* aesmLogic);

        //used to determine in which queue to be placed
        virtual RequestClass getRequestClass();

    protected:
        aesm::message::Request_ExchangeReportRequest* m_request;
};

#endif
