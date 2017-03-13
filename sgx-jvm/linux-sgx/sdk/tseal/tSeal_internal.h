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


#ifndef _TSEAL_INTERNAL_H_
#define _TSEAL_INTERNAL_H_

#include <stdint.h>
#include "sgx_tseal.h"

/* set MISCMASK.exinfo_bit = 0 for data migration to the enclave 
   built with the SDK that supports exinfo bit */
#define SGX_MISCSEL_EXINFO     0x00000001  /* report #PF and #GP inside enclave */
#define TSEAL_DEFAULT_MISCMASK (~SGX_MISCSEL_EXINFO)

#ifdef __cplusplus
extern "C" {
#endif

    sgx_status_t sgx_seal_data_iv(const uint32_t additional_MACtext_length,
        const uint8_t *p_additional_MACtext, const uint32_t text2encrypt_length,
        const uint8_t *p_text2encrypt, const uint8_t *p_payload_iv,
        const sgx_key_request_t* p_key_request, sgx_sealed_data_t *p_sealed_data);

    sgx_status_t sgx_unseal_data_helper(const sgx_sealed_data_t *p_sealed_data, uint8_t *p_additional_MACtext,
        uint32_t additional_MACtext_length, uint8_t *p_decrypted_text,
        uint32_t decrypted_text_length);

#ifdef __cplusplus
}
#endif

#endif
