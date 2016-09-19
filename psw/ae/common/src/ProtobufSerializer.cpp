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

/*
 * Implementation of serializer based on google protobufs
 */

#include <ProtobufSerializer.h>
#include <google/protobuf/message.h>
#include <messages.pb.h>

/* must include this AFTER ProtobufSerializer.h */
#include <IAERequest.h>
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

#include <stdlib.h>
#include <string.h>
#include <string>
#include <limits.h>

using namespace google::protobuf;

#define UNUSED(val) (void)(val)

/*
 *       InitQuote
 */


AEMessage* ProtobufSerializer::serialize(AEInitQuoteRequest* request)
{
    //kill the warning
    UNUSED(request);

    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Request msg;
    aesm::message::Request::InitQuoteRequest proto_req;
    proto_req.set_timeout(request->GetTimeout());

    if (proto_req.IsInitialized())
    {
        aesm::message::Request::InitQuoteRequest* mutableReq = msg.mutable_initquotereq();
        mutableReq->CopyFrom(proto_req);
        msg.SerializeToString(&data);
        msg.release_initquotereq(); //free the internal object

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.c_str(), ae_msg->size);
        }
        delete mutableReq;
    }
    return ae_msg;
}

AEMessage* ProtobufSerializer::serialize(AEInitQuoteResponse* response)
{
    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    aesm::message::Response::InitQuoteResponse  proto_res;

    if (response->GetGID() != NULL)
    {
        std::string gid((const char*)response->GetGID(), response->GetGIDLength());
        proto_res.set_gid(gid);
    }

    if (response->GetTargetInfo() != NULL)
    {
        std::string target_info((const char*)response->GetTargetInfo(), response->GetTargetInfoLength());
        proto_res.set_targetinfo(target_info);
    }

    proto_res.set_errorcode(response->GetErrorCode());

    if (proto_res.IsInitialized())
    {
        aesm::message::Response::InitQuoteResponse* mutableRes = msg.mutable_initquoteres();
        mutableRes->CopyFrom(proto_res);
        msg.SerializeToString(&data);
        msg.release_initquoteres();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
	    ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), data.size());
        }

        delete mutableRes;
    }
    return ae_msg;
}

IAERequest* ProtobufSerializer::inflateGetQuoteRequest(aesm::message::Request* reqMsg)
{
    aesm::message::Request::GetQuoteRequest proto_req = reqMsg->getquotereq();

    uint32_t report_length = 0;
    uint8_t* report = NULL;
    uint32_t spid_length = 0;
    uint8_t* spid = NULL;
    uint32_t nonce_length = 0;
    uint8_t* nonce = NULL;
    uint32_t sig_rl_length = 0;
    uint8_t* sig_rl = NULL;

    if (proto_req.has_report())
    {
        if(proto_req.report().size() > UINT_MAX) {
            return NULL;
        }
        report_length = (unsigned int) proto_req.report().size();
        report = (uint8_t*)const_cast<char *>(proto_req.report().data());
    }
    if (proto_req.has_spid())
    {
        if(proto_req.spid().size() > UINT_MAX) {
            return NULL;
        }
        spid_length = (unsigned int) proto_req.spid().size();
        spid = (uint8_t*)const_cast<char *>(proto_req.spid().data());
    }
    if (proto_req.has_nonce())
    {
        if(proto_req.nonce().size() > UINT_MAX) {
            return NULL;
        }
        nonce_length = (unsigned int) proto_req.nonce().size();
        nonce = (uint8_t*)const_cast<char *>(proto_req.nonce().data());
    }
    if (proto_req.has_sig_rl())
    {
        if(proto_req.sig_rl().size() > UINT_MAX) {
            return NULL;
        }
        sig_rl_length = (unsigned int) proto_req.sig_rl().size();
        sig_rl = (uint8_t*)const_cast<char *>(proto_req.sig_rl().data());
    }   
   
    AEGetQuoteRequest* request = new AEGetQuoteRequest();
    request->inflateValues(report_length, report,
            proto_req.quote_type(),
            spid_length, spid,
            nonce_length, nonce,
            sig_rl_length, sig_rl,
            proto_req.buf_size(),
            proto_req.qe_report(),
            proto_req.timeout());

    return request;
}

IAERequest* ProtobufSerializer::inflateInitQuoteRequest(aesm::message::Request* reqMsg)
{
    AEInitQuoteRequest* request = new AEInitQuoteRequest();
    aesm::message::Request::InitQuoteRequest proto_req = reqMsg->initquotereq();

    request->inflateValues(proto_req.timeout());
    return request;
}

IAERequest* ProtobufSerializer::inflateCloseSessionRequest(aesm::message::Request* reqMsg)
{
    AECloseSessionRequest* request = new AECloseSessionRequest();
    aesm::message::Request::CloseSessionRequest proto_req = reqMsg->closesessionreq();

    request->inflateValues(proto_req.session_id(), proto_req.timeout());
    return request;
}

IAERequest* ProtobufSerializer::inflateCreateSessionRequest(aesm::message::Request* reqMsg)
{
    AECreateSessionRequest* request = new AECreateSessionRequest();
    aesm::message::Request::CreateSessionRequest proto_req = reqMsg->createsessionreq();

    request->inflateValues(proto_req.dh_msg1_size(), proto_req.timeout());

    return request;
}

IAERequest* ProtobufSerializer::inflateExchangeReportRequest(aesm::message::Request* reqMsg)
{
    aesm::message::Request::ExchangeReportRequest proto_req = reqMsg->exchangereportreq();

    uint32_t dh_msg2_length = 0;
    uint8_t* dh_msg2 = NULL;

    if (proto_req.has_se_dh_msg2())
    {
        if(proto_req.se_dh_msg2().size() > UINT_MAX) {
            return NULL;
        }
        dh_msg2_length = (unsigned int)proto_req.se_dh_msg2().size();
        dh_msg2 = (uint8_t*)const_cast<char *>(proto_req.se_dh_msg2().data());
    }

    AEExchangeReportRequest* request = new AEExchangeReportRequest();
    request->inflateValues(proto_req.session_id(),
            dh_msg2_length, 
            dh_msg2,
            proto_req.se_dh_msg3_size(),
            proto_req.timeout());
    
    return request;
}

