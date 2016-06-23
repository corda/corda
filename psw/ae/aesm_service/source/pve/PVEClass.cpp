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
#include "PVEClass.h"
#include "QEClass.h"
#include "util.h"
#include "prof_fun.h"
#include "sgx_report.h"
#include "sgx_tseal.h"
#include "epid_pve_type.h"
#include "provision_enclave_u.h"
#include "provision_enclave_u.c"

void CPVEClass::before_enclave_load() {
    // always unload qe enclave before loading pve enclave
    CQEClass::instance().unload_enclave();
}

uint32_t CPVEClass::gen_prov_msg1_data(
    const psvn_t* psvn,
    const signed_pek_t *pek,
    bool performance_rekey_used,
    prov_msg1_output_t* output)
{
    uint32_t ret = AE_SUCCESS;
    sgx_status_t status = SGX_SUCCESS;
    AESM_PROFILE_FUN;
    if(m_enclave_id==0){
        AESM_DBG_ERROR("call gen_prov_msg1_data without loading PvE");
        return AE_FAILURE;
    }

    status = gen_prov_msg1_data_wrapper(
        m_enclave_id, &ret, 
        psvn, 
        pek,
        performance_rekey_used?1:0,
        output);
    if(status == SGX_ERROR_ENCLAVE_LOST)
        ret = AE_ENCLAVE_LOST;
    else if(status != SGX_SUCCESS)
        ret = AE_FAILURE;
    return ret;
}

uint32_t CPVEClass::get_ek2(
    const prov_get_ek2_input_t* input,
    prov_get_ek2_output_t* ek2)
{
    uint32_t ret = AE_SUCCESS;
    sgx_status_t status = SGX_SUCCESS;
    AESM_PROFILE_FUN;
    if(m_enclave_id==0){
        AESM_DBG_ERROR("call get_ek2 without loading PvE");
        return AE_FAILURE;
    }

    status = get_ek2_wrapper(
        m_enclave_id, &ret, 
        input,
        ek2);
    if(status == SGX_ERROR_ENCLAVE_LOST)
        ret = AE_ENCLAVE_LOST;
    else if(status != SGX_SUCCESS)
        ret = AE_FAILURE;
    return ret;
}

uint32_t CPVEClass::proc_prov_msg2_data(
    const proc_prov_msg2_blob_input_t* input,
    const uint8_t* sigrl,
    uint32_t sigrl_size,
    gen_prov_msg3_output_t* msg3_fixed_output,
    uint8_t* epid_sig,
    uint32_t epid_sig_buffer_size)
{
    uint32_t ret = AE_SUCCESS;
    sgx_status_t status = SGX_SUCCESS;
    AESM_PROFILE_FUN;
    if(m_enclave_id==0){
        AESM_DBG_ERROR("call proc_prov_msg2_data without loading PvE");
        return AE_FAILURE;
    }

    status = proc_prov_msg2_data_wrapper(
        m_enclave_id, &ret, 
        input,
        sigrl, sigrl_size,
        msg3_fixed_output,
        epid_sig, epid_sig_buffer_size);
    if(status == SGX_ERROR_ENCLAVE_LOST)
        ret = AE_ENCLAVE_LOST;
    else if(status != SGX_SUCCESS)
        ret = AE_FAILURE;
    return ret;
}

uint32_t CPVEClass::proc_prov_msg4_data(
    const proc_prov_msg4_input_t* msg4_input,
    proc_prov_msg4_output_t* data_blob)
{
    uint32_t ret = AE_SUCCESS;
    sgx_status_t status = SGX_SUCCESS;
    AESM_PROFILE_FUN;
    if(m_enclave_id==0){
        AESM_DBG_ERROR("call proc_prov_msg4_data without loading PvE");
        return AE_FAILURE;
    }

    status = proc_prov_msg4_data_wrapper(
        m_enclave_id, &ret, 
        msg4_input,
        data_blob);
    if(status == SGX_ERROR_ENCLAVE_LOST)
        ret = AE_ENCLAVE_LOST;
    else if(status != SGX_SUCCESS)
        ret = AE_FAILURE;
    return ret;
}

uint32_t CPVEClass::gen_es_msg1_data(
        gen_endpoint_selection_output_t* es_output)
{
    uint32_t ret = AE_SUCCESS;
    sgx_status_t status = SGX_SUCCESS;
    AESM_PROFILE_FUN;
    if(m_enclave_id==0){
        AESM_DBG_ERROR("call gen_es_msg1_data without loading PvE");
        return AE_FAILURE;
    }

    status = gen_es_msg1_data_wrapper(
        m_enclave_id, &ret, 
        es_output);
    if(status == SGX_ERROR_ENCLAVE_LOST)
        ret = AE_ENCLAVE_LOST;
    else if(status != SGX_SUCCESS)
        ret = AE_FAILURE;
    return ret;
}


