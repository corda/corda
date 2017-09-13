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


#include "sgx_sha256_128.h"

/*
** SHA256-128 implementation:
**    out-length := x ¨C number of bits to output
**    prefix := SHA-256(out-length)
**    digest := SHA-256(prefix || m)
**    output := truncate(digest, out-length) ? always return first out-length bits
*/
sgx_status_t SGXAPI sgx_sha256_128_msg(const uint8_t *p_src, uint32_t src_len, sgx_sha256_128_hash_t *p_hash)
{
    uint32_t outlength = 128;  /*number of bits to output */
    uint32_t sha256_128_digest_length;
    sgx_status_t ret;
    sgx_sha256_hash_t digest = {0};
    uint8_t* digest_buffer = NULL;

    /* check potential overflow and NULL pointer */
    if( (UINT32_MAX-src_len) < sizeof(sgx_sha256_hash_t) || !p_hash || !p_src)
        return SGX_ERROR_INVALID_PARAMETER;

    sha256_128_digest_length = (uint32_t)sizeof(sgx_sha256_hash_t)+ src_len;

    digest_buffer = (uint8_t*)malloc(sha256_128_digest_length);
    if(!digest_buffer)
        return SGX_ERROR_OUT_OF_MEMORY;
    memset(digest_buffer, 0, sha256_128_digest_length);

    /* get prefix := SHA-256(out-length) */
    ret = sgx_sha256_msg((const uint8_t*)&outlength, sizeof(uint32_t), (sgx_sha256_hash_t*)digest_buffer);
    if(SGX_SUCCESS != ret)
        goto clean_up;

    /* get digest := SHA-256(prefix || m) */
    memcpy(digest_buffer+sizeof(sgx_sha256_hash_t), p_src, src_len); /* copy m to digest_buffer */
    ret = sgx_sha256_msg((const uint8_t*)digest_buffer, sha256_128_digest_length, &digest);
    if(SGX_SUCCESS != ret)
        goto clean_up;

    /* output truncated hash
     return the first 128 bits */
    memcpy(p_hash, &digest, sizeof(sgx_sha256_128_hash_t));

clean_up:
    if(digest_buffer)
        free(digest_buffer);

    return ret;
}
