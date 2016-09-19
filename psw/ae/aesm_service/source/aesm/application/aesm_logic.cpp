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



#include "QEClass.h"
#include "LEClass.h"
#include "PVEClass.h"
#include "PCEClass.h"

#include "arch.h"
#include "sgx_report.h"
#include "sgx_tseal.h"
#include "epid_pve_type.h"
#include "util.h"
#include <assert.h>
#include <time.h>
#include "oal/oal.h"
#include "oal/aesm_thread.h"
#include "aesm_encode.h"
#include "aesm_epid_blob.h"
#include "aesm_xegd_blob.h"
#include "aesm_logic.h"
#include "pve_logic.h"
#include "qe_logic.h"
#include "platform_info_logic.h"
#include "prov_msg_size.h"
#include "se_sig_rl.h"
#include "se_quote_internal.h"
#include "endpoint_select_info.h"
#include "se_wrapper.h"
#include "ippcp.h"
#include "ippcore.h"
#include <time.h>
#include <string>
#include "ippcp.h"
#include "ippcore.h"
#include "prof_fun.h"
#include "aesm_long_lived_thread.h"
#include "sgx_profile.h"
#include "service_enclave_mrsigner.hh"

#define CHECK_SERVICE_STATUS     if (!is_service_running()) return AESM_SERVICE_STOPPED;
#define CHECK_SGX_STATUS         if (g_sgx_device_status != SGX_ENABLED) return AESM_SGX_DEVICE_NOT_AVAILABLE;

AESMLogicMutex AESMLogic::_qe_pve_mutex;
AESMLogicMutex AESMLogic::_pse_mutex;
AESMLogicMutex AESMLogic::_le_mutex;

bool AESMLogic::_is_qe_psvn_set;
bool AESMLogic::_is_pse_psvn_set;
bool AESMLogic::_is_pce_psvn_set;
psvn_t AESMLogic::_qe_psvn;
psvn_t AESMLogic::_pce_psvn;
psvn_t AESMLogic::_pse_psvn;

uint32_t AESMLogic::active_extended_epid_group_id;

static ae_error_t read_global_extended_epid_group_id(uint32_t *xeg_id)
{
    char path_name[MAX_PATH];
    ae_error_t ae_ret = aesm_get_pathname(FT_PERSISTENT_STORAGE, EXTENDED_EPID_GROUP_ID_FID, path_name, MAX_PATH);
    if(AE_SUCCESS != ae_ret){
        return ae_ret;
    }
    FILE * f = fopen(path_name, "r");
    if( f == NULL){
        return OAL_CONFIG_FILE_ERROR;
    }
    ae_ret = OAL_CONFIG_FILE_ERROR;
    if(fscanf(f, "%u", xeg_id)==1){
        ae_ret = AE_SUCCESS;
    }
    fclose(f);
    return ae_ret;
}
static ae_error_t set_global_extended_epid_group_id(uint32_t xeg_id)
{
    char path_name[MAX_PATH];
    ae_error_t ae_ret = aesm_get_pathname(FT_PERSISTENT_STORAGE, EXTENDED_EPID_GROUP_ID_FID, path_name, MAX_PATH);
    if(AE_SUCCESS != ae_ret){
        return ae_ret;
    }
    FILE *f = fopen(path_name, "w");
    if(f == NULL){
        return OAL_CONFIG_FILE_ERROR;
    }
    ae_ret = OAL_CONFIG_FILE_ERROR;
    if(fprintf(f, "%u", xeg_id)>0){
        ae_ret = AE_SUCCESS;
    }
    fclose(f);
    return ae_ret;
}

uint32_t AESMLogic::get_active_extended_epid_group_id()
{
    return active_extended_epid_group_id;
}

