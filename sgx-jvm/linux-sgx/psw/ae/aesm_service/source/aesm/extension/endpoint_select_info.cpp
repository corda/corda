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


#include "endpoint_select_info.h"
#include "PVEClass.h"
#include "prov_msg_size.h"
#include "network_encoding_wrapper.h"
#include "ipp_wrapper.h"
#include "sgx_tcrypto.h"
#include "ippcp.h"
#include "ippcore.h"
#include "aesm_xegd_blob.h"
#include "peksk_pub.hh"
#include "sgx_read_rand.h"
#include <time.h>


//Function to do basic checking of the endpoint selection blob. Esp to avoid no zero-ending in the input string url
static bool is_valid_endpoint_selection_info(const endpoint_selection_infos_t& es_info)
{
    if(es_info.aesm_data_type != AESM_DATA_ENDPOINT_SELECTION_INFOS)
        return false;
    if(es_info.aesm_data_version != AESM_DATA_ENDPOINT_SELECTION_VERSION)
        return false;
    if(strnlen(es_info.provision_url,MAX_PATH)>=MAX_PATH)
        return false;
    return true;
}

ae_error_t EndpointSelectionInfo::read_pek(endpoint_selection_infos_t& es_info)
{
    ae_error_t ae_err=AE_SUCCESS;
    uint32_t es_info_size = sizeof(es_info);

    ae_err = aesm_read_data(FT_PERSISTENT_STORAGE, PROVISION_PEK_BLOB_FID, reinterpret_cast<uint8_t *>(&es_info), &es_info_size);

    if(AE_SUCCESS == ae_err && (es_info_size != sizeof(es_info)||!is_valid_endpoint_selection_info(es_info))){
        AESM_DBG_ERROR("Invalid ES result in persistent storage:size %d, expected size %d", es_info_size, sizeof(es_info));
        ae_err = OAL_FILE_ACCESS_ERROR;
    }

    if(AE_SUCCESS == ae_err){
        AESM_DBG_INFO("Read ES result from persistent storage successfully");
    }else{
        AESM_DBG_WARN("ES result in persistent storage failed to load:%d", ae_err);
    }

    return ae_err;
}

ae_error_t EndpointSelectionInfo::write_pek(const endpoint_selection_infos_t& es_info)
{
    return aesm_write_data(FT_PERSISTENT_STORAGE, PROVISION_PEK_BLOB_FID, reinterpret_cast<const uint8_t *>(&es_info), sizeof(es_info));
}

static ae_error_t ipp_error_to_ae_error(IppStatus ipp_status)
{
    if(ipp_status == ippStsNoErr) return AE_SUCCESS;
    else if(ipp_status == ippStsMemAllocErr||
        ipp_status == ippStsNoMemErr) return AE_OUT_OF_MEMORY_ERROR;
    else return AE_FAILURE;//unknown or unexpected ipp error
}

static bool is_valid_server_url_infos(const aesm_server_url_infos_t& server_urls)
{
    if(server_urls.aesm_data_type!=AESM_DATA_SERVER_URL_INFOS||
        (server_urls.aesm_data_version!=AESM_DATA_SERVER_URL_VERSION&&
        server_urls.aesm_data_version != AESM_DATA_SERVER_URL_VERSION_1))//still support version 1 since the first 3 urls in version 1 is still same as the urls in version 2
        return false;
    if(strnlen(server_urls.endpoint_url,MAX_PATH)>=MAX_PATH)
        return false;
    if (strnlen(server_urls.pse_rl_url, MAX_PATH) >= MAX_PATH)
        return false;
    if (strnlen(server_urls.pse_ocsp_url, MAX_PATH) >= MAX_PATH)
        return false;
    return true;
}

ae_error_t EndpointSelectionInfo::verify_file_by_xgid(uint32_t xgid)
{
    if (xgid == DEFAULT_EGID){//always return true for DEFAULT_EGID
        return AE_SUCCESS;
    }
    aesm_server_url_infos_t urls;
    uint32_t server_urls_size = sizeof(urls);
    ae_error_t ae_err = aesm_read_data(FT_PERSISTENT_STORAGE, AESM_SERVER_URL_FID, reinterpret_cast<uint8_t *>(&urls), &server_urls_size, xgid);
    if (AE_SUCCESS != ae_err ||
        server_urls_size != sizeof(urls) ||
        !is_valid_server_url_infos(urls)){
        return OAL_CONFIG_FILE_ERROR;
    }
    return AE_SUCCESS;
}

