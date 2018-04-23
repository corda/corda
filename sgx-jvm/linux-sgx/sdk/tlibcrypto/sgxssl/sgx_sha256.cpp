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

#include "se_tcrypto_common.h"
#include <openssl/evp.h>
#include <openssl/err.h>
#include "sgx_tcrypto.h"
#include "stdlib.h"

/* Allocates and initializes sha256 state
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.h
*   Output: sgx_sha_state_handle_t *p_sha_handle - Pointer to the handle of the SHA256 state  */
sgx_status_t sgx_sha256_init(sgx_sha_state_handle_t* p_sha_handle)
{
    if (p_sha_handle == NULL) {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    EVP_MD_CTX* evp_ctx = NULL;
    const EVP_MD* sha256_md = NULL;
    sgx_status_t retval = SGX_ERROR_UNEXPECTED;
    CLEAR_OPENSSL_ERROR_QUEUE;

    do {
	    /* allocates, initializes and returns a digest context */
	    evp_ctx = EVP_MD_CTX_new();
	    if (evp_ctx == NULL) {
		retval = SGX_ERROR_OUT_OF_MEMORY;
		break;
	    }

	    /* return EVP_MD structures for SHA256 digest algorithm */
	    sha256_md = EVP_sha256();
	    if (sha256_md == NULL) {
		break;
	    }

	    /* sets up digest context ctx to use a digest type, if impl is NULL then the default implementation of digest type is used */
	    if (EVP_DigestInit_ex(evp_ctx, sha256_md, NULL) != 1) {
		break;
	    }

	    *p_sha_handle = evp_ctx;
	    retval = SGX_SUCCESS;
    } while(0);

    if (SGX_SUCCESS != retval) {
        GET_LAST_OPENSSL_ERROR;
        if (evp_ctx != NULL) {
            EVP_MD_CTX_free(evp_ctx);
        }
    }

    return retval;
}

/* Updates sha256 has calculation based on the input message
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.
*   Input:  sgx_sha_state_handle_t sha_handle - Handle to the SHA256 state
*           uint8_t *p_src - Pointer to the input stream to be hashed
*           uint32_t src_len - Length of the input stream to be hashed  */
sgx_status_t sgx_sha256_update(const uint8_t *p_src, uint32_t src_len, sgx_sha_state_handle_t sha_handle)
{
    if ((p_src == NULL) || (sha_handle == NULL))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    sgx_status_t retval = SGX_ERROR_UNEXPECTED;
    CLEAR_OPENSSL_ERROR_QUEUE;

    do {
	    /* hashes src_len bytes of data at p_src into the digest context sha_handle */
	    if(EVP_DigestUpdate((EVP_MD_CTX*)sha_handle, p_src, src_len) != 1) {
		GET_LAST_OPENSSL_ERROR;
		break;
	    }

	    retval = SGX_SUCCESS;
    } while (0);

    return retval;
}

/* Returns Hash calculation
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.h
*   Input:  sgx_sha_state_handle_t sha_handle - Handle to the SHA256 state
*   Output: sgx_sha256_hash_t *p_hash - Resultant hash from operation  */
sgx_status_t sgx_sha256_get_hash(sgx_sha_state_handle_t sha_handle, sgx_sha256_hash_t *p_hash)
{
    if ((sha_handle == NULL) || (p_hash == NULL))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    sgx_status_t retval = SGX_ERROR_UNEXPECTED;
    unsigned int hash_len = 0;
    CLEAR_OPENSSL_ERROR_QUEUE;

    do {
	    /* retrieves the digest value from sha_handle and places it in p_hash */
	    if (EVP_DigestFinal_ex((EVP_MD_CTX*)sha_handle, (unsigned char *)p_hash, &hash_len) != 1) {
		GET_LAST_OPENSSL_ERROR;
		break;
	    }

	    if (SGX_SHA256_HASH_SIZE != hash_len) {
		break;
	    }

	    retval = SGX_SUCCESS;
    } while(0);

    return retval;
}


/* Cleans up sha state
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.h
*   Input:  sgx_sha_state_handle_t sha_handle - Handle to the SHA256 state  */
sgx_status_t sgx_sha256_close(sgx_sha_state_handle_t sha_handle)
{
    if (sha_handle == NULL)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    EVP_MD_CTX_free((EVP_MD_CTX*)sha_handle);

    return SGX_SUCCESS;
}
