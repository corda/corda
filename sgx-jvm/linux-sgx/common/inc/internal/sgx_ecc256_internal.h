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

#ifndef _SGX_ECC256_INTERNAL_H
#define _SGX_ECC256_INTERNAL_H

#include "sgx_tcrypto.h"

typedef struct _sgx_ec256_shared_point_t
{
    uint8_t x[SGX_ECP256_KEY_SIZE];
    uint8_t y[SGX_ECP256_KEY_SIZE];
} sgx_ec256_shared_point_t;

#ifdef __cplusplus
extern "C"
#endif

/* NOTE: The function is for internal use ONLY
 *
 * Computes a point with scalar multiplication based on private B key (local) and remote public Ga Key 
 * Parameters:
 *   Return: sgx_status_t - SGX_SUCCESS or failure as defined in sgx_error.h
 *   Inputs: sgx_ecc_state_handle_t ecc_handle - Handle to the ECC crypto system
 *           sgx_ec256_private_t *p_private_b - Pointer to the local private key
 *           sgx_ec256_public_t *p_public_ga - Pointer to the remote public key
 *   Output: sgx_ec256_shared_point_t *p_shared_key - Pointer to the target shared point
 */
sgx_status_t SGXAPI sgx_ecc256_compute_shared_point(sgx_ec256_private_t *p_private_b,
                                                     sgx_ec256_public_t *p_public_ga,
                                                     sgx_ec256_shared_point_t *p_shared_key,
                                                     sgx_ecc_state_handle_t ecc_handle);


#endif
