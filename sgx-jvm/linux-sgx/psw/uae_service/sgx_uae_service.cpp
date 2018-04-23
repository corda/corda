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
#include <oal/uae_oal_api.h>
#include <aesm_error.h>
#include "sgx_uae_service.h"
#include "uae_service_internal.h"
#include "config.h"

#include "stdint.h"
#include "se_sig_rl.h"

#if !defined(ntohl)
#define ntohl(u32)                                      \
  ((uint32_t)(((((const unsigned char*)&(u32))[0]) << 24)     \
            + ((((const unsigned char*)&(u32))[1]) << 16)     \
            + ((((const unsigned char*)&(u32))[2]) << 8)      \
            + (((const unsigned char*)&(u32))[3])))
#endif


#define GET_LAUNCH_TOKEN_TIMEOUT_MSEC (IPC_LATENCY)
#define SE_INIT_QUOTE_TIMEOUT_MSEC (IPC_LATENCY)
//add 3 millisecond per sig_rl entry
#define SE_GET_QUOTE_TIMEOUT_MSEC(p_sig_rl) (IPC_LATENCY + ((p_sig_rl) ? 3*ntohl(((const se_sig_rl_t*)p_sig_rl)->sig_rl.n2) : 0))
#define SE_GET_PS_CAP_TIMEOUT_MSEC (IPC_LATENCY)
#define SE_REPORT_REMOTE_ATTESTATION_FAILURE_TIMEOUT_MSEC  (IPC_LATENCY)

