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

#ifndef __GNUC__
#include "StdAfx.h"
#include <intrin.h>
#endif
#include <assert.h>
#include "PCEClass.h"
#include "QEClass.h"
#include "PVEClass.h"
#include "util.h"
#include "prof_fun.h"
#include "sgx_report.h"
#include "sgx_tseal.h"
#include "epid_pve_type.h"
#include "pce_u.h"
#include "pce_u.c"

void CPCEClass::before_enclave_load() {
    // always unload qe/pve enclave before loading pce enclave
    CQEClass::instance().unload_enclave();
    CPVEClass::instance().unload_enclave();
}

uint32_t CPCEClass::get_pce_target(
    sgx_target_info_t *p_pce_target)
{
    token_t *p_token =
        reinterpret_cast<token_t *>(&m_launch_token);

    /* We need to make sure the PCE is successfully loaded and then we can use
    the cached attributes and launch token. */
    assert(m_enclave_id);
    memset(p_pce_target, 0, sizeof(sgx_target_info_t));
    memcpy_s(&p_pce_target->attributes, sizeof(p_pce_target->attributes),
        &m_attributes.secs_attr, sizeof(m_attributes.secs_attr));
    memcpy_s(&p_pce_target->misc_select, sizeof(p_pce_target->misc_select),
        &m_attributes.misc_select, sizeof(m_attributes.misc_select));
    memcpy_s(&p_pce_target->mr_enclave, sizeof(p_pce_target->mr_enclave),
        &p_token->body.mr_enclave,
        sizeof(p_token->body.mr_enclave));
    return AE_SUCCESS;
}

uint32_t CPCEClass::get_pce_info(const sgx_report_t& report, const signed_pek_t& pek, uint16_t& pce_id, uint16_t& isv_svn, uint8_t encrypted_ppid[PEK_MOD_SIZE])
{
    sgx_status_t status = SGX_SUCCESS;
    uint32_t ret_val = 0;
    uint32_t ret_size = PEK_MOD_SIZE;
    int retry = 0;
    pce_info_t pce_info;
    uint8_t signature_scheme;
    AESM_PROFILE_FUN;
    if (m_enclave_id == 0){
        AESM_DBG_ERROR("call get_pc_info without loading PCE");
        return AE_FAILURE;
    }

    status = ::get_pc_info(m_enclave_id, &ret_val, &report, (uint8_t*)&pek, static_cast<uint32_t>(PEK_MOD_SIZE + sizeof(pek.e)), ALG_RSA_OAEP_3072, encrypted_ppid, PEK_MOD_SIZE, &ret_size, &pce_info, &signature_scheme);
    for(; status == SGX_ERROR_ENCLAVE_LOST && retry < AESM_RETRY_COUNT; retry++)
    {
        unload_enclave();
        if(AE_SUCCESS != load_enclave())
            return AE_FAILURE;
        status = ::get_pc_info(m_enclave_id, &ret_val, &report, (uint8_t*)&pek, static_cast<uint32_t>(PEK_MOD_SIZE + sizeof(pek.e)), ALG_RSA_OAEP_3072, encrypted_ppid, PEK_MOD_SIZE, &ret_size, &pce_info, &signature_scheme);
    }
    if(status != SGX_SUCCESS)
        return AE_FAILURE;
    if (ret_val != AE_SUCCESS)
        return ret_val;
    if(signature_scheme != NIST_P256_ECDSA_SHA256){
        return AE_FAILURE;
    }
    if(ret_size != PEK_MOD_SIZE){
        return AE_FAILURE;
    }
    pce_id = pce_info.pce_id;
    isv_svn = pce_info.pce_isvn;
    return AE_SUCCESS;
}

uint32_t CPCEClass::sign_report(const psvn_t& cert_psvn, const sgx_report_t& report, uint8_t signed_sign[2*SE_ECDSA_SIGN_SIZE])
{
    sgx_status_t status = SGX_SUCCESS;
    uint32_t ret_val = 0;
    uint32_t ret_size = 2*SE_ECDSA_SIGN_SIZE;
    int retry = 0;
    AESM_PROFILE_FUN;
    if (m_enclave_id == 0){
        AESM_DBG_ERROR("call certify_enclave without loading PCE");
        return AE_FAILURE;
    }

    status = ::certify_enclave(m_enclave_id, &ret_val, &cert_psvn, &report, signed_sign, 2*SE_ECDSA_SIGN_SIZE, &ret_size);
    for(; status == SGX_ERROR_ENCLAVE_LOST && retry < AESM_RETRY_COUNT; retry++)
    {
        unload_enclave();
        if(AE_SUCCESS != load_enclave())
            return AE_FAILURE;
        status = ::certify_enclave(m_enclave_id, &ret_val, &cert_psvn, &report, signed_sign, 2*SE_ECDSA_SIGN_SIZE, &ret_size);
    }
    if(status != SGX_SUCCESS)
        return AE_FAILURE;
    if(ret_val != AE_SUCCESS){
        return ret_val;
    }
    if(ret_size != 2*SE_ECDSA_SIGN_SIZE)
        return AE_FAILURE;
    return AE_SUCCESS;
}
