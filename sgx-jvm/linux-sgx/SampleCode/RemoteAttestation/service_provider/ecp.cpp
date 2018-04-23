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



#include <stdlib.h>
#include <string.h>
#include "ecp.h"

#include "sample_libcrypto.h"


#define MAC_KEY_SIZE       16

errno_t memcpy_s(
    void *dest,
    size_t numberOfElements,
    const void *src,
    size_t count)
{
    if(numberOfElements<count)
        return -1;
    memcpy(dest, src, count);
    return 0;
}

bool verify_cmac128(
    sample_ec_key_128bit_t mac_key,
    const uint8_t *p_data_buf,
    uint32_t buf_size,
    const uint8_t *p_mac_buf)
{
    uint8_t data_mac[SAMPLE_EC_MAC_SIZE];
    sample_status_t sample_ret;

    sample_ret = sample_rijndael128_cmac_msg((sample_cmac_128bit_key_t*)mac_key,
        p_data_buf,
        buf_size,
        (sample_cmac_128bit_tag_t *)data_mac);
    if(sample_ret != SAMPLE_SUCCESS)
        return false;
    // In real implementation, should use a time safe version of memcmp here,
    // in order to avoid side channel attack.
    if(!memcmp(p_mac_buf, data_mac, SAMPLE_EC_MAC_SIZE))
        return true;
    return false;
}


#ifdef SUPPLIED_KEY_DERIVATION

#pragma message ("Supplied key derivation function is used.")

typedef struct _hash_buffer_t
{
    uint8_t counter[4];
    sample_ec_dh_shared_t shared_secret;
    uint8_t algorithm_id[4];
} hash_buffer_t;

const char ID_U[] = "SGXRAENCLAVE";
const char ID_V[] = "SGXRASERVER";

// Derive two keys from shared key and key id.
bool derive_key(
    const sample_ec_dh_shared_t *p_shared_key,
    uint8_t key_id,
    sample_ec_key_128bit_t *first_derived_key,
    sample_ec_key_128bit_t *second_derived_key)
{
    sample_status_t sample_ret = SAMPLE_SUCCESS;
    hash_buffer_t hash_buffer;
    sample_sha_state_handle_t sha_context;
    sample_sha256_hash_t key_material;
    
    memset(&hash_buffer, 0, sizeof(hash_buffer_t));

    /* counter in big endian  */
    hash_buffer.counter[3] = key_id;

    /*convert from little endian to big endian */
    for (size_t i = 0; i < sizeof(sample_ec_dh_shared_t) ; i++)
    {
        hash_buffer.shared_secret.s[i] = p_shared_key->s[sizeof(p_shared_key->s) - 1 - i];
    }

    sample_ret = sample_sha256_init(&sha_context);
    if (sample_ret != SAMPLE_SUCCESS)
    {
        return false;
    }
    sample_ret = sample_sha256_update((uint8_t*)&hash_buffer, sizeof(hash_buffer_t), sha_context);
    if (sample_ret != SAMPLE_SUCCESS)
    {
        sample_sha256_close(sha_context);
        return false;
    }
    sample_ret = sample_sha256_update((uint8_t*)ID_U, sizeof(ID_U), sha_context);
    if (sample_ret != SAMPLE_SUCCESS)
    {
        sample_sha256_close(sha_context);
        return false;
    }
    sample_ret = sample_sha256_update((uint8_t*)ID_V, sizeof(ID_V), sha_context);
    if (sample_ret != SAMPLE_SUCCESS)
    {
        sample_sha256_close(sha_context);
        return false;
    }
    sample_ret = sample_sha256_get_hash(sha_context, &key_material);
    if (sample_ret != SAMPLE_SUCCESS)
    {
        sample_sha256_close(sha_context);
        return false;
    }
    sample_ret = sample_sha256_close(sha_context);

    static_assert(sizeof(sample_ec_key_128bit_t)* 2 == sizeof(sample_sha256_hash_t), "structure size mismatch.");
    memcpy_s(first_derived_key, sizeof(sample_ec_key_128bit_t), &key_material, sizeof(sample_ec_key_128bit_t));
    memcpy_s(second_derived_key, sizeof(sample_ec_key_128bit_t), (uint8_t*)&key_material + sizeof(sample_ec_key_128bit_t), sizeof(sample_ec_key_128bit_t));

    // memset here can be optimized away by compiler, so please use memset_s on
    // windows for production code and similar functions on other OSes.
    memset(&key_material, 0, sizeof(sample_sha256_hash_t));

    return true;
}