static ae_error_t thread_to_load_qe(aesm_thread_arg_type_t arg)
{
    epid_blob_with_cur_psvn_t epid_data;
    ae_error_t ae_ret = AE_FAILURE;
    uint32_t epid_xeid = 0;
    UNUSED(arg);
    AESM_DBG_TRACE("start to load qe");
    memset(&epid_data, 0, sizeof(epid_data));
    AESMLogicLock lock(AESMLogic::_qe_pve_mutex);
    if((ae_ret = EPIDBlob::instance().read(epid_data)) == AE_SUCCESS)
    {
        AESM_DBG_TRACE("EPID blob is read successfully, loading QE ...");
        ae_ret = CQEClass::instance().load_enclave();
        if(AE_SUCCESS != ae_ret)
        {
            AESM_DBG_WARN("fail to load QE: %d", ae_ret);
        }else{
            AESM_DBG_TRACE("QE loaded successfully");
            bool resealed = false;
            // Just take this chance to reseal EPID blob in case TCB is
            // upgraded, return value is ignored and no provisioning is
            // triggered.
            ae_ret = static_cast<ae_error_t>(CQEClass::instance().verify_blob(
                epid_data.trusted_epid_blob,
                SGX_TRUSTED_EPID_BLOB_SIZE_PAK,
                &resealed));
            if(AE_SUCCESS != ae_ret)
            {
                AESM_DBG_WARN("Failed to verify EPID blob: %d", ae_ret);
                // The EPID blob is invalid.
                EPIDBlob::instance().remove();
            }else{
                // Check whether EPID blob XEGDID is aligned with active extended group id if it exists.
                if ((EPIDBlob::instance().get_extended_epid_group_id(&epid_xeid) == AE_SUCCESS) && (epid_xeid == AESMLogic::get_active_extended_epid_group_id())) {
                    AESM_DBG_TRACE("EPID blob Verified");
                    // XEGDID is aligned
                    if (true == resealed)
                    {
                        AESM_DBG_TRACE("EPID blob is resealed");
                        if ((ae_ret = EPIDBlob::instance().write(epid_data))
                            != AE_SUCCESS)
                        {
                            AESM_DBG_WARN("Failed to update epid blob: %d", ae_ret);
                        }
                    }
                }
                else { // XEGDID is NOT aligned
                    AESM_DBG_TRACE("XEGDID mismatch in EPIDBlob, loading PCE ...");
                    EPIDBlob::instance().remove();
                    ae_ret = CPCEClass::instance().load_enclave();
                    if (AE_SUCCESS != ae_ret)
                    {
                        AESM_DBG_WARN("fail to load PCE: %d", ae_ret);
                    }
                    else{
                        AESM_DBG_TRACE("PCE loaded successfully");
                    }
                }
            }
        }
    }else{
        AESM_DBG_TRACE("Fail to read EPID Blob");
    }
    AESM_DBG_TRACE("QE Thread finished succ");
    return AE_SUCCESS;
}

// Must be called when AESM starts
ae_error_t AESMLogic::service_start()
{
    AESM_PROFILE_INIT;
    ae_error_t ae_ret = AE_SUCCESS;

    AESM_LOG_INIT();

    //ippInit();//no ippInit available for c version ipp
    AESM_DBG_INFO("aesm service is starting");

    //Try to read current active extended epid group id
    ae_ret = read_global_extended_epid_group_id(&AESMLogic::active_extended_epid_group_id);
    if (AE_SUCCESS != ae_ret){
        AESM_DBG_INFO("Fail to read extended epid group id, default extended epid group used");
        AESMLogic::active_extended_epid_group_id = DEFAULT_EGID; //use default extended epid group id 0 if it is not available from data file

    }
    else{
        AESM_DBG_INFO("active extended group id %d used", AESMLogic::active_extended_epid_group_id);
    }
    extended_epid_group_blob_t xegb;
    aesm_server_url_infos_t urls;
    if (AE_SUCCESS != (XEGDBlob::verify_xegd_by_xgid(active_extended_epid_group_id)) ||
        AE_SUCCESS != (EndpointSelectionInfo::verify_file_by_xgid(active_extended_epid_group_id))){//try to load XEGD and URL file to make sure it is valid
        AESMLogic::active_extended_epid_group_id = DEFAULT_EGID;//If the active extended epid group id read from data file is not valid, switch to default extended epid group id
    }

    ae_ret = CLEClass::instance().load_enclave();
    if(AE_SUCCESS != ae_ret)
    {
        AESM_DBG_INFO("fail to load LE: %d", ae_ret);
        AESM_LOG_FATAL("%s", g_event_string_table[SGX_EVENT_SERVICE_UNAVAILABLE]);

        return ae_ret;
    }

    aesm_thread_t qe_thread=NULL;
    ae_error_t aesm_ret1 = aesm_create_thread(thread_to_load_qe, 0,&qe_thread);
    if(AE_SUCCESS != aesm_ret1 ){
        AESM_DBG_WARN("Fail to create thread to preload QE:%d",aesm_ret1);
    }else{
        (void)aesm_free_thread(qe_thread);//release thread handle to free memory
    }
 
    start_white_list_thread();       
    AESM_DBG_TRACE("aesm service is started");

    return AE_SUCCESS;
}

void AESMLogic::service_stop()
{
    stop_all_long_lived_threads();//waiting for pending threads util timeout
    CPVEClass::instance().unload_enclave();
    CPCEClass::instance().unload_enclave();
    CQEClass::instance().unload_enclave();
    CLEClass::instance().unload_enclave();
    stop_all_long_lived_threads();
    AESM_DBG_INFO("aesm service down");
    AESM_LOG_FINI();

    AESM_PROFILE_OUTPUT;
}

