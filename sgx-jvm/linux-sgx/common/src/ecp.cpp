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


#include "sgx_ecp_types.h"
#include "ecp_interface.h"
#include "stdlib.h"
#include "string.h"

#ifndef ERROR_BREAK
#define ERROR_BREAK(x)  if(x != ippStsNoErr){break;}
#endif
#ifndef NULL_BREAK
#define NULL_BREAK(x)   if(!x){break;}
#endif
#ifndef SAFE_FREE
#define SAFE_FREE(ptr) {if (NULL != (ptr)) {free(ptr); (ptr)=NULL;}}
#endif

#define MAC_KEY_SIZE       16

#define EC_DERIVATION_BUFFER_SIZE(label_length) ((label_length) +4)

sgx_status_t derive_key(
    const sgx_ec256_dh_shared_t* shared_key,
    const char* label,
    uint32_t label_length,
    sgx_ec_key_128bit_t* derived_key)
{
    sgx_status_t se_ret = SGX_SUCCESS;
    uint8_t cmac_key[MAC_KEY_SIZE];
    sgx_ec_key_128bit_t key_derive_key;
    if (!shared_key || !derived_key || !label)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    /*check integer overflow */
    if (label_length > EC_DERIVATION_BUFFER_SIZE(label_length))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    memset(cmac_key, 0, MAC_KEY_SIZE);
    se_ret = sgx_rijndael128_cmac_msg((sgx_cmac_128bit_key_t *)cmac_key,
        (uint8_t*)shared_key,
        sizeof(sgx_ec256_dh_shared_t),
        (sgx_cmac_128bit_tag_t *)&key_derive_key);
    if (SGX_SUCCESS != se_ret)
    {
        memset_s(&key_derive_key, sizeof(key_derive_key), 0, sizeof(key_derive_key));
        INTERNAL_SGX_ERROR_CODE_CONVERTOR(se_ret);
        return se_ret;
    }
    /* derivation_buffer = counter(0x01) || label || 0x00 || output_key_len(0x0080) */
    uint32_t derivation_buffer_length = EC_DERIVATION_BUFFER_SIZE(label_length);
    uint8_t *p_derivation_buffer = (uint8_t *)malloc(derivation_buffer_length);
    if (p_derivation_buffer == NULL)
    {
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    memset(p_derivation_buffer, 0, derivation_buffer_length);

    /*counter = 0x01 */
    p_derivation_buffer[0] = 0x01;
    /*label*/
    memcpy(&p_derivation_buffer[1], label, label_length);
    /*output_key_len=0x0080*/
    uint16_t *key_len = (uint16_t *)&p_derivation_buffer[derivation_buffer_length - 2];
    *key_len = 0x0080;

    se_ret = sgx_rijndael128_cmac_msg((sgx_cmac_128bit_key_t *)&key_derive_key,
                                      p_derivation_buffer,
                                      derivation_buffer_length,
                                      (sgx_cmac_128bit_tag_t *)derived_key);
    memset_s(&key_derive_key, sizeof(key_derive_key), 0, sizeof(key_derive_key));
    free(p_derivation_buffer);
    if(SGX_SUCCESS != se_ret)
    {
        INTERNAL_SGX_ERROR_CODE_CONVERTOR(se_ret);
    }
    return se_ret;
}