#define GET_WHITE_LIST_SIZE_MSEC (IPC_LATENCY)
#define GET_WHITE_LIST_MSEC (IPC_LATENCY)
#define SGX_GET_EXTENDED_GROUP_ID_MSEC (IPC_LATENCY)
#define SGX_SWITCH_EXTENDED_GROUP_MSEC (IPC_LATENCY)
extern "C" {

sgx_status_t get_launch_token(
    const enclave_css_t*        signature, 
    const sgx_attributes_t*     attribute, 
    sgx_launch_token_t*         launch_token)
{
    if (signature == NULL || attribute == NULL || launch_token == NULL)
        return SGX_ERROR_INVALID_PARAMETER;
    
    aesm_error_t    result = AESM_UNEXPECTED_ERROR;
    uae_oal_status_t status = oal_get_launch_token(signature, attribute, launch_token, GET_LAUNCH_TOKEN_TIMEOUT_MSEC*1000, &result);
    
    /*common mappings */
    sgx_status_t mapped = oal_map_status(status);
    if (mapped != SGX_SUCCESS)
        return mapped;

    mapped = oal_map_result(result);
    if (mapped != SGX_SUCCESS)
    {
        /*operation specific mapping */
        if (mapped == SGX_ERROR_UNEXPECTED && result != AESM_UNEXPECTED_ERROR)
        {
            switch (result)
            {
                case AESM_NO_DEVICE_ERROR:
                    mapped = SGX_ERROR_NO_DEVICE;
                    break;
                case AESM_GET_LICENSETOKEN_ERROR:
                    mapped = SGX_ERROR_SERVICE_INVALID_PRIVILEGE;
                    break;
                case AESM_OUT_OF_EPC:
                    mapped = SGX_ERROR_OUT_OF_EPC;
                    break;
                default:
                    mapped = SGX_ERROR_UNEXPECTED;
            }
        }
    } 

    return mapped;
}

sgx_status_t sgx_init_quote(
    sgx_target_info_t       *p_target_info,
    sgx_epid_group_id_t     *p_gid)
{
    if (p_target_info == NULL || p_gid == NULL)
        return SGX_ERROR_INVALID_PARAMETER;

    aesm_error_t    result = AESM_UNEXPECTED_ERROR;

    uae_oal_status_t status = oal_init_quote(p_target_info, p_gid, SE_INIT_QUOTE_TIMEOUT_MSEC*1000, &result);
   
    sgx_status_t mapped = oal_map_status(status); 
    if (mapped != SGX_SUCCESS)
        return mapped;

    mapped = oal_map_result(result);
    if (mapped != SGX_SUCCESS)                                 
    {
        /*operation specific mapping */
        if (mapped == SGX_ERROR_UNEXPECTED && result != AESM_UNEXPECTED_ERROR)
        {
            switch (result)
            {
                case AESM_EPIDBLOB_ERROR:
                    mapped = SGX_ERROR_AE_INVALID_EPIDBLOB;
                    break;
                case AESM_EPID_REVOKED_ERROR:
                    mapped = SGX_ERROR_EPID_MEMBER_REVOKED;
                    break;
                case AESM_BACKEND_SERVER_BUSY:
                    mapped = SGX_ERROR_BUSY;
                    break;
                case AESM_SGX_PROVISION_FAILED:
                    mapped = SGX_ERROR_UNEXPECTED;
                    break;
                case AESM_OUT_OF_EPC:
                    mapped = SGX_ERROR_OUT_OF_EPC;
                    break;
                default:
                    mapped = SGX_ERROR_UNEXPECTED;
            }
        }
    }

    return mapped;
}


sgx_status_t sgx_get_quote(
    const sgx_report_t      *p_report,
    sgx_quote_sign_type_t   quote_type,
    const sgx_spid_t        *p_spid,
    const sgx_quote_nonce_t *p_nonce,
    const uint8_t           *p_sig_rl,
    uint32_t                sig_rl_size,
    sgx_report_t            *p_qe_report,
    sgx_quote_t             *p_quote,
    uint32_t                quote_size)
{
    
    if (p_report == NULL || p_spid == NULL || p_quote == NULL || quote_size == 0 )
        return SGX_ERROR_INVALID_PARAMETER;
    if ((p_sig_rl == NULL && sig_rl_size != 0) ||
        (p_sig_rl != NULL && sig_rl_size == 0) )
        return SGX_ERROR_INVALID_PARAMETER;

    aesm_error_t    result = AESM_UNEXPECTED_ERROR;

    uae_oal_status_t status = oal_get_quote(p_report, quote_type, p_spid, p_nonce, p_sig_rl, sig_rl_size, p_qe_report, 
                                            p_quote, quote_size, SE_GET_QUOTE_TIMEOUT_MSEC(p_sig_rl)*1000, &result);

    sgx_status_t mapped = oal_map_status(status);
    if (mapped != SGX_SUCCESS)
        return mapped;
    
    mapped = oal_map_result(result);
    if (mapped != SGX_SUCCESS)
    {
        /*operation specific mapping */
        if (mapped == SGX_ERROR_UNEXPECTED && result != AESM_UNEXPECTED_ERROR)
        {
            switch (result) 
            {
                case AESM_EPIDBLOB_ERROR:
                    mapped = SGX_ERROR_AE_INVALID_EPIDBLOB;
                    break;
                case AESM_EPID_REVOKED_ERROR:
                    mapped = SGX_ERROR_EPID_MEMBER_REVOKED;
                    break;
                case AESM_BACKEND_SERVER_BUSY:
                    mapped = SGX_ERROR_BUSY;
                    break;
                case AESM_SGX_PROVISION_FAILED:
                    mapped = SGX_ERROR_UNEXPECTED;
                    break;
                case AESM_OUT_OF_EPC:
                    mapped = SGX_ERROR_OUT_OF_EPC;
                    break;
                default:
                    mapped = SGX_ERROR_UNEXPECTED;
            }
        }
    }

    return mapped;

}

sgx_status_t sgx_get_ps_cap(sgx_ps_cap_t* p_sgx_ps_cap)
{
    if (p_sgx_ps_cap == NULL)
        return SGX_ERROR_INVALID_PARAMETER;

    aesm_error_t    result = AESM_UNEXPECTED_ERROR;
    uint64_t ps_cap = 0;

    uae_oal_status_t status = oal_get_ps_cap(&ps_cap, SE_GET_PS_CAP_TIMEOUT_MSEC*1000, &result);
    p_sgx_ps_cap->ps_cap0 = (uint32_t)ps_cap;
    p_sgx_ps_cap->ps_cap1 = (uint32_t)(ps_cap >> 32);

    sgx_status_t mapped = oal_map_status(status);
    if (mapped != SGX_SUCCESS)
        return mapped;
    
    mapped = oal_map_result(result);
    if (mapped != SGX_SUCCESS)
    {
        /*operation specific mapping */
        if (mapped == SGX_ERROR_UNEXPECTED && result != AESM_UNEXPECTED_ERROR)
        {
            switch (result) 
            {
            case AESM_LONG_TERM_PAIRING_FAILED:
            case AESM_EPH_SESSION_FAILED:
            case AESM_PSDA_UNAVAILABLE:
                mapped = SGX_ERROR_SERVICE_UNAVAILABLE;
                break;
            default:
                    mapped = SGX_ERROR_UNEXPECTED;
            }
        }
    }

    return mapped;

}

sgx_status_t sgx_report_attestation_status(
    const sgx_platform_info_t*  p_platform_info,
    int                         attestation_status,
    sgx_update_info_bit_t*          p_update_info)
{
    if (p_platform_info == NULL || p_update_info == NULL)
        return SGX_ERROR_INVALID_PARAMETER;

    aesm_error_t    result = AESM_UNEXPECTED_ERROR;

    uae_oal_status_t status = oal_report_attestation_status(p_platform_info, attestation_status, p_update_info, SE_REPORT_REMOTE_ATTESTATION_FAILURE_TIMEOUT_MSEC*1000, &result);

    sgx_status_t mapped = oal_map_status(status);
    if (mapped != SGX_SUCCESS)
        return mapped;

    mapped = oal_map_result(result);
    if (mapped != SGX_SUCCESS)
    {
        /*operation specific mapping */
        if (mapped == SGX_ERROR_UNEXPECTED && result != AESM_UNEXPECTED_ERROR)
        {
            switch (result)
            {
                case AESM_BACKEND_SERVER_BUSY:
                    mapped = SGX_ERROR_BUSY;
                    break;
                case AESM_PLATFORM_INFO_BLOB_INVALID_SIG:
                    mapped = SGX_ERROR_INVALID_PARAMETER;
                    break;
                case AESM_EPIDBLOB_ERROR:
                    mapped = SGX_ERROR_AE_INVALID_EPIDBLOB;
                    break;
                case AESM_OUT_OF_EPC:
                    mapped = SGX_ERROR_OUT_OF_EPC;
                    break;
                case AESM_SGX_PROVISION_FAILED:
                default:
                    mapped = SGX_ERROR_UNEXPECTED;
            }
        }
    }

    return mapped;
}

sgx_status_t create_session_ocall(
        uint32_t        *session_id,
    uint8_t         *se_dh_msg1,
    uint32_t        dh_msg1_size,
    uint32_t timeout)
{
    
    if(!session_id || !se_dh_msg1)
        return SGX_ERROR_INVALID_PARAMETER;
    aesm_error_t    result = AESM_UNEXPECTED_ERROR;
    uae_oal_status_t status = oal_create_session(session_id, se_dh_msg1, dh_msg1_size, timeout*1000, &result);
    
    sgx_status_t mapped = oal_map_status(status);
    if (mapped != SGX_SUCCESS)
        return mapped;
    
    mapped = oal_map_result(result);
    if (mapped != SGX_SUCCESS)
    {
        /*operation specific mapping */
        if (mapped == SGX_ERROR_UNEXPECTED && result != AESM_UNEXPECTED_ERROR)
        {
            switch (result)
            {
                case AESM_MAX_NUM_SESSION_REACHED:
                    mapped = SGX_ERROR_BUSY;
                    break;
                case AESM_EPH_SESSION_FAILED:
                case AESM_LONG_TERM_PAIRING_FAILED:
                case AESM_PSDA_UNAVAILABLE:
                case AESM_SERVICE_NOT_AVAILABLE:
                    mapped = SGX_ERROR_SERVICE_UNAVAILABLE;
                    break;
                case AESM_OUT_OF_EPC:
                    mapped = SGX_ERROR_OUT_OF_EPC;
                    break;
                case AESM_MSG_ERROR:
                default:
                    mapped = SGX_ERROR_UNEXPECTED;
            }
        }
    }

    return mapped;
}

sgx_status_t exchange_report_ocall(
    uint32_t        session_id,
    const uint8_t   *se_dh_msg2,
    uint32_t        dh_msg2_size,
    uint8_t         *se_dh_msg3,
    uint32_t        dh_msg3_size,
    uint32_t        timeout)
{
    if (!se_dh_msg2 || !se_dh_msg3)
        return SGX_ERROR_INVALID_PARAMETER;

    aesm_error_t    result = AESM_UNEXPECTED_ERROR;
    uae_oal_status_t status = oal_exchange_report(session_id, se_dh_msg2, dh_msg2_size, se_dh_msg3, dh_msg3_size, timeout*1000, &result);

    sgx_status_t mapped = oal_map_status(status);
    if (mapped != SGX_SUCCESS)
        return mapped;

    mapped = oal_map_result(result);
    if (mapped != SGX_SUCCESS)
    {
        /*operation specific mapping */
        if (mapped == SGX_ERROR_UNEXPECTED && result != AESM_UNEXPECTED_ERROR)
        {
            switch (result)
            {
                case AESM_SESSION_INVALID:
                    mapped = SGX_ERROR_AE_SESSION_INVALID;
                    break;
                case AESM_KDF_MISMATCH:
                    mapped = SGX_ERROR_KDF_MISMATCH;
                    break;
                case AESM_EPH_SESSION_FAILED:
                case AESM_LONG_TERM_PAIRING_FAILED:
                case AESM_PSDA_UNAVAILABLE:
                case AESM_SERVICE_NOT_AVAILABLE:
                    mapped = SGX_ERROR_SERVICE_UNAVAILABLE;
                    break;
                case AESM_OUT_OF_EPC:
                    mapped = SGX_ERROR_OUT_OF_EPC;
                    break;
                default:
                    mapped = SGX_ERROR_UNEXPECTED;
            }
        }
    }

    return mapped;

}

sgx_status_t close_session_ocall(
    uint32_t        session_id,
    uint32_t        timeout)
{
    aesm_error_t    result = AESM_UNEXPECTED_ERROR;
    uae_oal_status_t status = oal_close_session(session_id, timeout*1000, &result);

    sgx_status_t mapped = oal_map_status(status);
    if (mapped != SGX_SUCCESS)
        return mapped;
    
    mapped = oal_map_result(result);
    if (mapped != SGX_SUCCESS)
    {
        /*operation specific mapping */
        if (mapped == SGX_ERROR_UNEXPECTED && result != AESM_UNEXPECTED_ERROR)
        {
            switch (result)
            {
                case AESM_SESSION_INVALID:
                    mapped = SGX_ERROR_AE_SESSION_INVALID;
                    break;
                case AESM_EPH_SESSION_FAILED:
                case AESM_LONG_TERM_PAIRING_FAILED:
                case AESM_SERVICE_NOT_AVAILABLE:
                    mapped = SGX_ERROR_SERVICE_UNAVAILABLE;
                    break;
                case AESM_OUT_OF_EPC:
                    mapped = SGX_ERROR_OUT_OF_EPC;
                    break;
                default:
                    mapped = SGX_ERROR_UNEXPECTED;
            }       
        }
    }
        
    return mapped;
}

sgx_status_t invoke_service_ocall(
    const uint8_t   *pse_message_req,
    uint32_t        pse_message_req_size,
    uint8_t         *pse_message_resp,
    uint32_t        pse_message_resp_size,
    uint32_t        timeout)
{
    if (pse_message_req == NULL || pse_message_resp == NULL)
        return SGX_ERROR_INVALID_PARAMETER;

    aesm_error_t    result = AESM_UNEXPECTED_ERROR;
    uae_oal_status_t status = oal_invoke_service(pse_message_req, pse_message_req_size, pse_message_resp, pse_message_resp_size, timeout*1000, &result);

    sgx_status_t mapped = oal_map_status(status);
    if (mapped != SGX_SUCCESS)
        return mapped;
    
    mapped = oal_map_result(result);
    if (mapped != SGX_SUCCESS)
    {
        /*operation specific mapping */
        if (mapped == SGX_ERROR_UNEXPECTED && result != AESM_UNEXPECTED_ERROR)
        {
            switch (result)
            {
                case AESM_SESSION_INVALID:
                    mapped = SGX_ERROR_AE_SESSION_INVALID;
                    break;
                case AESM_EPH_SESSION_FAILED:
                case AESM_LONG_TERM_PAIRING_FAILED:
                case AESM_PSDA_UNAVAILABLE:
                case AESM_SERVICE_NOT_AVAILABLE:
                    mapped = SGX_ERROR_SERVICE_UNAVAILABLE;
                    break;
                case AESM_OUT_OF_EPC:
                    mapped = SGX_ERROR_OUT_OF_EPC;
                    break;
                case AESM_MSG_ERROR:
                default:
                    mapped = SGX_ERROR_UNEXPECTED;
            }       
        }
    }
        
    return mapped;
}


sgx_status_t sgx_get_whitelist_size(
    uint32_t* p_whitelist_size)
{
    if (p_whitelist_size == NULL)
        return SGX_ERROR_INVALID_PARAMETER;

    aesm_error_t    result = AESM_UNEXPECTED_ERROR;
    uae_oal_status_t ret = UAE_OAL_ERROR_UNEXPECTED;
    ret = oal_get_whitelist_size(p_whitelist_size, GET_WHITE_LIST_SIZE_MSEC*1000, &result);

    //common mappings
    sgx_status_t mapped = oal_map_status(ret);
    if (mapped != SGX_SUCCESS)
        return mapped;

    mapped = oal_map_result(result);
    if (mapped != SGX_SUCCESS)
    {
        //operation specific mapping
        if (mapped == SGX_ERROR_UNEXPECTED && result != AESM_UNEXPECTED_ERROR)
        {
            switch (result)
            {
            default:
                mapped = SGX_ERROR_UNEXPECTED;
            }
        }
    }

    return mapped;
}


sgx_status_t sgx_get_whitelist(
    uint8_t* p_whitelist,
    uint32_t whitelist_size)
{
    if (p_whitelist == NULL || whitelist_size == 0)
        return SGX_ERROR_INVALID_PARAMETER;

    aesm_error_t    result = AESM_UNEXPECTED_ERROR;
    uae_oal_status_t ret = UAE_OAL_ERROR_UNEXPECTED;

    ret = oal_get_whitelist(p_whitelist, whitelist_size, GET_WHITE_LIST_MSEC*1000, &result);

    //common mappings
    sgx_status_t mapped = oal_map_status(ret);
    if (mapped != SGX_SUCCESS)
        return mapped;

    mapped = oal_map_result(result);
    if (mapped != SGX_SUCCESS)
    {
        //operation specific mapping
        if (mapped == SGX_ERROR_UNEXPECTED && result != AESM_UNEXPECTED_ERROR)
        {
            switch (result)
            {
            default:
                mapped = SGX_ERROR_UNEXPECTED;
            }
        }
    }

    return mapped;
}

sgx_status_t sgx_get_extended_epid_group_id(
    uint32_t* p_extended_epid_group_id)
{
    if (p_extended_epid_group_id == NULL)
        return SGX_ERROR_INVALID_PARAMETER;

    aesm_error_t    result = AESM_UNEXPECTED_ERROR;
    uae_oal_status_t ret = UAE_OAL_ERROR_UNEXPECTED;
    ret = oal_get_extended_epid_group_id(p_extended_epid_group_id, SGX_GET_EXTENDED_GROUP_ID_MSEC*1000, &result);

    //common mappings 
    sgx_status_t mapped = oal_map_status(ret);
    if (mapped != SGX_SUCCESS)
        return mapped;

    mapped = oal_map_result(result);
    if (mapped != SGX_SUCCESS)
    {
        //operation specific mapping
        if (mapped == SGX_ERROR_UNEXPECTED && result != AESM_UNEXPECTED_ERROR)
        {
            switch (result)
            {
            default:
                mapped = SGX_ERROR_UNEXPECTED;
            }
        }
    }
    return mapped;
}

sgx_status_t sgx_switch_extended_epid_group(uint32_t extended_epid_group_id)
{
    aesm_error_t    result = AESM_UNEXPECTED_ERROR;
    uae_oal_status_t ret = UAE_OAL_ERROR_UNEXPECTED;
    ret = oal_switch_extended_epid_group(extended_epid_group_id, SGX_SWITCH_EXTENDED_GROUP_MSEC*1000, &result);

    //common mappings 
    sgx_status_t mapped = oal_map_status(ret);
    if (mapped != SGX_SUCCESS)
        return mapped;

    mapped = oal_map_result(result);
    if (mapped != SGX_SUCCESS)
    {
        //operation specific mapping
        if (mapped == SGX_ERROR_UNEXPECTED && result != AESM_UNEXPECTED_ERROR)
        {
            switch (result)
            {
            default:
                mapped = SGX_ERROR_UNEXPECTED;
            }
        }
    }
    return mapped;
}

// common mapper function for all OAL specific error codes

sgx_status_t    oal_map_status(uae_oal_status_t status)
{
    sgx_status_t retVal;

    switch (status)
    {
        case UAE_OAL_SUCCESS:
            retVal = SGX_SUCCESS;
            break;
        case UAE_OAL_ERROR_UNEXPECTED:
            retVal = SGX_ERROR_UNEXPECTED;
            break;
        case UAE_OAL_ERROR_AESM_UNAVAILABLE:
            retVal = SGX_ERROR_SERVICE_UNAVAILABLE;
            break;
        case UAE_OAL_ERROR_TIMEOUT:
            retVal = SGX_ERROR_SERVICE_TIMEOUT;
            break;
        case UAE_OAL_ERROR_INVALID:
            retVal = SGX_ERROR_INVALID_PARAMETER;
            break;
        default:
            retVal = SGX_ERROR_UNEXPECTED;
    }

    return retVal;
}

sgx_status_t    oal_map_result(aesm_error_t result)
{
    sgx_status_t retVal = SGX_ERROR_UNEXPECTED;

    switch (result)
    {
        case AESM_SUCCESS:
            retVal = SGX_SUCCESS;
            break;
        case AESM_UPDATE_AVAILABLE:
            retVal = SGX_ERROR_UPDATE_NEEDED;
            break;
        case AESM_UNEXPECTED_ERROR:
            retVal = SGX_ERROR_UNEXPECTED;
            break;
        case AESM_PARAMETER_ERROR:
            retVal = SGX_ERROR_INVALID_PARAMETER;
            break;
        case AESM_SERVICE_STOPPED:
        case AESM_SERVICE_UNAVAILABLE:
            retVal = SGX_ERROR_SERVICE_UNAVAILABLE;
            break;
        case AESM_OUT_OF_MEMORY_ERROR:
            retVal = SGX_ERROR_OUT_OF_MEMORY;
            break;
        case AESM_BUSY:
            retVal = SGX_ERROR_BUSY;
            break;
        case AESM_UNRECOGNIZED_PLATFORM:
            retVal = SGX_ERROR_UNRECOGNIZED_PLATFORM;
            break;
        case AESM_NETWORK_ERROR:
        case AESM_NETWORK_BUSY_ERROR:
        case AESM_PROXY_SETTING_ASSIST:
            retVal = SGX_ERROR_NETWORK_FAILURE;
            break;
        case AESM_NO_DEVICE_ERROR:
            retVal = SGX_ERROR_NO_DEVICE;
            break;
        default:
            retVal = SGX_ERROR_UNEXPECTED;
    }

    return retVal;

}

} /* extern "C" */