bool AESMLogic::is_service_running()
{
    return true;
}

ae_error_t AESMLogic::save_unverified_white_list(const uint8_t *white_list_cert, uint32_t white_list_cert_size)
{
    wl_cert_chain_t old_cert;
    const wl_cert_chain_t *p_new_cert = reinterpret_cast<const wl_cert_chain_t *>(white_list_cert);
    uint32_t old_cert_size = sizeof(old_cert);
    memset(&old_cert, 0, sizeof(old_cert));
    if((aesm_read_data(FT_PERSISTENT_STORAGE, AESM_WHITE_LIST_CERT_TO_BE_VERIFY_FID, reinterpret_cast<uint8_t *>(&old_cert), &old_cert_size) == AE_SUCCESS)
        && (old_cert_size == sizeof(old_cert)) && (white_list_cert_size >= sizeof(wl_cert_chain_t)))
    {
        if(_ntohl(p_new_cert->wl_cert.wl_version) <= _ntohl(old_cert.wl_cert.wl_version))
        {
            AESM_DBG_WARN("White list version downgraded! current version is %d, new version is %d",
                          _ntohl(old_cert.wl_cert.wl_version), _ntohl(p_new_cert->wl_cert.wl_version));
            return OAL_PARAMETER_ERROR;  // OAL_PARAMETER_ERROR used here is to indicate the white list is incorrect
        }
    }
    return aesm_write_data(FT_PERSISTENT_STORAGE, AESM_WHITE_LIST_CERT_TO_BE_VERIFY_FID, white_list_cert, white_list_cert_size);
}

aesm_error_t AESMLogic::white_list_register(
        const uint8_t *white_list_cert, uint32_t white_list_cert_size)
{
    AESM_DBG_INFO("enter function");
    CHECK_SERVICE_STATUS;
    AESMLogicLock lock(_le_mutex);
    CHECK_SERVICE_STATUS;
    ae_error_t ret_le = AE_SUCCESS;
    if (NULL == white_list_cert||0==white_list_cert_size){
        AESM_DBG_TRACE("Invalid parameter");
        return AESM_PARAMETER_ERROR;
    }
    ae_error_t ae_ret = CLEClass::instance().load_enclave();
    if(ae_ret == AE_SERVER_NOT_AVAILABLE)
    {
        AESM_DBG_WARN("LE not loaded due to AE_SERVER_NOT_AVAILABLE, possible SGX Env Not Ready");
        ret_le = save_unverified_white_list(white_list_cert, white_list_cert_size);
    }
    else if(AE_FAILED(ae_ret))
    {
        AESM_DBG_ERROR("LE not loaded:%d", ae_ret);
        return AESM_UNEXPECTED_ERROR;
    }else{
        ret_le = static_cast<ae_error_t>(CLEClass::instance().white_list_register(
            white_list_cert, white_list_cert_size));
    }

    switch (ret_le)
    {
    case AE_SUCCESS:
        return AESM_SUCCESS;
    case LE_INVALID_PARAMETER:
        AESM_DBG_TRACE("Invalid parameter");
        return AESM_PARAMETER_ERROR;
    case LE_INVALID_ATTRIBUTE:
        AESM_DBG_TRACE("Launch token error");
        return AESM_GET_LICENSETOKEN_ERROR;
    default:
        AESM_DBG_WARN("unexpeted error %d", ret_le);
        return AESM_UNEXPECTED_ERROR;
    }
}

