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

/*
 * Implementation of serializer based on google protobufs
 */

#include <ProtobufSerializer.h>
#include <google/protobuf/message.h>
#include <IAEMessage.h>

#include <IAERequest.h>
#include <AEInitQuoteRequest.h>

#include <AEGetQuoteRequest.h>

#include <AEGetLaunchTokenRequest.h>

#include <AECreateSessionRequest.h>

#include <AEInvokeServiceRequest.h>

#include <AEExchangeReportRequest.h>

#include <AECloseSessionRequest.h>

#include <AEGetPsCapRequest.h>

#include <AEReportAttestationRequest.h>

#include <AEGetWhiteListSizeRequest.h>

#include <AEGetWhiteListRequest.h>

#include <AESGXGetExtendedEpidGroupIdRequest.h>

#include <AESGXSwitchExtendedEpidGroupRequest.h>


IAERequest* ProtobufSerializer::inflateRequest(AEMessage* message) {
    if (message == NULL || message->data == NULL)
        return NULL;

    aesm::message::Request* reqMsg = new aesm::message::Request();

    reqMsg->ParseFromArray(message->data, message->size);
    IAERequest* request = NULL;
    if (reqMsg->has_getlictokenreq() == true)
        request = new AEGetLaunchTokenRequest(reqMsg->getlictokenreq());
    else if (reqMsg->has_initquotereq() == true)
        request = new AEInitQuoteRequest(reqMsg->initquotereq());
    else if (reqMsg->has_getquotereq() == true)
        request = new AEGetQuoteRequest(reqMsg->getquotereq());
    else if (reqMsg->has_closesessionreq() == true)
        request = new AECloseSessionRequest(reqMsg->closesessionreq());
    else if (reqMsg->has_createsessionreq() == true)
        request = new AECreateSessionRequest(reqMsg->createsessionreq());
    else if (reqMsg->has_exchangereportreq() == true)
        request = new AEExchangeReportRequest(reqMsg->exchangereportreq());
    else if (reqMsg->has_getlictokenreq() == true)
        request = new AEGetLaunchTokenRequest(reqMsg->getlictokenreq());
    else if (reqMsg->has_invokeservicereq() == true)
        request = new AEInvokeServiceRequest(reqMsg->invokeservicereq());
    else if (reqMsg->has_getpscapreq() == true)
        request = new AEGetPsCapRequest(reqMsg->getpscapreq());
    else if (reqMsg->has_reporterrreq() == true)
        request = new AEReportAttestationRequest(reqMsg->reporterrreq());
    else if(reqMsg->has_getwhitelistsizereq() == true)
        request = new AEGetWhiteListSizeRequest(reqMsg->getwhitelistsizereq());
    else if(reqMsg->has_getwhitelistreq() == true)
        request = new AEGetWhiteListRequest(reqMsg->getwhitelistreq());
    else if(reqMsg->has_sgxgetextendedepidgroupidreq() == true)
        request = new AESGXGetExtendedEpidGroupIdRequest(reqMsg->sgxgetextendedepidgroupidreq());
    else if(reqMsg->has_sgxswitchextendedepidgroupreq() == true)
        request = new AESGXSwitchExtendedEpidGroupRequest(reqMsg->sgxswitchextendedepidgroupreq());

    delete reqMsg;
    return request;
}
