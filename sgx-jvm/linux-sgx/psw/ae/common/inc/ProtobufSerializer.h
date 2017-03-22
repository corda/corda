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
#ifndef __AE_PROTOBUF_SERIALIZER
#define __AE_PROTOBUF_SERIALIZER

#include <ISerializer.h>

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wshadow"
#pragma GCC diagnostic ignored "-Wconversion"
#include <messages.pb.h>
#pragma GCC diagnostic pop

class IAERequest;
class IAEResponse;


class ProtobufSerializer : public ISerializer{
    public:
        ~ProtobufSerializer() {}

        //request serializers
        AEMessage* serialize(AEInitQuoteRequest* request);
        AEMessage* serialize(AEGetQuoteRequest* request);
        AEMessage* serialize(AEGetLaunchTokenRequest* request);
        AEMessage* serialize(AECreateSessionRequest* request);
        AEMessage* serialize(AEInvokeServiceRequest* request);
        AEMessage* serialize(AEExchangeReportRequest* request);
        AEMessage* serialize(AECloseSessionRequest* request);
        AEMessage* serialize(AEGetPsCapRequest* request);
        AEMessage* serialize(AEReportAttestationRequest* request);
        AEMessage* serialize(AEGetWhiteListSizeRequest* request);
        AEMessage* serialize(AEGetWhiteListRequest* request);
        AEMessage* serialize(AESGXGetExtendedEpidGroupIdRequest* request);
        AEMessage* serialize(AESGXSwitchExtendedEpidGroupRequest* request);

        //response serializers
        AEMessage* serialize(AEInitQuoteResponse* response);
        AEMessage* serialize(AEGetQuoteResponse* response);
        AEMessage* serialize(AEGetLaunchTokenResponse* response);
        AEMessage* serialize(AECreateSessionResponse* response);
        AEMessage* serialize(AEInvokeServiceResponse* response);
        AEMessage* serialize(AEExchangeReportResponse* response);
        AEMessage* serialize(AECloseSessionResponse* response);
        AEMessage* serialize(AEGetPsCapResponse* response);
        AEMessage* serialize(AEReportAttestationResponse* response);
        AEMessage* serialize(AEGetWhiteListSizeResponse* response);
        AEMessage* serialize(AEGetWhiteListResponse* response);
        AEMessage* serialize(AESGXGetExtendedEpidGroupIdResponse* response);
        AEMessage* serialize(AESGXSwitchExtendedEpidGroupResponse* response);

        //base inflate request
        IAERequest* inflateRequest(AEMessage* message);

        //response inflaters
        bool inflateResponse(AEMessage* message, AEInitQuoteResponse* response);
        bool inflateResponse(AEMessage* message, AEGetQuoteResponse* response);
        bool inflateResponse(AEMessage* message, AEGetLaunchTokenResponse* response);
        bool inflateResponse(AEMessage* message, AECreateSessionResponse* response);
        bool inflateResponse(AEMessage* message, AEInvokeServiceResponse* response);
        bool inflateResponse(AEMessage* message, AEExchangeReportResponse* response);
        bool inflateResponse(AEMessage* message, AECloseSessionResponse* response);
        bool inflateResponse(AEMessage* message, AEGetPsCapResponse* response);
        bool inflateResponse(AEMessage* message, AEReportAttestationResponse* response);
        bool inflateResponse(AEMessage* message, AEGetWhiteListSizeResponse* response);
        bool inflateResponse(AEMessage* message, AEGetWhiteListResponse *response);
        bool inflateResponse(AEMessage* message, AESGXGetExtendedEpidGroupIdResponse* response);
        bool inflateResponse(AEMessage* message, AESGXSwitchExtendedEpidGroupResponse* response);

    private:
        //request inflaters
        IAERequest* inflateInitQuoteRequest(aesm::message::Request* reqMsg);
        IAERequest* inflateGetQuoteRequest(aesm::message::Request* reqMsg);
        IAERequest* inflateCloseSessionRequest(aesm::message::Request* reqMsg);
        IAERequest* inflateCreateSessionRequest(aesm::message::Request* reqMsg);
        IAERequest* inflateExchangeReportRequest(aesm::message::Request* reqMsg);
        IAERequest* inflateGetLaunchTokenRequest(aesm::message::Request* reqMsg);
        IAERequest* inflateInvokeServiceRequest(aesm::message::Request* reqMsg);
        IAERequest* inflateGetPsCapRequest(aesm::message::Request* reqMsg);
        IAERequest* inflateReportAttestationErrorRequest(aesm::message::Request* reqMsg);
        IAERequest* inflateGetWhiteListSizeRequest(aesm::message::Request* reqMsg);
        IAERequest* inflateGetWhiteListRequest(aesm::message::Request* reqMsg);
        IAERequest* inflateSGXGetExtendedEpidGroupIdRequest(aesm::message::Request* reqMsg);
        IAERequest* inflateSGXSwitchExtendedEpidGroupRequest(aesm::message::Request* reqMsg);
};

#endif