IAERequest* ProtobufSerializer::inflateGetLaunchTokenRequest(aesm::message::Request* reqMsg)
{
    aesm::message::Request::GetLaunchTokenRequest proto_req = reqMsg->getlictokenreq();

    uint32_t mr_enclave_length = 0;
    uint8_t* mr_enclave = NULL;
    uint32_t mr_signer_length = 0;
    uint8_t* mr_signer = NULL;
    uint32_t se_attributes_length = 0;
    uint8_t* se_attributes = NULL;
    
    if (proto_req.has_mr_enclave())
    {
        if(proto_req.mr_enclave().size() > UINT_MAX) {
            return NULL;
        }
        mr_enclave_length = (unsigned int)proto_req.mr_enclave().size();
        mr_enclave = (uint8_t*)const_cast<char *>(proto_req.mr_enclave().data());
    }
    if (proto_req.has_mr_signer())
    {
        if(proto_req.mr_signer().size() > UINT_MAX) {
            return NULL;
        }
        mr_signer_length = (unsigned int)proto_req.mr_signer().size();
        mr_signer = (uint8_t*)const_cast<char *>(proto_req.mr_signer().data());
    }
    if (proto_req.has_se_attributes())
    {
        if(proto_req.se_attributes().size() > UINT_MAX) {
            return NULL;
        }
        se_attributes_length = (unsigned int)proto_req.se_attributes().size();
        se_attributes = (uint8_t*)const_cast<char *>(proto_req.se_attributes().data());
    }

    AEGetLaunchTokenRequest* request = new AEGetLaunchTokenRequest();
    request->inflateValues(mr_enclave_length, mr_enclave,
        mr_signer_length, mr_signer,
        se_attributes_length, se_attributes,
        proto_req.timeout());

    return request;
}

IAERequest* ProtobufSerializer::inflateInvokeServiceRequest(aesm::message::Request* reqMsg)
{
    aesm::message::Request::InvokeServiceRequest proto_req = reqMsg->invokeservicereq();

    uint32_t pse_message_length = 0;
    uint8_t* pse_message = NULL;

    if (proto_req.has_pse_message())
    {
        if(proto_req.pse_message().size() > UINT_MAX) {
            return NULL;
        }
        pse_message_length = (unsigned int)proto_req.pse_message().size();
        pse_message = (uint8_t*)const_cast<char *>(proto_req.pse_message().data());
    }

    AEInvokeServiceRequest* request = new AEInvokeServiceRequest();
    request->inflateValues(pse_message_length, pse_message, proto_req.pse_resp_size(), proto_req.timeout());

    return request;
}

IAERequest* ProtobufSerializer::inflateGetPsCapRequest(aesm::message::Request* reqMsg)
{
    AEGetPsCapRequest* request = new AEGetPsCapRequest();
    aesm::message::Request::GetPsCapRequest proto_req = reqMsg->getpscapreq();

    request->inflateValues(proto_req.timeout());
    return request;
}

IAERequest* ProtobufSerializer::inflateReportAttestationErrorRequest(aesm::message::Request* reqMsg)
{
    aesm::message::Request::ReportAttestationErrorRequest proto_req = reqMsg->reporterrreq();

    uint32_t attestation_error_code = -1;
    if (proto_req.has_attestation_error_code())
    {
        attestation_error_code = proto_req.attestation_error_code();
    }
    uint32_t update_info_size = 0;
    if (proto_req.has_update_info_size())
    {
        update_info_size = proto_req.update_info_size();
    }
    uint32_t platform_info_length = 0;
    uint8_t* platform_info = NULL;
    if (proto_req.has_platform_info())
    {
        if(proto_req.platform_info().size() > UINT_MAX) {
            return NULL;
        }
        platform_info_length = (unsigned int)proto_req.platform_info().size();
        platform_info = (uint8_t*)const_cast<char *>(proto_req.platform_info().data());
    }

    AEReportAttestationRequest* request = new AEReportAttestationRequest();
    request->inflateValues(platform_info_length, platform_info, attestation_error_code, update_info_size, proto_req.timeout());
    return request;
}

IAERequest* ProtobufSerializer::inflateRequest(AEMessage* message) {
    if (message == NULL || message->data == NULL)
        return NULL;

    std::string data((char*)message->data, message->size);
    aesm::message::Request* reqMsg = new aesm::message::Request();

    reqMsg->ParseFromString(data);
    IAERequest* request = NULL;
    if (reqMsg->has_initquotereq() == true)
        request = inflateInitQuoteRequest(reqMsg);
    if (reqMsg->has_getquotereq() == true)
        request = inflateGetQuoteRequest(reqMsg);
    if (reqMsg->has_closesessionreq() == true)
        request = inflateCloseSessionRequest(reqMsg);
    if (reqMsg->has_createsessionreq() == true)
        request = inflateCreateSessionRequest(reqMsg);
    if (reqMsg->has_exchangereportreq() == true)
        request = inflateExchangeReportRequest(reqMsg);
    if (reqMsg->has_getlictokenreq() == true)
        request = inflateGetLaunchTokenRequest(reqMsg);
    if (reqMsg->has_invokeservicereq() == true)
        request = inflateInvokeServiceRequest(reqMsg);
    if (reqMsg->has_getpscapreq() == true)
        request = inflateGetPsCapRequest(reqMsg);
    if (reqMsg->has_reporterrreq() == true)
        request = inflateReportAttestationErrorRequest(reqMsg);
    if(reqMsg->has_getwhitelistsizereq() == true)
        request = inflateGetWhiteListSizeRequest(reqMsg);
    if(reqMsg->has_getwhitelistreq() == true)
        request = inflateGetWhiteListRequest(reqMsg);
    if(reqMsg->has_sgxgetextendedepidgroupidreq() == true)
        request = inflateSGXGetExtendedEpidGroupIdRequest(reqMsg);
    if(reqMsg->has_sgxswitchextendedepidgroupreq() == true)
        request = inflateSGXSwitchExtendedEpidGroupRequest(reqMsg);
    delete reqMsg;
    return request;
}

