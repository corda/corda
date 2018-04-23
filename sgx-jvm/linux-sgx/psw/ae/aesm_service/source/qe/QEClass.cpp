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


#include <assert.h>
#include "arch.h"
#include "QEClass.h"
#include "PVEClass.h"
#include "PCEClass.h"
#include "se_memcpy.h"
#include "prof_fun.h"
#include "quoting_enclave_u.h"
#include "quoting_enclave_u.c"

void CQEClass::before_enclave_load() {
    // always unload qe enclave before loading pve enclave
    CPVEClass::instance().unload_enclave();
    CPCEClass::instance().unload_enclave();
}

uint32_t CQEClass::get_qe_target(
    sgx_target_info_t *p_qe_target)
{
    token_t *p_launch =
        reinterpret_cast<token_t *>(&m_launch_token);

    /* We need to make sure the QE is successfully loaded and then we can use
    the cached attributes and launch token. */
    assert(m_enclave_id);
    memset(p_qe_target, 0, sizeof(sgx_target_info_t));
    memcpy_s(&p_qe_target->attributes, sizeof(p_qe_target->attributes),
        &m_attributes.secs_attr, sizeof(m_attributes.secs_attr));
    memcpy_s(&p_qe_target->misc_select, sizeof(p_qe_target->misc_select),
        &m_attributes.misc_select, sizeof(m_attributes.misc_select));
    memcpy_s(&p_qe_target->mr_enclave, sizeof(p_qe_target->mr_enclave),
        &p_launch->body.mr_enclave,
        sizeof(p_launch->body.mr_enclave));
    return AE_SUCCESS;
}

uint32_t CQEClass::verify_blob(
    uint8_t *p_epid_blob,
    uint32_t blob_size,
    bool *p_is_resealed)
{
    uint32_t ret = AE_SUCCESS;
    sgx_status_t status = SGX_SUCCESS;
    uint8_t is_resealed = 0;
    int retry = 0;
    AESM_PROFILE_FUN;

    assert(m_enclave_id);
    status = ::verify_blob(m_enclave_id, &ret, p_epid_blob, blob_size,
        &is_resealed);
    for(; status == SGX_ERROR_ENCLAVE_LOST && retry < AESM_RETRY_COUNT; retry++)
    {
        unload_enclave();
        // Reload an AE will not fail because of out of EPC, so AESM_AE_OUT_OF_EPC is not checked here
        if(AE_SUCCESS != load_enclave())
            return AE_FAILURE;
        status = ::verify_blob(m_enclave_id, &ret, p_epid_blob, blob_size,
            &is_resealed);
    }
    if(status != SGX_SUCCESS)
        return AE_FAILURE;
    if(ret == AE_SUCCESS)
    {
        *p_is_resealed = is_resealed != 0;
    }
    if(ret == QE_EPIDBLOB_ERROR)
    {
        AESM_LOG_FATAL("%s", g_event_string_table[SGX_EVENT_EPID_INTEGRITY_ERROR]);
    }
    return ret;
}

uint32_t CQEClass::get_quote(
    uint8_t *p_epid_blob,
    uint32_t blob_size,
    const sgx_report_t *p_report,
    sgx_quote_sign_type_t quote_type,
    const sgx_spid_t *p_spid,
    const sgx_quote_nonce_t *p_nonce,
    const uint8_t *p_sigrl,
    uint32_t sigrl_size,
    sgx_report_t *p_qe_report,
    uint8_t *p_quote,
    uint32_t quote_size,
    uint16_t pce_isv_svn)
{
    uint32_t ret = AE_SUCCESS;
    sgx_status_t status = SGX_SUCCESS;
    int retry = 0;
    AESM_PROFILE_FUN;

    assert(m_enclave_id);
    status = ::get_quote(
        m_enclave_id,
        &ret,
        p_epid_blob,
        blob_size,
        p_report,
        quote_type,
        p_spid,
        p_nonce,
        p_sigrl,
        sigrl_size,
        p_qe_report,
        p_quote,
        quote_size,
        pce_isv_svn);
    for(; status == SGX_ERROR_ENCLAVE_LOST && retry < AESM_RETRY_COUNT; retry++)
    {
        unload_enclave();
        // Reload an AE will not fail because of out of EPC, so AESM_AE_OUT_OF_EPC is not checked here
        if(AE_SUCCESS != load_enclave())
            return AE_FAILURE;
        status = ::get_quote(
            m_enclave_id,
            &ret,
            p_epid_blob,
            blob_size,
            p_report,
            quote_type,
            p_spid,
            p_nonce,
            p_sigrl,
            sigrl_size,
            p_qe_report,
            p_quote,
            quote_size,
            pce_isv_svn);
    }
    if(status != SGX_SUCCESS)
        return AE_FAILURE;
    if(ret == QE_REVOKED_ERROR)
    {
        AESM_LOG_FATAL("%s", g_event_string_table[SGX_EVENT_EPID_REVOCATION]);
    }
    else if(ret == QE_SIGRL_ERROR)
    {
        AESM_LOG_FATAL("%s", g_event_string_table[SGX_EVENT_EPID20_SIGRL_INTEGRITY_ERROR]);
    }
    return ret;
}
