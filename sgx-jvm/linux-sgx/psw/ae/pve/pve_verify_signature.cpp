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
  * File: pve_verify_signature.cpp
  * Description: Define function to check ISK signed signature at the end of SigRL.
  * It will use qe/pve shared code in file se_ecdsa_verify_internal
  */
#include "provision_msg.h"
#include "epid/common/errors.h"
#include "ae_ipp.h"
#include "epid/member/api.h"
#include <string.h>
#include "helper.h"
#include "cipher.h"
#include "pve_qe_common.h"
#include "se_ecdsa_verify_internal.h"
#include "byte_order.h"
#include "util.h"

/*
 * An internal function used to verify the ECDSA signature inside PvE given hash of message
 *
 * @param p_signature[in] Pointer to signature part of the message, the data in bigendian.
 * @param p_input_hash[in] The pointer of the hash of the whole message.
 * @param public_key_x and public_key_y, the two components of ECDSA public key
 * @return ae_error_t PVEC_SUCCESS for success and verification passed,
 * @   return PVEC_MSG_ERROR for invalid and signature and other for errors.
 */
static pve_status_t pve_verify_ecdsa_signature(
    const uint8_t *p_signature,
    const se_ae_ecdsa_hash_t *p_input_hash,
    const uint8_t public_key_x[SGX_ECP256_KEY_SIZE],
    const uint8_t public_key_y[SGX_ECP256_KEY_SIZE])
{
    pve_status_t ret = PVEC_SUCCESS;
    IppStatus ipp_ret = ippStsNoErr;
    IppsECCPState* p_ecp = NULL;
    sgx_ec256_public_t ec_pub_key;
    IppECResult ecc_result = ippECValid;
    sgx_status_t se_ret = SGX_SUCCESS;
    sgx_ec256_signature_t little_endian_signature;

    memset(&ec_pub_key, 0, sizeof(ec_pub_key));
    ipp_ret = new_std_256_ecp(&p_ecp);
    if(ipp_ret != ippStsNoErr)
    {
        ret = ipp_error_to_pve_error(ipp_ret);
        goto CLEANUP;
    }
    memcpy(&little_endian_signature.x , p_signature, ECDSA_SIGN_SIZE);
    memcpy(&little_endian_signature.y , p_signature+ECDSA_SIGN_SIZE, ECDSA_SIGN_SIZE);
    SWAP_ENDIAN_32B(little_endian_signature.x);
    SWAP_ENDIAN_32B(little_endian_signature.y);

    memcpy(ec_pub_key.gx, public_key_x, sizeof(ec_pub_key.gx));
    memcpy(ec_pub_key.gy, public_key_y, sizeof(ec_pub_key.gy));
    se_ret = se_ecdsa_verify_internal(p_ecp,
                                        &ec_pub_key,
                                        &little_endian_signature,
                                        p_input_hash,
                                        &ecc_result);
    if(se_ret != SGX_SUCCESS){
        ret = sgx_error_to_pve_error(se_ret);
        goto CLEANUP;
    }

    if(ecc_result != ippECValid){
        ret = PVEC_MSG_ERROR;
        goto CLEANUP;
    }

CLEANUP:
    secure_free_std_256_ecp(p_ecp);
    return ret;
}

pve_status_t verify_epid_ecdsa_signature(
    const uint8_t *p_sig_rl_sign,   //The ecdsa signature of message to be verified, the size of it should be 2*ECDSA_SIGN_SIZE which contains two big integers in big endian
    const extended_epid_group_blob_t& xegb,
    const se_ae_ecdsa_hash_t *p_sig_rl_hash) //The sha256 hash value of message to be verified
{
    uint8_t pub_x[ECDSA_SIGN_SIZE], pub_y[ECDSA_SIGN_SIZE];
    memcpy(pub_x, xegb.epid_sk, ECDSA_SIGN_SIZE);
    memcpy(pub_y, xegb.epid_sk+ECDSA_SIGN_SIZE, ECDSA_SIGN_SIZE);//epid sk has been of little endian format in xegb
    return pve_verify_ecdsa_signature(p_sig_rl_sign, p_sig_rl_hash, pub_x, pub_y);
}

static pve_status_t pve_check_ecdsa_signature(const uint8_t *data,
                                              uint32_t data_len,
                                              const uint8_t *signature,
                                              const uint8_t public_key_x[SGX_ECP256_KEY_SIZE],
                                              const uint8_t public_key_y[SGX_ECP256_KEY_SIZE])
{
    sgx_sha_state_handle_t sha_state = NULL;
    pve_status_t ret = PVEC_SUCCESS;
    sgx_status_t sgx_status = SGX_SUCCESS;
    //First generate SHA256 hash value of the data
    sgx_status = sgx_sha256_init(&sha_state);
    if(sgx_status != SGX_SUCCESS){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }

    sgx_status = sgx_sha256_update(data, data_len, sha_state);
    if(sgx_status != SGX_SUCCESS){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }

    se_ae_ecdsa_hash_t out;
    //get the SHA256 hash value of input data in buffer 'out'
    se_static_assert(sizeof(out)==sizeof(sgx_sha256_hash_t)); /*sgx_sha256_hash_t must have same size as se_ae_ecdsa_hash_t*/
    sgx_status =sgx_sha256_get_hash(sha_state, reinterpret_cast<sgx_sha256_hash_t *>(out.hash));
    if(sgx_status != SGX_SUCCESS){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }

    //call the function to verify the ECDSA signature of hash value 'out' matches 'signature'
    ret = pve_verify_ecdsa_signature(signature, &out, public_key_x, public_key_y);
ret_point:
    if(sha_state!=NULL){
        sgx_sha256_close(sha_state);
    }
    return ret;
}


//Function to verify the ECDSA signature is signed by EPID Signing Key
//The function will also check that version and type of the cert
//64 bytes signature is the last field of the input data structure
pve_status_t check_signature_of_group_pub_cert(const signed_epid_group_cert_t *group_cert, const uint8_t* epid_sk)
{
    uint8_t pub_x[ECDSA_SIGN_SIZE], pub_y[ECDSA_SIGN_SIZE];
    uint16_t version = lv_ntohs(group_cert->version);
    uint16_t type = lv_ntohs(group_cert->type);
    uint8_t version_minor = static_cast<uint8_t>(version>>8);//byte 0 of original data
    uint8_t version_major = static_cast<uint8_t>(version);//byte 1 of original data
    if(type != EPID_TYPE_GROUP_CERT){
        return PVEC_MSG_ERROR;
    }
    if(version_major != EPID_VERSION_MAJOR ||
        version_minor != EPID_VERSION_MINOR){
            return PVEC_UNSUPPORTED_VERSION_ERROR;
    }
    memcpy(pub_x, epid_sk, ECDSA_SIGN_SIZE);
    memcpy(pub_y, epid_sk + ECDSA_SIGN_SIZE, ECDSA_SIGN_SIZE);
    return pve_check_ecdsa_signature(reinterpret_cast<const uint8_t *>(group_cert),
                                 static_cast<uint32_t>(sizeof(signed_epid_group_cert_t)-sizeof(group_cert->ecdsa_signature)),
                                 group_cert->ecdsa_signature,
                                 pub_x, pub_y);
}