aesm_error_t AESMLogic::get_launch_token(
    const uint8_t * mrenclave, uint32_t mrenclave_size,
    const uint8_t *public_key, uint32_t public_key_size,
    const uint8_t *se_attributes, uint32_t se_attributes_size,
    uint8_t * lictoken, uint32_t lictoken_size)
{
    AESM_DBG_INFO("enter function");

    CHECK_SERVICE_STATUS;
    AESMLogicLock lock(_le_mutex);
    CHECK_SERVICE_STATUS;

    ae_error_t ret_le = AE_SUCCESS;
    if (NULL == mrenclave ||
        NULL == public_key ||
        NULL == se_attributes ||
        NULL == lictoken)
    {
        //sizes are checked in CLEClass::get_launch_token()
        AESM_DBG_TRACE("Invalid parameter");
        return AESM_PARAMETER_ERROR;
    }
    ae_error_t ae_ret = CLEClass::instance().load_enclave();
    if(ae_ret == AESM_AE_NO_DEVICE)
    {
        AESM_LOG_ERROR("%s", g_event_string_table[SGX_EVENT_SERVICE_UNAVAILABLE]);
        AESM_DBG_FATAL("LE not loaded due to AE_SERVER_NOT_AVAILABLE, possible SGX Env Not Ready");
        return AESM_NO_DEVICE_ERROR;
    }
    else if(ae_ret == AESM_AE_OUT_OF_EPC)
    {
        AESM_DBG_WARN("LE not loaded due to out of EPC", ae_ret);
        return AESM_OUT_OF_EPC;
    }
    else if(AE_FAILED(ae_ret))
    {
        AESM_DBG_ERROR("LE not loaded:%d", ae_ret);
        return AESM_SERVICE_UNAVAILABLE;
    }
    ret_le = static_cast<ae_error_t>(CLEClass::instance().get_launch_token(
        const_cast<uint8_t *>(mrenclave), mrenclave_size,
        const_cast<uint8_t *>(public_key), public_key_size,
        const_cast<uint8_t *>(se_attributes), se_attributes_size,
        lictoken, lictoken_size));

    switch (ret_le)
    {
    case AE_SUCCESS:
        return AESM_SUCCESS;
    case LE_INVALID_PARAMETER:
        AESM_DBG_TRACE("Invalid parameter");
        return AESM_PARAMETER_ERROR;
    case LE_INVALID_ATTRIBUTE:
    case LE_INVALID_PRIVILEGE_ERROR:
        AESM_DBG_TRACE("Launch token error");
        return AESM_GET_LICENSETOKEN_ERROR;
    case LE_WHITELIST_UNINITIALIZED_ERROR:
        AESM_DBG_TRACE("LE whitelist uninitialized error");
        return AESM_UNEXPECTED_ERROR;
    default:
        AESM_DBG_WARN("unexpeted error %d", ret_le);
        return AESM_UNEXPECTED_ERROR;
    }
}

/* This function will be called outside aesm(from urts_internal) */
extern "C" sgx_status_t get_launch_token(const enclave_css_t* signature,
                                         const sgx_attributes_t* attribute,
                                         sgx_launch_token_t* launch_token)
{
    AESM_DBG_INFO("enter function");
    return AESMLogic::get_launch_token(signature, attribute, launch_token);
}

ae_error_t AESMLogic::get_qe_isv_svn(uint16_t& isv_svn)
{
    if(!_is_qe_psvn_set){
        ae_error_t ae_err = CQEClass::instance().load_enclave();
        if(AE_SUCCESS != ae_err){
            AESM_DBG_ERROR("Fail to load QE Enclave:%d",ae_err);
            return ae_err;
        }
    }
    assert(_is_qe_psvn_set);
    if(0!=memcpy_s(&isv_svn, sizeof(isv_svn), &_qe_psvn.isv_svn, sizeof(_qe_psvn.isv_svn))){
        AESM_DBG_ERROR("memcpy failed");
        return AE_FAILURE;
    }
    return AE_SUCCESS;
}


ae_error_t AESMLogic::get_pce_isv_svn(uint16_t& isv_svn)
{
    if(!_is_pce_psvn_set){
        ae_error_t ae_err = CPCEClass::instance().load_enclave();
        if(AE_SUCCESS != ae_err){
            AESM_DBG_ERROR("Fail to load PCE Enclave:%d",ae_err);
            return ae_err;
        }
    }
    assert(_is_pce_psvn_set);
    if(0!=memcpy_s(&isv_svn, sizeof(isv_svn), &_pce_psvn.isv_svn, sizeof(_pce_psvn.isv_svn))){
        AESM_DBG_ERROR("memcpy failed");
        return AE_FAILURE;
    }
    return AE_SUCCESS;
}

ae_error_t AESMLogic::get_pse_isv_svn(uint16_t& isv_svn)
{
    return AE_FAILURE;
}

ae_error_t AESMLogic::get_qe_cpu_svn(sgx_cpu_svn_t& cpu_svn)
{
    if(!_is_qe_psvn_set){
        ae_error_t ae_err = CQEClass::instance().load_enclave();
        if(AE_SUCCESS != ae_err){
            AESM_DBG_ERROR("Fail to load QE Enclave:%d",ae_err);
            return ae_err;
        }
    }
    assert(_is_qe_psvn_set);
    if(0!=memcpy_s(&cpu_svn, sizeof(sgx_cpu_svn_t), &_qe_psvn.cpu_svn, sizeof(_qe_psvn.cpu_svn))){
        AESM_DBG_ERROR("memcpy failed");
        return AE_FAILURE;
    }
    return AE_SUCCESS;
}



