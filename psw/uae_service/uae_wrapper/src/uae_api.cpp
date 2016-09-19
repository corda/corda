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
#include <AEInternalServicesProvider.h>
#include <AEInternalServices.h>

#include <AEServicesProvider.h>
#include <AEServices.h>

#include <stdlib.h>

////////THE COMMON STUFF aka INTEGRATION with Linux API
#include <sgx_report.h>
#include <arch.h>
#include <sgx_urts.h>
#include <sgx_uae_service.h>

#include <Config.h>

#include <oal/uae_oal_api.h>
#include <aesm_error.h>

///////////////////////////////////////////////////////

// NOTE -> uAE works internally with milliseconds and cannot obtain a better resolution for timeout because 
// epoll_wait will get the timeout parameter in milliseconds

extern "C"
uae_oal_status_t oal_get_launch_token(const enclave_css_t* signature, const sgx_attributes_t* attribute, sgx_launch_token_t* launchToken, uint32_t timeout_usec, aesm_error_t *result)
{
    AEInternalServices* servicesProvider = AEInternalServicesProvider::GetInternalServicesProvider();
    if (servicesProvider == NULL)
        return UAE_OAL_ERROR_UNEXPECTED;

    EnclaveMeasurement* mrenclave = new EnclaveMeasurement;
    SEAttributes* attr = new SEAttributes;
    Signature* mrsigner = new Signature;

    mrenclave->data = new uint8_t[sizeof(sgx_measurement_t)];
    mrenclave->length = sizeof(sgx_measurement_t);
    memcpy(mrenclave->data, &signature->body.enclave_hash, mrenclave->length);

    attr->data = new uint8_t[sizeof(sgx_attributes_t)];
    attr->length = sizeof(sgx_attributes_t);
    memcpy(attr->data, attribute, attr->length);

    mrsigner->data = new uint8_t[sizeof(signature->key.modulus)];
    mrsigner->length = sizeof(signature->key.modulus);
    memcpy(mrsigner->data, &signature->key.modulus, mrsigner->length);

    LaunchToken* token = servicesProvider->GetLaunchToken(mrenclave, mrsigner, attr, timeout_usec / 1000);

    uae_oal_status_t ret = UAE_OAL_ERROR_UNEXPECTED;
    if (token != NULL)
    {
        *result = (aesm_error_t)token->errorCode;
        ret = token->uaeStatus;

        if (*result == AESM_SUCCESS)
            memcpy(launchToken, token->data, token->length);
    }

    delete mrenclave;
    delete mrsigner;
    delete attr;
    delete token;

    return ret;
}

/*
   QUOTING
*/

