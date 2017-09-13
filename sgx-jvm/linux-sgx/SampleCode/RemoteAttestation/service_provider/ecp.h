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

#ifndef _ECP_H
#define _ECP_H

#include <stdint.h>
#include <stdlib.h>

#include "remote_attestation_result.h"

#ifndef SAMPLE_FEBITSIZE
    #define SAMPLE_FEBITSIZE                    256
#endif

#define SAMPLE_ECP_KEY_SIZE                     (SAMPLE_FEBITSIZE/8)

typedef struct sample_ec_priv_t
{
    uint8_t r[SAMPLE_ECP_KEY_SIZE];
} sample_ec_priv_t;

typedef struct sample_ec_dh_shared_t
{
    uint8_t s[SAMPLE_ECP_KEY_SIZE];
}sample_ec_dh_shared_t;

typedef uint8_t sample_ec_key_128bit_t[16];

#define SAMPLE_EC_MAC_SIZE 16

#ifdef  __cplusplus
extern "C" {
#endif


#ifndef _ERRNO_T_DEFINED
#define _ERRNO_T_DEFINED
typedef int errno_t;
#endif
errno_t memcpy_s(void *dest, size_t numberOfElements, const void *src,
                 size_t count);


#ifdef SUPPLIED_KEY_DERIVATION

typedef enum _sample_derive_key_type_t
{
    SAMPLE_DERIVE_KEY_SMK_SK = 0,
    SAMPLE_DERIVE_KEY_MK_VK,
} sample_derive_key_type_t;

bool derive_key(
    const sample_ec_dh_shared_t *p_shared_key,
    uint8_t key_id,
    sample_ec_key_128bit_t *first_derived_key,
    sample_ec_key_128bit_t *second_derived_key);

#else

typedef enum _sample_derive_key_type_t
{
    SAMPLE_DERIVE_KEY_SMK = 0,
    SAMPLE_DERIVE_KEY_SK,
    SAMPLE_DERIVE_KEY_MK,
    SAMPLE_DERIVE_KEY_VK,
} sample_derive_key_type_t;

bool derive_key(
    const sample_ec_dh_shared_t *p_shared_key,
    uint8_t key_id,
    sample_ec_key_128bit_t *derived_key);

#endif

bool verify_cmac128(
    sample_ec_key_128bit_t mac_key,
    const uint8_t *p_data_buf,
    uint32_t buf_size,
    const uint8_t *p_mac_buf);
#ifdef  __cplusplus
}
#endif

#endif