ae_error_t AESMLogic::set_psvn(uint16_t prod_id, uint16_t isv_svn, sgx_cpu_svn_t cpu_svn, uint32_t mrsigner_index)
{
    if(prod_id == QE_PROD_ID){
        if(mrsigner_index == AE_MR_SIGNER){
            if(_is_qe_psvn_set){
                if(0!=memcmp(&_qe_psvn.isv_svn, &isv_svn, sizeof(isv_svn))||
                    0!=memcmp(&_qe_psvn.cpu_svn, &cpu_svn, sizeof(sgx_cpu_svn_t))){
                        AESM_DBG_ERROR("PSVN unmatched for QE/PVE");
                        return AE_PSVN_UNMATCHED_ERROR;
                }
            }else{
                if(0!=memcpy_s(&_qe_psvn.isv_svn, sizeof(_qe_psvn.isv_svn), &isv_svn, sizeof(isv_svn))||
                    0!=memcpy_s(&_qe_psvn.cpu_svn, sizeof(_qe_psvn.cpu_svn), &cpu_svn, sizeof(sgx_cpu_svn_t))){
                        AESM_DBG_ERROR("memcpy failed");
                        return AE_FAILURE;
                }
                AESM_DBG_TRACE("get QE or PvE isv_svn=%d",(int)isv_svn);
                _is_qe_psvn_set = true;
                return AE_SUCCESS;
            }
        }else if(mrsigner_index==PCE_MR_SIGNER){
            if(_is_pce_psvn_set){
                if(0!=memcmp(&_pce_psvn.isv_svn, &isv_svn, sizeof(isv_svn))||
                    0!=memcmp(&_pce_psvn.cpu_svn, &cpu_svn, sizeof(sgx_cpu_svn_t))){
                        AESM_DBG_ERROR("PSVN unmatched for PCE");
                        return AE_PSVN_UNMATCHED_ERROR;
                }
            }else{
                if(0!=memcpy_s(&_pce_psvn.isv_svn, sizeof(_pce_psvn.isv_svn), &isv_svn, sizeof(isv_svn))||
                    0!=memcpy_s(&_pce_psvn.cpu_svn, sizeof(_pce_psvn.cpu_svn), &cpu_svn, sizeof(sgx_cpu_svn_t))){
                        AESM_DBG_ERROR("memcpy failed");
                        return AE_FAILURE;
                }
                AESM_DBG_TRACE("get PCE isv_svn=%d", (int)isv_svn);
                _is_pce_psvn_set = true;
                return AE_SUCCESS;
            }
        }
    }else if(prod_id == PSE_PROD_ID){
        if(mrsigner_index == AE_MR_SIGNER){
            if(_is_pse_psvn_set){
                if(0!=memcmp(&_pse_psvn.isv_svn, &isv_svn, sizeof(isv_svn))||
                    0!=memcmp(&_pse_psvn.cpu_svn, &cpu_svn, sizeof(sgx_cpu_svn_t))){
                        AESM_DBG_ERROR("PSVN unmatched for PSE");
                        return AE_PSVN_UNMATCHED_ERROR;
                }
            }else{
                if(0!=memcpy_s(&_pse_psvn.isv_svn, sizeof(_pse_psvn.isv_svn), &isv_svn, sizeof(isv_svn))||
                    0!=memcpy_s(&_pse_psvn.cpu_svn, sizeof(_pse_psvn.cpu_svn), &cpu_svn, sizeof(sgx_cpu_svn_t))){
                        AESM_DBG_ERROR("memcpy failed");
                        return AE_FAILURE;
                }
                AESM_DBG_TRACE("get PSE isv_svn=%d", (int)isv_svn);
                _is_pse_psvn_set = true;
               return AE_SUCCESS;
            }
        }
    }
    return AE_SUCCESS;
}

