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
#include <AEServicesImpl.h>

#include <AEInitQuoteRequest.h>
#include <AEInitQuoteResponse.h>

#include <AEGetQuoteRequest.h>
#include <AEGetQuoteResponse.h>

#include <AEGetLaunchTokenRequest.h>
#include <AEGetLaunchTokenResponse.h>

#include <AECreateSessionRequest.h>
#include <AECreateSessionResponse.h>

#include <AEInvokeServiceRequest.h>
#include <AEInvokeServiceResponse.h>

#include <AEExchangeReportRequest.h>
#include <AEExchangeReportResponse.h>

#include <AECloseSessionRequest.h>
#include <AECloseSessionResponse.h>

#include <AEGetPsCapRequest.h>
#include <AEGetPsCapResponse.h>

#include <AEReportAttestationRequest.h>
#include <AEReportAttestationResponse.h>

#include <AEGetWhiteListSizeRequest.h>
#include <AEGetWhiteListSizeResponse.h>

#include <AEGetWhiteListRequest.h>
#include <AEGetWhiteListResponse.h>

#include <AESGXGetExtendedEpidGroupIdRequest.h>
#include <AESGXGetExtendedEpidGroupIdResponse.h>

#include <AESGXSwitchExtendedEpidGroupRequest.h>
#include <AESGXSwitchExtendedEpidGroupResponse.h>

#include <SocketTransporter.h>
#include <ProtobufSerializer.h>
#include <UnixSocketFactory.h>
#include <NonBlockingUnixSocketFactory.h>

#include <stdlib.h>

AEServicesImpl::AEServicesImpl(const char* socketbase) :
    mTransporter(NULL)
{
    ProtobufSerializer * serializer             = new ProtobufSerializer();
    NonBlockingUnixSocketFactory *socketFactory = new NonBlockingUnixSocketFactory(socketbase);
    mTransporter                                = new SocketTransporter(socketFactory, serializer);
}

AEServicesImpl::~AEServicesImpl()
{
    delete mTransporter;
}

QuoteInfo* AEServicesImpl::GetQuote(const Report* report, const uint32_t quoteType, const SPID* spid, const Nonce* nonce,
                                const SignatureRevocationList* sig_rl, const uint32_t bufSize, const bool qe_report, 
                                uint32_t timeout_msec)
{
    AEGetQuoteRequest* getQuoteRequest = new AEGetQuoteRequest(report->length, report->data,
                                                               quoteType,
                                                               spid->length, spid->data,
                                                               nonce->length, nonce->data,
                                                               sig_rl->length, sig_rl->data,
                                                               bufSize,
                                                               qe_report,
                                                               timeout_msec);

    if(getQuoteRequest->check() == false)
    {
        delete getQuoteRequest;
        QuoteInfo* quoteInfo = new QuoteInfo;
        quoteInfo->uaeStatus = UAE_OAL_ERROR_UNEXPECTED;
        return quoteInfo;
    }

    AEGetQuoteResponse* getQuoteResponse = new AEGetQuoteResponse();

    uae_oal_status_t ipc_status = mTransporter->transact(getQuoteRequest, getQuoteResponse);

    //public exposed quote container
    QuoteInfo* quoteInfo = new QuoteInfo;
    quoteInfo->uaeStatus = ipc_status;

    if (ipc_status == UAE_OAL_SUCCESS && getQuoteResponse->check() == true)
    {
        Quote* quote = new Quote;
        Report* qeReport = NULL;

        quote->length = getQuoteResponse->GetQuoteLength();
        quote->data   = new uint8_t[quote->length];
        memcpy(quote->data, getQuoteResponse->GetQuote(), getQuoteResponse->GetQuoteLength());
        if (getQuoteResponse->GetQEReport() != NULL)    //If we have also a report
        {
            qeReport = new Report;
            qeReport->data = new uint8_t[getQuoteResponse->GetQEReportLength()];
            qeReport->length = getQuoteResponse->GetQEReportLength();
            memcpy(qeReport->data, getQuoteResponse->GetQEReport(), qeReport->length);
        }

        quoteInfo->quote = quote;
        quoteInfo->qeReport = qeReport;
    }

    quoteInfo->errorCode = getQuoteResponse->GetErrorCode();

    delete getQuoteRequest;
    delete getQuoteResponse;

    return quoteInfo;
}

