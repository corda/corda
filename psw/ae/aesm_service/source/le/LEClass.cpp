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


#include <assert.h>
#include "LEClass.h"
#include "aeerror.h"
#include "arch.h"
#include "ae_ipp.h"
#include "util.h"
#include "service_enclave_mrsigner.hh"
#include "aesm_long_lived_thread.h"

extern "C" sgx_status_t sgx_create_le(const char *file_name, const char *prd_css_file_name, const int debug, sgx_launch_token_t *launch_token, int *launch_token_updated, sgx_enclave_id_t *enclave_id, sgx_misc_attribute_t *misc_attr, int *production_loaded);

#include "launch_enclave_u.h"
#include "launch_enclave_u.c"

int CLEClass::white_list_register(
    const uint8_t *white_list_cert,
    uint32_t white_list_cert_size,
    bool save_to_persistent_storage)
{
    sgx_status_t ret = SGX_SUCCESS;
    int retry = 0;
    uint32_t status = 0;
    AESMLogicLock locker(AESMLogic::_le_mutex);

    assert(m_enclave_id);

    ret = le_init_white_list_wrapper(m_enclave_id, &status,
                                       const_cast<uint8_t*>(white_list_cert),
                                       white_list_cert_size);
    for(; ret == SGX_ERROR_ENCLAVE_LOST && retry < AESM_RETRY_COUNT; retry++)
    {
        unload_enclave();
        if(AE_SUCCESS != load_enclave_only())
            return AE_FAILURE;
        ret = le_init_white_list_wrapper(m_enclave_id, &status,
                                          const_cast<uint8_t*>(white_list_cert),
                                       white_list_cert_size);
    }

    if(SGX_SUCCESS!=ret)
        return sgx_error_to_ae_error(ret);
    AESM_DBG_TRACE("le_init_white_list_wrapper return %d",status);
    if(AE_SUCCESS == status&&save_to_persistent_storage){//successfully register the white list cert
        if(AE_SUCCESS != aesm_write_data(FT_PERSISTENT_STORAGE,AESM_WHITE_LIST_CERT_FID,white_list_cert, white_list_cert_size)){//ignore error if failed to save in persistent storage
            AESM_DBG_WARN("Fail to save white list cert in persistent storage");
        }
    }
    return status;
}


void CLEClass::load_white_cert_list()
{
    load_verified_white_cert_list();
    load_white_cert_list_to_be_verify();//If this version is older than previous one, it will not be loaded
}
#include <time.h>
#include "endpoint_select_info.h"
#include "stdint.h"

#define UPDATE_DURATION (24*3600)
ae_error_t CLEClass::update_white_list_by_url()
{
    static time_t last_updated_time = 0;
    int i = 0;
    ae_error_t ret = AE_FAILURE;
    time_t cur_time = time(NULL);
    if (last_updated_time + UPDATE_DURATION > cur_time){
        return LE_WHITE_LIST_QUERY_BUSY;
    }
    for (i = 0; i < 2; i++){//at most retry once if network error
        uint8_t *resp_buf = NULL;
        uint32_t resp_size = 0;
        const char *url = EndpointSelectionInfo::instance().get_server_url(SGX_WHITE_LIST_FILE); 
        if (NULL == url){
            return OAL_CONFIG_FILE_ERROR;
        }
        ret = aesm_network_send_receive(url,
            NULL, 0, &resp_buf, &resp_size,GET, false);
        if (ret == OAL_NETWORK_UNAVAILABLE_ERROR){
            AESM_DBG_WARN("Network failure in getting white list...");
            continue;
        }
        if (ret == AE_SUCCESS){
            if (resp_buf != NULL && resp_size > 0){
                ret = (ae_error_t)instance().white_list_register(resp_buf, resp_size, true);
                if (AE_SUCCESS == ret&&resp_size >= sizeof(wl_cert_chain_t)){
                    const wl_cert_chain_t* wl = reinterpret_cast<const wl_cert_chain_t*>(resp_buf);
                }
                else{
                    ret = AE_FAILURE;//Internal error, maybe LE not consistent with AESM?
                }
            }
            last_updated_time = cur_time;
            aesm_free_network_response_buffer(resp_buf);
        }
        break;
    }
    return ret;
}

