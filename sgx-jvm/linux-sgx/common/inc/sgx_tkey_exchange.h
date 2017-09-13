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

#ifndef _SGX_TKEY_EXCHANGE_H_
#define _SGX_TKEY_EXCHANGE_H_

#include "sgx.h"
#include "sgx_defs.h"
#include "sgx_key_exchange.h"

#ifdef  __cplusplus
extern "C" {
#endif

/*
 * The sgx_ra_init function creates a context for the remote attestation and
 * key exchange process.
 *
 * @param p_pub_key The EC public key of the service provider based on the NIST
 *                  P-256 elliptic curve.
 * @param b_pse     If true, platform service information is needed in message
 *                  3. The caller should make sure a PSE session has been
 *                  established using sgx_create_pse_session before attempting
 *                  to establish a remote attestation and key exchange session
 *                  involving platform service information.
 * @param p_context The output context for the subsequent remote attestation
 *                  and key exchange process, to be used in sgx_ra_get_msg1 and
 *                  sgx_ra_proc_msg2.
 * @return sgx_status_t SGX_SUCCESS                     Indicates success.
 *                      SGX_ERROR_INVALID_PARAMETER     Indicates an error that
 *                                                      the input parameters are
 *                                                      invalid.
 *                      SGX_ERROR_OUT_OF_MEMORY         There is not enough
 *                                                      memory available to
 *                                                      complete this operation.
 *                      SGX_ERROR_AE_SESSION_INVALID    Session is invalid or
 *                                                      ended by server.
 *                      SGX_ERROR_UNEXPECTED            Indicates an unexpected
 *                                                      error occurs.
 */
sgx_status_t SGXAPI sgx_ra_init(
    const sgx_ec256_public_t *p_pub_key,
    int b_pse,
    sgx_ra_context_t *p_context);

/*
 * The sgx_ra_derive_secret_keys_t function should takes the Diffie-Hellman
 * shared secret as input to allow the ISV enclave to generate their own derived
 * shared keys (SMK, SK, MK and VK).
 *
 * @param p_shared_key The the Diffie-Hellman shared secret.
 * @param kdf_id,      Key Derivation Function ID 
 * @param p_smk_key    The output SMK.
 * @param p_sk_key     The output SK.
 * @param p_mk_key     The output MK.
 * @param p_vk_key     The output VK.
 * @return sgx_status_t SGX_SUCCESS                     Indicates success.
 *                      SGX_ERROR_INVALID_PARAMETER     Indicates an error that
 *                                                      the input parameters are
 *                                                      invalid.
 *                      SGX_ERROR_KDF_MISMATCH          Indicates key derivation
 *                                                      function doesn't match.
 *                      SGX_ERROR_OUT_OF_MEMORY         There is not enough
 *                                                      memory available to
 *                                                      complete this operation.
 *                      SGX_ERROR_UNEXPECTED            Indicates an unexpected
 *                                                      error occurs.
 */

typedef sgx_status_t(*sgx_ra_derive_secret_keys_t)(
    const sgx_ec256_dh_shared_t* p_shared_key,
    uint16_t kdf_id,
    sgx_ec_key_128bit_t* p_smk_key,
    sgx_ec_key_128bit_t* p_sk_key,
    sgx_ec_key_128bit_t* p_mk_key,
    sgx_ec_key_128bit_t* p_vk_key);

/*
 * The sgx_ra_init_ex function creates a context for the remote attestation and
 * key exchange process asociated with a key derive function.
 *
 * @param p_pub_key The EC public key of the service provider based on the NIST
 *                  P-256 elliptic curve.
 * @param b_pse     If true, platform service information is needed in message
 *                  3. The caller should make sure a PSE session has been
 *                  established using sgx_create_pse_session before attempting
 *                  to establish a remote attestation and key exchange session
 *                  involving platform service information.
 * @param derive_key_cb A pointer to a call back routine matching the
 *                      function prototype of sgx_ra_derive_secret_keys_t.  This
 *                      function takes the Diffie-Hellman shared secret as input
 *                      to allow the ISV enclave to generate their own derived
 *                      shared keys (SMK, SK, MK and VK).
 * @param p_context The output context for the subsequent remote attestation
 *                  and key exchange process, to be used in sgx_ra_get_msg1 and
 *                  sgx_ra_proc_msg2.
 * @return sgx_status_t SGX_SUCCESS                     Indicates success.
 *                      SGX_ERROR_INVALID_PARAMETER     Indicates an error that
 *                                                      the input parameters are
 *                                                      invalid.
 *                      SGX_ERROR_OUT_OF_MEMORY         There is not enough
 *                                                      memory available to
 *                                                      complete this operation.
 *                      SGX_ERROR_AE_SESSION_INVALID    Session is invalid or
 *                                                      ended by server.
 *                      SGX_ERROR_UNEXPECTED            Indicates an unexpected
 *                                                      error occurs.
 */

sgx_status_t SGXAPI sgx_ra_init_ex(
    const sgx_ec256_public_t *p_pub_key,
    int b_pse,
    sgx_ra_derive_secret_keys_t derive_key_cb,
    sgx_ra_context_t *p_context);
/*
 * The sgx_ra_get_keys function is used to get the negotiated keys of a remote
 * attestation and key exchange session. This function should only be called
 * after the service provider accepts the remote attestation and key exchange
 * protocol message 3 produced by sgx_ra_proc_msg2.
 *
 * @param context   Context returned by sgx_ra_init.
 * @param type      The specifier of keys, can be SGX_RA_KEY_MK, SGX_RA_KEY_SK
 *                  and SGX_RA_VK.
 * @param p_key     The key returned.
 * @return sgx_status_t SGX_SUCCESS                     Indicates success.
 *                      SGX_ERROR_INVALID_PARAMETER     Indicates an error that
 *                                                      the input parameters are
 *                                                      invalid.
 *                      SGX_ERROR_INVALID_STATE         Indicates this function
 *                                                      is called out of order.
 */
sgx_status_t SGXAPI sgx_ra_get_keys(
    sgx_ra_context_t context,
    sgx_ra_key_type_t type,
    sgx_ra_key_128_t *p_key);

/*
 * Call the sgx_ra_close function to release the remote attestation and key
 * exchange context after the process is done and the context isn't needed
 * anymore.
 *
 * @param context   Context returned by sgx_ra_init.
 * @return sgx_status_t SGX_SUCCESS                     Indicates success.
 *                      SGX_ERROR_INVALID_PARAMETER     Indicates an error that
 *                                                      the input parameters are
 *                                                      invalid.
 */
sgx_status_t SGXAPI sgx_ra_close(
    sgx_ra_context_t context);

#ifdef  __cplusplus
}
#endif

#endif