AttestationInformation* AEServicesImpl::InitQuote(uint32_t timeout_msec)
{
    AEInitQuoteResponse* initQuoteResponse = new AEInitQuoteResponse(); /* empty response */
    AEInitQuoteRequest*  initQuoteRequest  = new AEInitQuoteRequest(timeout_msec);

    uae_oal_status_t ipc_status = mTransporter->transact(initQuoteRequest, initQuoteResponse);

    AttestationInformation* info = new AttestationInformation;
    info->uaeStatus = ipc_status;

    if (ipc_status == UAE_OAL_SUCCESS && initQuoteResponse->check() == true) {
        /* populate the public exposed data structure */

        TargetInfo* tInfo = new TargetInfo;
        PlatformGID* pGID = new PlatformGID;

        tInfo->length = initQuoteResponse->GetTargetInfoLength();
        pGID->length  = initQuoteResponse->GetGIDLength();

        tInfo->data = new uint8_t[tInfo->length];
        pGID->data  = new uint8_t[pGID->length];

        memcpy(tInfo->data, initQuoteResponse->GetTargetInfo(), tInfo->length);
        memcpy(pGID->data,  initQuoteResponse->GetGID(), pGID->length);

        info->quotingTarget = tInfo;
        info->platformGID = pGID;
    }
    info->errorCode = initQuoteResponse->GetErrorCode();

    delete initQuoteRequest;
    delete initQuoteResponse;

    return info;
}

LaunchToken* AEServicesImpl::GetLaunchToken(EnclaveMeasurement* mr_enclave, Signature* mr_signer, SEAttributes* se_attributes, uint32_t timeout_msec)
{
    AEGetLaunchTokenRequest* getLaunchTokenRequest = new AEGetLaunchTokenRequest(mr_enclave->length,
                                                                                    mr_enclave->data,
                                                                                    mr_signer->length,
                                                                                    mr_signer->data,
                                                                                    se_attributes->length,
                                                                                    se_attributes->data,
                                                                                    timeout_msec);
    if(getLaunchTokenRequest->check() == false)
    {
        delete getLaunchTokenRequest;
        LaunchToken* token = new LaunchToken;
        token->uaeStatus = UAE_OAL_ERROR_UNEXPECTED;
        return token;
    }

    AEGetLaunchTokenResponse* getLaunchTokenResponse = new AEGetLaunchTokenResponse();

    uae_oal_status_t ipc_status = mTransporter->transact(getLaunchTokenRequest, getLaunchTokenResponse);

    LaunchToken* token = new LaunchToken;
    token->uaeStatus = ipc_status;

    if (ipc_status == UAE_OAL_SUCCESS && getLaunchTokenResponse->check() == true )
    {
        token->length = getLaunchTokenResponse->GetTokenLength();
        token->data = new uint8_t[token->length];

        memcpy(token->data, getLaunchTokenResponse->GetToken(), token->length);
    }

    token->errorCode = getLaunchTokenResponse->GetErrorCode();

    delete getLaunchTokenRequest;
    delete getLaunchTokenResponse;

    return token;
}

