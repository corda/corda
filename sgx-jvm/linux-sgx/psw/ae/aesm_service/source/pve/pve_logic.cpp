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


#include "pve_logic.h"
#include "aesm_logic.h"
#include "PVEClass.h"
#include "QEClass.h"
#include "PCEClass.h"
#include "oal/oal.h"
#include "aesm_epid_blob.h"
#include "se_wrapper.h"
#include "prov_msg_size.h"
#include "network_encoding_wrapper.h"
#include "prof_fun.h"
#include "aesm_long_lived_thread.h"
#include "endpoint_select_info.h"
#include <assert.h>

#define SAFE_FREE(ptr)     {if (NULL != (ptr)) {free(ptr); (ptr)=NULL;}}

//Function to continue process Provisioning logic when the response of ProvMsg1 is ProvMsg2
ae_error_t PvEAESMLogic::process_pve_msg2(pve_data_t& data, const uint8_t* msg2, uint32_t msg2_size, const endpoint_selection_infos_t& es_info)
{
    uint32_t msg_size = 0;
    uint8_t *msg = NULL;
    uint8_t *resp_msg = NULL;
    uint32_t resp_size = 0;
    epid_blob_with_cur_psvn_t epid_data;
    ae_error_t ret = AE_SUCCESS;
    AESM_PROFILE_FUN;

    AESM_DBG_DEBUG("enter fun");
    AESM_DBG_TRACE("processing msg2 whose length is %d",msg2_size);
    memset(&epid_data, 0, sizeof(epid_data));


    if(EPIDBlob::instance().read(epid_data)!=AE_SUCCESS){
        //First try to read existing EPID Blob to get old epid blob
        //error code of reading epid blob will be ignored since old epid blob is optional
        AESM_DBG_TRACE("read old epid blob fail");
    }else{
        AESM_DBG_TRACE("succ read old epid blob");
    }

    msg_size = estimate_msg3_size_by_msg2_size(msg2_size); //estimate an upbound for msg3 size
    AESM_DBG_TRACE("estimate msg3 size: %d",msg_size);

    assert(msg_size > 0);
    msg = static_cast<uint8_t *>(malloc(msg_size));
    if(msg == NULL){
        AESM_DBG_ERROR("malloc failed");
        ret = AE_OUT_OF_MEMORY_ERROR;
        goto CLEANUP;
    }
    memset(msg, 0, msg_size);
    AESM_DBG_TRACE("start processing msg2 and gen msg3");
    ret = static_cast<ae_error_t>(CPVEClass::instance().proc_prov_msg2(data, msg2, msg2_size,
        epid_data.trusted_epid_blob, SGX_TRUSTED_EPID_BLOB_SIZE_SDK,//discard curpsvn in epid blob
         msg, msg_size));//with help of PvE, process ProvMsg2 and generate ProvMsg3

    if(ret == AE_SUCCESS){
        if(GET_SIZE_FROM_PROVISION_REQUEST(msg)>msg_size){
            AESM_DBG_ERROR("prov msg2 size %d is larger than buffer size %d", GET_SIZE_FROM_PROVISION_REQUEST(msg), msg_size);
            ret = PVE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
        AESM_DBG_TRACE("Start send msg3 and recv msg4");
        msg_size = static_cast<uint32_t>(GET_SIZE_FROM_PROVISION_REQUEST(msg));//get the real size of ProvMsg3
        ret = AESMNetworkEncoding::aesm_send_recv_msg_encoding(es_info.provision_url,
            msg, msg_size, resp_msg, resp_size); //Encoding ProvMsg3, send to server, receive ProvMsg4 and decode
        if(ret != AE_SUCCESS){
            AESM_LOG_ERROR("%s",g_event_string_table[SGX_EVENT_EPID_PROV_FAILURE]);
            AESM_DBG_WARN("send prov msg3 via network failed:(ae%d)",ret);
            goto CLEANUP;
        }
        assert(resp_msg!=NULL);
        AESM_DBG_TRACE("Start to proc msg4");
        ret = process_pve_msg4(data, resp_msg, resp_size);//The response msg must be ProvMsg4, process it to generate EPIDBlob
        if(ret != AE_SUCCESS){
            AESM_DBG_TRACE("processing msg4 failed:(ae%d)",ret);
            goto CLEANUP;
        }
        ret = AE_SUCCESS;
        AESM_DBG_TRACE("processing msg4 succ");
    }else{
        AESM_DBG_WARN("fail to process prov msg2:(ae%d)",ret);
    }
CLEANUP:
    SAFE_FREE(msg);
    if(resp_msg!=NULL)
        AESMNetworkEncoding::aesm_free_response_msg(resp_msg);
    return ret;
}

//Function to finish the Provisioning Logic when a ProvMsg4 is expected or encountered
ae_error_t PvEAESMLogic::process_pve_msg4(const pve_data_t& data, const uint8_t* msg4, uint32_t msg4_size)
{
    AESM_PROFILE_FUN;
    epid_blob_with_cur_psvn_t epid_data;
    ae_error_t ret = AE_SUCCESS;

    AESM_DBG_DEBUG("enter fun");
    AESM_DBG_TRACE("processing msg4 with size %d",msg4_size);
    memset(&epid_data, 0, sizeof(epid_data));

    //with the help of PvE to process ProvMsg4 and generate EPIDDataBlob
    if((ret = static_cast<ae_error_t>(CPVEClass::instance().proc_prov_msg4(data,  msg4, msg4_size,
        epid_data.trusted_epid_blob, SGX_TRUSTED_EPID_BLOB_SIZE_SDK)))!=AE_SUCCESS){
            AESM_DBG_WARN("proc prov msg4 fail:(ae%d)",ret);
            goto fini;
    }
    if(0!=memcpy_s(&epid_data.cur_pi, sizeof(epid_data.cur_pi),
        &data.bpi, sizeof(data.bpi))){
            AESM_DBG_ERROR("memcpy failed");
            ret = PVE_UNEXPECTED_ERROR;
            goto fini;
    }
#ifdef DBG_LOG
    char dbg_str[256];
    aesm_dbg_format_hex(reinterpret_cast<const uint8_t *>(&epid_data), sizeof(epid_data), dbg_str, 256);
    AESM_DBG_TRACE("write epid_data=%s",dbg_str);
#endif
    ret=EPIDBlob::instance().write(epid_data);//save the data into persistent data storage
    if(AE_SUCCESS!=ret){
        AESM_DBG_WARN("fail to write epid_data:(ae%d)",ret);
    }
fini:
    return (ae_error_t)ret;
}

//Function to process the Provisioning Logic for backup retrieval of old epid data blob
//The function assumes that the PvE state has been IDLE
ae_error_t PvEAESMLogic::update_old_blob(pve_data_t& data, const endpoint_selection_infos_t& es_info)
{
    uint32_t       msg_size = 0;
    uint8_t        *msg = NULL;
    uint32_t       ae_ret = AE_SUCCESS;
    uint8_t        *resp_msg = NULL;
    uint32_t       resp_size = 0;

    AESM_PROFILE_FUN;
    AESM_DBG_DEBUG("enter fun");

    msg_size = estimate_msg1_size(false);
    assert(msg_size > 0);

    msg = static_cast<uint8_t *>(malloc(msg_size));
    if(msg == NULL){
        AESM_DBG_ERROR("malloc fail");
        ae_ret = AE_OUT_OF_MEMORY_ERROR;
        goto ret_point;
    }
    memset(msg, 0, msg_size);

    AESM_DBG_TRACE("start to gen prov msg1, estimate size %d", msg_size);
    data.is_backup_retrieval = true;
    data.is_performance_rekey = false;
    ae_ret = CPVEClass::instance().gen_prov_msg1(data, msg, msg_size);//generate ProvMsg1
    if (ae_ret != AE_SUCCESS)
    {
        AESM_DBG_WARN("gen prov msg1 failed:(ae%d)",ae_ret);
        goto ret_point;
    }
    msg_size = static_cast<uint32_t>(GET_SIZE_FROM_PROVISION_REQUEST(msg));

    AESM_DBG_TRACE("start to send msg1 to server and recv msg4");
    ae_ret = AESMNetworkEncoding::aesm_send_recv_msg_encoding(es_info.provision_url,
        msg, msg_size, resp_msg,resp_size);//encoding/send/receive/decoding
    if(ae_ret != AE_SUCCESS){
        AESM_LOG_ERROR("%s",g_event_string_table[SGX_EVENT_EPID_PROV_FAILURE]);
        AESM_DBG_WARN("send prov msg1 via network failed:%d",ae_ret);
        goto ret_point;
    }
    assert(resp_msg != NULL);
    if (resp_size < PROVISION_RESPONSE_HEADER_SIZE) {
        AESM_DBG_WARN("response message %d too small",resp_size);
        ae_ret = PVE_UNEXPECTED_ERROR;
        goto ret_point;
    }

    AESM_DBG_TRACE("start to send msg4 to server");

    if(GET_TYPE_FROM_PROVISION_RESPONSE(resp_msg) == TYPE_PROV_MSG4){
        ae_ret = process_pve_msg4(data, resp_msg, resp_size);//process ProvMsg4 and generated/save EPID Data Blob
        AESM_DBG_TRACE("msg4 processing finished, status (ae%d)",ae_ret);
    }else{
        AESM_DBG_WARN("response message is not prov msg4");
        ae_ret = PVE_UNEXPECTED_ERROR;
    }
ret_point:
    if(msg)free(msg);
    if(resp_msg!=NULL){
        AESMNetworkEncoding::aesm_free_response_msg(resp_msg);
    }
    return (ae_error_t)ae_ret;
}

aesm_error_t PvEAESMLogic::pve_error_postprocess(ae_error_t ae_error)
{
    switch(ae_error){
    case AE_SUCCESS:
        return AESM_SUCCESS;
    case OAL_NETWORK_UNAVAILABLE_ERROR:
    {
        AESM_LOG_FATAL("%s", g_event_string_table[SGX_EVENT_EPID_PROV_FAILURE]);
        return AESM_NETWORK_ERROR;
    }
    case OAL_THREAD_TIMEOUT_ERROR:
        return AESM_BUSY;
    case OAL_NETWORK_BUSY:
        return AESM_NETWORK_BUSY_ERROR;
    case OAL_PROXY_SETTING_ASSIST:
        return AESM_PROXY_SETTING_ASSIST;
    case OAL_FILE_ACCESS_ERROR:
    case OAL_CONFIG_FILE_ERROR:
        return AESM_FILE_ACCESS_ERROR;
    case PVE_PARAMETER_ERROR:
    case AE_INVALID_PARAMETER:
    case OAL_PARAMETER_ERROR:
        return AESM_PARAMETER_ERROR;
    case PVE_EPIDBLOB_ERROR:
        return AESM_EPIDBLOB_ERROR;
    case AE_ENCLAVE_LOST:
        return AESM_NO_DEVICE_ERROR;
    case AE_SERVER_NOT_AVAILABLE:
        return AESM_SERVICE_UNAVAILABLE;
    case PVE_INTEGRITY_CHECK_ERROR:
    {
        AESM_LOG_FATAL("%s", g_event_string_table[SGX_EVENT_EPID_PROV_INTEGRITY_ERROR]);
        return AESM_SGX_PROVISION_FAILED;
    }
    case PVE_SIGRL_INTEGRITY_CHECK_ERROR:
    {
        AESM_LOG_FATAL("%s", g_event_string_table[SGX_EVENT_EPID20_SIGRL_INTEGRITY_ERROR]);
        return AESM_SGX_PROVISION_FAILED;
    }
    case PVE_SERVER_REPORTED_ERROR:
    case PVE_MSG_ERROR:
        return AESM_SGX_PROVISION_FAILED;
    case PVE_REVOKED_ERROR:
        return AESM_EPID_REVOKED_ERROR;
    case PVE_SERVER_BUSY_ERROR:
        return AESM_BACKEND_SERVER_BUSY;
    case PVE_PROV_ATTEST_KEY_NOT_FOUND:
        return AESM_UNRECOGNIZED_PLATFORM;
    case AE_OUT_OF_MEMORY_ERROR:
        return AESM_OUT_OF_MEMORY_ERROR;
    case PSW_UPDATE_REQUIRED:
        return AESM_UPDATE_AVAILABLE;
    case AESM_AE_OUT_OF_EPC:
        return AESM_OUT_OF_EPC;
    default:
        return AESM_UNEXPECTED_ERROR;
    }
}

aesm_error_t PvEAESMLogic::provision(bool performance_rekey_used, uint32_t timeout_usec)
{
    ae_error_t     ae_ret = AE_SUCCESS;
    AESM_PROFILE_FUN;
    AESM_DBG_DEBUG("enter fun");
    AESM_DBG_TRACE("start end point selection");

    ae_ret = start_epid_provision_thread(performance_rekey_used, timeout_usec);

    return pve_error_postprocess(ae_ret);
}

static void log_provision_result(ae_error_t ae_ret)
{
    // Log provisioning results to the Admin Log
    switch (ae_ret) {
    case AE_SUCCESS:
        AESM_LOG_INFO_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_EPID_PROV_SUCCESS]);
        break;
    case OAL_NETWORK_UNAVAILABLE_ERROR:
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_EPID_PROV_FAIL_NW]);
        break;
    case PSW_UPDATE_REQUIRED:
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_EPID_PROV_FAIL_PSWVER]);
        break;
    case PVE_REVOKED_ERROR:
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_EPID_PROV_FAIL_REVOKED]);
        break;
    case OAL_PROXY_SETTING_ASSIST://do not log for proxy assist and thread time out error
    case OAL_THREAD_TIMEOUT_ERROR:
        break;
    default:
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_EPID_PROV_FAIL]);
        break;
    }
}