//Function to read urls from configure files
ae_error_t EndpointSelectionInfo::get_url_info()
{
    ae_error_t ae_err=AE_SUCCESS;
    uint32_t server_urls_size = sizeof(_server_urls);

    ae_err = aesm_read_data(FT_PERSISTENT_STORAGE, AESM_SERVER_URL_FID, reinterpret_cast<uint8_t *>(&_server_urls), &server_urls_size, AESMLogic::get_active_extended_epid_group_id());

    if(AE_SUCCESS != ae_err || 
        server_urls_size != sizeof(_server_urls)||
        !is_valid_server_url_infos(_server_urls)){ //If fail to read or data format error, use default value
            _is_server_url_valid = false;
            if(AE_SUCCESS == ae_err){//File available but format error, report ERROR LOG
                AESM_LOG_WARN("Server URL Blob file format error");
                AESM_DBG_INFO("fail to read server url info from persistent storage, error code (%d), size %d, expected size %d",
                    ae_err, server_urls_size, sizeof(_server_urls));
                ae_err = OAL_CONFIG_FILE_ERROR;
            }else{
                AESM_DBG_INFO("server url blob file not available in persistent storage");
            }
            if (AESMLogic::get_active_extended_epid_group_id() == DEFAULT_EGID){
                if (strcpy_s(_server_urls.endpoint_url, MAX_PATH, DEFAULT_URL) != 0)
                    return AE_FAILURE;
                if (strcpy_s(_server_urls.pse_rl_url, MAX_PATH, DEFAULT_PSE_RL_URL) != 0)
                    return AE_FAILURE;
                if (strcpy_s(_server_urls.pse_ocsp_url, MAX_PATH, DEFAULT_PSE_OCSP_URL) != 0)
                    return AE_FAILURE;
                _is_server_url_valid = true;
                return AE_SUCCESS;
            }
            else{
                return ae_err;
            }
    }

    _is_server_url_valid = true;
    return AE_SUCCESS;
}

ae_error_t EndpointSelectionInfo::get_url_info(aesm_server_url_infos_t& server_url)
{
    AESMLogicLock lock(_es_lock);
    if (!_is_server_url_valid){
        (void)get_url_info();
    }
    if (_is_server_url_valid)
    {
        if (memcpy_s(&server_url, sizeof(server_url), &_server_urls, sizeof(_server_urls)) != 0){
            return AE_FAILURE;
        }
    }
    else
    {
        return AE_FAILURE;
    }
    return AE_SUCCESS;

}