//return false if inflate failed
bool ProtobufSerializer::inflateResponse(AEMessage* message, AEInitQuoteResponse* response)
{
    std::string data((char*)message->data, message->size);
    aesm::message::Response msg;

    msg.ParseFromString(data);
    if (msg.has_initquoteres() == false)
        return false;

    //this is an AEInitQuoteResponse
    aesm::message::Response::InitQuoteResponse proto_res = msg.initquoteres();

    uint32_t gid_length = 0;
    uint8_t* gid = NULL;
    uint32_t target_info_length = 0;
    uint8_t* target_info = NULL;

    if (proto_res.has_gid())
    {
        if(proto_res.gid().size() > UINT_MAX) {
            return NULL;
        }
        gid_length = (unsigned int)proto_res.gid().size();
        gid = (uint8_t*)const_cast<char *>(proto_res.gid().data());
    }
    if (proto_res.has_targetinfo())
    {
        if(proto_res.targetinfo().size() > UINT_MAX) {
            return NULL;
        }

        target_info_length = (unsigned int) proto_res.targetinfo().size();
        target_info = (uint8_t*)const_cast<char *>(proto_res.targetinfo().data());
    }

    response->inflateValues(proto_res.errorcode(),
        gid_length, gid,
        target_info_length, target_info);

    return true;
}


/*
 *       GetQuote
 */


AEMessage* ProtobufSerializer::serialize(AEGetQuoteRequest* request)
{
    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Request msg;
    aesm::message::Request::GetQuoteRequest  proto_req;//= new aesm::message::Response::InitQuoteResponse();
    
    if (request->GetReport() != NULL)
    {
        std::string report((const char*)request->GetReport(), request->GetReportLength());
        proto_req.set_report(report);
    }

    if (request->GetSigRL() != NULL)
    {
        std::string sig_rl((const char*)request->GetSigRL(), request->GetSigRLLength());
        proto_req.set_sig_rl(sig_rl);
    }

    if (request->GetNonce() != NULL)
    {
        std::string nonce((const char*)request->GetNonce(), request->GetNonceLength());
        proto_req.set_nonce(nonce);
    }

    if (request->GetSPID() != NULL)
    {
        std::string spid((const char*)request->GetSPID(), request->GetSPIDLength());
        proto_req.set_spid(spid);
    }

    proto_req.set_quote_type(request->GetQuoteType());
    proto_req.set_buf_size(request->GetBufferSize());
    proto_req.set_qe_report(request->GetQEReport());
    proto_req.set_timeout(request->GetTimeout());

    if (proto_req.IsInitialized())
    {
        aesm::message::Request::GetQuoteRequest* mutableReq = msg.mutable_getquotereq();
        mutableReq->CopyFrom(proto_req);
        msg.SerializeToString(&data);
        msg.release_getquotereq();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.c_str(), ae_msg->size);
        }
        delete mutableReq;
    }

    return ae_msg;
}

AEMessage* ProtobufSerializer::serialize(AEGetQuoteResponse* response)
{
    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    aesm::message::Response::GetQuoteResponse  proto_res;//= new aesm::message::Response::InitQuoteResponse();

    proto_res.set_errorcode(response->GetErrorCode());

    if (response->GetQuote() != NULL) 
    {
        std::string quote((const char*)response->GetQuote(), response->GetQuoteLength());
        proto_res.set_quote(quote);
    }


    if (response->GetQEReport() != NULL)
    {
        std::string qe_report((const char*)response->GetQEReport(), response->GetQEReportLength());
        proto_res.set_qe_report(qe_report);
    }

    if (proto_res.IsInitialized())
    {
        aesm::message::Response::GetQuoteResponse* mutableRes = msg.mutable_getquoteres();
        mutableRes->CopyFrom(proto_res);
        msg.SerializeToString(&data);
        msg.release_getquoteres();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.c_str(), ae_msg->size);
        }
        delete mutableRes;
    }
    return ae_msg;
}

bool ProtobufSerializer::inflateResponse(AEMessage* message, AEGetQuoteResponse* response)
{
    std::string data((char*)message->data, message->size);
    aesm::message::Response msg;

    msg.ParseFromString(data);
    if (msg.has_getquoteres() == false)
        return false;

    //this is an AEInitQuoteResponse
    aesm::message::Response::GetQuoteResponse proto_res = msg.getquoteres();

    uint32_t qe_report_length = 0;
    uint8_t* qe_report = NULL;
    uint32_t quote_length = 0;
    uint8_t* quote = NULL;
    uint32_t errorCode = proto_res.errorcode();

    if (proto_res.has_quote())
    {
        if (proto_res.quote().size() > UINT_MAX) {
            return false;
        }
        quote_length = (unsigned int) proto_res.quote().size();
        quote = (uint8_t*)const_cast<char *>(proto_res.quote().data());
    }

    if (proto_res.has_qe_report())
    {
        if (proto_res.qe_report().size() > UINT_MAX) {
            return false;
        }
        qe_report_length = (unsigned int) proto_res.qe_report().size();
        qe_report = (uint8_t*)const_cast<char *>(proto_res.qe_report().data());
    }


    response->inflateValues(errorCode,
                            quote_length,
                            quote,
                            qe_report_length,
                            qe_report);

    return true;
}

/********
  GET Launch Token
 ********/