extern "C"
uae_oal_status_t SGXAPI oal_init_quote(sgx_target_info_t *p_target_info, sgx_epid_group_id_t *p_gid, uint32_t timeout_usec, aesm_error_t *result)
{
    AEServices *servicesProvider = AEServicesProvider::GetServicesProvider();
    if (servicesProvider == NULL)
        return UAE_OAL_ERROR_UNEXPECTED;

    AttestationInformation* attestationInfo = servicesProvider->InitQuote(timeout_usec / 1000);

    uae_oal_status_t ret = UAE_OAL_ERROR_UNEXPECTED; 
    if (attestationInfo != NULL)
    {
        *result = (aesm_error_t)attestationInfo->errorCode;
        ret = attestationInfo->uaeStatus;

        if (*result == AESM_SUCCESS)
        {
            if (p_target_info != NULL)
                memcpy(p_target_info, attestationInfo->quotingTarget->data, attestationInfo->quotingTarget->length);
            if (p_gid != NULL)
                memcpy(p_gid, attestationInfo->platformGID->data, attestationInfo->platformGID->length);
        }
        delete attestationInfo;
    }
    return ret;
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
    
    if (quote_size > MAX_MEMORY_ALLOCATION)
    {
        *result = AESM_PARAMETER_ERROR;
        return UAE_OAL_SUCCESS;
    }

    AEServices *servicesProvider = AEServicesProvider::GetServicesProvider();
    if (servicesProvider == NULL)
        return UAE_OAL_ERROR_UNEXPECTED;

    Report* l_report = new Report;
    SPID* l_spid = new SPID;
    Nonce* l_nonce = new Nonce;
    SignatureRevocationList* l_sigRL = new SignatureRevocationList;

    l_report->length = sizeof(sgx_report_t);
    l_report->data = new uint8_t[sizeof(sgx_report_t)];
    memcpy(l_report->data, p_report, l_report->length);

    l_spid->length = sizeof(sgx_spid_t);
    l_spid->data = new uint8_t[sizeof(sgx_spid_t)];
    memcpy(l_spid->data, p_spid, l_spid->length);

    if (p_nonce != NULL)
    {
        l_nonce->length = sizeof(sgx_quote_nonce_t);
        l_nonce->data = new uint8_t[sizeof(sgx_quote_nonce_t)];
        memcpy(l_nonce->data, p_nonce, l_nonce->length);
    }

    if (p_sig_rl != NULL && sig_rl_size > 0)
    {
        l_sigRL->length = sig_rl_size;
        l_sigRL->data = new uint8_t[sig_rl_size];
        memcpy(l_sigRL->data, p_sig_rl, l_sigRL->length);
    }

    bool getQEReport = false;
    if (p_qe_report != NULL)
        getQEReport = true;

    QuoteInfo* quoteInfo = servicesProvider->GetQuote(l_report, quote_type, l_spid, l_nonce, l_sigRL, quote_size, getQEReport, timeout_usec / 1000);

    uae_oal_status_t ret = UAE_OAL_ERROR_UNEXPECTED; 

    if (quoteInfo != NULL)
    {
        ret = quoteInfo->uaeStatus;
        *result = (aesm_error_t)quoteInfo->errorCode;

        if (*result == AESM_SUCCESS)
        { 
            Quote* quote = quoteInfo->quote;
            if (quote->data != NULL)
                memcpy(p_quote, quote->data, quote->length);
            //do we have a qe_report?
            Report* qeReport = quoteInfo->qeReport;
            if (qeReport != NULL)
            {
                if (p_qe_report == NULL)
                {
                    ret = UAE_OAL_ERROR_UNEXPECTED;
                }
                else
                {
                    memcpy(p_qe_report, qeReport->data, qeReport->length);
                }
            }
        }

        delete quoteInfo;
    }

    //cleanup
    delete l_report;
    delete l_spid;
    delete l_nonce;
    delete l_sigRL;

    return ret; 
}


extern "C"
uae_oal_status_t SGXAPI oal_get_ps_cap(uint64_t* ps_cap, uint32_t timeout_usec, aesm_error_t *result)
{
    AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
    if (servicesProvider == NULL)
        return UAE_OAL_ERROR_UNEXPECTED;

    PsCap* cap = servicesProvider->GetPsCap(timeout_usec / 1000);

    uae_oal_status_t ret = UAE_OAL_ERROR_UNEXPECTED;
    if (cap != NULL)
    {
        ret = cap->uaeStatus;
        *result = (aesm_error_t)cap->errorCode;

        if (*result == AESM_SUCCESS)
        {
            *ps_cap = cap->ps_cap;
        }
    }
    delete cap;
    return ret;
}