sgx_status_t AESMLogic::get_launch_token(const enclave_css_t* signature,
                                         const sgx_attributes_t* attribute,
                                         sgx_launch_token_t* launch_token)
{
    AESM_DBG_INFO("enter function");
    AESMLogicLock lock(_le_mutex);

    ae_error_t ret_le = AE_SUCCESS;
    uint32_t mrsigner_index = UINT32_MAX;
    // load LE to get launch token
    if((ret_le=CLEClass::instance().load_enclave()) != AE_SUCCESS)
    {
        if(ret_le == AESM_AE_NO_DEVICE)
        {
            AESM_DBG_FATAL("LE not loaded due to no SGX device available, possible SGX Env Not Ready");
            return SGX_ERROR_NO_DEVICE;
        }
        else if(ret_le == AESM_AE_OUT_OF_EPC)
        {
            AESM_DBG_FATAL("LE not loaded due to out of EPC");
            return SGX_ERROR_OUT_OF_EPC;
        }
        else
        {
            AESM_DBG_FATAL("fail to load LE:%d",ret_le);
            return SGX_ERROR_SERVICE_UNAVAILABLE;
        }
    }


    ret_le = static_cast<ae_error_t>(CLEClass::instance().get_launch_token(
        const_cast<uint8_t*>(reinterpret_cast<const uint8_t *>(&signature->body.enclave_hash)),
        sizeof(sgx_measurement_t),
        const_cast<uint8_t*>(reinterpret_cast<const uint8_t *>(&signature->key.modulus)),
        sizeof(signature->key.modulus),
        const_cast<uint8_t*>(reinterpret_cast<const uint8_t *>(attribute)),
        sizeof(sgx_attributes_t),
        reinterpret_cast<uint8_t*>(launch_token),
        sizeof(token_t),
        &mrsigner_index));
    switch (ret_le)
    {
    case AE_SUCCESS:
        break;
    case LE_INVALID_PARAMETER:
        AESM_DBG_TRACE("Invalid parameter");
        return SGX_ERROR_INVALID_PARAMETER;
    case LE_INVALID_ATTRIBUTE:
    case LE_INVALID_PRIVILEGE_ERROR:
        AESM_DBG_TRACE("Launch token error");
        return SGX_ERROR_SERVICE_INVALID_PRIVILEGE;
    case LE_WHITELIST_UNINITIALIZED_ERROR:
        AESM_DBG_TRACE("LE whitelist uninitialized error");
        return SGX_ERROR_UNEXPECTED;
    default:
        AESM_DBG_WARN("unexpeted error %d", ret_le);
        return SGX_ERROR_UNEXPECTED;
    }

    token_t *lt = reinterpret_cast<token_t *>(launch_token);
    ret_le = set_psvn(signature->body.isv_prod_id, signature->body.isv_svn, lt->cpu_svn_le, mrsigner_index);
    if(AE_PSVN_UNMATCHED_ERROR == ret_le)
    {
        //QE or PSE has been changed, but AESM doesn't restart. Will not provide service.
        return SGX_ERROR_SERVICE_UNAVAILABLE;
    }else if(AE_SUCCESS != ret_le) {
        AESM_DBG_ERROR("fail to save psvn:%d", ret_le);
        return SGX_ERROR_UNEXPECTED;
    }

    return SGX_SUCCESS;
}

aesm_error_t AESMLogic::create_session(
    uint32_t* session_id,
    uint8_t* se_dh_msg1, uint32_t se_dh_msg1_size)
{
    return AESM_SERVICE_UNAVAILABLE;
}

aesm_error_t AESMLogic::exchange_report(
    uint32_t session_id,
    const uint8_t* se_dh_msg2, uint32_t se_dh_msg2_size,
    uint8_t* se_dh_msg3, uint32_t se_dh_msg3_size)
{
    return AESM_SERVICE_UNAVAILABLE;
}

aesm_error_t AESMLogic::close_session(
    uint32_t session_id)
{
    return AESM_SERVICE_UNAVAILABLE; 
}

aesm_error_t AESMLogic::invoke_service(
    const uint8_t* pse_message_req, uint32_t pse_message_req_size,
    uint8_t* pse_message_resp, uint32_t pse_message_resp_size)
{
    return AESM_SERVICE_UNAVAILABLE;;
}

aesm_error_t AESMLogic::get_ps_cap(
    uint64_t* ps_cap)
{
    return AESM_PSDA_UNAVAILABLE;
}

#define CHECK_EPID_PROVISIONG_STATUS \
    if(!query_pve_thread_status()){\
        return AESM_BUSY;\
    }

