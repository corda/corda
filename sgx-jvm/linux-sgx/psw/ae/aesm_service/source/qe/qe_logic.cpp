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


#include "aesm_epid_blob.h"
#include "aesm_xegd_blob.h"
#include "qe_logic.h"
#include "pve_logic.h"
#include "aesm_logic.h"
#include "PVEClass.h"
#include "QEClass.h"
#include "se_sig_rl.h"
#include "se_quote_internal.h"
#include "se_wrapper.h"
#include "prof_fun.h"
#include "util.h"

static ae_error_t get_qe_target(sgx_target_info_t *p_qe_target)
{
    ae_error_t ae_ret = AE_SUCCESS;

    if((ae_ret = CQEClass::instance().load_enclave())!=AE_SUCCESS)
    {
        AESM_DBG_ERROR("Fail to load QE:(ae%d)",ae_ret);
        return ae_ret;
    }
    ae_ret = static_cast<ae_error_t>(CQEClass::instance().get_qe_target(p_qe_target));
    if(ae_ret != AE_SUCCESS)
        return ae_ret;
    return AE_SUCCESS;
}

//Function to do reprovision if flag updated is false and set it to true if updated successfully
//The flag updated is used to simpilify logic in caller's code so that provision will not be invoked again 
//if a previous provision has been successfully run
//After reprovision, the output epid_blob will be copied into output parameter epid_data
static aesm_error_t try_reprovision_if_not(bool& updated, epid_blob_with_cur_psvn_t& epid_data)
{
    aesm_error_t aesm_result;
    ae_error_t ae_ret;
    if(updated){
        // We've just got a EPID blob. It's a rare case to reach here.
        // No retry, just return error.
        AESM_DBG_ERROR("try to reprovision again after another provision");
        return AESM_EPIDBLOB_ERROR;
    }
    // The EPID blob is corrupted, and we've not provisioned yet, then
    // we need to start the provision process.
    if((aesm_result = PvEAESMLogic::provision(false, THREAD_TIMEOUT))!=AESM_SUCCESS){
        
        AESM_DBG_ERROR("pve provision failed:(aesm%d)", aesm_result);
        return aesm_result;
    }
    updated = true;
    // Update the epid blob after a successful provisioning.
    if((ae_ret = EPIDBlob::instance().read(epid_data))!=AE_SUCCESS){
        AESM_DBG_ERROR("read epid blob failed:(ae%d)", ae_ret);
        return AESM_EPIDBLOB_ERROR;
    }
    return AESM_SUCCESS;
}

//Function to fetch gid from Epid Data Blob and also return target info
//EPID Provisioning will be redone if Epid Data Blob is not existing/invalid or
// the qe_isv_svn or cpu_svn don't match that in Epid Data Blob
aesm_error_t QEAESMLogic::init_quote(
    sgx_target_info_t *target,
    uint8_t *gid, uint32_t gid_size, uint16_t pce_isv_svn,
    uint16_t qe_isv_svn, const sgx_cpu_svn_t qe_cpu_svn)
{
    ae_error_t ae_ret = AE_SUCCESS;
    EPIDBlob& epid_blob = EPIDBlob::instance();
    AESM_DBG_DEBUG("enter fun");
    aesm_error_t aesm_result = AESM_UNEXPECTED_ERROR;

    AESM_PROFILE_FUN;

    epid_blob_with_cur_psvn_t epid_data;
    bool resealed = false;
    bool updated = false;
    memset(&epid_data,0,sizeof(epid_data));

    uint32_t xegd_xeid = AESMLogic::get_active_extended_epid_group_id();
    AESM_DBG_TRACE("start read and verify old epid blob");
    uint32_t epid_xeid = 0;
    //EPID BLOB not exist
    if ((ae_ret = epid_blob.read(epid_data)) != AE_SUCCESS ){
        if (AESM_SUCCESS != (aesm_result = try_reprovision_if_not(updated, epid_data))){
            goto ret_point;
        }
    }
    //ExtEPIDGroupID not match
    else if ((ae_ret = epid_blob.get_extended_epid_group_id(&epid_xeid)) == AE_SUCCESS &&
        xegd_xeid != epid_xeid)
    {
        (void)epid_blob.remove();
        if (AESM_SUCCESS != (aesm_result = try_reprovision_if_not(updated, epid_data))){
            goto ret_point;
        }
    }

    if((ae_ret = CQEClass::instance().load_enclave())!=AE_SUCCESS)
    {
        AESM_DBG_ERROR("Fail to load QE:(ae%d)", ae_ret);
        if(ae_ret == AESM_AE_OUT_OF_EPC)
            aesm_result = AESM_OUT_OF_EPC;
        else
            aesm_result = AESM_UNEXPECTED_ERROR;
        goto ret_point;
    }
    se_static_assert(SGX_TRUSTED_EPID_BLOB_SIZE_SDK>=SGX_TRUSTED_EPID_BLOB_SIZE_SIK);
    ae_ret = static_cast<ae_error_t>(CQEClass::instance().verify_blob(epid_data.trusted_epid_blob,
        SGX_TRUSTED_EPID_BLOB_SIZE_SDK,
        &resealed));
    if(ae_ret == QE_EPIDBLOB_ERROR){
        (void)epid_blob.remove();
        if(AESM_SUCCESS!=(aesm_result = try_reprovision_if_not(updated, epid_data))){
            goto ret_point;
        }
    }
    else if(ae_ret == AESM_AE_OUT_OF_EPC)
    {
        aesm_result = AESM_OUT_OF_EPC;
        goto ret_point;
    }
    else if(ae_ret != AE_SUCCESS) 
    {
        aesm_result = AESM_UNEXPECTED_ERROR;
        goto ret_point;
    }

    // Assert the size of GID, we have already checked it in upper level.
    assert(sizeof(uint32_t) ==  gid_size);
    UNUSED(gid_size);

    ae_ret = get_qe_target(target);
    if(ae_ret!=AE_SUCCESS){
        AESM_DBG_ERROR("get qe target failed (ae%d)",ae_ret);
        if(ae_ret==AESM_AE_OUT_OF_EPC)
            aesm_result = AESM_OUT_OF_EPC;
        else
            aesm_result = AESM_UNEXPECTED_ERROR;
        goto ret_point;
    }
    AESM_DBG_TRACE("get qe_target flags:%llx xfrm:%llx",
                   target->attributes.flags, target->attributes.xfrm);
    //Any Quoting enclave related code must be before this section to avoid QE/PvE unloading each other
    //do the upgrade reprovision after all Quoting Enclave works
    AESM_DBG_TRACE("qe_isv_svn %d, epid_isv_svn %d",qe_isv_svn, epid_data.cur_pi.pve_svn);
    if((qe_isv_svn > epid_data.cur_pi.pve_svn)
       || (pce_isv_svn > epid_data.cur_pi.pce_svn)
       || (0!=memcmp(&qe_cpu_svn, &epid_data.cur_pi.cpu_svn,
           sizeof(sgx_cpu_svn_t))))
    {
        
        if(AESM_SUCCESS == (aesm_result = try_reprovision_if_not(updated, epid_data))){
            resealed = false;
        }else if(AESM_PROXY_SETTING_ASSIST == aesm_result ||
            AESM_BUSY == aesm_result ||
            AESM_UPDATE_AVAILABLE == aesm_result ){//we should not ignore the three special error
            goto ret_point;
        }
    }
    //Any Quoting enclave related code must be before this section to avoid QE/PvE unloading each other */
    aesm_result = AESM_SUCCESS;
ret_point:
    if(aesm_result == AESM_SUCCESS){
        if (resealed) {
            AESM_DBG_TRACE("Update epid blob");
            if ((ae_ret = epid_blob.write(epid_data)) != AE_SUCCESS) {
                AESM_DBG_WARN("Fail to update epid blob:(ae%d)", ae_ret);
            }
        }
        if (AE_SUCCESS != EPIDBlob::instance().get_sgx_gid((uint32_t*)gid)) {
            aesm_result = AESM_UNEXPECTED_ERROR;
        }
        else {
            AESM_DBG_TRACE("get gid %d from epid blob (little-endian)",
                *(uint32_t*)gid);
        }
    }
    return aesm_result;
}

