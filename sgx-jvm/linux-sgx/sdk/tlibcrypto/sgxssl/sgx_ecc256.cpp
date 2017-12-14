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

#include "string.h"
#include "se_tcrypto_common.h"
#include <openssl/evp.h>
#include <openssl/ec.h>
#include <openssl/err.h>
#include "sgx_tcrypto.h"

#define POINT_NOT_ON_CURVE 0x1007c06b

/*
* Elliptic Curve Cryptography - Based on GF(p), 256 bit
*/
/* Allocates and initializes ecc context
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined sgx_error.h
*   Output: sgx_ecc_state_handle_t *p_ecc_handle - Pointer to the handle of ECC crypto system  */
sgx_status_t sgx_ecc256_open_context(sgx_ecc_state_handle_t* p_ecc_handle)
{
	if (p_ecc_handle == NULL) {
		return SGX_ERROR_INVALID_PARAMETER;
	}

	sgx_status_t retval = SGX_SUCCESS;
	CLEAR_OPENSSL_ERROR_QUEUE;

	/* construct a curve p-256 */
	EC_GROUP* ec_group = EC_GROUP_new_by_curve_name(NID_X9_62_prime256v1);
	if (NULL == ec_group) {
		GET_LAST_OPENSSL_ERROR;
		retval = SGX_ERROR_UNEXPECTED;
	} else {
		*p_ecc_handle = (void*)ec_group;
	}
	return retval;
}

/* Cleans up ecc context
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined sgx_error.h
*   Output: sgx_ecc_state_handle_t ecc_handle - Handle to ECC crypto system  */
sgx_status_t sgx_ecc256_close_context(sgx_ecc_state_handle_t ecc_handle)
{
	if (ecc_handle == NULL) {
		return SGX_ERROR_INVALID_PARAMETER;
	}

	EC_GROUP_free((EC_GROUP*)ecc_handle);

	return SGX_SUCCESS;
}

