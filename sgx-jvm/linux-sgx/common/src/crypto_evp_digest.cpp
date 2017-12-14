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

#include <openssl/evp.h>
#include <stdint.h>
#include <se_memcpy.h>
#include "crypto_wrapper.h"


sgx_status_t sgx_EVP_Digest(const EVP_MD *type, const uint8_t *p_src, unsigned int src_len, uint8_t *digest, unsigned int *digest_len)
{
    if(!type || !p_src || src_len == 0 || !digest || digest_len == 0)
        return SGX_ERROR_INVALID_PARAMETER;

    uint8_t tmp_digest[EVP_MAX_MD_SIZE];
    memset(tmp_digest, 0, EVP_MAX_MD_SIZE);
    unsigned int tmp_digest_len;

    unsigned int digest_buf_len = *digest_len;

    EVP_MD_CTX *ctx;
    if(NULL == (ctx = EVP_MD_CTX_create()))
        return SGX_ERROR_OUT_OF_MEMORY;

    if(!EVP_DigestInit_ex(ctx, type, NULL))
    {
        EVP_MD_CTX_destroy(ctx);
        return SGX_ERROR_UNEXPECTED;
    }
    if(!EVP_DigestUpdate(ctx, p_src, src_len))
    {
        EVP_MD_CTX_destroy(ctx);
        return SGX_ERROR_UNEXPECTED;
    }
    if(!EVP_DigestFinal_ex(ctx, tmp_digest, &tmp_digest_len))
    {
        EVP_MD_CTX_destroy(ctx);
        return SGX_ERROR_UNEXPECTED;
    }
    EVP_MD_CTX_destroy(ctx);

    if(tmp_digest_len > digest_buf_len)
    {
        return SGX_ERROR_UNEXPECTED;
    }
    if(memcpy_s(digest, digest_buf_len, tmp_digest, tmp_digest_len))
        return SGX_ERROR_UNEXPECTED;

    *digest_len = tmp_digest_len;
    return SGX_SUCCESS;
}
