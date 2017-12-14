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
#include <openssl/sha.h>
#include <openssl/ec.h>
#include <openssl/bn.h>
#include <openssl/err.h>
#include "sgx_tcrypto.h"

/* Computes signature for data based on private key
* Parameters:
*   Return: sgx_status_t - SGX_SUCCESS or failure as defined sgx_error.h
*   Inputs: sgx_ecc_state_handle_t ecc_handle - Handle to ECC crypto system
*           sgx_ec256_private_t *p_private - Pointer to the private key - LITTLE ENDIAN
*           sgx_uint8_t *p_data - Pointer to the data to be signed
*           uint32_t data_size - Size of the data to be signed
*   Output: sgx_ec256_signature_t *p_signature - Pointer to the signature - LITTLE ENDIAN  */
sgx_status_t sgx_ecdsa_sign(const uint8_t *p_data,
                            uint32_t data_size,
                            sgx_ec256_private_t *p_private,
                            sgx_ec256_signature_t *p_signature,
                            sgx_ecc_state_handle_t ecc_handle)
{
	if ((ecc_handle == NULL) || (p_private == NULL) || (p_signature == NULL) || (p_data == NULL) || (data_size < 1))
	{
		return SGX_ERROR_INVALID_PARAMETER;
	}

	EC_KEY *private_key = NULL;
	BIGNUM *bn_priv = NULL;
	ECDSA_SIG *ecdsa_sig = NULL;
	const BIGNUM *r = NULL;
	const BIGNUM *s = NULL;
	unsigned char digest[SGX_SHA256_HASH_SIZE] = { 0 };
	int written_bytes = 0;
	int sig_size = 0;
	int max_sig_size = 0;
	sgx_status_t retval = SGX_ERROR_UNEXPECTED;
	CLEAR_OPENSSL_ERROR_QUEUE;

	do {
		// converts the r value of private key, represented as positive integer in little-endian into a BIGNUM
		//
		bn_priv = BN_lebin2bn((unsigned char*)p_private->r, sizeof(p_private->r), 0);
		if (NULL == bn_priv) {
			break;
		}

		// create empty ecc key
		//
		private_key = EC_KEY_new();
		if (NULL == private_key) {
			retval = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// sets ecc key group (set curve)
		//
		if (1 != EC_KEY_set_group(private_key, (EC_GROUP*)ecc_handle)) {
			break;
		}

		// uses bn_priv to set the ecc private key
		//
		if (1 != EC_KEY_set_private_key(private_key, bn_priv)) {
			break;
		}

		/* generates digest of p_data */
		if (NULL == SHA256((const unsigned char *)p_data, data_size, (unsigned char *)digest)) {
			break;
		}

		// computes a digital signature of the SGX_SHA256_HASH_SIZE bytes hash value dgst using the private EC key private_key.
		// the signature is returned as a newly allocated ECDSA_SIG structure.
		//
		ecdsa_sig = ECDSA_do_sign(digest, SGX_SHA256_HASH_SIZE, private_key);
		if (NULL == ecdsa_sig) {
			break;
		}

		// returns internal pointers the r and s values contained in ecdsa_sig.
		ECDSA_SIG_get0(ecdsa_sig, &r, &s);

		// converts the r BIGNUM of the signature to little endian buffer, bounded with the len of out buffer
		//
		written_bytes = BN_bn2lebinpad(r, (unsigned char*)p_signature->x, SGX_ECP256_KEY_SIZE);
		if (0 >= written_bytes) {
			break;
		}
		sig_size = written_bytes;

		// converts the s BIGNUM of the signature to little endian buffer, bounded with the len of out buffer
		//
		written_bytes = BN_bn2lebinpad(s, (unsigned char*)p_signature->y, SGX_ECP256_KEY_SIZE);
		if (0 >= written_bytes) {
			break;
		}
		sig_size += written_bytes;

		// returns the maximum length of a DER encoded ECDSA signature created with the private EC key.
		//
		max_sig_size = ECDSA_size(private_key);
		if (max_sig_size <= 0) {
			break;
		}

		// checks if the signature size not larger than the max len of valid signature
		// this check if done for validity, not for overflow.
		//
		if (sig_size > max_sig_size) {
			break;
		}

		retval = SGX_SUCCESS;
	} while(0);

	if (SGX_SUCCESS != retval) {
		GET_LAST_OPENSSL_ERROR;
	}

	if (bn_priv)
		BN_clear_free(bn_priv);
	if (ecdsa_sig)
		ECDSA_SIG_free(ecdsa_sig);
	if (private_key)
		EC_KEY_free(private_key);

	return retval;
}

/* Verifies the signature for the given data based on the public key
*
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined sgx_error.h
*   Inputs: sgx_ecc_state_handle_t ecc_handle - Handle to ECC crypto system
*           sgx_ec256_public_t *p_public - Pointer to the public key - LITTLE ENDIAN
*           uint8_t *p_data - Pointer to the data to be signed
*           uint32_t data_size - Size of the data to be signed
*           sgx_ec256_signature_t *p_signature - Pointer to the signature - LITTLE ENDIAN
*   Output: uint8_t *p_result - Pointer to the result of verification check  */
sgx_status_t sgx_ecdsa_verify(const uint8_t *p_data,
                              uint32_t data_size,
                              const sgx_ec256_public_t *p_public,
                              sgx_ec256_signature_t *p_signature,
                              uint8_t *p_result,
                              sgx_ecc_state_handle_t ecc_handle)
{
	if ((ecc_handle == NULL) || (p_public == NULL) || (p_signature == NULL) ||
		(p_data == NULL) || (data_size < 1) || (p_result == NULL))
	{
		return SGX_ERROR_INVALID_PARAMETER;
	}

	EC_KEY *public_key = NULL;
	BIGNUM *bn_pub_x = NULL;
	BIGNUM *bn_pub_y = NULL;
	BIGNUM *bn_r = NULL;
	BIGNUM *bn_s = NULL;
	BIGNUM *prev_bn_r = NULL;
	BIGNUM *prev_bn_s = NULL;
	EC_POINT *public_point = NULL;
	ECDSA_SIG *ecdsa_sig = NULL;
	unsigned char digest[SGX_SHA256_HASH_SIZE] = { 0 };
	sgx_status_t retval = SGX_ERROR_UNEXPECTED;
	int valid = 0;

	*p_result = SGX_EC_INVALID_SIGNATURE;

	CLEAR_OPENSSL_ERROR_QUEUE;

	do {
		// converts the x value of public key, represented as positive integer in little-endian into a BIGNUM
		//
		bn_pub_x = BN_lebin2bn((unsigned char*)p_public->gx, sizeof(p_public->gx), 0);
		if (NULL == bn_pub_x) {
			break;
		}

		// converts the y value of public key, represented as positive integer in little-endian into a BIGNUM
		//
		bn_pub_y = BN_lebin2bn((unsigned char*)p_public->gy, sizeof(p_public->gy), 0);
		if (NULL == bn_pub_y) {
			break;
		}

		// converts the x value of the signature, represented as positive integer in little-endian into a BIGNUM
		//
		bn_r = BN_lebin2bn((unsigned char*)p_signature->x, sizeof(p_signature->x), 0);
		if (NULL == bn_r) {
			break;
		}

		// converts the y value of the signature, represented as positive integer in little-endian into a BIGNUM
		//
		bn_s = BN_lebin2bn((unsigned char*)p_signature->y, sizeof(p_signature->y), 0);
		if (NULL == bn_s) {
			break;
		}

		// creates new point and assigned the group object that the point relates to
		//
		public_point = EC_POINT_new((EC_GROUP*)ecc_handle);
		if (public_point == NULL) {
			retval = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// sets point based on public key's x,y coordinates
		//
		if (1 != EC_POINT_set_affine_coordinates_GFp((EC_GROUP*)ecc_handle, public_point, bn_pub_x, bn_pub_y, NULL)) {
			break;
		}

		// check point if the point is on curve
		//
		if (1 != EC_POINT_is_on_curve((EC_GROUP*)ecc_handle, public_point, NULL)) {
			break;
		}

		// create empty ecc key
		//
		public_key = EC_KEY_new();
		if (NULL == public_key) {
			retval = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// sets ecc key group (set curve)
		//
		if (1 != EC_KEY_set_group(public_key, (EC_GROUP*)ecc_handle)) {
			break;
		}

		// uses the created point to set the public key value
		//
		if (1 != EC_KEY_set_public_key(public_key, public_point)) {
			break;
		}

		/* generates digest of p_data */
		if (NULL == SHA256((const unsigned char *)p_data, data_size, (unsigned char *)digest)) {
			break;
		}

		// allocates a new ECDSA_SIG structure (note: this function also allocates the BIGNUMs) and initialize it
		//
		ecdsa_sig = ECDSA_SIG_new();
		if (NULL == ecdsa_sig) {
			retval = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// free internal allocated BIGBNUMs
		ECDSA_SIG_get0(ecdsa_sig, (const BIGNUM **)&prev_bn_r, (const BIGNUM **)&prev_bn_s);
		if (prev_bn_r)
			BN_clear_free(prev_bn_r);
		if (prev_bn_s)
			BN_clear_free(prev_bn_s);

		// setes the r and s values of ecdsa_sig
		// calling this function transfers the memory management of the values to the ECDSA_SIG object,
		// and therefore the values that have been passed in should not be freed directly after this function has been called
		//
		if (1 != ECDSA_SIG_set0(ecdsa_sig, bn_r, bn_s)) {
			ECDSA_SIG_free(ecdsa_sig);
			ecdsa_sig = NULL;
			break;
		}

		// verifies that the signature ecdsa_sig is a valid ECDSA signature of the hash value digest of size SGX_SHA256_HASH_SIZE using the public key public_key
		//
		valid = ECDSA_do_verify(digest, SGX_SHA256_HASH_SIZE, ecdsa_sig, public_key);
		if (-1 == valid) {
			break;
		}

		// sets the p_result based on ECDSA_do_verify result
		//
		if (valid) {
			*p_result = SGX_EC_VALID;
		}

		retval = SGX_SUCCESS;
	} while(0);

	if (SGX_SUCCESS != retval) {
		GET_LAST_OPENSSL_ERROR;
	}

	if (bn_pub_x)
		BN_clear_free(bn_pub_x);
	if (bn_pub_y)
		BN_clear_free(bn_pub_y);
	if (public_point)
		EC_POINT_clear_free(public_point);
	if (ecdsa_sig) {
		ECDSA_SIG_free(ecdsa_sig);
		bn_r = NULL;
		bn_s = NULL;
	}
	if (public_key)
		EC_KEY_free(public_key);
	if (bn_r)
		BN_clear_free(bn_r);
	if (bn_s)
		BN_clear_free(bn_s);

	return retval;
}
