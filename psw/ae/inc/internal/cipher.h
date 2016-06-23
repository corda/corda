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


/**
 * File: cipher.h
 * Description: Header file to wrap cipher related function from IPP for Provision Enclave 
 *
 * Wrap for ipp function like aes-cmac, aes-gcm, rsa-oaep, sha512.
 */

#ifndef _CIPHER_H
#define _CIPHER_H

#include "se_cdefs.h"
#include "ae_ipp.h"
#include "sgx_tseal.h"
#include "epid_types.h"
#include "se_types.h"
#include "sgx_key.h"
#include "provision_msg.h"
#include "se_sig_rl.h"

/*Function to generate a random parameter f (0<f<p) for epid private key which will be used in generation of ProvMsg3
 *   return PVEC_SUCCESS on success
 */
pve_status_t gen_epid_priv_f(
    const uint8_t* p,      /*input, the modulo p*/
    PElemStr*      f);     /*output, the generated parameter f of EPID private key provided by enclave in big endian*/

/*Function to return Intel EPID 2.0 parameter such as parameters for the elliptic curve group*/
const EPID2Params*
    get_epid_para_cert();

/*function to generate random number of num_bits
 *   return PVEC_SUCCESS on success
 */
pve_status_t pve_rng_generate(
    int            num_bits,     /*bits of random info to be generated*/
    unsigned char* p_rand_data); /*buffer to hold output, the length of it should be at least (num_bits+7)/8*/

/*This function will do the rsa oaep encrypt with input src[0:src_len] and put the output to buffer src too
 * The function will assume that buffer src_len is no more than PVE_RSAOAEP_ENCRYPT_MAXLEN and the buffer size is at least PVE_RSA_KEY_BYTES
 *   And the length of output data is always PVE_RSA_KEY_BYTES
 * return PVEC_SUCCESS on success
 */
pve_status_t pve_rsa_oaep_encrypt(
    const uint8_t               *src,                    /*input and output buffer*/
    uint32_t                    src_len, 
    const IppsRSAPublicKeyState *rsa,                    /*input rsa public key*/
    uint8_t                     dst[PVE_RSA_KEY_BYTES]); /*output buffer*/

/*Functions for piece meal aes_gcm encryption
 *aes_gcm encryption init function, 128 bits encryption used
 *The function will return PVEC_SUCCESS on success
 *  If the function success, we must call pve_aes_gcm_encrypt_fini after the usage of it to free memory
 *    if an error code is returned, no following pve_aes_gcm_encrypt_fini should be called
 */
pve_status_t pve_aes_gcm_encrypt_init(
    const uint8_t    *key,                 /*16 bytes key*/
    const uint8_t    *iv,                  /*input initial vector. randomly generated value and encryption of different msg should use different iv*/
    uint32_t         iv_len,               /*length of initial vector, usually IV_SIZE*/
    const uint8_t    *aad,                 /*AAD of AES-GCM, it could be NULL and aad_len must be 0 if it is NULL*/
    uint32_t         aad_len,              /*length of bytes of AAD*/
    IppsAES_GCMState **aes_gcm_state,      /*state buffer to return, using pve_aes_gcm_encrypt_fini to free it if the function return success*/
    uint32_t         *state_buffer_size);  /*return buffer size here which used by fini function*/
/*aes_gcm function to get mac value*/
pve_status_t pve_aes_gcm_get_mac(IppsAES_GCMState *aes_gcm_state,uint8_t *mac);/*output mac value, the length of buffer is MAC_SIZE*/
/*aes_gcm encryption fini function which is used to free memory for the aes_gcm_state*/
void pve_aes_gcm_encrypt_fini(
    IppsAES_GCMState *aes_gcm_state,       /*the state buffer*/
    uint32_t         state_buffer_size);   /*size of the buffer, the function need it to free the memory*/

/*This function will do aes_gcm encryption update where both data before/after encryption will share same memory 'buf'
 *    return PVEC_SUCCESS on success
 */
pve_status_t pve_aes_gcm_encrypt_inplace_update(
    IppsAES_GCMState *aes_gcm_state,      /*pointer to a state*/
    uint8_t          *buf,                /*start address to data before/after encryption*/
    uint32_t         buf_len);            /*length of data, for aes-gcm, the data before/after encryption has same size*/

/*declare the function defined inside pve_verify_signature.cpp to Verify Intel ECDSA signature
 *return PVEC_SUCCESS if the signature verification passed
 *return PVEC_MSG_ERROR if signature not matched
 *return other error code for other kinds of error
 */
pve_status_t verify_intel_ecdsa_signature(
    const uint8_t            *p_sig_rl_sign, /*The ecdsa signature of message to be verify, the size of it should be 2*ECDSA_SIGN_SIZE which contains two big integer in big endian*/
    const se_ae_ecdsa_hash_t *p_sig_rl_hash);/*The sha256 hash value of message to be verify*/

/*Function to verify the ECDSA signature of Intel Binary EPID Group Public Cert*/
pve_status_t check_intel_signature_of_group_pub_cert(const signed_epid_group_cert_t *group_cert);
/*Function to verify the ECDSA signature in a signed PEK*/
sgx_status_t check_pek_signature(const signed_pek_t& signed_pek, uint8_t *result);
#endif

