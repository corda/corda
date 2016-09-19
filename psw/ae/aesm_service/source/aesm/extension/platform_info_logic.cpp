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

#include "util.h"
#include "platform_info_logic.h"
#include "sgx_quote.h"
#include "aesm_encode.h"
#include "pve_logic.h"
#include "aesm_logic.h"
#include "le2be_macros.h"
#include "pibsk_pub.hh"
#include "sgx_sha256_128.h"
#include <assert.h>
#include "sgx_profile.h"
#include "aesm_long_lived_thread.h"


ae_error_t pib_verify_signature(platform_info_blob_wrapper_t& piBlobWrapper)
{
    ae_error_t ae_err = AE_FAILURE;
    sgx_ecc_state_handle_t ecc_handle = NULL;

    uint8_t result = SGX_EC_INVALID_SIGNATURE;

    const uint32_t data_size = static_cast<uint32_t>(sizeof(piBlobWrapper.platform_info_blob) - sizeof(piBlobWrapper.platform_info_blob.signature));


    piBlobWrapper.valid_info_blob = false;
    do
    {
        sgx_ec256_public_t publicKey;
        sgx_ec256_signature_t signature;
        sgx_status_t sgx_status;

        se_static_assert(sizeof(publicKey) == sizeof(s_pib_pub_key_big_endian));
        se_static_assert(sizeof(signature) == sizeof(piBlobWrapper.platform_info_blob.signature));

        // convert the public key to little endian
        if(0!=memcpy_s(&publicKey, sizeof(publicKey), s_pib_pub_key_big_endian, sizeof(s_pib_pub_key_big_endian))){
            ae_err = AE_FAILURE;
            break;
        }
        SwapEndian_32B(((uint8_t*)&publicKey) +  0);
        SwapEndian_32B(((uint8_t*)&publicKey) + 32);

        // convert the signature to little endian
        if(0!=memcpy_s(&signature, sizeof(signature), &piBlobWrapper.platform_info_blob.signature, sizeof(piBlobWrapper.platform_info_blob.signature))){
            ae_err = AE_FAILURE;
            break;
        }
        SwapEndian_32B(((uint8_t*)&signature) +  0);
        SwapEndian_32B(((uint8_t*)&signature) + 32);

        sgx_status = sgx_ecc256_open_context(&ecc_handle);
        BREAK_IF_TRUE((SGX_SUCCESS != sgx_status), ae_err, AE_FAILURE);

        sgx_status = sgx_ecdsa_verify((uint8_t*)&piBlobWrapper.platform_info_blob, data_size, &publicKey, &signature, &result, ecc_handle);
        BREAK_IF_TRUE((SGX_SUCCESS != sgx_status), ae_err, AE_FAILURE);

        if (SGX_EC_VALID != result)
        {
            AESM_LOG_WARN(g_event_string_table[SGX_EVENT_PID_SIGNATURE_FAILURE]);
            break;
        }

        piBlobWrapper.valid_info_blob = true;

        ae_err = AE_SUCCESS;

    } while (0);
    if (ecc_handle != NULL) {
        sgx_ecc256_close_context(ecc_handle);
    }

    return ae_err;
}

