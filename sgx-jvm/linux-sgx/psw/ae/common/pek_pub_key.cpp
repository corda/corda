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

#include "arch.h"
#include "sgx_tcrypto.h"
#include "pek_pub_key.h"
#include "pve_qe_common.h"
#include <string.h>
#include "xegdsk_pub.hh"
#include "peksk_pub.hh"
#include "qsdk_pub.hh"
#include "isk_pub.hh"
#include "byte_order.h"


//Function to verify the ECDSA signature of a PEK
//SHA1 value for integrity checking is not verified since the ECDSA verification could make sure the integrity at the same time.
sgx_status_t check_pek_signature(const signed_pek_t& signed_pek, const sgx_ec256_public_t* pek_sk, uint8_t *result)
{
    sgx_status_t status = SGX_SUCCESS;
    sgx_ecc_state_handle_t handle= 0;
    sgx_ec256_signature_t ec_signature;
    status = sgx_ecc256_open_context(&handle);
    if(SGX_SUCCESS!=status){
        return status;
    }
    se_static_assert(sizeof(ec_signature)==sizeof(signed_pek.pek_signature));
    memcpy(&ec_signature, signed_pek.pek_signature, sizeof(signed_pek.pek_signature));
    SWAP_ENDIAN_32B(ec_signature.x);
    SWAP_ENDIAN_32B(ec_signature.y);
    status = sgx_ecdsa_verify(reinterpret_cast<const uint8_t *>(&signed_pek),
        static_cast<uint32_t>(sizeof(signed_pek.n)+sizeof(signed_pek.e)),
        pek_sk,
        &ec_signature,
        result,
        handle);
    (void)sgx_ecc256_close_context(handle);
    return status;
}

//Function to verify that ECDSA signature of XEGB is correct
sgx_status_t verify_xegb(const extended_epid_group_blob_t& xegb, uint8_t *result){
    if (lv_htons(xegb.data_length) != EXTENDED_EPID_GROUP_BLOB_DATA_LEN
        || xegb.format_id != XEGB_FORMAT_ID){
        return SGX_ERROR_INVALID_PARAMETER;
    }

    sgx_status_t status = SGX_SUCCESS;
    sgx_ecc_state_handle_t handle= 0;
    sgx_ec256_signature_t ec_signature;
    status = sgx_ecc256_open_context(&handle);
    if(SGX_SUCCESS!=status){
        return status;
    }
    se_static_assert(sizeof(ec_signature)==sizeof(xegb.signature));
    memcpy(&ec_signature, xegb.signature, sizeof(xegb.signature));
    SWAP_ENDIAN_32B(ec_signature.x);
    SWAP_ENDIAN_32B(ec_signature.y);
    status = sgx_ecdsa_verify(reinterpret_cast<const uint8_t *>(&xegb),
        static_cast<uint32_t>(sizeof(xegb)-sizeof(xegb.signature)),
        const_cast<sgx_ec256_public_t *>(&g_sdsk_pub_key_little_endian),
        &ec_signature,
        result,
        handle);
    (void)sgx_ecc256_close_context(handle);
    if(SGX_SUCCESS!=status){
        return status;
    }
    return SGX_SUCCESS;
}

sgx_status_t verify_xegb_with_default(const extended_epid_group_blob_t& xegb, uint8_t *result, extended_epid_group_blob_t& out_xegb)
{
    const uint8_t *pxegb = reinterpret_cast<const uint8_t *>(&xegb);
    uint32_t i;
    //check whether all bytes of xegb is 0, if so we should use default xegb
    for (i = 0; i < sizeof(xegb); i++){
        if (pxegb[i] != 0){
            break;
        }
    }
    if (i == sizeof(xegb)){//using default xegb value if all bytes are 0, for hardcoded xegb, no ecdsa signature is available so that no ecdsa verification requried too.
        out_xegb.xeid = 0;
        out_xegb.format_id = XEGB_FORMAT_ID;
        memcpy(out_xegb.epid_sk, &g_sgx_isk_pubkey, 2*ECDSA_SIGN_SIZE);
        memcpy(out_xegb.pek_sk, &g_pek_pub_key_little_endian, 2 * ECDSA_SIGN_SIZE);
        memcpy(out_xegb.qsdk_exp, g_qsdk_pub_key_e, sizeof(g_qsdk_pub_key_e));
        memcpy(out_xegb.qsdk_mod, g_qsdk_pub_key_n, RSA_2048_KEY_BYTES);
        *result = SGX_EC_VALID;
        return SGX_SUCCESS;
    }
    memcpy(&out_xegb, &xegb, sizeof(xegb));//use the input xegb if any bytes in it is non-zero
    return verify_xegb(out_xegb, result);
}