ae_error_t aesm_check_pek_signature(const signed_pek_t& signed_pek, const extended_epid_group_blob_t& xegb);
IppStatus get_provision_server_rsa_pub_key_in_ipp_format(const signed_pek_t& pek, IppsRSAPublicKeyState **rsa_pub_key);
//The function is to verify the PEK ECDSA Signature and RSA Signature for ES Msg2
//   When PvE uses PEK, it will re-check the ECDSA Signature
//The function will only be called after ES protocol is completed. But it will not be called when reading data back from persitent storage
//@param provision_ttl: The TTL field from ES Msg2 in little endian format
//@param rsa_signature: The RSA Signature in ES Msg2, it is RSA Signature to XID:TTL:provision_url
//@param xid: The transaction id (XID) of the ES Protocol
//@return AE_SUCCESS if signature verification success and passed
//@return PVE_MSG_ERROR if signature verification failed or message error
//other kinds of error code could be returned too due to corresponding error situation
ae_error_t EndpointSelectionInfo::verify_signature(const endpoint_selection_infos_t& es_info, uint8_t xid[XID_SIZE], uint8_t rsa_signature[RSA_3072_KEY_BYTES], uint16_t provision_ttl)
{
    //Do signature verification here
    ae_error_t ae_err = AE_SUCCESS;
    IppsRSAPublicKeyState *rsa_pub_key = NULL;
    Ipp8u *buffer = NULL;
    int public_key_buffer_size = 0;
    int vr = 0;
    uint16_t ttl=_htons(provision_ttl);
    IppStatus ipp_status = ippStsNoErr;
    uint8_t msg_buf[XID_SIZE + sizeof(ttl) + MAX_PATH];
    uint32_t buf_size = 0;
    extended_epid_group_blob_t xegb;

    memset(&xegb, 0, sizeof(xegb));
    if (AE_SUCCESS != (ae_err=XEGDBlob::instance().read(xegb))){
        return ae_err;
    }

    ae_err = aesm_check_pek_signature(es_info.pek, xegb);
    if(AE_SUCCESS != ae_err){
        AESM_DBG_ERROR("PEK Signature verifcation not passed:%d",ae_err);
        goto ret_point;
    }
    AESM_DBG_INFO("PEK signature verified successfully");
    buf_size = XID_SIZE +static_cast<uint32_t>(sizeof(ttl) + strnlen(es_info.provision_url, MAX_PATH));
    if(0!=memcpy_s(msg_buf,sizeof(msg_buf), xid, XID_SIZE)||
        0!=memcpy_s(msg_buf+XID_SIZE, sizeof(ttl) + MAX_PATH, &ttl, sizeof(ttl))||
        0!=memcpy_s(msg_buf+XID_SIZE+sizeof(ttl),  MAX_PATH, es_info.provision_url, buf_size-XID_SIZE-sizeof(ttl))){
            ae_err = AE_FAILURE;
            AESM_DBG_ERROR("memcpy error");
            goto ret_point;
    }

    ipp_status = get_provision_server_rsa_pub_key_in_ipp_format(es_info.pek, &rsa_pub_key);
    if(ippStsNoErr != ipp_status){
        AESM_DBG_ERROR("Fail to load rsa public key from PEK:%d", ipp_status);
        ae_err = ipp_error_to_ae_error(ipp_status);
        goto ret_point;
    }
    ipp_status = ippsRSA_GetBufferSizePublicKey(&public_key_buffer_size, rsa_pub_key);
    if(ippStsNoErr != ipp_status){
        AESM_DBG_ERROR("Fail to get rsa public key size:%s", ipp_status);
        ae_err = ipp_error_to_ae_error(ipp_status);
        goto ret_point;
    }
    buffer = (Ipp8u *)malloc(public_key_buffer_size);
    if(NULL == buffer){
        AESM_DBG_ERROR("malloc error");
        ae_err = AE_OUT_OF_MEMORY_ERROR;
        goto ret_point;
    }
    ipp_status = ippsRSAVerify_PKCS1v15(msg_buf, buf_size, rsa_signature, &vr, rsa_pub_key, ippHashAlg_SHA256, buffer);
    if(ippStsNoErr != ipp_status){
        AESM_DBG_ERROR("Fail to verify rsa signature:%d", ipp_status);
        ae_err = ipp_error_to_ae_error(ipp_status);
        goto ret_point;
    }
    if(vr == 0){
        AESM_DBG_TRACE("rsa signature verification failed");
        ae_err = PVE_MSG_ERROR;
        goto ret_point;
    }else{
        AESM_DBG_TRACE("rsa signature verification passed");
        ae_err = AE_SUCCESS;
    }
ret_point:
    if(NULL != rsa_pub_key){
        secure_free_rsa_pub_key(RSA_3072_KEY_BYTES, sizeof(uint32_t), rsa_pub_key);
    }
    if(NULL != buffer){
        free(buffer);
    }
    return ae_err;
}

#define MAX_ENCLAVE_LOST_RETRY_TIME 1

bool read_aesm_config(aesm_config_infos_t& infos);