/* Populates private/public key pair - caller code allocates memory
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined sgx_error.h
*   Inputs: sgx_ecc_state_handle_t ecc_handle - Handle to ECC crypto system
*   Outputs: sgx_ec256_private_t *p_private - Pointer to the private key
*            sgx_ec256_public_t *p_public - Pointer to the public key  */
sgx_status_t sgx_ecc256_create_key_pair(sgx_ec256_private_t *p_private,
    sgx_ec256_public_t *p_public,
    sgx_ecc_state_handle_t ecc_handle)
{
	if ((ecc_handle == NULL) || (p_private == NULL) || (p_public == NULL)) {
		return SGX_ERROR_INVALID_PARAMETER;
	}

	EC_GROUP *ec_group = (EC_GROUP*) ecc_handle;
	EC_KEY *ec_key = NULL;
	BIGNUM *pub_k_x = NULL;
	BIGNUM *pub_k_y = NULL;
	const EC_POINT *public_k = NULL;
	const BIGNUM *private_k = NULL;
	sgx_status_t ret = SGX_ERROR_UNEXPECTED;

	CLEAR_OPENSSL_ERROR_QUEUE;

	do {
		// create new EC key
		//
		ec_key = EC_KEY_new();
		if (NULL == ec_key) {
			ret = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// set key's group (curve)
		//
		if (0 == EC_KEY_set_group (ec_key, ec_group)) {
			break;
		}

		// generate key pair, based on the curve set
		//
		if (0 == EC_KEY_generate_key(ec_key)) {
			break;
		}

		pub_k_x = BN_new();
		pub_k_y = BN_new();
		if (NULL ==  pub_k_x || NULL == pub_k_y) {
			ret = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// This OPENSSL API doesn't validate user's parameters
		// get public and private keys
		//
		public_k = EC_KEY_get0_public_key(ec_key);
		if (NULL == ec_key) {
			break;
		}

		private_k = EC_KEY_get0_private_key(ec_key);
		if (NULL == ec_key) {
			break;
		}

		// extract two BNs representing the public key
		//
		if (!EC_POINT_get_affine_coordinates_GFp(ec_group, public_k, pub_k_x, pub_k_y, NULL)) {
			break;
		}

		// convert private key BN to little-endian unsigned char form
		//
		if (-1 == BN_bn2lebinpad(private_k, (unsigned char*)p_private, SGX_ECP256_KEY_SIZE)) {
			break;
		}

		// convert public key BN to little-endian unsigned char form
		//
		if (-1 == BN_bn2lebinpad(pub_k_x, (unsigned char*)p_public->gx, SGX_ECP256_KEY_SIZE)) {
			break;
		}
		// convert public key BN to little-endian unsigned char form
		//
		if (-1 == BN_bn2lebinpad(pub_k_y, (unsigned char*)p_public->gy, SGX_ECP256_KEY_SIZE)) {
			break;
		}

		ret = SGX_SUCCESS;
	} while(0);

	if (SGX_SUCCESS != ret) {
		GET_LAST_OPENSSL_ERROR;
		// in case of error, clear output buffers
		//
		memset_s(p_private, sizeof(p_private), 0, sizeof(p_private));
		memset_s(p_public->gx, sizeof(p_public->gx), 0, sizeof(p_public->gx));
		memset_s(p_public->gy, sizeof(p_public->gy), 0, sizeof(p_public->gy));
	}

	//free temp data
	//
	EC_KEY_free(ec_key);
	BN_clear_free(pub_k_x);
	BN_clear_free(pub_k_y);

	return ret;
}

/* Checks whether the input point is a valid point on the given elliptic curve
* Parameters:
*   Return: sgx_status_t - SGX_SUCCESS or failure as defined sgx_error.h
*   Inputs: sgx_ecc_state_handle_t ecc_handle - Handle to ECC crypto system
*           sgx_ec256_public_t *p_point - Pointer to perform validity check on - LITTLE ENDIAN
*   Output: int *p_valid - Return 0 if the point is an invalid point on ECC curve */
sgx_status_t sgx_ecc256_check_point(const sgx_ec256_public_t *p_point,
                                    const sgx_ecc_state_handle_t ecc_handle,
                                    int *p_valid)
{
	if ((ecc_handle == NULL) || (p_point == NULL) || (p_valid == NULL)) {
		return SGX_ERROR_INVALID_PARAMETER;
	}

	sgx_status_t retval = SGX_ERROR_UNEXPECTED;
	EC_POINT *ec_point = NULL;
	BIGNUM *b_x = NULL;
	BIGNUM *b_y = NULL;
	int ret_point_on_curve = 0;
	unsigned long internal_openssl_error = 0;

	CLEAR_OPENSSL_ERROR_QUEUE;

	do {
		// converts the x value of the point, represented as positive integer in little-endian into a BIGNUM
		//
		b_x = BN_lebin2bn(p_point->gx, SGX_ECP256_KEY_SIZE, NULL);
		if (NULL == b_x) {
			break;
		}

		// converts the y value of the point, represented as positive integer in little-endian into a BIGNUM
		//
		b_y = BN_lebin2bn(p_point->gy, SGX_ECP256_KEY_SIZE, NULL);
		if (NULL == b_y) {
			break;
		}

		// creates new point and assigned the group object that the point relates to
		//
		ec_point = EC_POINT_new((const EC_GROUP*)ecc_handle);
		if (NULL == ec_point) {
			retval = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// sets point based on x,y coordinates
		//
		if (1 != EC_POINT_set_affine_coordinates_GFp((const EC_GROUP*)ecc_handle, ec_point, b_x, b_y, NULL)) {
			internal_openssl_error = ERR_get_error();
			if (internal_openssl_error == POINT_NOT_ON_CURVE) {
				/* fails if point not on curve */
				*p_valid = 0;
				retval = SGX_SUCCESS;
			} else {
				#ifdef DEBUG
				openssl_last_err = internal_openssl_error;
				#endif /* DEBUG */
			}
			break;
		}

		// checks if point is on curve
		//
		ret_point_on_curve = EC_POINT_is_on_curve((const EC_GROUP*)ecc_handle, ec_point, NULL);
		if (-1 == ret_point_on_curve) {
			break;
		}

		*p_valid = ret_point_on_curve;

		retval = SGX_SUCCESS;
	} while(0);

	#ifdef DEBUG
	if (SGX_SUCCESS != retval && 0 != openssl_last_err) {
		GET_LAST_OPENSSL_ERROR;
	}
	#endif /* DEBUG */

	if (ec_point)
		EC_POINT_clear_free(ec_point);
	if (b_x)
		BN_clear_free(b_x);
	if (b_y)
		BN_clear_free(b_y);

	return retval;
}

/* Computes DH shared key based on private B key (local) and remote public Ga Key
* Parameters:
*   Return: sgx_status_t - SGX_SUCCESS or failure as defined sgx_error.h
*   Inputs: sgx_ecc_state_handle_t ecc_handle - Handle to ECC crypto system
*           sgx_ec256_private_t *p_private_b - Pointer to the local private key - LITTLE ENDIAN
*           sgx_ec256_public_t *p_public_ga - Pointer to the remote public key - LITTLE ENDIAN
*   Output: sgx_ec256_dh_shared_t *p_shared_key - Pointer to the shared DH key - LITTLE ENDIAN
x-coordinate of (privKeyB - pubKeyA) */
sgx_status_t sgx_ecc256_compute_shared_dhkey(sgx_ec256_private_t *p_private_b,
                                             sgx_ec256_public_t *p_public_ga,
                                             sgx_ec256_dh_shared_t *p_shared_key,
                                             sgx_ecc_state_handle_t ecc_handle)
{
	if ((ecc_handle == NULL) || (p_private_b == NULL) || (p_public_ga == NULL) || (p_shared_key == NULL)) {
		return SGX_ERROR_INVALID_PARAMETER;
	}

	sgx_status_t ret = SGX_ERROR_UNEXPECTED;
	EC_GROUP *ec_group = (EC_GROUP*) ecc_handle;
	EC_POINT *point_pubA = NULL;
	EC_KEY* private_key = NULL;
	BIGNUM *BN_dh_privB = NULL;
	BIGNUM *pubA_gx = NULL;
	BIGNUM *pubA_gy = NULL;
	BIGNUM *tmp = NULL;

	CLEAR_OPENSSL_ERROR_QUEUE;

	do {
		// get BN from public key and private key
		//
		BN_dh_privB = BN_lebin2bn((unsigned char*)p_private_b->r, sizeof(sgx_ec256_private_t), 0);
		if (BN_dh_privB == NULL) {
			break;
		}

		pubA_gx = BN_lebin2bn((unsigned char*)p_public_ga->gx, sizeof(sgx_ec256_private_t), 0);
		if (pubA_gx == NULL) {
			break;
		}

		pubA_gy = BN_lebin2bn((unsigned char*)p_public_ga->gy, sizeof(sgx_ec256_private_t), 0);
		if (pubA_gy == NULL) {
			break;
		}

		// set point based on pub key x and y
		//
		point_pubA = EC_POINT_new(ec_group);
		if (point_pubA == NULL) {
			ret = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// create point (public key) based on public key's x,y coordinates
		//
		if (EC_POINT_set_affine_coordinates_GFp(ec_group, point_pubA, pubA_gx, pubA_gy, NULL) != 1) {
			break;
		}

		// check point if valid, point is on curve
		//
		if (EC_POINT_is_on_curve(ec_group, point_pubA, NULL) != 1) {
			break;
		}

		// create empty shared key BN
		//
		private_key = EC_KEY_new();
		if (private_key == NULL) {
			ret = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		// init private key group (set curve)
		//
		if (EC_KEY_set_group (private_key, ec_group) != 1) {
			break;
		}

		// init private key with BN value
		//
		if (EC_KEY_set_private_key(private_key, BN_dh_privB) != 1) {
			break;
		}

		// calculate shared dh key
		//
		size_t shared_key_len = sizeof(sgx_ec256_dh_shared_t);
		shared_key_len = ECDH_compute_key(&(p_shared_key->s), shared_key_len, point_pubA, private_key, NULL);
		if (shared_key_len <= 0) {
			break;
		}

		// convert big endian to little endian
		//
		tmp = BN_bin2bn((unsigned char*)&(p_shared_key->s), sizeof(sgx_ec256_dh_shared_t), 0);
	if (tmp == NULL) {
		break;
	}
		if (BN_bn2lebinpad(tmp, p_shared_key->s, sizeof(sgx_ec256_dh_shared_t)) == -1) {
			break;
		}
		ret = SGX_SUCCESS;
	} while(0);

	if (ret != SGX_SUCCESS) {
		GET_LAST_OPENSSL_ERROR;
		memset_s(p_shared_key->s, sizeof(p_shared_key->s), 0, sizeof(p_shared_key->s));
	}

	// clear and free memory
	//
	EC_POINT_clear_free(point_pubA);
	EC_KEY_free(private_key);
	BN_clear_free(BN_dh_privB);
	BN_clear_free(pubA_gx);
	BN_clear_free(pubA_gy);
	BN_clear_free(tmp);

	return ret;
}