CreateSessionInformation* AEServicesImpl::CreateSession(uint32_t dhMsg1Size, uint32_t timeout)
{
    AECreateSessionRequest*  createSessionRequest  = new AECreateSessionRequest(dhMsg1Size, timeout);
    AECreateSessionResponse* createSessionResponse = new AECreateSessionResponse();

    uae_oal_status_t ipc_status = mTransporter->transact(createSessionRequest, createSessionResponse);

    CreateSessionInformation* info = new CreateSessionInformation();
    info->uaeStatus = ipc_status;

    if (ipc_status == UAE_OAL_SUCCESS && createSessionResponse->check() == true)
    {
        info->sessionId = createSessionResponse->GetSessionId();
        info->dh_msg1 = new PlainData;

        info->dh_msg1->length = createSessionResponse->GetDHMsg1Length();
        info->dh_msg1->data = new uint8_t[createSessionResponse->GetDHMsg1Length()];
        
        memcpy(info->dh_msg1->data, createSessionResponse->GetDHMsg1(), info->dh_msg1->length);
    }

    info->errorCode = createSessionResponse->GetErrorCode();

    delete createSessionRequest;
    delete createSessionResponse;

    return info;
}

PSEMessage* AEServicesImpl::InvokeService(PSEMessage* targetServiceMessage, uint32_t pseResponseSize,uint32_t timeout)
{
    AEInvokeServiceRequest* invokeServiceRequest   = new AEInvokeServiceRequest(targetServiceMessage->length,
                                                                                targetServiceMessage->data,
                                                                                pseResponseSize,
                                                                                timeout);
    if(invokeServiceRequest->check() == false)
    {
        delete invokeServiceRequest;
        PSEMessage* msg = new PSEMessage;
        msg->uaeStatus = UAE_OAL_ERROR_UNEXPECTED;
        return msg;
    }


    AEInvokeServiceResponse* invokeServiceResponse = new AEInvokeServiceResponse();

    uae_oal_status_t ipc_status = mTransporter->transact(invokeServiceRequest, invokeServiceResponse);

    PSEMessage* msg = new PSEMessage;
    msg->uaeStatus = ipc_status;

    if (ipc_status == UAE_OAL_SUCCESS && invokeServiceResponse->check())
    {
        msg->length = invokeServiceResponse->GetPSEMessageLength();
        if (msg->length > 0)
        {
            msg->data = new uint8_t[msg->length];
            memcpy(msg->data, invokeServiceResponse->GetPSEMessage(), msg->length);
        }
    }

    msg->errorCode = invokeServiceResponse->GetErrorCode();

    delete invokeServiceRequest;
    delete invokeServiceResponse;

    return msg;
}

PlainData* AEServicesImpl::ExchangeReport(uint32_t sessionId, PlainData* dhMsg, uint32_t pseResponseSize, uint32_t timeout)
{
    AEExchangeReportRequest* exchangeReportRequest = new AEExchangeReportRequest(sessionId, dhMsg->length, dhMsg->data,
                                                                                 pseResponseSize, timeout);

    if(exchangeReportRequest->check() == false)
    {
        delete exchangeReportRequest;
        PlainData* dhMsg3 = new PlainData;
        dhMsg3->uaeStatus = UAE_OAL_ERROR_UNEXPECTED;
        return dhMsg3;
    }

    AEExchangeReportResponse* exchangeReportResponse = new AEExchangeReportResponse();

    uae_oal_status_t ipc_status = mTransporter->transact(exchangeReportRequest, exchangeReportResponse);

    PlainData* dhMsg3 = new PlainData;
    dhMsg3->uaeStatus = ipc_status;

    if (ipc_status == UAE_OAL_SUCCESS && exchangeReportResponse->check())
    {
        dhMsg3->length = exchangeReportResponse->GetDHMsg3Length();

        if (dhMsg3->length > 0)
        {
            dhMsg3->data = new uint8_t[dhMsg3->length];
            memcpy(dhMsg3->data, exchangeReportResponse->GetDHMsg3(), dhMsg3->length);
        }
    }

    dhMsg3->errorCode = exchangeReportResponse->GetErrorCode();

    delete exchangeReportRequest;
    delete exchangeReportResponse;

    return dhMsg3;
}