ae_error_t PvEAESMLogic::epid_provision_thread_func(bool performance_rekey_used)
{
    uint32_t       msg_size = 0;
    uint8_t        *msg = NULL;
    uint8_t        *resp_msg = NULL;
    uint32_t       resp_size = 0;
    ae_error_t     ae_ret = AE_SUCCESS;
    uint32_t       repeat = 0;
    endpoint_selection_infos_t   es_info;
    pve_data_t     pve_data;
    
    AESM_LOG_INFO_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_EPID_PROV_START]);
    memset(&pve_data, 0, sizeof(pve_data));
    if(AE_SUCCESS!=(ae_ret=aesm_start_request_wake_execution())){
        AESM_DBG_ERROR("fail to request wake execution:(ae%d)", ae_ret);
        log_provision_result(ae_ret);
        return ae_ret;
    }

    AESM_DBG_TRACE("start end point selection");
    if((ae_ret = EndpointSelectionInfo::instance().start_protocol(es_info))!=AE_SUCCESS){//EndPoint Selection Protocol to setup Provisioning URL
        (void)aesm_stop_request_wake_execution();
        AESM_DBG_WARN("end point selection failed:(ae%d)",ae_ret);
        log_provision_result(ae_ret);
        return ae_ret;
    }

    //If enclave_lost encountered(such as S3/S4 reached, the retry will be increased by 1, for other kinds of exception like network error, repeat is increased by 1)
    while(repeat < AESM_RETRY_COUNT){
        //estimate upbound of ProvMsg1 and alloc memory for it
        msg_size = estimate_msg1_size(performance_rekey_used);
        AESM_DBG_TRACE("estimate msg1 size :%d",msg_size);
        assert(msg_size > 0);
        if(msg!=NULL)free(msg);
        msg = (uint8_t *)malloc(msg_size);
        if(msg == NULL){
            AESM_DBG_TRACE("malloc failed");
            ae_ret = AE_OUT_OF_MEMORY_ERROR;
            break;
        }
        memset(msg, 0, msg_size);

        //Generate ProvMsg1
        pve_data.is_backup_retrieval = false;
        pve_data.is_performance_rekey = performance_rekey_used;
        if(0!=memcpy_s(&pve_data.pek, sizeof(pve_data.pek), &es_info.pek, sizeof(es_info.pek))){
            AESM_DBG_ERROR("memcpy error");
            ae_ret = AE_FAILURE;
            break;
        }
        ae_ret = static_cast<ae_error_t>(CPVEClass::instance().gen_prov_msg1(pve_data, msg, msg_size));//Generate ProvMsg1
        if (ae_ret != AE_SUCCESS)
        {
            AESM_DBG_WARN("fail to generate prov msg1:(ae%d)",ae_ret);
            break;
        }
        assert( msg != NULL && GET_SIZE_FROM_PROVISION_REQUEST(msg) >= PROVISION_REQUEST_HEADER_SIZE);
        msg_size = static_cast<uint32_t>(GET_SIZE_FROM_PROVISION_REQUEST(msg));
        AESM_DBG_TRACE("msg1 generated with size %d",msg_size);

        if(resp_msg!=NULL){
            AESMNetworkEncoding::aesm_free_response_msg(resp_msg);
            resp_msg=NULL;
        }
        AESM_DBG_TRACE("start to send prov msg1 and recv response");
        ae_ret = AESMNetworkEncoding::aesm_send_recv_msg_encoding(es_info.provision_url,
            msg,msg_size,  resp_msg, resp_size);//encoding/send ProvMsg1, receiving and decoding resp message
        if(ae_ret != AE_SUCCESS){
            AESM_DBG_WARN("send msg1 via network fail:(ae%d)",ae_ret);
            break;//aesm_send_recv_se_msg will not return AE_ENCLAVE_LOST
        }

        assert (resp_msg != NULL && resp_size >= PROVISION_RESPONSE_HEADER_SIZE);

        if(GET_TYPE_FROM_PROVISION_RESPONSE(resp_msg) == TYPE_PROV_MSG2){//If responsed msg is ProvMsg2
            AESM_DBG_TRACE("start to process prov msg2, size %d", resp_size);
            ae_ret = process_pve_msg2(pve_data, resp_msg, resp_size, es_info);//processing following flow if response message is ProvMsg2
            if(ae_ret != AE_SUCCESS){
                if(ae_ret == PVE_EPIDBLOB_ERROR){//If it reports old EPID Blob Error
                    AESM_DBG_TRACE("retrieve old epid blob");
                    if((ae_ret = update_old_blob(pve_data, es_info))!=AE_SUCCESS){//try to retrieve old EPID blob from backend server
                        AESM_DBG_WARN("fail to retrieve old epid blob:(ae%d)",ae_ret);
                        break;
                    }else{
                        AESM_DBG_TRACE("retrieve old epid blob successfully");
                        ae_ret = AE_FAILURE;//set to failure
                        repeat++;//only retry after update old epid blob
                        continue;
                    }
                }else{
                    AESM_DBG_WARN("processing prov msg2 failed:(ae%d)",ae_ret);
                    break;
                }
            }
        }else if(GET_TYPE_FROM_PROVISION_RESPONSE(resp_msg) == TYPE_PROV_MSG4){
            AESM_DBG_TRACE("start to process prov msg4 for current psvn");
            if((ae_ret = process_pve_msg4(pve_data, resp_msg,resp_size))!=AE_SUCCESS){//process ProvMsg4 to generate EPID blob if resp is Msg4
                AESM_DBG_WARN("fail to process prov msg4:(ae%d)",ae_ret);
                break;
            }
        }else{
            AESM_DBG_ERROR("Invalid resp msg type from backend server:%d",(int)GET_TYPE_FROM_PROVISION_RESPONSE(resp_msg));
            ae_ret = AE_FAILURE;
            break;
        }
        AESM_DBG_TRACE("provisioning succ");
        ae_ret = AE_SUCCESS;
        break;
    }

    SAFE_FREE(msg);
    if(resp_msg!=NULL){
        AESMNetworkEncoding::aesm_free_response_msg(resp_msg);
    }
    (void)aesm_stop_request_wake_execution();

    log_provision_result(ae_ret);
    return ae_ret;

}
