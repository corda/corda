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


#ifndef _SE_SIG_RL_H_
#define _SE_SIG_RL_H_

#include "se_types.h"
#include "epid/common/types.h"
#include "sgx_error.h"

#ifdef  __cplusplus
extern "C" {
#endif


#define SE_EPID_SIG_RL_VERSION      0x200
#define SE_EPID_SIG_RL_ID           0xE00
/* This is the size of ECDSA signature appended at the end of SIG-RL,
   in bytes. */
#define SE_ECDSA_SIGN_SIZE             32

#pragma pack(push, 1)
typedef struct _se_sig_rl_t {
    uint16_t  protocol_version;   /* Big-endian*/
    uint16_t  epid_identifier;    /* Big-endian, 14 for sig_rl.*/
    SigRl sig_rl;
}se_sig_rl_t;

typedef struct _se_ae_ecdsa_hash_t {
    uint32_t hash[8];
}se_ae_ecdsa_hash_t;
#pragma pack(pop)

uint64_t se_get_sig_rl_size(const se_sig_rl_t *p_sig_rl);

#ifndef _SGX_UAE_SERVICE_H_
sgx_status_t sgx_calc_quote_size(const uint8_t *sig_rl, uint32_t sig_rl_size, uint32_t* p_quote_size);
sgx_status_t sgx_get_quote_size(const uint8_t *sig_rl, uint32_t* p_quote_size);
#endif

#ifdef __cplusplus
 }
#endif


#endif

