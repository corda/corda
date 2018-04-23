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
#include "PVEClass.h"
#include "QEClass.h"
#include "PCEClass.h"
#include "util.h"
#include "prof_fun.h"
#include "sgx_report.h"
#include "sgx_tseal.h"
#include "epid_pve_type.h"
#include "aesm_xegd_blob.h"
#include "provision_enclave_u.h"
#include "provision_enclave_u.c"

void CPVEClass::before_enclave_load() {
    // always unload qe enclave before loading pve enclave
    CQEClass::instance().unload_enclave();
    CPCEClass::instance().unload_enclave();
}

uint32_t CPVEClass::gen_prov_msg1_data(
    const signed_pek_t *pek,
    const sgx_target_info_t *pce_target_info,
    sgx_report_t *pek_report)
{
    uint32_t ret = AE_SUCCESS;
    sgx_status_t status = SGX_SUCCESS;
    extended_epid_group_blob_t xegb;
    int retry = 0;
    AESM_PROFILE_FUN;
    memset(&xegb, 0, sizeof(xegb));
    if(m_enclave_id==0){
        AESM_DBG_ERROR("call gen_prov_msg1_data without loading PvE");
        return AE_FAILURE;
    }
    if (AE_SUCCESS != (ret = XEGDBlob::instance().read(xegb))){
        return ret;
    }

    status = gen_prov_msg1_data_wrapper(
        m_enclave_id, &ret,
        &xegb,
        pek,
        pce_target_info,
        pek_report);
    for(; status == SGX_ERROR_ENCLAVE_LOST && retry < AESM_RETRY_COUNT; retry++)
    {
        unload_enclave();
        // Reload an AE will not fail because of out of EPC, so AESM_AE_OUT_OF_EPC is not checked here
        if(AE_SUCCESS != load_enclave())
            return AE_FAILURE;
        status = gen_prov_msg1_data_wrapper(
            m_enclave_id, &ret,
            &xegb,
            pek,
            pce_target_info,
            pek_report);
    }
    if (PVE_XEGDSK_SIGN_ERROR == ret) {
        AESM_DBG_ERROR("XEGD signature mismatch in gen_prov_msg1_data");
    }

    if(status != SGX_SUCCESS)
        ret = AE_FAILURE;
    return ret;
}


uint32_t CPVEClass::proc_prov_msg2_data(
    const proc_prov_msg2_blob_input_t* input,
    bool performance_rekey_used,
    const uint8_t* sigrl,
    uint32_t sigrl_size,
    gen_prov_msg3_output_t* msg3_fixed_output,
    uint8_t* epid_sig,
    uint32_t epid_sig_buffer_size)
{
    uint32_t ret = AE_SUCCESS;
    int retry = 0;
    sgx_status_t status = SGX_SUCCESS;
    uint8_t b_performance_rekey_used = performance_rekey_used?1:0;
    AESM_PROFILE_FUN;
    if(m_enclave_id==0){
        AESM_DBG_ERROR("call proc_prov_msg2_data without loading PvE");
        return AE_FAILURE;
    }

    status = proc_prov_msg2_data_wrapper(
        m_enclave_id, &ret,
        input,
        b_performance_rekey_used,
        sigrl, sigrl_size,
        msg3_fixed_output,
        epid_sig, epid_sig_buffer_size);
    for(; status == SGX_ERROR_ENCLAVE_LOST && retry < AESM_RETRY_COUNT; retry++)
    {
        unload_enclave();
        // Reload an AE will not fail because of out of EPC, so AESM_AE_OUT_OF_EPC is not checked here
        if(AE_SUCCESS != load_enclave())
            return AE_FAILURE;
        status = proc_prov_msg2_data_wrapper(
            m_enclave_id, &ret,
            input,
            b_performance_rekey_used,
            sigrl, sigrl_size,
            msg3_fixed_output,
            epid_sig, epid_sig_buffer_size);
    }
    if (PVE_XEGDSK_SIGN_ERROR == ret) {
        AESM_DBG_ERROR("XEGD signature mismatch in proc_prov_msg2_data");
    }

    if(status != SGX_SUCCESS)
        ret = AE_FAILURE;
    return ret;
}

uint32_t CPVEClass::proc_prov_msg4_data(
    const proc_prov_msg4_input_t* msg4_input,
    proc_prov_msg4_output_t* data_blob)
{
    uint32_t ret = AE_SUCCESS;
    sgx_status_t status = SGX_SUCCESS;
    int retry = 0;
    AESM_PROFILE_FUN;
    if(m_enclave_id==0){
        AESM_DBG_ERROR("call proc_prov_msg4_data without loading PvE");
        return AE_FAILURE;
    }

    status = proc_prov_msg4_data_wrapper(
        m_enclave_id, &ret,
        msg4_input,
        data_blob);
    for(; status == SGX_ERROR_ENCLAVE_LOST && retry < AESM_RETRY_COUNT; retry++)
    {
        unload_enclave();
        // Reload an AE will not fail because of out of EPC, so AESM_AE_OUT_OF_EPC is not checked here
        if(AE_SUCCESS != load_enclave())
            return AE_FAILURE;
        status = proc_prov_msg4_data_wrapper(
            m_enclave_id, &ret,
            msg4_input,
            data_blob);
    }
    if (PVE_XEGDSK_SIGN_ERROR == ret) {
        AESM_DBG_ERROR("XEGD signature mismatch in proc_prov_msg4_data");
    }

    if(status != SGX_SUCCESS)
        ret = AE_FAILURE;
    return ret;
}

uint32_t CPVEClass::gen_es_msg1_data(
        gen_endpoint_selection_output_t* es_output)
{
    uint32_t ret = AE_SUCCESS;
    sgx_status_t status = SGX_SUCCESS;
    int retry = 0;
    AESM_PROFILE_FUN;
    if(m_enclave_id==0){
        AESM_DBG_ERROR("call gen_es_msg1_data without loading PvE");
        return AE_FAILURE;
    }

    status = gen_es_msg1_data_wrapper(
        m_enclave_id, &ret,
        es_output);
    for(; status == SGX_ERROR_ENCLAVE_LOST && retry < AESM_RETRY_COUNT; retry++)
    {
        unload_enclave();
        // Reload an AE will not fail because of out of EPC, so AESM_AE_OUT_OF_EPC is not checked here
        if(AE_SUCCESS != load_enclave())
            return AE_FAILURE;
        status = gen_es_msg1_data_wrapper(
            m_enclave_id, &ret,
            es_output);
    }

    if(status != SGX_SUCCESS)
        ret = AE_FAILURE;
    return ret;
}


