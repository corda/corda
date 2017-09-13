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



#ifndef _SGX_ECP_TYPES_H_
#define _SGX_ECP_TYPES_H_

#include <stdint.h>

#pragma pack(push, 1)

#include "sgx_tcrypto.h"

#ifndef SGX_FEBITSIZE
#define SGX_FEBITSIZE                   256
#endif

typedef struct _ecc_param_t
{
    uint32_t eccP[SGX_NISTP_ECP256_KEY_SIZE];     /* EC prime field */
    uint32_t eccA[SGX_NISTP_ECP256_KEY_SIZE];     /* EC curve coefficient A */
    uint32_t eccB[SGX_NISTP_ECP256_KEY_SIZE];     /* EC curve coefficient B */
    uint32_t eccG[2][SGX_NISTP_ECP256_KEY_SIZE];  /* ECC base point */
    uint32_t eccR[SGX_NISTP_ECP256_KEY_SIZE];     /* ECC base point order */
} sgx_ecc_param_t;

typedef uint8_t sgx_ec_key_128bit_t[SGX_CMAC_KEY_SIZE];

#pragma pack(pop)

#endif