PlainData* AEServicesImpl::CloseSession(uint32_t sessionId, uint32_t timeout)
{
    AECloseSessionRequest* closeSessionRequest   = new AECloseSessionRequest(sessionId, timeout);
    AECloseSessionResponse* closeSessionResponse = new AECloseSessionResponse();

    uae_oal_status_t ipc_status = mTransporter->transact(closeSessionRequest, closeSessionResponse);

    PlainData* res = new PlainData;
    
    res->errorCode = closeSessionResponse->GetErrorCode();
    res->uaeStatus = ipc_status;

    delete closeSessionRequest;
    delete closeSessionResponse;

    return res;
}

PsCap* AEServicesImpl::GetPsCap(uint32_t timeout_msec)
{
    AEGetPsCapRequest*  getPsCapRequest  = new AEGetPsCapRequest(timeout_msec);
    AEGetPsCapResponse* getPsCapResponse = new AEGetPsCapResponse();

    uae_oal_status_t ipc_status = mTransporter->transact(getPsCapRequest, getPsCapResponse);

    PsCap* ps_cap = new PsCap();
    ps_cap->uaeStatus = ipc_status;

    if (ipc_status == UAE_OAL_SUCCESS && getPsCapResponse->check() == true)
        ps_cap->ps_cap= getPsCapResponse->GetPsCap();

    ps_cap->errorCode = getPsCapResponse->GetErrorCode();

    delete getPsCapRequest;
    delete getPsCapResponse;

    return ps_cap;
}


AttestationStatus* AEServicesImpl::ReportAttestationError(const PlatformInfo* platformInfo, uint32_t attestation_error_code, uint32_t updateInfoLength, uint32_t timeout_msec)
{
    AEReportAttestationRequest* reportAttestationErrorRequest   = new AEReportAttestationRequest(platformInfo->length,
                                                                                                 platformInfo->data,
                                                                                                 attestation_error_code, updateInfoLength, timeout_msec);
    if(reportAttestationErrorRequest->check() == false)
    {
        delete reportAttestationErrorRequest;
        AttestationStatus* attestationStatus = new AttestationStatus;
        attestationStatus->uaeStatus = UAE_OAL_ERROR_UNEXPECTED; 
        return attestationStatus;
    }
    AEReportAttestationResponse* reportAttestationErrorResponse = new AEReportAttestationResponse();

    uae_oal_status_t ipc_status = mTransporter->transact(reportAttestationErrorRequest, reportAttestationErrorResponse);

    AttestationStatus* attestationStatus = new AttestationStatus;
    attestationStatus->uaeStatus = ipc_status;

    if (ipc_status == UAE_OAL_SUCCESS && reportAttestationErrorResponse->check())
    {
        UpdateInfo* tInfo = new UpdateInfo;
        tInfo->length = reportAttestationErrorResponse->GetUpdateInfoLength();

        tInfo->data = new uint8_t[tInfo->length];
        memcpy(tInfo->data, reportAttestationErrorResponse->GetUpdateInfo(), tInfo->length);
        attestationStatus->updateInfo = tInfo;
    }

    attestationStatus->errorCode = reportAttestationErrorResponse->GetErrorCode();

    delete reportAttestationErrorRequest;
    delete reportAttestationErrorResponse;

    return attestationStatus;

}

WhiteListSize* AEServicesImpl::GetWhiteListSize(uint32_t timeout_msec)
{
    AEGetWhiteListSizeRequest*  getWhiteListSizeRequest  = new AEGetWhiteListSizeRequest(timeout_msec);
    AEGetWhiteListSizeResponse* getWhiteListSizeResponse = new AEGetWhiteListSizeResponse();

    uae_oal_status_t ipc_status = mTransporter->transact(getWhiteListSizeRequest, getWhiteListSizeResponse);

    WhiteListSize* white_list_size = new WhiteListSize();
    white_list_size->uaeStatus = ipc_status;

    if (ipc_status == UAE_OAL_SUCCESS && getWhiteListSizeResponse->check() == true)
        white_list_size->white_list_size= getWhiteListSizeResponse->GetWhiteListSize();

    white_list_size->errorCode = getWhiteListSizeResponse->GetErrorCode();

    delete getWhiteListSizeRequest;
    delete getWhiteListSizeResponse;

    return white_list_size;
}

