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

#ifndef _SE_QUOTE_INTERNAL_H_
#define _SE_QUOTE_INTERNAL_H_

#include "se_types.h"
#include "epid/common/types.h"

#ifdef  __cplusplus
extern "C" {
#endif

#define QUOTE_IV_SIZE    12

#pragma pack(push, 1)
typedef struct _se_wrap_key_t {
    uint8_t             encrypted_key[256];
    uint8_t             key_hash[32];
} se_wrap_key_t;

typedef struct _se_encrypted_sign
{
    se_wrap_key_t       wrap_key;               /* 0 */
    uint8_t             iv[QUOTE_IV_SIZE];      /* 288 */
    uint32_t            payload_size;           /* 300 */
    BasicSignature      basic_sign;             /* 304, this field is encrypted, and contributes to the mac */
    uint32_t            rl_ver;                 /* 656, this field is encrypted, and contributes to the mac */
    uint32_t            rl_num;                 /* 660, this field is encrypted, and contributes to the mac */
    uint8_t             nrp_mac[];              /* 664, this filed contains the encrypted nrps followed by the mac */
}se_encrypted_sign_t;
#pragma pack(pop)

#define SE_QUOTE_LENGTH_WITHOUT_SIG         (sizeof(sgx_quote_t)        \
                                            + sizeof(se_wrap_key_t)     \
                                            + QUOTE_IV_SIZE             \
                                            + sizeof(uint32_t)          \
                                            + sizeof(sgx_mac_t))


#ifdef  __cplusplus
}
#endif

#endif
