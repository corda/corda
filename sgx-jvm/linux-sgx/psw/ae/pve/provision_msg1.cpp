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

#include "provision_msg.h"
#include "sgx_trts.h"
#include "cipher.h"
#include "helper.h"
#include "protocol.h"
#include "ipp_wrapper.h"
#include "sgx_tcrypto.h"
#include "pve_hardcoded_tlv_data.h"
#include "sgx_utils.h"
#include "pve_qe_common.h"
#include "pek_pub_key.h"
#include "pce_cert.h"
#include "arch.h"
#include <string.h>
#include <stdlib.h>

 /**
  * File: provision_msg1.cpp
  * Description: Provide the implementation of code to generate data for ProvMsg1
  *
  * Core Code of Provision Enclave
  */

//generate data for ProvMsg1, the REPORT for PEK public key
//The function return PVEC_SUCCESS on success or other error code to indicate error
//@pce_target_info: target_info of PCE enclave
//@xegb: extended epid group blob used
//@pek: The PEK public key signed by PEKSK
//@pek_report: output PvE REPORT of PEK public key so that PCE could verify it by using Local Attestation
//@return PVEC_SUCCESS on success
pve_status_t gen_prov_msg1_data(const sgx_target_info_t& pce_target_info,
                           const extended_epid_group_blob_t& xegb,
                           const signed_pek_t& pek,
                           sgx_report_t& pek_report)
{
    pve_status_t ret = PVEC_SUCCESS;
    sgx_status_t sgx_status = SGX_SUCCESS;
    uint8_t pek_result = SGX_EC_INVALID_SIGNATURE;
    sgx_report_data_t report_data = {0};
    extended_epid_group_blob_t local_xegb;
    sgx_sha_state_handle_t sha_handle = NULL;
    uint8_t crypto_suite = ALG_RSA_OAEP_3072;

    static_assert(sizeof(pek.n) == 384, "pek.n should be 384 bytes");
    sgx_status = verify_xegb_with_default(xegb, &pek_result, local_xegb);
    if(SGX_SUCCESS != sgx_status){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }else if(pek_result != SGX_EC_VALID){
        ret = PVEC_XEGDSK_SIGN_ERROR;
        goto ret_point;
    }
    sgx_status = check_pek_signature(pek, (sgx_ec256_public_t*)local_xegb.pek_sk, &pek_result);
    if(SGX_SUCCESS != sgx_status){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }else if(pek_result != SGX_EC_VALID){
        ret = PVEC_PEK_SIGN_ERROR; //use a special error code to indicate PEK Signature error
        goto ret_point;
    }
    se_static_assert(sizeof(report_data)>=sizeof(sgx_sha256_hash_t)); /*hash size is too large to be hold by report*/


    //report_data = SHA256(crypto_suite||public_key)||0-padding
    do
    {
        sgx_status = sgx_sha256_init(&sha_handle);
        if (SGX_SUCCESS != sgx_status)
            break;

        sgx_status = sgx_sha256_update(&crypto_suite, sizeof(uint8_t),
            sha_handle);
        if (SGX_SUCCESS != sgx_status)
            break;
        //(MOD followed by e)
        sgx_status = sgx_sha256_update(pek.n, sizeof(pek.n),
            sha_handle);
        if (SGX_SUCCESS != sgx_status)
            break;
        sgx_status = sgx_sha256_update(pek.e, sizeof(pek.e),
            sha_handle);
        if (SGX_SUCCESS != sgx_status)
            break;
        sgx_status = sgx_sha256_get_hash(sha_handle, reinterpret_cast<sgx_sha256_hash_t *>(&report_data));
    } while (0);
    if (sha_handle != NULL)
        sgx_sha256_close(sha_handle);
    if(SGX_SUCCESS != sgx_status){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }

    if((pce_target_info.attributes.flags & SGX_FLAGS_PROVISION_KEY)!=SGX_FLAGS_PROVISION_KEY ||
        (pce_target_info.attributes.flags & SGX_FLAGS_DEBUG) != 0){
        //PCE must have access to provisioning key
        //Can't be debug PCE
        ret = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }

    sgx_status = sgx_create_report(&pce_target_info, &report_data, &pek_report);
    if(SGX_SUCCESS != sgx_status){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }

ret_point:
    if(PVEC_SUCCESS != ret){//clear critical output data on error
        (void)memset_s(&pek_report, sizeof(pek_report), 0, sizeof(pek_report));
    }
    return ret;
}