AEMessage* ProtobufSerializer::serialize(AEGetLaunchTokenRequest* request)
{
    if (request->check() == false)
        return NULL;

    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Request msg;
    aesm::message::Request::GetLaunchTokenRequest proto_req;

    if (request->GetMeasurement() != NULL)
    {
        std::string mr_enclave((const char*)request->GetMeasurement(), request->GetMeasurementLength());
        proto_req.set_mr_enclave(mr_enclave);
    }

    if (request->GetSigstruct() != NULL)
    {
        std::string mr_signer((const char*)request->GetSigstruct(), request->GetSigstructLength());
        proto_req.set_mr_signer(mr_signer);
    }

    if (request->GetAttributes() != NULL)
    {
        std::string se_attributes((const char*)request->GetAttributes(), request->GetAttributesLength());
        proto_req.set_se_attributes(se_attributes);
    }   

    proto_req.set_timeout(request->GetTimeout());

    if (proto_req.IsInitialized())
    {
        aesm::message::Request::GetLaunchTokenRequest* mutableReq = msg.mutable_getlictokenreq();
        mutableReq->CopyFrom(proto_req);
        msg.SerializeToString(&data);
        msg.release_getlictokenreq();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, (const char*)data.data(), ae_msg->size);
        }
        delete mutableReq;
    }
    return ae_msg;
}

AEMessage* ProtobufSerializer::serialize(AEGetLaunchTokenResponse* response)
{
    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    aesm::message::Response::GetLaunchTokenResponse proto_res;

    if (response->GetToken() != NULL)
    {
        std::string token((const char*)response->GetToken(), response->GetTokenLength());
        proto_res.set_token(token);
    }

    proto_res.set_errorcode(response->GetErrorCode());

    if (proto_res.IsInitialized())
    {
        aesm::message::Response::GetLaunchTokenResponse* mutableRes = msg.mutable_getlictokenres();
        mutableRes->CopyFrom(proto_res);
        msg.SerializeToString(&data);
        msg.release_getlictokenres();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), ae_msg->size);
        }
        delete mutableRes;
    }
    return ae_msg;
}

bool ProtobufSerializer::inflateResponse(AEMessage* message, AEGetLaunchTokenResponse* response)
{
    std::string data((char*)message->data, message->size);
    aesm::message::Response msg;

    msg.ParseFromString(data);
    if (msg.has_getlictokenres() == false)
        return false;

    //this is an AEInitQuoteResponse
    aesm::message::Response::GetLaunchTokenResponse proto_res = msg.getlictokenres();

    uint32_t errorCode = proto_res.errorcode();
    uint32_t token_length = 0;
    uint8_t* token = NULL;
    
    if (proto_res.has_token())
    {
        if (proto_res.token().size() > UINT_MAX) {
            return false;
        }
        token_length = (unsigned int)proto_res.token().size();
        token = (uint8_t*)const_cast<char *>(proto_res.token().data());
    } 

    response->inflateValues(errorCode,
                            token_length,
                            token);

    return true;
}

AEMessage* ProtobufSerializer::serialize(AECreateSessionRequest* request){
    
    //kill the warning
    UNUSED(request);

    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Request msg;
    aesm::message::Request::CreateSessionRequest proto_req;

    proto_req.set_dh_msg1_size(request->GetDHMsg1Size());
    proto_req.set_timeout(request->GetTimeout());

    if (proto_req.IsInitialized())
    {
        aesm::message::Request::CreateSessionRequest* mutableReq = msg.mutable_createsessionreq();
        mutableReq->CopyFrom(proto_req);
        msg.SerializeToString(&data);
        msg.release_createsessionreq();     //free the internal object

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), ae_msg->size);
        }

        delete mutableReq;
    }
    return ae_msg;
}

AEMessage* ProtobufSerializer::serialize(AECreateSessionResponse* response)
{
    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    aesm::message::Response::CreateSessionResponse proto_res;

    if (response->GetDHMsg1() != NULL)
    {
        std::string se_dh_msg1((const char*)response->GetDHMsg1(), response->GetDHMsg1Length());
        proto_res.set_se_dh_msg1(se_dh_msg1);
    }

    proto_res.set_errorcode(response->GetErrorCode());
    proto_res.set_session_id(response->GetSessionId());

    if (proto_res.IsInitialized())
    {
        aesm::message::Response::CreateSessionResponse* mutableRes = msg.mutable_createsessionres();
        mutableRes->CopyFrom(proto_res);
        msg.SerializeToString(&data);
        msg.release_createsessionres();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), ae_msg->size);
        }
        delete mutableRes;
    }
    return ae_msg;
}

bool ProtobufSerializer::inflateResponse(AEMessage* message, AECreateSessionResponse* response)
{
    std::string data((char*)message->data, message->size);
    aesm::message::Response msg;

    msg.ParseFromString(data);
    if (msg.has_createsessionres() == false)
        return false;

    aesm::message::Response::CreateSessionResponse proto_res = msg.createsessionres();

    uint32_t errorCode = proto_res.errorcode();
    uint32_t session_id = proto_res.session_id();
    uint32_t dh_msg1_length = 0;
    uint8_t* dh_msg1 = NULL;

    if (proto_res.has_se_dh_msg1())
    {
        if (proto_res.se_dh_msg1().size() > UINT_MAX) {
            return false;
        }
        dh_msg1_length = (unsigned int) proto_res.se_dh_msg1().size();
        dh_msg1 = (uint8_t*)const_cast<char *>(proto_res.se_dh_msg1().data());
    }

    response->inflateValues(errorCode,
                            session_id,
                            dh_msg1_length,
                            dh_msg1);

    return true;
}

/*
 *   Invoke Service
 */

