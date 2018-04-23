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
#include "openssl/aes.h"
#include "openssl/evp.h"
#include "openssl/err.h"
#define OPENSSL_DEFAULT_IV_LEN 12

/* Rijndael AES-GCM
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined sgx_error.h
*   Inputs: sgx_aes_gcm_128bit_key_t *p_key - Pointer to key used in encryption/decryption operation
*           uint8_t *p_src - Pointer to input stream to be encrypted/decrypted
*           uint32_t src_len - Length of input stream to be encrypted/decrypted
*           uint8_t *p_iv - Pointer to initialization vector to use
*           uint32_t iv_len - Length of initialization vector
*           uint8_t *p_aad - Pointer to input stream of additional authentication data
*           uint32_t aad_len - Length of additional authentication data stream
*           sgx_aes_gcm_128bit_tag_t *p_in_mac - Pointer to expected MAC in decryption process
*   Output: uint8_t *p_dst - Pointer to cipher text. Size of buffer should be >= src_len.
*           sgx_aes_gcm_128bit_tag_t *p_out_mac - Pointer to MAC generated from encryption process
* NOTE: Wrapper is responsible for confirming decryption tag matches encryption tag */
sgx_status_t sgx_rijndael128GCM_encrypt(const sgx_aes_gcm_128bit_key_t *p_key, const uint8_t *p_src, uint32_t src_len,
                                        uint8_t *p_dst, const uint8_t *p_iv, uint32_t iv_len, const uint8_t *p_aad, uint32_t aad_len,
                                        sgx_aes_gcm_128bit_tag_t *p_out_mac)
{
	if ((src_len > INT_MAX) || (aad_len > INT_MAX) || (p_key == NULL) || ((src_len > 0) && (p_dst == NULL)) || ((src_len > 0) && (p_src == NULL))
		|| (p_out_mac == NULL) || (iv_len != SGX_AESGCM_IV_SIZE) || ((aad_len > 0) && (p_aad == NULL))
		|| (p_iv == NULL) || ((p_src == NULL) && (p_aad == NULL)))
	{
		return SGX_ERROR_INVALID_PARAMETER;
	}
	sgx_status_t ret = SGX_ERROR_UNEXPECTED;
	int len = 0;
	EVP_CIPHER_CTX * pState = NULL;

	CLEAR_OPENSSL_ERROR_QUEUE;

	do {
		// Create and init ctx
		//
		if (!(pState = EVP_CIPHER_CTX_new())) {
			ret = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// Initialise encrypt, key and IV
		//
		if (1 != EVP_EncryptInit_ex(pState, EVP_aes_128_gcm(), NULL, (unsigned char*)p_key, p_iv)) {
			break;
		}

		// Provide AAD data if exist
		//
		if (NULL != p_aad) {
			if (1 != EVP_EncryptUpdate(pState, NULL, &len, p_aad, aad_len)) {
				break;
			}
		}

		// Provide the message to be encrypted, and obtain the encrypted output.
		//
		if (1 != EVP_EncryptUpdate(pState, p_dst, &len, p_src, src_len)) {
			break;
		}

		// Finalise the encryption
		//
		if (1 != EVP_EncryptFinal_ex(pState, p_dst + len, &len)) {
			break;
		}

		// Get tag
		//
		if (1 != EVP_CIPHER_CTX_ctrl(pState, EVP_CTRL_GCM_GET_TAG, SGX_AESGCM_MAC_SIZE, p_out_mac)) {
			break;
		}
		ret = SGX_SUCCESS;
	} while (0);

	if (ret != SGX_SUCCESS) {
        GET_LAST_OPENSSL_ERROR;
	}

	// Clean up and return
	//
	if (pState) {
			EVP_CIPHER_CTX_free(pState);
	}
	return ret;
}

sgx_status_t sgx_rijndael128GCM_decrypt(const sgx_aes_gcm_128bit_key_t *p_key, const uint8_t *p_src,
                                        uint32_t src_len, uint8_t *p_dst, const uint8_t *p_iv, uint32_t iv_len,
                                        const uint8_t *p_aad, uint32_t aad_len, const sgx_aes_gcm_128bit_tag_t *p_in_mac)
{
	uint8_t l_tag[SGX_AESGCM_MAC_SIZE];

	if ((src_len > INT_MAX) || (aad_len > INT_MAX) || (p_key == NULL) || ((src_len > 0) && (p_dst == NULL)) || ((src_len > 0) && (p_src == NULL))
		|| (p_in_mac == NULL) || (iv_len != SGX_AESGCM_IV_SIZE) || ((aad_len > 0) && (p_aad == NULL))
		|| (p_iv == NULL) || ((p_src == NULL) && (p_aad == NULL)))
	{
		return SGX_ERROR_INVALID_PARAMETER;
	}
	int len = 0;
	sgx_status_t ret = SGX_ERROR_UNEXPECTED;
	EVP_CIPHER_CTX * pState = NULL;

	CLEAR_OPENSSL_ERROR_QUEUE;

	// Autenthication Tag returned by Decrypt to be compared with Tag created during seal
	//
	memset_s(&l_tag, SGX_AESGCM_MAC_SIZE, 0, SGX_AESGCM_MAC_SIZE);
	memcpy(l_tag, p_in_mac, SGX_AESGCM_MAC_SIZE);

	do {
		// Create and initialise the context
		//
		if (!(pState = EVP_CIPHER_CTX_new())) {
			ret = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// Initialise decrypt, key and IV
		//
		if (!EVP_DecryptInit_ex(pState, EVP_aes_128_gcm(), NULL, (unsigned char*)p_key, p_iv)) {
			break;
		}

		// Provide AAD data if exist
		//
		if (NULL != p_aad) {
			if (!EVP_DecryptUpdate(pState, NULL, &len, p_aad, aad_len)) {
				break;
			}
		}

		// Decrypt message, obtain the plaintext output
		//
		if (!EVP_DecryptUpdate(pState, p_dst, &len, p_src, src_len)) {
			break;
		}

		// Update expected tag value
		//
		if (!EVP_CIPHER_CTX_ctrl(pState, EVP_CTRL_GCM_SET_TAG, SGX_AESGCM_MAC_SIZE, l_tag)) {
			break;
		}

		// Finalise the decryption. A positive return value indicates success,
		// anything else is a failure - the plaintext is not trustworthy.
		//
		if (EVP_DecryptFinal_ex(pState, p_dst + len, &len) <= 0) {
			break;
		}
		ret = SGX_SUCCESS;
	} while (0);

	if (ret != SGX_SUCCESS) {
		GET_LAST_OPENSSL_ERROR;
	}

	// Clean up and return
	//
	if (pState != NULL) {
		EVP_CIPHER_CTX_free(pState);
	}
	memset_s(&l_tag, SGX_AESGCM_MAC_SIZE, 0, SGX_AESGCM_MAC_SIZE);
	return ret;
}