ae_error_t CLEClass::load_verified_white_cert_list()
{
    ae_error_t ae_err;
    uint32_t white_cert_size=0;
    ae_err = aesm_query_data_size(FT_PERSISTENT_STORAGE, AESM_WHITE_LIST_CERT_FID, &white_cert_size);
    if(AE_SUCCESS == ae_err && white_cert_size ==0){//file not existing or 0 size
        AESM_DBG_TRACE("no white cert list available in persistent storage");
        return AE_SUCCESS;
    }
    if(AE_SUCCESS != ae_err)
        return ae_err;
    uint8_t *p = (uint8_t *)malloc(white_cert_size);
    if(NULL == p){
        AESM_DBG_ERROR("out of memory");
        return AE_OUT_OF_MEMORY_ERROR;
    }
    ae_err = aesm_read_data(FT_PERSISTENT_STORAGE, AESM_WHITE_LIST_CERT_FID, p, &white_cert_size);
    if(AE_SUCCESS != ae_err){
        AESM_DBG_WARN("Fail to read white cert list file");
        free(p);
        return ae_err;
    }
    ae_err = (ae_error_t)white_list_register(p, white_cert_size,false);//Need not save the data to file again
    if(AE_SUCCESS!=ae_err){
        AESM_DBG_WARN("fail to register white cert list file in persistent storage");
    }
    free(p);
    return ae_err;
}

//This function must be called after white list cert has been verified and the file may overwrite the original one
ae_error_t CLEClass::load_white_cert_list_to_be_verify()
{
    ae_error_t ae_err;
    uint32_t white_cert_size=0;
    ae_err = aesm_query_data_size(FT_PERSISTENT_STORAGE, AESM_WHITE_LIST_CERT_TO_BE_VERIFY_FID, &white_cert_size);
    if(AE_SUCCESS != ae_err || white_cert_size ==0){//file not existing or 0 size
        AESM_DBG_TRACE("no white cert list to be verify in persistent storage");
        return AE_SUCCESS;
    }
    uint8_t *p = (uint8_t *)malloc(white_cert_size);
    if(NULL == p){
        AESM_DBG_ERROR("out of memory");
        return AE_OUT_OF_MEMORY_ERROR;
    }
    ae_err = aesm_read_data(FT_PERSISTENT_STORAGE, AESM_WHITE_LIST_CERT_TO_BE_VERIFY_FID, p, &white_cert_size);
    if(AE_SUCCESS != ae_err){
        AESM_DBG_WARN("Fail to read white cert list file");
        free(p);
        return ae_err;
    }
    ae_err = (ae_error_t)white_list_register(p, white_cert_size,true);//We need to overwrite the original white list file if the file is passed
    if(AE_SUCCESS!=ae_err){
        AESM_DBG_WARN("fail to register white cert list file in persistent storage");
    }
    {//Always remove the file now. If it is not verified, the file has problem and remove it; otherwise, it has been saved as the AESM_WHITE_LIST_CERT_FID
        char white_list_to_be_verify_path_name[MAX_PATH];
        ae_err = aesm_get_pathname(FT_PERSISTENT_STORAGE, AESM_WHITE_LIST_CERT_TO_BE_VERIFY_FID, white_list_to_be_verify_path_name, MAX_PATH);
        if(AE_SUCCESS == ae_err){
            se_delete_tfile(white_list_to_be_verify_path_name);
        }
    }
    free(p);
    return ae_err;
}

ae_error_t CLEClass::load_enclave_only()
{
    before_enclave_load();

    assert(m_enclave_id==0);
    sgx_status_t ret;
    ae_error_t ae_err;
    char prod_css_path[MAX_PATH]={0};
    char enclave_path[MAX_PATH]= {0};
    char *p_prod_css_path = prod_css_path;
    int production_le_loaded = 0;
    if((ae_err = aesm_get_pathname(FT_PERSISTENT_STORAGE, LE_PROD_SIG_STRUCT_FID, prod_css_path, MAX_PATH))!=AE_SUCCESS){
        AESM_DBG_WARN("fail to get production sig struction of LE");
        p_prod_css_path = NULL;
    }
    if((ae_err = aesm_get_pathname(FT_ENCLAVE_NAME, get_enclave_fid(), enclave_path,//get non-production signed LE pathname
        MAX_PATH))
        !=AE_SUCCESS){
        AESM_DBG_ERROR("fail to get LE pathname");
        return ae_err;
    }
    int launch_token_update;
#ifdef AESM_SIM
    UNUSED(p_prod_css_path);
    UNUSED(production_le_loaded);
    ret = sgx_create_enclave(enclave_path, get_debug_flag(), &m_launch_token,
        &launch_token_update, &m_enclave_id,
        &m_attributes);//simulation  mode has no sgx_create_le function. Use sgx_create_enclave 
    if(ret != SGX_SUCCESS){
        AESM_DBG_ERROR("Fail to load LE");
        return AE_FAILURE;
    }
    return AE_SUCCESS;
#else
    ret = sgx_create_le(enclave_path, p_prod_css_path, get_debug_flag(), &m_launch_token,
        &launch_token_update, &m_enclave_id,
        &m_attributes, &production_le_loaded);
    if (ret == SGX_ERROR_NO_DEVICE){
        AESM_DBG_ERROR("AE SERVER NOT AVAILABLE in load non-production signed LE: %s",enclave_path);
        return AESM_AE_NO_DEVICE;
    }
    if(ret == SGX_ERROR_OUT_OF_EPC)
    {
        AESM_DBG_ERROR("Loading LE failed due to out of epc");
        return AESM_AE_OUT_OF_EPC;
    }
    if (ret != SGX_SUCCESS){
        AESM_DBG_ERROR("Loading LE failed:%d",ret);
        return AE_SERVER_NOT_AVAILABLE;
    }else if(production_le_loaded!=0){//production signed LE loaded
        AESM_DBG_INFO("Production signed LE loaded, try loading white list now");
    }else{
        AESM_DBG_INFO("Debug signed LE loaded");
    }
    return AE_SUCCESS;
#endif
}