aesm_error_t AESMLogic::init_quote(
    uint8_t *target_info, uint32_t target_info_size,
    uint8_t *gid, uint32_t gid_size)
{
    ae_error_t ret = AE_SUCCESS;
    uint16_t qe_isv_svn = 0xFFFF;
    uint16_t pce_isv_svn = 0xFFFF;
    sgx_cpu_svn_t qe_cpu_svn;
    memset(&qe_cpu_svn, 0, sizeof(qe_cpu_svn));
    AESM_DBG_INFO("init_quote");
    if(sizeof(sgx_target_info_t) != target_info_size ||
       sizeof(sgx_epid_group_id_t) != gid_size)
    {
        return AESM_PARAMETER_ERROR;
    }
    AESMLogicLock lock(_qe_pve_mutex);
    CHECK_EPID_PROVISIONG_STATUS;
    ret = get_pce_isv_svn(pce_isv_svn);
    if(AE_SUCCESS != ret)
    {
        if(AESM_AE_OUT_OF_EPC == ret)
            return AESM_OUT_OF_EPC;
        else if(AESM_AE_NO_DEVICE == ret)
            return AESM_NO_DEVICE_ERROR;
        else if(AE_SERVER_NOT_AVAILABLE == ret)
            return AESM_SERVICE_UNAVAILABLE;
        return AESM_UNEXPECTED_ERROR;
    }
    ret = get_qe_cpu_svn(qe_cpu_svn);
    if(AE_SUCCESS != ret)
    {
        if(AESM_AE_OUT_OF_EPC == ret)
            return AESM_OUT_OF_EPC;
        else if(AESM_AE_NO_DEVICE == ret)
            return AESM_NO_DEVICE_ERROR;
        else if(AE_SERVER_NOT_AVAILABLE == ret)
            return AESM_SERVICE_UNAVAILABLE;
        return AESM_UNEXPECTED_ERROR;
    }
    ret = get_qe_isv_svn(qe_isv_svn);
    if(AE_SUCCESS != ret)
    {
        if(AESM_AE_OUT_OF_EPC == ret)
            return AESM_OUT_OF_EPC;
        else if(AESM_AE_NO_DEVICE == ret)
            return AESM_NO_DEVICE_ERROR;
        else if(AE_SERVER_NOT_AVAILABLE == ret)
            return AESM_SERVICE_UNAVAILABLE;
        return AESM_UNEXPECTED_ERROR;
    }
    return QEAESMLogic::init_quote(
               reinterpret_cast<sgx_target_info_t *>(target_info),
               gid, gid_size, pce_isv_svn, qe_isv_svn, qe_cpu_svn);
}

aesm_error_t AESMLogic::get_quote(const uint8_t *report, uint32_t report_size,
                             uint32_t quote_type,
                             const uint8_t *spid, uint32_t spid_size,
                             const uint8_t *nonce, uint32_t nonce_size,
                             const uint8_t *sigrl, uint32_t sigrl_size,
                             uint8_t *qe_report, uint32_t qe_report_size,
                             uint8_t *quote, uint32_t buf_size)
{
    ae_error_t ret = AE_SUCCESS;
    uint16_t pce_isv_svn = 0xFFFF;
    AESM_DBG_INFO("get_quote");
    if(sizeof(sgx_report_t) != report_size ||
       sizeof(sgx_spid_t) != spid_size)
    {
        return AESM_PARAMETER_ERROR;
    }
    if((nonce && sizeof(sgx_quote_nonce_t) != nonce_size)
        || (qe_report && sizeof(sgx_report_t) != qe_report_size))

    {
        return AESM_PARAMETER_ERROR;
    }
    AESMLogicLock lock(_qe_pve_mutex);
    CHECK_EPID_PROVISIONG_STATUS;
    ret = get_pce_isv_svn(pce_isv_svn);
    if(AE_SUCCESS != ret)
    {
        if(AESM_AE_OUT_OF_EPC == ret)
            return AESM_OUT_OF_EPC;
        else if(AESM_AE_NO_DEVICE == ret)
            return AESM_NO_DEVICE_ERROR;
        else if(AE_SERVER_NOT_AVAILABLE == ret)
            return AESM_SERVICE_UNAVAILABLE;
        return AESM_UNEXPECTED_ERROR;
    }
    return QEAESMLogic::get_quote(report, quote_type, spid, nonce, sigrl,
                                  sigrl_size, qe_report, quote, buf_size, pce_isv_svn);
}

uint32_t AESMLogic::endpoint_selection(endpoint_selection_infos_t& es_info)
{
    AESMLogicLock lock(_qe_pve_mutex);
    SGX_DBGPRINT_ONE_STRING_TWO_INTS_ENDPOINT_SELECTION(__FUNCTION__" (line, 0)", __LINE__, 0);
    return EndpointSelectionInfo::instance().start_protocol(es_info);
}


aesm_error_t AESMLogic::report_attestation_status(
        uint8_t* platform_info, uint32_t platform_info_size,
        uint32_t attestation_status,
        uint8_t* update_info, uint32_t update_info_size)
{
    AESM_DBG_INFO("report_attestation_status");
    AESMLogicLock lock(_pse_mutex);
    return PlatformInfoLogic::report_attestation_status(platform_info, platform_info_size, attestation_status, update_info, update_info_size);
}