AEMessage* ProtobufSerializer::serialize(AEInvokeServiceRequest* request)
{
    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Request msg;
    aesm::message::Request::InvokeServiceRequest proto_req;

    if (request->GetPSEMessage() != NULL)
    {
        std::string pse_message((const char*)request->GetPSEMessage(), request->GetPSEMessageLength());
        proto_req.set_pse_message(pse_message);
    }

    proto_req.set_pse_resp_size(request->GetResponseSize());
    proto_req.set_timeout(request->GetTimeout());

    if (proto_req.IsInitialized())
    {
        aesm::message::Request::InvokeServiceRequest* mutableReq = msg.mutable_invokeservicereq();
        mutableReq->CopyFrom(proto_req);
        msg.SerializeToString(&data);
        msg.release_invokeservicereq();     //free the internal object

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), ae_msg->size);
        }
        delete mutableReq;
    }
    return ae_msg;
}

AEMessage* ProtobufSerializer::serialize(AEInvokeServiceResponse* response)
{
    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    aesm::message::Response::InvokeServiceResponse proto_res;

    if (response->GetPSEMessage() != NULL)  
    {
        std::string pse_message((const char*)response->GetPSEMessage(), response->GetPSEMessageLength());
        proto_res.set_pse_message(pse_message);
    }

    proto_res.set_errorcode(response->GetErrorCode());

    if (proto_res.IsInitialized())
    {
        aesm::message::Response::InvokeServiceResponse* mutableRes = msg.mutable_invokeserviceres();
        mutableRes->CopyFrom(proto_res);
        msg.SerializeToString(&data);
        msg.release_invokeserviceres();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), ae_msg->size);
        }
        delete mutableRes;
    }
    return ae_msg;
}

bool ProtobufSerializer::inflateResponse(AEMessage* message, AEInvokeServiceResponse* response)
{
    std::string data((char*)message->data, message->size);
    aesm::message::Response msg;

    msg.ParseFromString(data);
    if (msg.has_invokeserviceres() == false)
        return false;

    aesm::message::Response::InvokeServiceResponse proto_res = msg.invokeserviceres();

    uint32_t errorCode = proto_res.errorcode();
    uint32_t pse_message_length = 0;
    uint8_t* pse_message = NULL;

    if (proto_res.has_pse_message())
    {
        if (proto_res.pse_message().size() > UINT_MAX) {
            return false;
        }
        pse_message_length = (unsigned int) proto_res.pse_message().size();
        pse_message = (uint8_t*)const_cast<char *>(proto_res.pse_message().data());
    }

    response->inflateValues(errorCode,
                            pse_message_length,
                            pse_message);

    return true;
}

/*
 *       Exchange REPORT
 */

AEMessage* ProtobufSerializer::serialize(AEExchangeReportRequest* request)
{
    AEMessage* ae_msg = NULL;

    std::string data;

    aesm::message::Request msg;
    aesm::message::Request::ExchangeReportRequest proto_req;

    if (request->GetDHMsg2() != NULL)
    {
        std::string se_dh_msg2((const char*)request->GetDHMsg2(), request->GetDHMsg2Length());
        proto_req.set_se_dh_msg2(se_dh_msg2);
    }

    proto_req.set_session_id(request->GetSessionId());
    proto_req.set_se_dh_msg3_size(request->GetDHMsg3Length());
    proto_req.set_timeout(request->GetTimeout());

    if (proto_req.IsInitialized())
    {
        aesm::message::Request::ExchangeReportRequest* mutableReq = msg.mutable_exchangereportreq();

        mutableReq->CopyFrom(proto_req);
        msg.SerializeToString(&data);
        msg.release_exchangereportreq();     //free the internal object

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), ae_msg->size);
        }
        delete mutableReq;

    }

    return ae_msg;
}

AEMessage* ProtobufSerializer::serialize(AEExchangeReportResponse* response)
{
    std::string data;
    AEMessage* ae_msg = NULL;

    aesm::message::Response msg;
    aesm::message::Response::ExchangeReportResponse proto_res;

    if (response->GetDHMsg3() != NULL)
    {
        std::string se_dh_msg3((const char*)response->GetDHMsg3(), response->GetDHMsg3Length());
        proto_res.set_se_dh_msg3(se_dh_msg3);
    }

    proto_res.set_errorcode(response->GetErrorCode());

    if (proto_res.IsInitialized())
    {
        aesm::message::Response::ExchangeReportResponse* mutableRes = msg.mutable_exchangereportres();
        mutableRes->CopyFrom(proto_res);
        msg.SerializeToString(&data);
        msg.release_exchangereportres();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), ae_msg->size);
        }
        delete mutableRes;
    }

    return ae_msg;
}

bool ProtobufSerializer::inflateResponse(AEMessage* message, AEExchangeReportResponse* response)
{
    std::string data((char*)message->data, message->size);
    aesm::message::Response msg;

    msg.ParseFromString(data);
    if (msg.has_exchangereportres() == false)
        return false;

    aesm::message::Response::ExchangeReportResponse proto_res = msg.exchangereportres();

    uint32_t errorCode = proto_res.errorcode();
    uint32_t dh_msg3_length = 0;
    uint8_t* dh_msg3 = NULL;
    
    if (proto_res.has_se_dh_msg3())
    {
        if (proto_res.se_dh_msg3().size() > UINT_MAX) {
            return false;
        }
        dh_msg3_length = (unsigned int) proto_res.se_dh_msg3().size();
        dh_msg3 = (uint8_t*)const_cast<char *>(proto_res.se_dh_msg3().data());
    }


    response->inflateValues(errorCode,
                            dh_msg3_length,
                            dh_msg3);

    return true;

}

/*
 *       CLOSE SESSION
 */

