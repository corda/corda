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

#include <AEServicesProvider.h>
#include <AEServices.h>

#include <stdlib.h>
#include <AEInitQuoteRequest.h>
#include <AEInitQuoteResponse.h>

#include <AEGetQuoteRequest.h>
#include <AEGetQuoteResponse.h>

#include <AEGetLaunchTokenRequest.h>
#include <AEGetLaunchTokenResponse.h>

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
////////THE COMMON STUFF aka INTEGRATION with Linux API
#include <sgx_report.h>
#include <arch.h>
#include <sgx_urts.h>
#include <sgx_uae_service.h>

#include <oal/uae_oal_api.h>
#include <aesm_error.h>

#include <new>

#define TRY_CATCH_BAD_ALLOC(block) \
    try{ \
        block; \
    } \
    catch(std::bad_alloc& e) \
    { \
        *result = AESM_OUT_OF_MEMORY_ERROR; \
        return UAE_OAL_SUCCESS; \
    }

///////////////////////////////////////////////////////

// NOTE -> uAE works internally with milliseconds and cannot obtain a better resolution for timeout because 
// epoll_wait will get the timeout parameter in milliseconds

extern "C"
uae_oal_status_t oal_get_launch_token(const enclave_css_t* signature, const sgx_attributes_t* attribute, sgx_launch_token_t* launchToken, uint32_t timeout_usec, aesm_error_t *result)
{
    TRY_CATCH_BAD_ALLOC({
        AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
        if (servicesProvider == NULL)
            return UAE_OAL_ERROR_UNEXPECTED;


        AEGetLaunchTokenRequest getLaunchTokenRequest(sizeof(sgx_measurement_t),
            (const uint8_t*)signature->body.enclave_hash.m,
            sizeof(signature->key.modulus),
            (const uint8_t*)signature->key.modulus,
            sizeof(sgx_attributes_t),
            (const uint8_t*)attribute,
            timeout_usec/1000);

        AEGetLaunchTokenResponse getLaunchTokenResponse;
        uae_oal_status_t ret  = servicesProvider->InternalInterface(&getLaunchTokenRequest, &getLaunchTokenResponse, timeout_usec / 1000);
        if (ret == UAE_OAL_SUCCESS)
        {
            bool valid = getLaunchTokenResponse.GetValues((uint32_t*)result, (uint8_t*)launchToken, sizeof(sgx_launch_token_t));
            if (!valid)
                ret = UAE_OAL_ERROR_UNEXPECTED;
        }
        return ret;
    });
}

/*
   QUOTING
*/

extern "C"
uae_oal_status_t SGXAPI oal_init_quote(sgx_target_info_t *p_target_info, sgx_epid_group_id_t *p_gid, uint32_t timeout_usec, aesm_error_t *result)
{
    TRY_CATCH_BAD_ALLOC({
        AEServices *servicesProvider = AEServicesProvider::GetServicesProvider();
        if (servicesProvider == NULL)
            return UAE_OAL_ERROR_UNEXPECTED;

        AEInitQuoteRequest initQuoteRequest(timeout_usec / 1000);
        AEInitQuoteResponse initQuoteResponse;
        uae_oal_status_t ret  = servicesProvider->InternalInterface(&initQuoteRequest, &initQuoteResponse, timeout_usec / 1000);
        if (ret == UAE_OAL_SUCCESS)
        {
            bool valid = initQuoteResponse.GetValues((uint32_t*)result, sizeof(sgx_epid_group_id_t), (uint8_t*)p_gid, sizeof(sgx_target_info_t), (uint8_t*)p_target_info);
            if (!valid)
                ret = UAE_OAL_ERROR_UNEXPECTED;
        }
        return ret;
    });

}

    extern "C"