uint32_t AESMLogic::is_gid_matching_result_in_epid_blob(const GroupID& gid)
{
    AESMLogicLock lock(_qe_pve_mutex);
    EPIDBlob& epid_blob = EPIDBlob::instance();
    uint32_t le_gid;
    if(epid_blob.get_sgx_gid(&le_gid)!=AE_SUCCESS){//get littlen endian gid
        return GIDMT_UNEXPECTED_ERROR;
    }
    le_gid=_htonl(le_gid);//use bigendian gid
    se_static_assert(sizeof(le_gid)==sizeof(gid));
    if(memcmp(&le_gid,&gid,sizeof(gid))!=0){
        return GIDMT_UNMATCHED;
    }
    return GIDMT_MATCHED;
}

ae_error_t AESMLogic::get_white_list_size_without_lock(uint32_t *white_list_cert_size)
{
    uint32_t white_cert_size = 0;
    ae_error_t ae_ret = aesm_query_data_size(FT_PERSISTENT_STORAGE, AESM_WHITE_LIST_CERT_FID, &white_cert_size);
    if (AE_SUCCESS == ae_ret)
    {
        if (white_cert_size != 0){//file existing and not 0 size
            *white_list_cert_size = white_cert_size;
            return AE_SUCCESS;
        }
        else
            return AE_FAILURE;
    }
    else
    {
        return ae_ret;
    }
}

aesm_error_t AESMLogic::get_white_list_size(
        uint32_t* white_list_cert_size)
{
    if (NULL == white_list_cert_size){
        return AESM_PARAMETER_ERROR;
    }
    CHECK_SERVICE_STATUS;
    AESMLogicLock lock(_le_mutex);
    CHECK_SERVICE_STATUS;
    ae_error_t ae_ret = get_white_list_size_without_lock(white_list_cert_size);
    if (AE_SUCCESS == ae_ret)
        return AESM_SUCCESS;
    else
        return AESM_UNEXPECTED_ERROR;
}


aesm_error_t AESMLogic::get_white_list(
    uint8_t *white_list_cert, uint32_t buf_size)
{
    uint32_t white_cert_size=0;
    if (NULL == white_list_cert){
        return AESM_PARAMETER_ERROR;
    }
    CHECK_SERVICE_STATUS;
    AESMLogicLock lock(_le_mutex);
    CHECK_SERVICE_STATUS;
    ae_error_t ae_ret = get_white_list_size_without_lock(&white_cert_size);
    if (AE_SUCCESS != ae_ret)
        return AESM_UNEXPECTED_ERROR;
    if (white_cert_size != buf_size)
    {
        return AESM_PARAMETER_ERROR;
    }

    ae_ret = aesm_read_data(FT_PERSISTENT_STORAGE, AESM_WHITE_LIST_CERT_FID, white_list_cert, &white_cert_size);
    if (AE_SUCCESS != ae_ret){
        AESM_DBG_WARN("Fail to read white cert list file");
        return AESM_UNEXPECTED_ERROR;
    }
    return AESM_SUCCESS;
}

ae_error_t sgx_error_to_ae_error(sgx_status_t status)
{
    if(SGX_ERROR_OUT_OF_MEMORY == status)
        return AE_OUT_OF_MEMORY_ERROR;
    if(SGX_SUCCESS == status)
        return AE_SUCCESS;
    return AE_FAILURE;
}

aesm_error_t AESMLogic::switch_extended_epid_group(
    uint32_t extended_epid_group_id
    )
{
    AESM_DBG_INFO("AESMLogic::switch_extended_epid_group");
    ae_error_t ae_ret;
    extended_epid_group_blob_t xegb;
    aesm_server_url_infos_t urls;
    if ((ae_ret = XEGDBlob::verify_xegd_by_xgid(extended_epid_group_id)) != AE_SUCCESS ||
        (ae_ret = EndpointSelectionInfo::verify_file_by_xgid(extended_epid_group_id)) != AE_SUCCESS){
        AESM_DBG_INFO("Fail to switch to extended epid group to %d due to XEGD blob for URL blob not available", extended_epid_group_id);
        return AESM_PARAMETER_ERROR;
    }
    ae_ret = set_global_extended_epid_group_id(extended_epid_group_id);
    if (ae_ret != AE_SUCCESS){
        AESM_DBG_INFO("Fail to switch to extended epid group %d", extended_epid_group_id);
        return AESM_UNEXPECTED_ERROR;
    }

    AESM_DBG_INFO("Succ to switch to extended epid group %d in data file, restart aesm required to use it", extended_epid_group_id);
    return AESM_SUCCESS;
}
aesm_error_t AESMLogic::get_extended_epid_group_id(
    uint32_t* extended_epid_group_id)
{
    AESM_DBG_INFO("AESMLogic::get_extended_epid_group");
    if (NULL == extended_epid_group_id)
    {
        return AESM_PARAMETER_ERROR;
    }
    *extended_epid_group_id = get_active_extended_epid_group_id();
    return AESM_SUCCESS;
}