PlainData* AEServicesImpl::GetWhiteList(uint32_t white_list_size, uint32_t timeout)
{
    AEGetWhiteListRequest* getWhiteListRequest = new AEGetWhiteListRequest(white_list_size, timeout);

    if(getWhiteListRequest->check() == false)
    {
        delete getWhiteListRequest;
        PlainData* whiteList = new PlainData;
        whiteList->uaeStatus = UAE_OAL_ERROR_UNEXPECTED;
        return whiteList;
    }

    AEGetWhiteListResponse* getWhiteListResponse = new AEGetWhiteListResponse();

    uae_oal_status_t ipc_status = mTransporter->transact(getWhiteListRequest, getWhiteListResponse);

    PlainData* whiteList = new PlainData;
    whiteList->uaeStatus = ipc_status;

    if (ipc_status == UAE_OAL_SUCCESS && getWhiteListResponse->check())
    {
        whiteList->length = getWhiteListResponse->GetWhiteListLength();

        if (whiteList->length > 0)
        {
            whiteList->data = new uint8_t[whiteList->length];
            memcpy(whiteList->data, getWhiteListResponse->GetWhiteList(), whiteList->length);
        }
    }

    whiteList->errorCode = getWhiteListResponse->GetErrorCode();

    delete getWhiteListRequest;
    delete getWhiteListResponse;

    return whiteList;
}

PlainData* AEServicesImpl::SGXSwitchExtendedEpidGroup(uint32_t x_group_id, uint32_t timeout)
{
    AESGXSwitchExtendedEpidGroupRequest* switchExtendedEpidGroupRequest   = new AESGXSwitchExtendedEpidGroupRequest(x_group_id, timeout);
    AESGXSwitchExtendedEpidGroupResponse* switchExtendedEpidGroupResponse = new AESGXSwitchExtendedEpidGroupResponse();

    uae_oal_status_t ipc_status = mTransporter->transact(switchExtendedEpidGroupRequest, switchExtendedEpidGroupResponse);

    PlainData* res = new PlainData;
    
    res->errorCode = switchExtendedEpidGroupResponse->GetErrorCode();
    res->uaeStatus = ipc_status;

    delete switchExtendedEpidGroupRequest;
    delete switchExtendedEpidGroupResponse;

    return res;
}

ExtendedEpidGroupId* AEServicesImpl::SGXGetExtendedEpidGroupId(uint32_t timeout_msec)
{
    AESGXGetExtendedEpidGroupIdRequest*  getExtendedEpidGroupIdRequest  = new AESGXGetExtendedEpidGroupIdRequest(timeout_msec);
    AESGXGetExtendedEpidGroupIdResponse* getExtendedEpidGroupIdResponse = new AESGXGetExtendedEpidGroupIdResponse();

    uae_oal_status_t ipc_status = mTransporter->transact(getExtendedEpidGroupIdRequest, getExtendedEpidGroupIdResponse);

    ExtendedEpidGroupId* extended_group_id = new ExtendedEpidGroupId();
    extended_group_id->uaeStatus = ipc_status;

    if (ipc_status == UAE_OAL_SUCCESS && getExtendedEpidGroupIdResponse->check() == true)
        extended_group_id->x_group_id= getExtendedEpidGroupIdResponse->GetExtendedEpidGroupId();

    extended_group_id->errorCode = getExtendedEpidGroupIdResponse->GetErrorCode();

    delete getExtendedEpidGroupIdRequest;
    delete getExtendedEpidGroupIdResponse;

    return extended_group_id;
}