uae_oal_status_t SGXAPI oal_get_quote(
        const sgx_report_t *p_report,
    sgx_quote_sign_type_t quote_type,
    const sgx_spid_t *p_spid,
    const sgx_quote_nonce_t *p_nonce,
    const uint8_t *p_sig_rl,
    uint32_t sig_rl_size,
    sgx_report_t *p_qe_report,
    sgx_quote_t *p_quote,
    uint32_t quote_size, 
    uint32_t timeout_usec, 
    aesm_error_t *result)
{
    TRY_CATCH_BAD_ALLOC({
        AEServices *servicesProvider = AEServicesProvider::GetServicesProvider();
        if (servicesProvider == NULL)
            return UAE_OAL_ERROR_UNEXPECTED;
        AEGetQuoteRequest getQuoteRequest(sizeof(sgx_report_t), (const uint8_t*)p_report,
            (uint32_t)quote_type,
            sizeof(sgx_spid_t), (const uint8_t*)p_spid,
            sizeof(sgx_quote_nonce_t), (const uint8_t*)p_nonce,
            sig_rl_size, (const uint8_t*)p_sig_rl,
            quote_size,
            p_qe_report != NULL,
            timeout_usec / 1000);
        AEGetQuoteResponse getQuoteResponse;
        uae_oal_status_t ret = servicesProvider->InternalInterface(&getQuoteRequest, &getQuoteResponse, timeout_usec / 1000);
        if (ret == UAE_OAL_SUCCESS)
        {
            bool valid = getQuoteResponse.GetValues((uint32_t*)result, quote_size, (uint8_t*)p_quote, sizeof(sgx_report_t), (uint8_t*)p_qe_report);
            if (!valid)
                ret = UAE_OAL_ERROR_UNEXPECTED;
        }
        return ret;
    });
}


extern "C"
uae_oal_status_t SGXAPI oal_get_ps_cap(uint64_t* ps_cap, uint32_t timeout_usec, aesm_error_t *result)
{
    TRY_CATCH_BAD_ALLOC({
        AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
        if (servicesProvider == NULL)
            return UAE_OAL_ERROR_UNEXPECTED;

        AEGetPsCapRequest getPsCapRequest(timeout_usec/1000);

        AEGetPsCapResponse getPsCapResponse;
        uae_oal_status_t ret = servicesProvider->InternalInterface(&getPsCapRequest, &getPsCapResponse, timeout_usec / 1000);
        if (ret == UAE_OAL_SUCCESS)
        {
            bool valid = getPsCapResponse.GetValues((uint32_t*)result, ps_cap);
            if (!valid)
                ret = UAE_OAL_ERROR_UNEXPECTED;
        }
        return ret;
    });

}

extern "C"
uae_oal_status_t SGXAPI oal_report_attestation_status(
    const sgx_platform_info_t* platform_info,
    int attestation_error_code,
    sgx_update_info_bit_t* platform_update_info,
    uint32_t timeout_usec, 
    aesm_error_t *result)
{
    TRY_CATCH_BAD_ALLOC({
        AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
        if (servicesProvider == NULL)
            return UAE_OAL_ERROR_UNEXPECTED; 

        AEReportAttestationRequest reportAttestationRequest(sizeof(sgx_platform_info_t), (const uint8_t*)platform_info, (uint32_t)attestation_error_code, sizeof(sgx_update_info_bit_t), timeout_usec / 1000);

        AEReportAttestationResponse reportAttestationResponse;
        uae_oal_status_t ret = servicesProvider->InternalInterface(&reportAttestationRequest, &reportAttestationResponse, timeout_usec / 1000);
        if (ret == UAE_OAL_SUCCESS)
        {
            bool valid = reportAttestationResponse.GetValues((uint32_t*)result, sizeof(sgx_update_info_bit_t), (uint8_t*)platform_update_info);
            if (!valid)
                ret = UAE_OAL_ERROR_UNEXPECTED;
        }
        return ret;
    });
}