extern "C"
uae_oal_status_t SGXAPI oal_report_attestation_status(
    const sgx_platform_info_t* platform_info,
    int attestation_error_code,
    sgx_update_info_bit_t* platform_update_info,
    uint32_t timeout_usec, 
    aesm_error_t *result)
{
    AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
    if (servicesProvider == NULL)
        return UAE_OAL_ERROR_UNEXPECTED; 


    PlatformInfo* platformInfo = new PlatformInfo;

    platformInfo->data = new uint8_t[sizeof(sgx_platform_info_t)];
    platformInfo->length = sizeof(sgx_platform_info_t);
    memcpy(platformInfo->data, platform_info, platformInfo->length);

    AttestationStatus* attestationStatus = servicesProvider->ReportAttestationError(platformInfo, attestation_error_code,sizeof(sgx_update_info_bit_t), timeout_usec / 1000);

    uae_oal_status_t ret = UAE_OAL_ERROR_UNEXPECTED;     
    if (attestationStatus != NULL)
    {
        *result = (aesm_error_t)attestationStatus->errorCode;
        ret = attestationStatus->uaeStatus;     

        if (*result == AESM_SUCCESS)
            memcpy(platform_update_info, attestationStatus->updateInfo->data, attestationStatus->updateInfo->length);
    }

    delete attestationStatus;
    delete platformInfo;
    return ret;
}

extern "C"
uae_oal_status_t oal_get_whitelist_size(uint32_t* white_list_size, uint32_t timeout_usec, aesm_error_t *result)
{
    AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
    if (servicesProvider == NULL)
        return UAE_OAL_ERROR_UNEXPECTED;

    WhiteListSize* whiteListSize = servicesProvider->GetWhiteListSize(timeout_usec / 1000);

    uae_oal_status_t ret = UAE_OAL_ERROR_UNEXPECTED;
    if (whiteListSize != NULL)
    {
        ret = whiteListSize->uaeStatus;
        *result = (aesm_error_t)whiteListSize->errorCode;

        if (*result == AESM_SUCCESS)
        {
            *white_list_size = whiteListSize->white_list_size;
        }
    }
    delete whiteListSize;
    return ret;
}

extern "C"
uae_oal_status_t oal_get_whitelist(uint8_t *white_list, uint32_t white_list_size, uint32_t timeout_usec, aesm_error_t *result)
{
    AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
    if (servicesProvider == NULL)
        return UAE_OAL_ERROR_UNEXPECTED;

    WhiteList* whiteList = servicesProvider->GetWhiteList(white_list_size, timeout_usec / 1000);

    uae_oal_status_t ret = UAE_OAL_ERROR_UNEXPECTED;
    if (whiteList != NULL)
    {
        ret = whiteList->uaeStatus;
        *result = (aesm_error_t)whiteList->errorCode;

        if (*result == AESM_SUCCESS)
        {           
            memcpy(white_list,whiteList->data,whiteList->length);
        }
    }
    delete whiteList;
    return ret;
}

extern "C"
uae_oal_status_t oal_get_extended_epid_group_id(uint32_t* extended_group_id, uint32_t timeout_usec, aesm_error_t *result)
{
    AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
    if (servicesProvider == NULL)
        return UAE_OAL_ERROR_UNEXPECTED;

    ExtendedEpidGroupId* extendedGroupId = servicesProvider->SGXGetExtendedEpidGroupId(timeout_usec / 1000);

    uae_oal_status_t ret = UAE_OAL_ERROR_UNEXPECTED;
    if (extendedGroupId != NULL)
    {
        ret = extendedGroupId->uaeStatus;
        *result = (aesm_error_t)extendedGroupId->errorCode;

        if (*result == AESM_SUCCESS)
        {
            *extended_group_id = extendedGroupId->x_group_id;
        }
    }
    delete extendedGroupId;
    return ret;
}

extern "C"
uae_oal_status_t oal_switch_extended_epid_group(uint32_t x_group_id, uint32_t timeout_usec, aesm_error_t *result)
{
    AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
    if (servicesProvider == NULL)
        return UAE_OAL_ERROR_UNEXPECTED;

    PlainData* plainData = servicesProvider->SGXSwitchExtendedEpidGroup(x_group_id, timeout_usec / 1000);

    uae_oal_status_t ret = UAE_OAL_ERROR_UNEXPECTED;
    if (plainData != NULL)
    {
        ret = plainData->uaeStatus;
        *result = (aesm_error_t)plainData->errorCode;
    }
    delete plainData;
    return ret;
}
