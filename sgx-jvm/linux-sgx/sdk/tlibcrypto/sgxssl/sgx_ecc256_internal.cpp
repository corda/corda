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
#include "sgx_ecc256_internal.h"
#include <openssl/evp.h>
#include <openssl/ec.h>
#include <openssl/err.h>
#include "sgx_tcrypto.h"

/* Computes a point with scalar multiplication based on private B key (local) and remote public Ga Key
 * Parameters:
 *    Return: sgx_status_t - SGX_SUCCESS or failure as defined sgx_error.h
 *    Inputs: sgx_ecc_state_handle_t ecc_handle - Handle to ECC crypto system
 *            sgx_ec256_private_t *p_private_b - Pointer to the local private key - LITTLE ENDIAN
 *            sgx_ec256_public_t *p_public_ga - Pointer to the remote public key - LITTLE ENDIAN
 *    Output: sgx_ec256_shared_point_t *p_shared_key - Pointer to the target shared point - LITTLE ENDIAN
                                                    x-coordinate of (privKeyB - pubKeyA) */
sgx_status_t sgx_ecc256_compute_shared_point(sgx_ec256_private_t *p_private_b,
                                           sgx_ec256_public_t *p_public_ga,
                                           sgx_ec256_shared_point_t *p_shared_key,
                                           sgx_ecc_state_handle_t ecc_handle)
{
	if ((ecc_handle == NULL) || (p_private_b == NULL) || (p_public_ga == NULL) || (p_shared_key == NULL))
	{
		return SGX_ERROR_INVALID_PARAMETER;
	}

	sgx_status_t ret = SGX_ERROR_UNEXPECTED;
	EC_GROUP *ec_group = (EC_GROUP*) ecc_handle;
	EC_POINT *point_pubA = NULL;
	EC_POINT *point_R = NULL;
	BIGNUM *BN_dh_privB = NULL;
	BIGNUM *pubA_gx = NULL;
	BIGNUM *pubA_gy = NULL;
	BIGNUM *BN_dh_shared_x = NULL;
	BIGNUM *BN_dh_shared_y = NULL;

	CLEAR_OPENSSL_ERROR_QUEUE;

	do {
		//get BN from public key and private key
		//
		BN_dh_privB = BN_lebin2bn((unsigned char*)p_private_b->r, sizeof(sgx_ec256_private_t), 0);
		if (BN_dh_privB == NULL) {
			break;
		}

		pubA_gx = BN_lebin2bn((unsigned char*)p_public_ga->gx, sizeof(p_public_ga->gx), 0);
		if (pubA_gx == NULL) {
			break;
		}

		pubA_gy = BN_lebin2bn((unsigned char*)p_public_ga->gy, sizeof(p_public_ga->gy), 0);
		if (pubA_gy == NULL) {
			break;
		}

		//set point based on pub key x and y
		//
		point_pubA = EC_POINT_new(ec_group);
		if (point_pubA == NULL) {
			ret = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		//create point (public key) based on public key's x,y coordinates
		//
		if (EC_POINT_set_affine_coordinates_GFp(ec_group, point_pubA, pubA_gx, pubA_gy, NULL) != 1) {
			break;
		}

		//check point if valid, point is on curve
		//
		if (EC_POINT_is_on_curve(ec_group, point_pubA, NULL) != 1) {
			break;
		}

		//create new point R
		//
		point_R = EC_POINT_new(ec_group);
		if (point_R == NULL) {
			ret = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		//multiply pointA with privateKey BN scalar, to get point R.
		//after, R's x and y are the shred d key
		//
		if (EC_POINT_mul(ec_group, point_R, NULL, point_pubA, BN_dh_privB, NULL) != 1) {
			break;
		}

		//check point if valid, point is on curve
		//
		if (EC_POINT_is_on_curve(ec_group, point_R, NULL) != 1) {
			break;
		}

		BN_dh_shared_x = BN_new();
		if (BN_dh_shared_x == NULL) {
			ret = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}
		BN_dh_shared_y = BN_new();
		if (BN_dh_shared_y == NULL) {
			ret = SGX_ERROR_OUT_OF_MEMORY;
			break;
		}

		if (EC_POINT_get_affine_coordinates_GFp(ec_group, point_R, BN_dh_shared_x, BN_dh_shared_y, NULL) != 1) {
			break;
		}
	
		if (BN_bn2lebinpad(BN_dh_shared_x, (unsigned char*)p_shared_key->x, sizeof(p_shared_key->x)) == -1) {
			break;
		}

		if (BN_bn2lebinpad(BN_dh_shared_y, (unsigned char*)p_shared_key->y, sizeof(p_shared_key->y)) == -1) {
			break;
		}

		ret = SGX_SUCCESS;
	} while(0);

	if (ret != SGX_SUCCESS) {
		GET_LAST_OPENSSL_ERROR;
		memset_s(p_shared_key->x, sizeof(p_shared_key->x), 0, sizeof(p_shared_key->x));
		memset_s(p_shared_key->y, sizeof(p_shared_key->y), 0, sizeof(p_shared_key->y));
	}

	//Free and clean all memory
	//
	EC_POINT_clear_free(point_pubA);
	EC_POINT_clear_free(point_R);
	BN_clear_free(BN_dh_shared_x);
	BN_clear_free(BN_dh_shared_y);
	BN_clear_free(BN_dh_privB);
	BN_clear_free(pubA_gx);
	BN_clear_free(pubA_gy);

	return ret;
}
