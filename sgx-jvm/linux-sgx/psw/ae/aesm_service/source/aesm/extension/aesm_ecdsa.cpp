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
#include "sgx_memset_s.h"
#include "sgx_tcrypto.h"
#include "aeerror.h"
#include "tlv_common.h"
#include "pek_pub_key.h"
#include "peksk_pub.hh"

ae_error_t aesm_check_pek_signature(const signed_pek_t& signed_pek, const extended_epid_group_blob_t& xegb)
{
    uint8_t result = SGX_EC_INVALID_SIGNATURE;
    uint32_t i;
    sgx_status_t sgx_code;
    const uint8_t *p = (const uint8_t *)&xegb;
    for (i = 0; i < sizeof(xegb); i++){
        if (p[i] != 0){
            break;
        }
    }
    if (i == sizeof(xegb)){//if all bytes of xegb is 0, using hardcoded PEKSK public key
        sgx_code = check_pek_signature(signed_pek, (const sgx_ec256_public_t*)&g_pek_pub_key_little_endian, &result);
    }
    else{
        sgx_code = check_pek_signature(signed_pek, reinterpret_cast<const sgx_ec256_public_t*>(xegb.pek_sk), &result);
    }
    if(sgx_code == SGX_ERROR_OUT_OF_MEMORY)
        return AE_OUT_OF_MEMORY_ERROR;
    else if(sgx_code != SGX_SUCCESS)
        return AE_FAILURE; //unknown error code
    else if(result != SGX_EC_VALID)//sgx_code is SGX_SUCCESS
        return PVE_MSG_ERROR; //signature verification failed
    else
        return AE_SUCCESS;//PEK Singatue verified successfully
}

ae_error_t aesm_verify_xegb(const extended_epid_group_blob_t& signed_xegb)
{
    uint8_t result = SGX_EC_INVALID_SIGNATURE;
    sgx_status_t sgx_code = verify_xegb(signed_xegb, &result);
    if (sgx_code == SGX_ERROR_INVALID_PARAMETER)
        return AE_INVALID_PARAMETER; 
    else if(sgx_code == SGX_ERROR_OUT_OF_MEMORY)
        return AE_OUT_OF_MEMORY_ERROR;
    else if (sgx_code != SGX_SUCCESS)
        return AE_FAILURE; //unknown error code
    else if (result != SGX_EC_VALID)//sgx_code is SGX_SUCCESS
        return AE_INVALID_PARAMETER; //signature verification failed
    else
        return AE_SUCCESS;//XEGB Signature verified successfully
}