extern "C"
uae_oal_status_t oal_get_whitelist_size(uint32_t* white_list_size, uint32_t timeout_usec, aesm_error_t *result)
{
    TRY_CATCH_BAD_ALLOC({
        AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
        if (servicesProvider == NULL)
            return UAE_OAL_ERROR_UNEXPECTED;

        AEGetWhiteListSizeRequest getWhiteListSizeRequest(timeout_usec / 1000);

        AEGetWhiteListSizeResponse getWhiteListSizeResponse;
        uae_oal_status_t ret = servicesProvider->InternalInterface(&getWhiteListSizeRequest, &getWhiteListSizeResponse, timeout_usec / 1000);
        if (ret == UAE_OAL_SUCCESS)
        {
            bool valid = getWhiteListSizeResponse.GetValues((uint32_t*)result, white_list_size);
            if (!valid)
                ret = UAE_OAL_ERROR_UNEXPECTED;
        }
        return ret;
    });
}

extern "C"
uae_oal_status_t oal_get_whitelist(uint8_t *white_list, uint32_t white_list_size, uint32_t timeout_usec, aesm_error_t *result)
{
    TRY_CATCH_BAD_ALLOC({
        AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
        if (servicesProvider == NULL)
            return UAE_OAL_ERROR_UNEXPECTED;

        AEGetWhiteListRequest getWhiteListRequest(white_list_size, timeout_usec / 1000);

        AEGetWhiteListResponse getWhiteListResponse;
        uae_oal_status_t ret = servicesProvider->InternalInterface(&getWhiteListRequest, &getWhiteListResponse, timeout_usec / 1000);
        if (ret == UAE_OAL_SUCCESS)
        {
            bool valid = getWhiteListResponse.GetValues((uint32_t*)result, white_list_size, white_list);
            if (!valid)
                ret = UAE_OAL_ERROR_UNEXPECTED;
        }
        return ret;
    });
}

extern "C"
uae_oal_status_t oal_get_extended_epid_group_id(uint32_t* extended_group_id, uint32_t timeout_usec, aesm_error_t *result)
{
    TRY_CATCH_BAD_ALLOC({
        AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
        if (servicesProvider == NULL)
            return UAE_OAL_ERROR_UNEXPECTED;

        AESGXGetExtendedEpidGroupIdRequest getExtendedEpidGroupIdRequest(timeout_usec / 1000);

        AESGXGetExtendedEpidGroupIdResponse getExtendedEpidGroupIdResponse;
        uae_oal_status_t ret = servicesProvider->InternalInterface(&getExtendedEpidGroupIdRequest, &getExtendedEpidGroupIdResponse, timeout_usec / 1000);
        if (ret == UAE_OAL_SUCCESS)
        {
            bool valid = getExtendedEpidGroupIdResponse.GetValues((uint32_t*)result, extended_group_id);
            if (!valid)
                ret = UAE_OAL_ERROR_UNEXPECTED;
        }
        return ret;
    });
}

extern "C"
uae_oal_status_t oal_switch_extended_epid_group(uint32_t x_group_id, uint32_t timeout_usec, aesm_error_t *result)
{
    TRY_CATCH_BAD_ALLOC({
        AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
        if (servicesProvider == NULL)
            return UAE_OAL_ERROR_UNEXPECTED;

        AESGXSwitchExtendedEpidGroupRequest switchExtendedEpidGroupRequest(x_group_id, timeout_usec / 1000);

        AESGXSwitchExtendedEpidGroupResponse switchExtendedEpidGroupResponse;
        uae_oal_status_t ret = servicesProvider->InternalInterface(&switchExtendedEpidGroupRequest, &switchExtendedEpidGroupResponse, timeout_usec / 1000);
        if (ret == UAE_OAL_SUCCESS)
        {
            bool valid = switchExtendedEpidGroupResponse.GetValues((uint32_t*)result);
            if (!valid)
                ret = UAE_OAL_ERROR_UNEXPECTED;
        }
        return ret;
    });
}