AEMessage* ProtobufSerializer::serialize(AECloseSessionRequest* request)
{
    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Request msg;
    aesm::message::Request::CloseSessionRequest proto_req;

    proto_req.set_session_id(request->GetSessionId());
    proto_req.set_timeout(request->GetTimeout());
    
    if (proto_req.IsInitialized())
    {
        aesm::message::Request::CloseSessionRequest* mutableReq = msg.mutable_closesessionreq();
        mutableReq->CopyFrom(proto_req);
        msg.SerializeToString(&data);
        msg.release_closesessionreq();     //free the internal object

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), ae_msg->size);
        }
        delete mutableReq;
    }
    return ae_msg;
}

AEMessage* ProtobufSerializer::serialize(AECloseSessionResponse* response)
{
    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    aesm::message::Response::CloseSessionResponse proto_res;

    proto_res.set_errorcode(response->GetErrorCode());

    if (proto_res.IsInitialized())
    {
        aesm::message::Response::CloseSessionResponse* mutableRes = msg.mutable_closesessionres();
        mutableRes->CopyFrom(proto_res);
        msg.SerializeToString(&data);
        msg.release_closesessionres();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), ae_msg->size);
        }
        delete mutableRes;
    }
    return ae_msg;
}

bool ProtobufSerializer::inflateResponse(AEMessage* message, AECloseSessionResponse* response)
{
    std::string data((char*)message->data, message->size);
    aesm::message::Response msg;

    msg.ParseFromString(data);
    if (msg.has_closesessionres() == false)
        return false;

    aesm::message::Response::CloseSessionResponse proto_res = msg.closesessionres();
    response->inflateValues(proto_res.errorcode());

    return true;
}


AEMessage* ProtobufSerializer::serialize(AEGetPsCapRequest* request)
{
    //kill the warning
    UNUSED(request);

    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Request msg;
    aesm::message::Request::GetPsCapRequest proto_req;
    proto_req.set_timeout(request->GetTimeout());

    if (proto_req.IsInitialized())
    {
        aesm::message::Request::GetPsCapRequest* mutableReq = msg.mutable_getpscapreq();
        mutableReq->CopyFrom(proto_req);
        msg.SerializeToString(&data);
        msg.release_getpscapreq(); //free the internal object

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.c_str(), ae_msg->size);
        }
        delete mutableReq;
    }
    return ae_msg;
}

AEMessage* ProtobufSerializer::serialize(AEGetPsCapResponse* response)
{
    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    aesm::message::Response::GetPsCapResponse  proto_res;

    proto_res.set_errorcode(response->GetErrorCode());
    proto_res.set_ps_cap(response->GetPsCap());

    if (proto_res.IsInitialized())
    {
        aesm::message::Response::GetPsCapResponse* mutableRes = msg.mutable_getpscapres();
        mutableRes->CopyFrom(proto_res);
        msg.SerializeToString(&data);
        msg.release_getpscapres();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), data.size());
        }
        delete mutableRes;
    }
    return ae_msg;
}

bool ProtobufSerializer::inflateResponse(AEMessage* message, AEGetPsCapResponse* response)
{
    std::string data((char*)message->data, message->size);
    aesm::message::Response msg;

    msg.ParseFromString(data);
    if (msg.has_getpscapres() == false)
        return false;

    aesm::message::Response::GetPsCapResponse proto_res = msg.getpscapres();
    uint32_t errorCode = proto_res.errorcode();
    uint64_t ps_cap = -1;
    if (proto_res.has_ps_cap())
    {
        ps_cap = proto_res.ps_cap();
    }
    response->inflateValues(errorCode,
                            ps_cap);

    return true;
}


AEMessage* ProtobufSerializer::serialize(AEReportAttestationRequest* request)
{
    //kill the warning
    UNUSED(request);

    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Request msg;
    aesm::message::Request::ReportAttestationErrorRequest proto_req;
    if (request->GetPlatformInfo() != NULL)
    {
        std::string platform_info((const char*)request->GetPlatformInfo(), request->GetPlatformInfoLength());
        proto_req.set_platform_info(platform_info);
    }
    proto_req.set_attestation_error_code(request->GetAttestationErrorCode());
    proto_req.set_update_info_size(request->GetUpdateInfoLength());
    proto_req.set_timeout(request->GetTimeout());

    if (proto_req.IsInitialized())
    {
        aesm::message::Request::ReportAttestationErrorRequest* mutableReq = msg.mutable_reporterrreq();
        mutableReq->CopyFrom(proto_req);
        msg.SerializeToString(&data);
        msg.release_reporterrreq(); //free the internal object
        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.c_str(), ae_msg->size);
        }
        delete mutableReq;
    }
    return ae_msg;
}

AEMessage* ProtobufSerializer::serialize(AEReportAttestationResponse* response)
{
    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    aesm::message::Response::ReportAttestationErrorResponse  proto_res;

    if (response->GetUpdateInfo() != NULL)
    {
        std::string platform_update_info((const char*)response->GetUpdateInfo(), response->GetUpdateInfoLength());
        proto_res.set_platform_update_info(platform_update_info);
    }


    proto_res.set_errorcode(response->GetErrorCode());

    if (proto_res.IsInitialized())
    {
        aesm::message::Response::ReportAttestationErrorResponse* mutableRes = msg.mutable_reporterrres();
        mutableRes->CopyFrom(proto_res);
        msg.SerializeToString(&data);
        msg.release_reporterrres();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), data.size());
        }
        delete mutableRes;
    }
    return ae_msg;
}

bool ProtobufSerializer::inflateResponse(AEMessage* message, AEReportAttestationResponse* response)
{
    std::string data((char*)message->data, message->size);
    aesm::message::Response msg;

    msg.ParseFromString(data);
    if (msg.has_reporterrres() == false)
        return false;

    aesm::message::Response::ReportAttestationErrorResponse proto_res = msg.reporterrres();
    uint32_t errorCode = proto_res.errorcode();
    uint32_t platform_update_info_length = 0;
    uint8_t* platform_update_info = NULL;

    if (proto_res.has_platform_update_info())
    {
        if (proto_res.platform_update_info().size() > UINT_MAX) {
            return false;
        }
        platform_update_info_length = (unsigned int) proto_res.platform_update_info().size();
        platform_update_info = (uint8_t*)const_cast<char *>(proto_res.platform_update_info().data());
    }
    response->inflateValues(errorCode,
                            platform_update_info_length,
                            platform_update_info);

    return true;
}