ae_error_t CLEClass::load_enclave()
{
    if(m_enclave_id){//LE has been loaded before
         return AE_SUCCESS;
    }
    ae_error_t ae_err = load_enclave_only();
    if( AE_SUCCESS == ae_err){
        load_white_cert_list();
    }
    return ae_err;
}

int CLEClass::get_launch_token(
    uint8_t * mrenclave, uint32_t mrenclave_size,
    uint8_t *public_key, uint32_t public_key_size,
    uint8_t *se_attributes, uint32_t se_attributes_size,
    uint8_t * lictoken, uint32_t lictoken_size,
    uint32_t *ae_mrsigner_index
    )
{
    sgx_status_t ret = SGX_SUCCESS;
    int retry = 0;
    int status = 0;

    assert(m_enclave_id);
    sgx_measurement_t mrsigner;

    if(mrenclave_size !=sizeof(sgx_measurement_t) ||
        SE_KEY_SIZE != public_key_size ||
        se_attributes_size != sizeof(sgx_attributes_t) ||
        lictoken_size < sizeof(token_t) )
        return LE_INVALID_PARAMETER;
    //set mrsigner based on the hash of isv pub key from enclave signature
    IppStatus ipperrorCode = ippStsNoErr;
    ipperrorCode = ippsHashMessage(reinterpret_cast<const Ipp8u *>(public_key), public_key_size, reinterpret_cast<Ipp8u *>(&mrsigner), IPP_ALG_HASH_SHA256);
    if( ipperrorCode != ippStsNoErr){
        return AE_FAILURE;
    }
    if(ae_mrsigner_index!=NULL){
        *ae_mrsigner_index = UINT32_MAX;
        for(uint32_t i=0;i<sizeof(G_SERVICE_ENCLAVE_MRSIGNER)/sizeof(G_SERVICE_ENCLAVE_MRSIGNER[0]);i++){
            if(memcmp(&G_SERVICE_ENCLAVE_MRSIGNER[i], &mrsigner, sizeof(mrsigner))==0){
                *ae_mrsigner_index=i;
                break;
            }
        }
    }

#ifdef DBG_LOG
    char mrsigner_info[256];
    sgx_attributes_t *attr = (sgx_attributes_t *)se_attributes;
    aesm_dbg_format_hex((uint8_t *)&mrsigner, sizeof(mrsigner), mrsigner_info, 256);
    AESM_DBG_INFO("try to load Enclave with mrsigner:%s , attr %llx, xfrm %llx", mrsigner_info, attr->flags, attr->xfrm);
#endif

    //get launch token by ecall into LE
    ret = le_get_launch_token_wrapper(m_enclave_id, &status,
                                       reinterpret_cast<sgx_measurement_t*>(mrenclave),
                                       &mrsigner,
                                       reinterpret_cast<sgx_attributes_t*>(se_attributes),
                                       reinterpret_cast<token_t*>(lictoken));
    for(; ret == SGX_ERROR_ENCLAVE_LOST && retry < AESM_RETRY_COUNT; retry++)
    {
        unload_enclave();
        if(AE_SUCCESS != load_enclave())
            return AE_FAILURE;
        ret = le_get_launch_token_wrapper(m_enclave_id, &status,
                                          reinterpret_cast<sgx_measurement_t*>(mrenclave),
                                          &mrsigner,
                                          reinterpret_cast<sgx_attributes_t*>(se_attributes),
                                          reinterpret_cast<token_t*>(lictoken));
    }

    if(SGX_SUCCESS!=ret)
        return sgx_error_to_ae_error(ret);
    if (status == LE_WHITELIST_UNINITIALIZED_ERROR || status == LE_INVALID_PRIVILEGE_ERROR){
        start_white_list_thread(0);//try to query white list unblocking
    }
    return status;
}
