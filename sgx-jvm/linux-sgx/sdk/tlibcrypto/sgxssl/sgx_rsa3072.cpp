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

#include "sgx_tcrypto.h"
#include <openssl/bn.h>
#include <openssl/rsa.h>
#include <openssl/evp.h>
#include <openssl/err.h>
#include "se_tcrypto_common.h"

sgx_status_t sgx_rsa3072_sign(const uint8_t * p_data,
	uint32_t data_size,
	const sgx_rsa3072_key_t * p_key,
	sgx_rsa3072_signature_t * p_signature)
{
	if ((p_data == NULL) || (data_size < 1) || (p_key == NULL) ||
		(p_signature == NULL))
	{
		return SGX_ERROR_INVALID_PARAMETER;
	}

	sgx_status_t retval = SGX_ERROR_UNEXPECTED;
	RSA *priv_rsa_key = NULL;
	EVP_PKEY* priv_pkey = NULL;
	BIGNUM *n = NULL;
	BIGNUM *d = NULL;
	BIGNUM *e = NULL;
	EVP_MD_CTX* ctx = NULL;
	const EVP_MD* sha256_md = NULL;
	size_t siglen = SGX_RSA3072_KEY_SIZE;
	int ret = 0;

	CLEAR_OPENSSL_ERROR_QUEUE;

	do {
		// converts the modulus value of rsa key, represented as positive integer in little-endian into a BIGNUM
		//
		n = BN_lebin2bn((const unsigned char *)p_key->mod, sizeof(p_key->mod), 0);
		if (n == NULL) {
			break;
		}

		// converts the private exp value of rsa key, represented as positive integer in little-endian into a BIGNUM
		//
		d = BN_lebin2bn((const unsigned char *)p_key->d, sizeof(p_key->d), 0);
		if (d == NULL) {
			break;
		}

		// converts the public exp value of rsa key, represented as positive integer in little-endian into a BIGNUM
		//
		e = BN_lebin2bn((const unsigned char *)p_key->e, sizeof(p_key->e), 0);
		if (e == NULL) {
			break;
		}

		// allocates and initializes an RSA key structure
		//
		priv_rsa_key = RSA_new();
		if (priv_rsa_key == NULL) {
			retval = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// sets the modulus, private exp and public exp values of the RSA key
		//
		if (RSA_set0_key(priv_rsa_key, n, e, d) != 1) {
			BN_clear_free(n);
			BN_clear_free(d);
			BN_clear_free(e);
			break;
		}

		// allocates an empty EVP_PKEY structure
		//
		priv_pkey = EVP_PKEY_new();
		if (priv_pkey == NULL) {
			retval = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// set the referenced key to pub_rsa_key, however these use the supplied key internally and so key will be freed when the parent pkey is freed
		//
		if (EVP_PKEY_assign_RSA(priv_pkey, priv_rsa_key) != 1) {
			RSA_free(priv_rsa_key);
			break;
		}

		// allocates, initializes and returns a digest context
		//
		ctx = EVP_MD_CTX_new();
		if (NULL == ctx) {
			retval = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// return EVP_MD structures for SHA256 digest algorithm */
		//
		sha256_md = EVP_sha256();
		if (sha256_md == NULL) {
			break;
		}

		// sets up signing context ctx to use digest type
		//
		if (EVP_DigestSignInit(ctx, NULL, sha256_md, NULL, priv_pkey) <= 0) {
			break;
		}

		// hashes data_size bytes of data at p_data into the signature context ctx
		//
		if (EVP_DigestSignUpdate(ctx, (const void *)p_data, data_size) <= 0) {
			break;
		}

		// signs the data in ctx places the signature in p_signature.
		//
		ret = EVP_DigestSignFinal(ctx, (unsigned char *)p_signature, &siglen);//fails
		if (ret <= 0) {
			break;
		}

		// validates the signature size
		//
		if (SGX_RSA3072_KEY_SIZE != siglen) {
			break;
		}

		retval = SGX_SUCCESS;
	} while (0);

	if (retval != SGX_SUCCESS) {
		GET_LAST_OPENSSL_ERROR;
	}

	if (ctx)
		EVP_MD_CTX_free(ctx);
	if (priv_pkey) {
		EVP_PKEY_free(priv_pkey);
		priv_rsa_key = NULL;
		n = NULL;
		d = NULL;
		e = NULL;
	}
	if (priv_rsa_key) {
		RSA_free(priv_rsa_key);
		n = NULL;
		d = NULL;
		e = NULL;
	}
	if (n)
		BN_clear_free(n);
	if (d)
		BN_clear_free(d);
	if (e)
		BN_clear_free(e);

	return retval;
}

sgx_status_t sgx_rsa3072_verify(const uint8_t *p_data,
	uint32_t data_size,
	const sgx_rsa3072_public_key_t *p_public,
	const sgx_rsa3072_signature_t *p_signature,
	sgx_rsa_result_t *p_result)
{
	if ((p_data == NULL) || (data_size < 1) || (p_public == NULL) ||
		(p_signature == NULL) || (p_result == NULL))
	{
		return SGX_ERROR_INVALID_PARAMETER;
	}
	*p_result = SGX_RSA_INVALID_SIGNATURE;

	sgx_status_t retval = SGX_ERROR_UNEXPECTED;
	int verified = 0;
	RSA *pub_rsa_key = NULL;
	EVP_PKEY *pub_pkey = NULL;
	BIGNUM *n = NULL;
	BIGNUM *e = NULL;
	const EVP_MD* sha256_md = NULL;
	EVP_MD_CTX *ctx = NULL;

	CLEAR_OPENSSL_ERROR_QUEUE;

	do {
		// converts the modulus value of rsa key, represented as positive integer in little-endian into a BIGNUM
		//
		n = BN_lebin2bn((const unsigned char *)p_public->mod, sizeof(p_public->mod), 0);
		if (n == NULL) {
			break;
		}

		// converts the public exp value of rsa key, represented as positive integer in little-endian into a BIGNUM
		//
		e = BN_lebin2bn((const unsigned char *)p_public->exp, sizeof(p_public->exp), 0);
		if (e == NULL) {
			break;
		}

		// allocates and initializes an RSA key structure
		//
		pub_rsa_key = RSA_new();
		if (pub_rsa_key == NULL) {
			retval = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// sets the modulus and public exp values of the RSA key
		//
		if (RSA_set0_key(pub_rsa_key, n, e, NULL) != 1) {
			BN_clear_free(n);
			BN_clear_free(e);
			break;
		}

		// allocates an empty EVP_PKEY structure
		//
		pub_pkey = EVP_PKEY_new();
		if (pub_pkey == NULL) {
			retval = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// set the referenced key to pub_rsa_key, however these use the supplied key internally and so key will be freed when the parent pkey is freed
		//
		if (EVP_PKEY_assign_RSA(pub_pkey, pub_rsa_key) != 1) {
			RSA_free(pub_rsa_key);
			break;
		}

		// allocates, initializes and returns a digest context
		//
		ctx = EVP_MD_CTX_new();
		if (ctx == NULL) {
			retval = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// return EVP_MD structures for SHA256 digest algorithm */
		//
		sha256_md = EVP_sha256();
		if (sha256_md == NULL) {
			break;
		}

		// sets up verification context ctx to use digest type
		//
		if (EVP_DigestVerifyInit(ctx, NULL, sha256_md, NULL, pub_pkey) <= 0) {
			break;
		}

		// hashes data_size bytes of data at p_data into the verification context ctx.
		// this function can be called several times on the same ctx to hash additional data
		//
		if (EVP_DigestVerifyUpdate(ctx, (const void *)p_data, data_size) <= 0) {
			break;
		}

		// verifies the data in ctx against the signature in p_signature of length SGX_RSA3072_KEY_SIZE
		//
		verified = EVP_DigestVerifyFinal(ctx, (const unsigned char *)p_signature, SGX_RSA3072_KEY_SIZE);
		if (verified) {
			*p_result = SGX_RSA_VALID;
		}
		else if (verified != 0) {
			break;
		}

		retval = SGX_SUCCESS;
	} while (0);

	if (retval != SGX_SUCCESS) {
		GET_LAST_OPENSSL_ERROR;
	}

	if (ctx)
		EVP_MD_CTX_free(ctx);
	if (pub_pkey) {
		EVP_PKEY_free(pub_pkey);
		pub_rsa_key = NULL;
		n = NULL;
		e = NULL;
	}
	if (pub_rsa_key) {
		RSA_free(pub_rsa_key);
		n = NULL;
		e = NULL;
	}
	if (n)
		BN_clear_free(n);
	if (e)
		BN_clear_free(e);

	return retval;
}