/*
  Get white list size
*/
IAERequest* ProtobufSerializer::inflateGetWhiteListSizeRequest(aesm::message::Request* reqMsg)
{
    AEGetWhiteListSizeRequest* request = new AEGetWhiteListSizeRequest();
    aesm::message::Request::GetWhiteListSizeRequest proto_req = reqMsg->getwhitelistsizereq();

    request->inflateValues(proto_req.timeout());
    return request;
}

AEMessage* ProtobufSerializer::serialize(AEGetWhiteListSizeRequest* request)
{
    //kill the warning
    UNUSED(request);

    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Request msg;
    aesm::message::Request::GetWhiteListSizeRequest proto_req;
    proto_req.set_timeout(request->GetTimeout());

    if (proto_req.IsInitialized())
    {
        aesm::message::Request::GetWhiteListSizeRequest* mutableReq = msg.mutable_getwhitelistsizereq();
        mutableReq->CopyFrom(proto_req);
        msg.SerializeToString(&data);
        msg.release_getwhitelistsizereq(); //free the internal object

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.c_str(), ae_msg->size);
        }
        delete mutableReq;
    }
    return ae_msg;
}

AEMessage* ProtobufSerializer::serialize(AEGetWhiteListSizeResponse* response)
{
    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    aesm::message::Response::GetWhiteListSizeResponse  proto_res;

    proto_res.set_errorcode(response->GetErrorCode());
    proto_res.set_white_list_size(response->GetWhiteListSize());

    if (proto_res.IsInitialized())
    {
        aesm::message::Response::GetWhiteListSizeResponse* mutableRes = msg.mutable_getwhitelistsizeres();
        mutableRes->CopyFrom(proto_res);
        msg.SerializeToString(&data);
        msg.release_getwhitelistsizeres();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), data.size());
        }
        delete mutableRes;
    }
    return ae_msg;
}

bool ProtobufSerializer::inflateResponse(AEMessage* message, AEGetWhiteListSizeResponse* response)
{
    std::string data((char*)message->data, message->size);
    aesm::message::Response msg;

    msg.ParseFromString(data);
    if (msg.has_getwhitelistsizeres() == false)
        return false;

    aesm::message::Response::GetWhiteListSizeResponse proto_res = msg.getwhitelistsizeres();
    uint32_t errorCode = proto_res.errorcode();
    uint32_t white_list_size = -1;
    if (proto_res.has_white_list_size())
    {
        white_list_size = proto_res.white_list_size();
    }
    response->inflateValues(errorCode, white_list_size);

    return true;
}

/*
  Get white list
*/
IAERequest* ProtobufSerializer::inflateGetWhiteListRequest(aesm::message::Request* reqMsg)
{
    AEGetWhiteListRequest* request = new AEGetWhiteListRequest();
    aesm::message::Request::GetWhiteListRequest proto_req = reqMsg->getwhitelistreq();

    request->inflateValues(proto_req.white_list_size(),proto_req.timeout());
    return request;
}

AEMessage* ProtobufSerializer::serialize(AEGetWhiteListRequest* request)
{
    //kill the warning
    UNUSED(request);

    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Request msg;
    aesm::message::Request::GetWhiteListRequest proto_req;
    proto_req.set_white_list_size(request->GetWhiteListSize());
    proto_req.set_timeout(request->GetTimeout());
    
    if (proto_req.IsInitialized())
    {
        aesm::message::Request::GetWhiteListRequest* mutableReq = msg.mutable_getwhitelistreq();
        mutableReq->CopyFrom(proto_req);
        msg.SerializeToString(&data);
        msg.release_getwhitelistreq(); //free the internal object

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.c_str(), ae_msg->size);
        }
        delete mutableReq;
    }
    return ae_msg;
}

AEMessage* ProtobufSerializer::serialize(AEGetWhiteListResponse* response)
{
    std::string data;
    AEMessage* ae_msg = NULL;

    aesm::message::Response msg;
    aesm::message::Response::GetWhiteListResponse proto_res;

    if (response->GetWhiteList() != NULL)
    {
        std::string se_white_list((const char*)response->GetWhiteList(), response->GetWhiteListLength());
        proto_res.set_white_list(se_white_list);
    }

    proto_res.set_errorcode(response->GetErrorCode());

    if (proto_res.IsInitialized())
    {
        aesm::message::Response::GetWhiteListResponse* mutableRes = msg.mutable_getwhitelistres();
        mutableRes->CopyFrom(proto_res);
        msg.SerializeToString(&data);
        msg.release_getwhitelistres();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), ae_msg->size);
        }
        delete mutableRes;
    }

    return ae_msg;
}

bool ProtobufSerializer::inflateResponse(AEMessage* message, AEGetWhiteListResponse* response)
{
    std::string data((char*)message->data, message->size);
    aesm::message::Response msg;

    msg.ParseFromString(data);
    if (msg.has_getwhitelistres() == false)
        return false;

    aesm::message::Response::GetWhiteListResponse proto_res = msg.getwhitelistres();
    uint32_t errorCode = proto_res.errorcode();
    uint32_t white_list_length = 0;
    uint8_t * white_list = NULL;

    if (proto_res.has_white_list())
    {
        if(proto_res.white_list().size() > UINT_MAX) {
            return false;
        }
        white_list_length = (unsigned int)proto_res.white_list().size();
        white_list = (uint8_t*)const_cast<char *>(proto_res.white_list().data());
    }
    response->inflateValues(errorCode, white_list_length, white_list);

    return true;
}