/* Assuming buffer size is checked before calling this function, and get_quote
   in QE will also check size. */
aesm_error_t QEAESMLogic::get_quote(const uint8_t *report,
                                    uint32_t quote_type,
                                    const uint8_t *spid,
                                    const uint8_t *nonce,
                                    const uint8_t *sigrl, uint32_t sigrl_size,
                                    uint8_t *qe_report,
                                    uint8_t *quote, uint32_t buf_size, uint16_t pce_isv_svn)
{
    epid_blob_with_cur_psvn_t epid_data;
    uint32_t ae_ret = AE_SUCCESS;
    aesm_error_t aesm_result = AESM_UNEXPECTED_ERROR;
    EPIDBlob& epid_blob = EPIDBlob::instance();

    AESM_PROFILE_FUN;
    memset(&epid_data, 0, sizeof(epid_data));

    AESM_DBG_TRACE("start to read and verify epid blob");

    if((ae_ret = epid_blob.read(epid_data))!=AE_SUCCESS){
        if((aesm_result = PvEAESMLogic::provision(false, THREAD_TIMEOUT))!=AESM_SUCCESS){
            
            AESM_DBG_ERROR("pve provision failed:(aesm%d)", aesm_result);
            goto CLEANUP;
        }
    }

    if((ae_ret = CQEClass::instance().load_enclave())!=AE_SUCCESS)
    {
        AESM_DBG_ERROR("load QE failed(ae%d)",ae_ret);
        if(ae_ret == AESM_AE_OUT_OF_EPC)
            aesm_result = AESM_OUT_OF_EPC;
        else
            aesm_result = AESM_UNEXPECTED_ERROR;
        goto CLEANUP;
    }
    AESM_DBG_TRACE("start to get quote");
    ae_ret = CQEClass::instance().get_quote(epid_data.trusted_epid_blob,
        SGX_TRUSTED_EPID_BLOB_SIZE_SDK,
        reinterpret_cast<const sgx_report_t *>(report),
        static_cast<sgx_quote_sign_type_t>(quote_type),
        reinterpret_cast<const sgx_spid_t *>(spid),
        reinterpret_cast<const sgx_quote_nonce_t *>(nonce),
        sigrl,
        sigrl_size,
        reinterpret_cast<sgx_report_t *>(qe_report),
        quote,
        buf_size, pce_isv_svn);
    if(ae_ret != AE_SUCCESS)
    {
        AESM_DBG_TRACE("get_quote failed:(ae%d)",ae_ret);
        if(ae_ret == QE_EPIDBLOB_ERROR)
            aesm_result = AESM_EPIDBLOB_ERROR;
        else if(ae_ret == QE_PARAMETER_ERROR)
            aesm_result = AESM_PARAMETER_ERROR;
        else if(ae_ret == QE_REVOKED_ERROR)
            aesm_result = AESM_EPID_REVOKED_ERROR;
        else
            aesm_result = AESM_UNEXPECTED_ERROR;
        goto CLEANUP;
    }
    AESM_DBG_TRACE("get quote succ");
    aesm_result = AESM_SUCCESS;
CLEANUP:
    return aesm_result;
}

