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


 /**
  * File: provision_enclave.cpp
  * Description: Definition for interfaces provided by provision enclave..
  *
  * Definition for interfaces provided by provision enclave.
  */

#include "se_cdefs.h"
#include "string.h"
#include "helper.h"
#include "cipher.h"
#include "protocol.h"
#include "provision_msg.h"
#include "provision_enclave_t.c"
#include "sgx_utils.h"
#include "aeerror.h"

ae_error_t pve_error_2_ae_error(pve_status_t pve_error)
{
    switch(pve_error){
    case PVEC_SUCCESS:
        return AE_SUCCESS;
    case PVEC_PARAMETER_ERROR:
        return PVE_PARAMETER_ERROR;
    case PVEC_EPID_BLOB_ERROR:
        return PVE_EPIDBLOB_ERROR;
    case PVEC_INSUFFICIENT_MEMORY_ERROR:
        return PVE_INSUFFICIENT_MEMORY_ERROR;
    case PVEC_REVOKED_ERROR:
        return PVE_REVOKED_ERROR;
    case PVEC_INTEGER_OVERFLOW_ERROR:
        return PVE_INTEGRITY_CHECK_ERROR;
    case PVEC_SIGRL_INTEGRITY_CHECK_ERROR:
        return PVE_SIGRL_INTEGRITY_CHECK_ERROR;
    case PVEC_PEK_SIGN_ERROR:
        return PVE_PEK_SIGN_ERROR;
    case PVEC_XEGDSK_SIGN_ERROR:
        return PVE_XEGDSK_SIGN_ERROR;
    case PVEC_MSG_ERROR:
    case PVEC_UNSUPPORTED_VERSION_ERROR:
    case PVEC_INVALID_CPU_ISV_SVN:
    case PVEC_INVALID_EPID_KEY:
        return PVE_MSG_ERROR;
    default:
        return PVE_UNEXPECTED_ERROR;
    }
}

//proxy function to generate data for ProvMsg1
uint32_t gen_prov_msg1_data_wrapper(
    const extended_epid_group_blob_t *xegb,
    const signed_pek_t *pek,
    const sgx_target_info_t *pce_target_info,
    sgx_report_t *pek_report)//output data for generating ProvMsg1
{
    pve_status_t status = PVEC_SUCCESS;

    if(pce_target_info == NULL || !sgx_is_within_enclave(pce_target_info, sizeof(sgx_target_info_t))){
        status = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }
    if(xegb == NULL || !sgx_is_within_enclave(xegb, sizeof(extended_epid_group_blob_t))){
        status = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }

    if(pek==NULL||!sgx_is_within_enclave(pek, sizeof(signed_pek_t))){
        status = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }

    if( pek_report==NULL||!sgx_is_within_enclave(pek_report, sizeof(sgx_report_t))){//require pek_report not to be NULL and memory allocated in EPC
        status = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }

    status = gen_prov_msg1_data(
        *pce_target_info,
        *xegb,
        *pek,
        *pek_report);
ret_point:
    return pve_error_2_ae_error(status);
}

//edger8r will copy memory in/out EPC for all buffers except for  sigrl in msg2 and epidSignature of msg3 in proc_prov_msg2_data_wrapper.
//proxy function to process data from ProvMsg2 and generate ProvMsg3 data
uint32_t proc_prov_msg2_data_wrapper(
    const proc_prov_msg2_blob_input_t *msg2_blob_input,
    uint8_t performance_rekey_used,
    const uint8_t *sigrl, uint32_t sigrl_size,//pointer to external memory if the sigrl is not NULL
    gen_prov_msg3_output_t *msg3_fixed_output,
    uint8_t *epid_sig, uint32_t epid_sig_buffer_size)//This is an optional pointer to external memory for epid signature to be generated
{
    pve_status_t status  = PVEC_SUCCESS;
    const external_memory_byte_t *emp_sigrl = NULL;
    external_memory_byte_t *emp_epid_sig = NULL;

    if( msg2_blob_input==NULL||!sgx_is_within_enclave(msg2_blob_input, sizeof(proc_prov_msg2_blob_input_t))){//input data should be inside EPC
        status = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }
    if( sigrl!=NULL && !sgx_is_outside_enclave(sigrl, sigrl_size)){//sigrl should be outside enclave if it is not NULL
        status = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }

    if((sigrl==NULL&&sigrl_size!=0)||
        (sigrl!=NULL&&sigrl_size==0)){
        status = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }

    if(msg3_fixed_output==NULL ||!sgx_is_within_enclave(msg3_fixed_output, sizeof(gen_prov_msg3_output_t))){
        status = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }

    if(epid_sig == NULL && epid_sig_buffer_size!=0){
        status = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }

    if(epid_sig!=NULL && !sgx_is_outside_enclave(epid_sig, epid_sig_buffer_size)){
        status = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }

    //type-cast the pointer to emp after pointer checking
    emp_sigrl = reinterpret_cast<const external_memory_byte_t *>(sigrl);
    emp_epid_sig = reinterpret_cast<external_memory_byte_t *>(epid_sig);

    status = proc_prov_msg2_data(msg2_blob_input, performance_rekey_used, emp_sigrl, sigrl_size,
        msg3_fixed_output, emp_epid_sig,
        epid_sig_buffer_size);

ret_point:
    return pve_error_2_ae_error(status);
}

//Proxy function to process ProvMsg4 data to generate sealed EPID data blob
uint32_t proc_prov_msg4_data_wrapper(
    const proc_prov_msg4_input_t *msg4_input,
    proc_prov_msg4_output_t* data_blob)
{
    pve_status_t status = PVEC_SUCCESS;

    if(msg4_input == NULL || !sgx_is_within_enclave(msg4_input, sizeof(proc_prov_msg4_input_t))){
        status = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }

    if(data_blob == NULL||!sgx_is_within_enclave(data_blob, sizeof(proc_prov_msg4_output_t)))
    {
        status = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }

    status = proc_prov_msg4_data( msg4_input, reinterpret_cast<sgx_sealed_data_t*>(data_blob));

ret_point:
    return pve_error_2_ae_error(status);
}

//proxy function to create es selector for endpoint selection
uint32_t gen_es_msg1_data_wrapper(gen_endpoint_selection_output_t *es_output)
{
    pve_status_t status = PVEC_SUCCESS;

    if(es_output == NULL || !sgx_is_within_enclave(es_output, sizeof(gen_endpoint_selection_output_t))){
        status = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }


    status = gen_es_msg1_data(es_output);
ret_point:
    return pve_error_2_ae_error(status);
}