#else

#pragma message ("Default key derivation function is used.")

#define EC_DERIVATION_BUFFER_SIZE(label_length) ((label_length) +4)

const char str_SMK[] = "SMK";
const char str_SK[] = "SK";
const char str_MK[] = "MK";
const char str_VK[] = "VK";

// Derive key from shared key and key id.
// key id should be sample_derive_key_type_t.
bool derive_key(
    const sample_ec_dh_shared_t *p_shared_key,
    uint8_t key_id,
    sample_ec_key_128bit_t* derived_key)
{
    sample_status_t sample_ret = SAMPLE_SUCCESS;
    uint8_t cmac_key[MAC_KEY_SIZE];
    sample_ec_key_128bit_t key_derive_key;
    
    memset(&cmac_key, 0, MAC_KEY_SIZE);

    sample_ret = sample_rijndael128_cmac_msg(
        (sample_cmac_128bit_key_t *)&cmac_key,
        (uint8_t*)p_shared_key,
        sizeof(sample_ec_dh_shared_t),
        (sample_cmac_128bit_tag_t *)&key_derive_key);
    if (sample_ret != SAMPLE_SUCCESS)
    {
        // memset here can be optimized away by compiler, so please use memset_s on
        // windows for production code and similar functions on other OSes.
        memset(&key_derive_key, 0, sizeof(key_derive_key));
        return false;
    }

    const char *label = NULL;
    uint32_t label_length = 0;
    switch (key_id)
    {
    case SAMPLE_DERIVE_KEY_SMK:
        label = str_SMK;
        label_length = sizeof(str_SMK) -1;
        break;
    case SAMPLE_DERIVE_KEY_SK:
        label = str_SK;
        label_length = sizeof(str_SK) -1;
        break;
    case SAMPLE_DERIVE_KEY_MK:
        label = str_MK;
        label_length = sizeof(str_MK) -1;
        break;
    case SAMPLE_DERIVE_KEY_VK:
        label = str_VK;
        label_length = sizeof(str_VK) -1;
        break;
    default:
        // memset here can be optimized away by compiler, so please use memset_s on
        // windows for production code and similar functions on other OSes.
        memset(&key_derive_key, 0, sizeof(key_derive_key));
        return false;
        break;
    }
    /* derivation_buffer = counter(0x01) || label || 0x00 || output_key_len(0x0080) */
    uint32_t derivation_buffer_length = EC_DERIVATION_BUFFER_SIZE(label_length);
    uint8_t *p_derivation_buffer = (uint8_t *)malloc(derivation_buffer_length);
    if (p_derivation_buffer == NULL)
    {
        // memset here can be optimized away by compiler, so please use memset_s on
        // windows for production code and similar functions on other OSes.
        memset(&key_derive_key, 0, sizeof(key_derive_key));
        return false;
    }
    memset(p_derivation_buffer, 0, derivation_buffer_length);

    /*counter = 0x01 */
    p_derivation_buffer[0] = 0x01;
    /*label*/
    memcpy_s(&p_derivation_buffer[1], derivation_buffer_length - 1, label, label_length);
    /*output_key_len=0x0080*/
    uint16_t *key_len = (uint16_t *)(&(p_derivation_buffer[derivation_buffer_length - 2]));
    *key_len = 0x0080;


    sample_ret = sample_rijndael128_cmac_msg(
        (sample_cmac_128bit_key_t *)&key_derive_key,
        p_derivation_buffer,
        derivation_buffer_length,
        (sample_cmac_128bit_tag_t *)derived_key);
    free(p_derivation_buffer);
    // memset here can be optimized away by compiler, so please use memset_s on
    // windows for production code and similar functions on other OSes.
    memset(&key_derive_key, 0, sizeof(key_derive_key));
    if (sample_ret != SAMPLE_SUCCESS)
    {
        return false;
    }
    return true;
}
#endif