//Function to implement the end point selection protocol
ae_error_t EndpointSelectionInfo::start_protocol(endpoint_selection_infos_t& es_info)
{
    AESMLogicLock lock(_es_lock);
    uint32_t msg_size = 0;
    uint8_t *resp = NULL;
    uint32_t resp_size = 0;
    uint16_t provision_ttl = 0;
    uint8_t *msg = NULL;
    uint8_t rsa_signature[RSA_3072_KEY_BYTES];
    gen_endpoint_selection_output_t enclave_output;
    ae_error_t ae_ret = AE_SUCCESS;
    uint32_t enclave_lost_count = 0;

    AESM_DBG_DEBUG("enter fun");
    memset(&es_info, 0, sizeof(es_info));
    memset(&enclave_output, 0, sizeof(enclave_output));
    if(!_is_server_url_valid){
        ae_ret = get_url_info();
        if(AE_SUCCESS != ae_ret){//It is not likely happen, only fail when memcpy_s failed
            AESM_DBG_ERROR("Fail to initialize server URL information");
            goto final_point;
        }
    }

    do{
        if((ae_ret = CPVEClass::instance().load_enclave())!=AE_SUCCESS){
            AESM_DBG_ERROR("Fail to load PVE enclave:%d", ae_ret);
            goto final_point;
        }
        //call PvE to generate the partition and xid
        ae_ret = static_cast<ae_error_t>(CPVEClass::instance().gen_es_msg1_data(&enclave_output));
        if(ae_ret == AE_ENCLAVE_LOST&& (++enclave_lost_count)<=MAX_ENCLAVE_LOST_RETRY_TIME ){
            CPVEClass::instance().unload_enclave();//unload and reload PvE when enclave lost encountered
            continue;
        }else if(ae_ret == AE_SUCCESS){
            break;
        }else{
            AESM_DBG_ERROR("fail to generate parition by PvE");
            goto final_point;
        }
    }while(1);

    AESM_DBG_TRACE("use parition %d from PvE", (int)enclave_output.selector_id);

    AESM_DBG_INFO("Connect to server url \"%s\" for endpoint selection", _server_urls.endpoint_url);

    msg_size = estimate_es_msg1_size();
    assert(msg_size>0);
    msg = static_cast<uint8_t *>(malloc(msg_size));
    if(msg == NULL){
        AESM_DBG_ERROR("malloc error");
        ae_ret = AE_OUT_OF_MEMORY_ERROR;
        goto final_point;
    }
    memset(msg, 0, msg_size);

    ae_ret = static_cast<ae_error_t>(CPVEClass::instance().gen_es_msg1(msg, msg_size, enclave_output));//Generate EndPoint Selection Msg1
    if(ae_ret != AE_SUCCESS){
        AESM_DBG_ERROR("ES msg1 generation failed:%d",ae_ret);
        goto final_point;
    }
    AESM_DBG_TRACE("ES msg1 generated");

    ae_ret = AESMNetworkEncoding::aesm_send_recv_msg_encoding(_server_urls.endpoint_url, msg, msg_size, resp, resp_size);//Encoding/send/receive/Decoding

    if(ae_ret != AE_SUCCESS){
        AESM_DBG_ERROR("fail to send ES msg1 to backend server:%d",ae_ret);
        if(OAL_PROXY_SETTING_ASSIST == ae_ret){//when proxy setting assistant required, return directly
            goto final_point;
        }
        if(read_pek(es_info)==AE_SUCCESS){
            ae_ret = AE_SUCCESS;//use es_info inside persistent storage and ignore network error
        }
        goto final_point;
    }
    assert(resp != NULL);
    AESM_DBG_TRACE("start to process ES msg2");
    ae_ret = static_cast<ae_error_t>(CPVEClass::instance().proc_es_msg2(resp, resp_size, es_info.provision_url, provision_ttl, enclave_output.xid, rsa_signature , es_info.pek));
    if(AE_SUCCESS != ae_ret){
        AESM_DBG_WARN("Fail to process ES msg2 from backend server:%d",ae_ret);
        goto final_point;
    }

    AESM_DBG_TRACE("ES Msg2 decoded successfully, ttl %ds",provision_ttl);
    ae_ret = verify_signature(es_info, enclave_output.xid, rsa_signature, provision_ttl);
    if(AE_SUCCESS != ae_ret){
        AESM_DBG_WARN("Signature verification in ES Msg2 failed");
        goto final_point;
    }
    AESM_DBG_TRACE("Signature in ES Msg2 verified");
    es_info.aesm_data_type = AESM_DATA_ENDPOINT_SELECTION_INFOS;
    es_info.aesm_data_version = AESM_DATA_ENDPOINT_SELECTION_VERSION;
    (void)write_pek(es_info);//ignore file writing error
    AESM_DBG_TRACE("end point selection succ,  provisioning url: %s",es_info.provision_url);

final_point:
    if(msg!=NULL)free(msg);
    if(resp!=NULL){
        AESMNetworkEncoding::aesm_free_response_msg(resp);
    }

    return ae_ret;
}

const char *EndpointSelectionInfo::get_server_url(aesm_network_server_enum_type_t type)
{
    AESMLogicLock lock(_es_lock);
    if (type == SGX_WHITE_LIST_FILE){
        if (!_is_white_list_url_valid){
           (void)read_aesm_config(_config_urls);
            _is_white_list_url_valid = true;
        }
        return _config_urls.white_list_url;
    }
    if(!_is_server_url_valid){
        (void)get_url_info();
    }
    if(!_is_server_url_valid){
         return NULL;
    }
    switch(type){
    case ENDPOINT_SELECTION:
        return _server_urls.endpoint_url;
    case REVOCATION_LIST_RETRIEVAL:
        return _server_urls.pse_rl_url;
    case PSE_OCSP:
        return _server_urls.pse_ocsp_url;
    default://invalid case
        assert(0);
        return NULL;
    }
}

void EndpointSelectionInfo::get_proxy(uint32_t& proxy_type, char proxy_url[MAX_PATH])
{
    AESMLogicLock lock(_es_lock);
    if(!_is_white_list_url_valid){
         (void)read_aesm_config(_config_urls);
         _is_white_list_url_valid=true;
    }
    proxy_type = _config_urls.proxy_type;
    strcpy_s(proxy_url, MAX_PATH, _config_urls.aesm_proxy);
}
const char *EndpointSelectionInfo::get_pse_provisioning_url(const endpoint_selection_infos_t& es_info)
{
    return es_info.provision_url;
}
