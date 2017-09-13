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

#ifndef _SGX_KEY_EXCHANGE_H_
#define _SGX_KEY_EXCHANGE_H_

#include <stdint.h>
#include "sgx_quote.h"
#include "sgx_ecp_types.h"
#include "sgx_tae_service.h"

#ifdef  __cplusplus
extern "C" {
#endif

typedef uint32_t sgx_ra_context_t;

typedef sgx_key_128bit_t sgx_ra_key_128_t;

typedef enum _ra_key_type_t
{
    SGX_RA_KEY_SK = 1,
    SGX_RA_KEY_MK,
    SGX_RA_KEY_VK,
} sgx_ra_key_type_t;

typedef struct _ra_msg1_t
{
    sgx_ec256_public_t       g_a;         /* the Endian-ness of Ga is Little-Endian */
    sgx_epid_group_id_t      gid;         /* the Endian-ness of GID is Little-Endian */
} sgx_ra_msg1_t;


typedef struct _ra_msg2_t
{
    sgx_ec256_public_t       g_b;         /* the Endian-ness of Gb is Little-Endian */
    sgx_spid_t               spid;
    uint16_t                 quote_type;  /* unlinkable Quote(0) or linkable Quote(1) in little endian*/
    uint16_t                 kdf_id;      /* key derivation function id in little endian. */
    sgx_ec256_signature_t    sign_gb_ga;  /* In little endian */
    sgx_mac_t                mac;         /* mac_smk(g_b||spid||quote_type||kdf_id||sign_gb_ga) */
    uint32_t                 sig_rl_size;
    uint8_t                  sig_rl[];
} sgx_ra_msg2_t;

typedef struct _ra_msg3_t
{
    sgx_mac_t                mac;         /* mac_smk(g_a||ps_sec_prop||quote) */
    sgx_ec256_public_t       g_a;         /* the Endian-ness of Ga is Little-Endian */
    sgx_ps_sec_prop_desc_t   ps_sec_prop;
    uint8_t                  quote[];
} sgx_ra_msg3_t;

#ifdef  __cplusplus
}
#endif

#endif