/*
  SGX Get extended epid group id
*/
IAERequest* ProtobufSerializer::inflateSGXGetExtendedEpidGroupIdRequest(aesm::message::Request* reqMsg)
{
    AESGXGetExtendedEpidGroupIdRequest* request = new AESGXGetExtendedEpidGroupIdRequest();
    aesm::message::Request::SGXGetExtendedEpidGroupIdRequest proto_req = reqMsg->sgxgetextendedepidgroupidreq();

    request->inflateValues(proto_req.timeout());
    return request;
}

AEMessage* ProtobufSerializer::serialize(AESGXGetExtendedEpidGroupIdRequest* request)
{
    //kill the warning
    UNUSED(request);

    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Request msg;
    aesm::message::Request::SGXGetExtendedEpidGroupIdRequest proto_req;
    proto_req.set_timeout(request->GetTimeout());

    if (proto_req.IsInitialized())
    {
        aesm::message::Request::SGXGetExtendedEpidGroupIdRequest* mutableReq = msg.mutable_sgxgetextendedepidgroupidreq();
        mutableReq->CopyFrom(proto_req);
        msg.SerializeToString(&data);
        msg.release_sgxgetextendedepidgroupidreq(); //free the internal object

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.c_str(), ae_msg->size);
        }
        delete mutableReq;
    }
    return ae_msg;
}

AEMessage* ProtobufSerializer::serialize(AESGXGetExtendedEpidGroupIdResponse* response)
{
    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    aesm::message::Response::SGXGetExtendedEpidGroupIdResponse  proto_res;

    proto_res.set_errorcode(response->GetErrorCode());
    proto_res.set_x_group_id(response->GetExtendedEpidGroupId());

    if (proto_res.IsInitialized())
    {
        aesm::message::Response::SGXGetExtendedEpidGroupIdResponse* mutableRes = msg.mutable_sgxgetextendedepidgroupidres();
        mutableRes->CopyFrom(proto_res);
        msg.SerializeToString(&data);
        msg.release_sgxgetextendedepidgroupidres();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), data.size());
        }
        delete mutableRes;
    }
    return ae_msg;
}

bool ProtobufSerializer::inflateResponse(AEMessage* message, AESGXGetExtendedEpidGroupIdResponse* response)
{
    std::string data((char*)message->data, message->size);
    aesm::message::Response msg;

    msg.ParseFromString(data);
    if (msg.has_sgxgetextendedepidgroupidres() == false)
        return false;

    aesm::message::Response::SGXGetExtendedEpidGroupIdResponse proto_res = msg.sgxgetextendedepidgroupidres();
    uint32_t errorCode = proto_res.errorcode();
    uint32_t x_group_id = -1;
    if (proto_res.has_x_group_id())
    {
        x_group_id = proto_res.x_group_id();
    }
    response->inflateValues(errorCode, x_group_id);

    return true;
}

/*
  SGX Switch extended epid group
*/
IAERequest* ProtobufSerializer::inflateSGXSwitchExtendedEpidGroupRequest(aesm::message::Request* reqMsg)
{
    AESGXSwitchExtendedEpidGroupRequest* request = new AESGXSwitchExtendedEpidGroupRequest();
    aesm::message::Request::SGXSwitchExtendedEpidGroupRequest proto_req = reqMsg->sgxswitchextendedepidgroupreq();

    request->inflateValues(proto_req.x_group_id(), proto_req.timeout());
    return request;
}

AEMessage* ProtobufSerializer::serialize(AESGXSwitchExtendedEpidGroupRequest* request)
{
    //kill the warning
    UNUSED(request);

    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Request msg;
    aesm::message::Request::SGXSwitchExtendedEpidGroupRequest proto_req;
    proto_req.set_x_group_id(request->GetExtendedEpidGroupId());
    proto_req.set_timeout(request->GetTimeout());

    if (proto_req.IsInitialized())
    {
        aesm::message::Request::SGXSwitchExtendedEpidGroupRequest* mutableReq = msg.mutable_sgxswitchextendedepidgroupreq();
        mutableReq->CopyFrom(proto_req);
        msg.SerializeToString(&data);
        msg.release_sgxswitchextendedepidgroupreq(); //free the internal object

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.c_str(), ae_msg->size);
        }
        delete mutableReq;
    }
    return ae_msg;
}

AEMessage* ProtobufSerializer::serialize(AESGXSwitchExtendedEpidGroupResponse* response)
{
    std::string data;
    AEMessage *ae_msg = NULL;

    aesm::message::Response msg;
    aesm::message::Response::SGXSwitchExtendedEpidGroupResponse  proto_res;

    proto_res.set_errorcode(response->GetErrorCode());

    if (proto_res.IsInitialized())
    {
        aesm::message::Response::SGXSwitchExtendedEpidGroupResponse* mutableRes = msg.mutable_sgxswitchextendedepidgroupres();
        mutableRes->CopyFrom(proto_res);
        msg.SerializeToString(&data);
        msg.release_sgxswitchextendedepidgroupres();

        if (data.size() <= UINT_MAX) {
            ae_msg = new AEMessage;
            ae_msg->size = (unsigned int) data.size();
            ae_msg->data = new char[ae_msg->size];
            memcpy(ae_msg->data, data.data(), data.size());
        }
        delete mutableRes;
    }
    return ae_msg;
}

bool ProtobufSerializer::inflateResponse(AEMessage* message, AESGXSwitchExtendedEpidGroupResponse* response)
{
    std::string data((char*)message->data, message->size);
    aesm::message::Response msg;

    msg.ParseFromString(data);
    if (msg.has_sgxswitchextendedepidgroupres() == false)
        return false;

    aesm::message::Response::SGXSwitchExtendedEpidGroupResponse proto_res = msg.sgxswitchextendedepidgroupres();
    uint32_t errorCode = proto_res.errorcode();   
    response->inflateValues(errorCode);

    return true;
}