aesm_error_t PlatformInfoLogic::report_attestation_status(
    uint8_t* platform_info, uint32_t platform_info_size,
    uint32_t attestation_status,
    uint8_t* update_info, uint32_t update_info_size)
{

    AESM_DBG_TRACE("enter fun");
    //
    // we don't do anything without platform info
    //
    if (NULL == platform_info) {
        return AESM_PARAMETER_ERROR;
    }

    platform_info_blob_wrapper_t pibw;


    //
    // presence of platform info is conditional, on whether we're up to date
    // if we're up to date, no platform info and no need for update info
    //
    if (((sizeof(pibw.platform_info_blob) > platform_info_size)) || ((NULL != update_info) && (sizeof(sgx_update_info_bit_t) > update_info_size))) {
        return AESM_PARAMETER_ERROR;
    }

    pibw.valid_info_blob = false;
    memcpy_s(&pibw.platform_info_blob, sizeof(pibw.platform_info_blob), platform_info, platform_info_size);

    aesm_error_t status = AESM_SUCCESS;       // status only tells app to look at updateInfo

    //
    // contents of input platform info can get stale, but not by virtue of anything we do
    // (the latest/current versions can change)
    // therefore, we'll use the same platform info the whole time
    //
    bool pibSigGood = (AE_SUCCESS == pib_verify_signature(pibw));
    //
    // invalid pib is an error whenever it's provided
    //
    if (!pibSigGood) {
        AESM_DBG_ERROR("pib verify signature failed");
        return AESM_PLATFORM_INFO_BLOB_INVALID_SIG;
    }
    if(pibw.platform_info_blob.xeid != AESMLogic::get_active_extended_epid_group_id()){
        return AESM_UNEXPECTED_ERROR;
    }
    uint32_t gid_mt_result = AESMLogic::is_gid_matching_result_in_epid_blob( pibw.platform_info_blob.gid);
    if(AESMLogic::GIDMT_UNMATCHED == gid_mt_result||
        AESMLogic::GIDMT_UNEXPECTED_ERROR == gid_mt_result){
            return AESM_UNEXPECTED_ERROR;
    }
    else if (AESMLogic::GIDMT_NOT_AVAILABLE == gid_mt_result) {
            return AESM_EPIDBLOB_ERROR;
    }

    ae_error_t nepStatus = need_epid_provisioning(&pibw);
    AESM_DBG_TRACE("need_epid_provisioning return %d",nepStatus);
    switch (nepStatus)
    {
    case AESM_NEP_DONT_NEED_EPID_PROVISIONING:
        {
            break;
        }
    case AESM_NEP_DONT_NEED_UPDATE_PVEQE:       // sure thing
        {
            AESMLogicLock lock(AESMLogic::_qe_pve_mutex);
            if(!query_pve_thread_status()){//If another thread is busy on Epid Provisioning
                status = AESM_SUCCESS;
                break;
            }
            bool perfRekey = false;
            status = PvEAESMLogic::provision(perfRekey, THREAD_TIMEOUT);
            if (AESM_BUSY == status || //thread timeout
                AESM_PROXY_SETTING_ASSIST == status || //uae service need to set up proxy info and retry
                AESM_UPDATE_AVAILABLE == status || //PSW need be updated
                AESM_OUT_OF_EPC == status) // out of EPC
            {
                return status;//We should return to uae serivce directly
            }
            if (AESM_SUCCESS != status &&
                AESM_OUT_OF_MEMORY_ERROR != status &&
                AESM_BACKEND_SERVER_BUSY != status &&
                AESM_NETWORK_ERROR != status &&
                AESM_NETWORK_BUSY_ERROR != status)
            {
                status = AESM_SGX_PROVISION_FAILED;
            }
            break;
        }
    case AESM_NEP_PERFORMANCE_REKEY:
        {
            if (0 == attestation_status)           // pr only if we succeeded (also we'll never get pr unless gid up-to-date)
            {
                bool perfRekey = true;
                AESMLogicLock lock(AESMLogic::_qe_pve_mutex);
                if(!query_pve_thread_status()){//If another thread is busy on Epid Provisioning
                    status = AESM_SUCCESS;
                    break;
                }
                status = PvEAESMLogic::provision(perfRekey, THREAD_TIMEOUT);
                if (AESM_BUSY == status ||//thread timeout
                    AESM_PROXY_SETTING_ASSIST == status ||//uae service need to set up proxy info and retry
                    AESM_UPDATE_AVAILABLE == status ||
                    AESM_OUT_OF_EPC == status)
                {
                    return status;//We should return to uae serivce directly
                }
                if (AESM_SUCCESS != status &&
                    AESM_OUT_OF_MEMORY_ERROR != status &&
                    AESM_BACKEND_SERVER_BUSY != status &&
                    AESM_NETWORK_ERROR != status &&
                    AESM_NETWORK_BUSY_ERROR != status)
                {
                    status = AESM_SGX_PROVISION_FAILED;
                }
            }
            break;
        }
    default:
        {
            status = AESM_UNEXPECTED_ERROR;
            break;
        }
    }

    //
    // don't nag happy app about updates
    //
    if ((0 != attestation_status) && (NULL != update_info))
    {
        sgx_update_info_bit_t* p_update_info = (sgx_update_info_bit_t*)update_info;
        memset(p_update_info, 0, sizeof(*p_update_info));

        //
        // here, we treat values that get reported live - cpusvn, qe.isvsvn,
        // in normal flow, live values reported to attestation server will be the same as current values now so
        // we just look at out-of-date bits corresponding to these values.
        // the alternative would be to compare current with latest as reported by IAS. this
        // isn't an option for cpusvn since what we get from IAS is equivalent cpusvn.
        //
        
        
        if (cpu_svn_out_of_date(&pibw))
        {
            p_update_info->ucodeUpdate = 1;
            status = AESM_UPDATE_AVAILABLE;
        }
        if (qe_svn_out_of_date(&pibw) ||
            pce_svn_out_of_date(&pibw)
          )
        {
            p_update_info->pswUpdate = 1;
            status = AESM_UPDATE_AVAILABLE;
        }
    }
    return status;
}


