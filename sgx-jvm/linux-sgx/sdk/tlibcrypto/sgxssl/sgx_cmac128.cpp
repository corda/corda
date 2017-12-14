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

#include "stdlib.h"
#include "string.h"
#include "sgx_tcrypto.h"
#include "se_tcrypto_common.h"
#include "openssl/cmac.h"
#include "openssl/err.h"

/* Message Authentication - Rijndael 128 CMAC
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined sgx_error.h
*   Inputs: sgx_cmac_128bit_key_t *p_key - Pointer to key used in encryption/decryption operation
*           uint8_t *p_src - Pointer to input stream to be MACed
*           uint32_t src_len - Length of input stream to be MACed
*   Output: sgx_cmac_gcm_128bit_tag_t *p_mac - Pointer to resultant MAC */
sgx_status_t sgx_rijndael128_cmac_msg(const sgx_cmac_128bit_key_t *p_key, const uint8_t *p_src,
                                      uint32_t src_len, sgx_cmac_128bit_tag_t *p_mac)
{
	void* pState = NULL;

	if ((p_key == NULL) || (p_src == NULL) || (p_mac == NULL))  {
		return SGX_ERROR_INVALID_PARAMETER;
	}

	size_t mactlen;
	sgx_status_t ret = SGX_ERROR_UNEXPECTED;

	CLEAR_OPENSSL_ERROR_QUEUE;

	do {
		//create a new ctx of CMAC
		//
		pState = CMAC_CTX_new();
		if (pState == NULL) {
			ret = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// init CMAC ctx with the corresponding size, key and AES alg.
		//
		if (!CMAC_Init((CMAC_CTX*)pState, (const void *)p_key, SGX_CMAC_KEY_SIZE, EVP_aes_128_cbc(), NULL)) {
			break;
		}

		// perform CMAC hash on p_src
		//
		if (!CMAC_Update((CMAC_CTX *)pState, p_src, src_len)) {
			break;
		}

		// finalize CMAC hashing
		//
		if (!CMAC_Final((CMAC_CTX*)pState, (unsigned char*)p_mac, &mactlen)) {
			break;
		}

		//validate mac size
		//
		if (mactlen != SGX_CMAC_MAC_SIZE) {
			break;
		}

		ret = SGX_SUCCESS;
	} while (0);

	// in case of error in debug mode, update openssl last error variable.
	//
	if (ret != SGX_SUCCESS) {
        GET_LAST_OPENSSL_ERROR;
	}

	// we're done, clear and free CMAC ctx
	//
	if (pState) {
		CMAC_CTX_free((CMAC_CTX*)pState);
	}
	return ret;
}

/* Allocates and initializes CMAC state
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.h
*   Inputs: sgx_cmac_128bit_key_t *p_key - Pointer to the key used in encryption/decryption operation
*   Output: sgx_cmac_state_handle_t *p_cmac_handle - Pointer to the handle of the CMAC state  */
sgx_status_t sgx_cmac128_init(const sgx_cmac_128bit_key_t *p_key, sgx_cmac_state_handle_t* p_cmac_handle)

{
	if ((p_key == NULL) || (p_cmac_handle == NULL)) {
		return SGX_ERROR_INVALID_PARAMETER;
	}

	sgx_status_t ret = SGX_ERROR_UNEXPECTED;
	void* pState = NULL;

	CLEAR_OPENSSL_ERROR_QUEUE;

	do {
		// create CMAC ctx
		//
		pState = CMAC_CTX_new();
		if (pState == NULL) {
			ret = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		//init CMAC ctx
		//
		if (!CMAC_Init((CMAC_CTX*)pState, (const void *)p_key, SGX_CMAC_KEY_SIZE, EVP_aes_128_cbc(), NULL)) {
			CMAC_CTX_free((CMAC_CTX*)pState);
			break;
		}

		*p_cmac_handle = pState;
		ret = SGX_SUCCESS;
	} while (0);

	// in case of error in debug mode, update openssl last error variable.
	//
	if (ret != SGX_SUCCESS) {
		GET_LAST_OPENSSL_ERROR;
	}

	return ret;
}

/* Updates CMAC has calculation based on the input message
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.
*	Input:  sgx_cmac_state_handle_t cmac_handle - Handle to the CMAC state
*	        uint8_t *p_src - Pointer to the input stream to be hashed
*          uint32_t src_len - Length of the input stream to be hashed  */
sgx_status_t sgx_cmac128_update(const uint8_t *p_src, uint32_t src_len, sgx_cmac_state_handle_t cmac_handle)

{
	if ((p_src == NULL) || (cmac_handle == NULL)) {
		return SGX_ERROR_INVALID_PARAMETER;
	}

	CLEAR_OPENSSL_ERROR_QUEUE;

	if (!CMAC_Update((CMAC_CTX *)cmac_handle, p_src, src_len)) {
        GET_LAST_OPENSSL_ERROR;
		return SGX_ERROR_UNEXPECTED;
	}
	return SGX_SUCCESS;
}

/* Returns Hash calculation
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.h
*	Input:  sgx_cmac_state_handle_t cmac_handle - Handle to the CMAC state
*   Output: sgx_cmac_128bit_tag_t *p_hash - Resultant hash from operation  */
sgx_status_t sgx_cmac128_final(sgx_cmac_state_handle_t cmac_handle, sgx_cmac_128bit_tag_t *p_hash)

{
	if ((cmac_handle == NULL) || (p_hash == NULL))
	{
		return SGX_ERROR_INVALID_PARAMETER;
	}

	size_t mactlen;

	CLEAR_OPENSSL_ERROR_QUEUE;

	if (!CMAC_Final((CMAC_CTX*)cmac_handle, (unsigned char*)p_hash, &mactlen)) {
        GET_LAST_OPENSSL_ERROR;
		return SGX_ERROR_UNEXPECTED;
	}
	return SGX_SUCCESS;
}


/* Clean up the CMAC state
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.h
*   Input:  sgx_cmac_state_handle_t cmac_handle  - Handle to the CMAC state  */
sgx_status_t sgx_cmac128_close(sgx_cmac_state_handle_t cmac_handle)
{
	if (cmac_handle == NULL) {
		return SGX_ERROR_INVALID_PARAMETER;
	}

	CMAC_CTX* pState = (CMAC_CTX*)cmac_handle;
	CMAC_CTX_free(pState);
	return SGX_SUCCESS;
}
