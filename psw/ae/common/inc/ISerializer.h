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
#ifndef __AE_SERIALIZER_H_
#define __AE_SERIALIZER_H_

struct AEMessage;

class AEInitQuoteRequest;
class AEInitQuoteResponse;

class AEGetQuoteRequest;
class AEGetQuoteResponse;

class AEGetLaunchTokenRequest;
class AEGetLaunchTokenResponse;

class AECreateSessionRequest;
class AECreateSessionResponse;

class AEInvokeServiceRequest;
class AEInvokeServiceResponse;

class AEExchangeReportRequest;
class AEExchangeReportResponse;

class AECloseSessionRequest;
class AECloseSessionResponse;

class AEGetPsCapRequest;
class AEGetPsCapResponse;

class AEReportAttestationRequest;
class AEReportAttestationResponse;

class AEGetWhiteListSizeRequest;
class AEGetWhiteListSizeResponse;
 
class AEGetWhiteListRequest;
class AEGetWhiteListResponse;

class AESGXGetExtendedEpidGroupIdRequest;
class AESGXGetExtendedEpidGroupIdResponse;

class AESGXSwitchExtendedEpidGroupRequest;
class AESGXSwitchExtendedEpidGroupResponse;


class IAERequest;
class IAEResponse;

class ISerializer{
    public:
        //request serializers
        virtual AEMessage* serialize(AEInitQuoteRequest*       request) = 0;
        virtual AEMessage* serialize(AEGetQuoteRequest*        request) = 0;
        virtual AEMessage* serialize(AEGetLaunchTokenRequest* request) = 0;
        virtual AEMessage* serialize(AECreateSessionRequest*   request) = 0;
        virtual AEMessage* serialize(AEInvokeServiceRequest*   request) = 0;
        virtual AEMessage* serialize(AEExchangeReportRequest*  request) = 0;
        virtual AEMessage* serialize(AECloseSessionRequest*    request) = 0;
        virtual AEMessage* serialize(AEGetPsCapRequest*        request) = 0;
        virtual AEMessage* serialize(AEReportAttestationRequest* request) = 0;
        virtual AEMessage* serialize(AEGetWhiteListRequest* request) = 0;
        virtual AEMessage* serialize(AEGetWhiteListSizeRequest* request) = 0;
        virtual AEMessage* serialize(AESGXGetExtendedEpidGroupIdRequest* request) = 0;
        virtual AEMessage* serialize(AESGXSwitchExtendedEpidGroupRequest* request) = 0;

        //response serializers
        virtual AEMessage* serialize(AEInitQuoteResponse*       response)  = 0;
        virtual AEMessage* serialize(AEGetQuoteResponse*        response) = 0;
        virtual AEMessage* serialize(AEGetLaunchTokenResponse* response) = 0;
        virtual AEMessage* serialize(AECreateSessionResponse*   response) = 0;
        virtual AEMessage* serialize(AEInvokeServiceResponse*   response) = 0;
        virtual AEMessage* serialize(AEExchangeReportResponse*  response) = 0;
        virtual AEMessage* serialize(AECloseSessionResponse*    response) = 0;
        virtual AEMessage* serialize(AEGetPsCapResponse*        response) = 0;
        virtual AEMessage* serialize(AEReportAttestationResponse* response) = 0;
        virtual AEMessage* serialize(AEGetWhiteListSizeResponse* response) = 0;
        virtual AEMessage* serialize(AEGetWhiteListResponse*     response) = 0;
        virtual AEMessage* serialize(AESGXGetExtendedEpidGroupIdResponse* response) = 0;
        virtual AEMessage* serialize(AESGXSwitchExtendedEpidGroupResponse* response) = 0;

        //request inflater -> will inflate request objects by unmarshaling communication level data (this will be used by server)
        virtual IAERequest* inflateRequest(AEMessage* message) = 0;

        //response inflater -> will inflate response objects with data by unmarshaling communication level data
        virtual bool inflateResponse(AEMessage* message, AEInitQuoteResponse*       response) = 0;
        virtual bool inflateResponse(AEMessage* message, AEGetQuoteResponse*        response) = 0;
        virtual bool inflateResponse(AEMessage* message, AEGetLaunchTokenResponse* response) = 0;
        virtual bool inflateResponse(AEMessage* message, AECreateSessionResponse*   response) = 0;
        virtual bool inflateResponse(AEMessage* message, AEInvokeServiceResponse*   response) = 0;
        virtual bool inflateResponse(AEMessage* message, AEExchangeReportResponse*  response) = 0;
        virtual bool inflateResponse(AEMessage* message, AECloseSessionResponse*    response) = 0;
        virtual bool inflateResponse(AEMessage* message, AEGetPsCapResponse*        response) = 0;
        virtual bool inflateResponse(AEMessage* message, AEReportAttestationResponse* response) = 0;
        virtual bool inflateResponse(AEMessage* message, AEGetWhiteListSizeResponse* response) = 0;
        virtual bool inflateResponse(AEMessage* message, AEGetWhiteListResponse* response) = 0;
        virtual bool inflateResponse(AEMessage* message, AESGXGetExtendedEpidGroupIdResponse* response) = 0;
        virtual bool inflateResponse(AEMessage* message, AESGXSwitchExtendedEpidGroupResponse* response) = 0;

        virtual ~ISerializer() {}
};

#endif
