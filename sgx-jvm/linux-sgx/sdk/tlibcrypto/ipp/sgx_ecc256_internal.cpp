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




#include "sgx_ecc256_common.h"
#include "sgx_ecc256_internal.h"



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

    IppsBigNumState*    BN_dh_privB = NULL;
    IppsBigNumState*    BN_dh_shared_x = NULL;
    IppsBigNumState*    BN_dh_shared_y = NULL;
    IppsBigNumState*    pubA_gx = NULL;
    IppsBigNumState*    pubA_gy = NULL;
    IppsECCPPointState* point_pubA = NULL;
    IppsECCPPointState* point_R = NULL;
    IppStatus           ipp_ret = ippStsNoErr;
    int                 ecPointSize = 0;
    IppECResult         ipp_result = ippECValid;
    IppsECCPState* p_ecc_state = (IppsECCPState*)ecc_handle;

    do
    {
        ipp_ret = sgx_ipp_newBN((Ipp32u*)p_private_b->r, sizeof(sgx_ec256_private_t), &BN_dh_privB);
        ERROR_BREAK(ipp_ret);
        ipp_ret = sgx_ipp_newBN((uint32_t*)p_public_ga->gx, sizeof(p_public_ga->gx), &pubA_gx);
        ERROR_BREAK(ipp_ret);
        ipp_ret = sgx_ipp_newBN((uint32_t*)p_public_ga->gy, sizeof(p_public_ga->gy), &pubA_gy);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsECCPPointGetSize(256, &ecPointSize);
        ERROR_BREAK(ipp_ret);
        point_pubA = (IppsECCPPointState*)( malloc(ecPointSize) );
        if(!point_pubA)
        {
            ipp_ret = ippStsNoMemErr;
            break;
        }
        ipp_ret = ippsECCPPointInit(256, point_pubA);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsECCPSetPoint(pubA_gx, pubA_gy, point_pubA, p_ecc_state);
        ERROR_BREAK(ipp_ret);

        //defense in depth to verify that input public key in ECC group
        //a return value of ippECValid indicates the point is on the elliptic curve
        //and is not the point at infinity
        ipp_ret = ippsECCPCheckPoint(point_pubA, &ipp_result, p_ecc_state);
        ERROR_BREAK(ipp_ret);

        if (ipp_result != ippECValid )
        {
            ipp_ret = ippStsIvalidPublicKey;
            break;
        }

        point_R = (IppsECCPPointState*)( malloc(ecPointSize) );
        if(!point_R)
        {
            ipp_ret = ippStsNoMemErr;
            break;
        }
        ipp_ret = ippsECCPPointInit(256, point_R);
        ERROR_BREAK(ipp_ret);

        ipp_ret = sgx_ipp_newBN(NULL, sizeof(sgx_ec256_dh_shared_t), &BN_dh_shared_x);
        ERROR_BREAK(ipp_ret);
        ipp_ret = sgx_ipp_newBN(NULL, sizeof(sgx_ec256_dh_shared_t), &BN_dh_shared_y);
        ERROR_BREAK(ipp_ret);

        ipp_ret = ippsECCPMulPointScalar(point_pubA, BN_dh_privB, point_R, p_ecc_state);
        ERROR_BREAK(ipp_ret);

        //defense in depth to verify that point_R in ECC group
        //a return value of ippECValid indicates the point is on the elliptic curve
        //and is not the point at infinity
        ipp_ret = ippsECCPCheckPoint(point_R, &ipp_result, p_ecc_state);
        ERROR_BREAK(ipp_ret);

        if (ipp_result != ippECValid)
        {
            ipp_ret = ippStsIvalidPublicKey;
            break;
        }

        ipp_ret = ippsECCPGetPoint(BN_dh_shared_x, BN_dh_shared_y, point_R, p_ecc_state);
        ERROR_BREAK(ipp_ret);

        IppsBigNumSGN sgn = IppsBigNumPOS;
        int length = 0;
        Ipp32u *pdata = NULL;
        ipp_ret = ippsRef_BN(&sgn, &length, &pdata, BN_dh_shared_x);
        ERROR_BREAK(ipp_ret);
        memset(p_shared_key->x, 0, sizeof(p_shared_key->x));
        memcpy(p_shared_key->x, pdata, ROUND_TO(length, 8)/8);
        // Clear memory securely
        memset_s(pdata, sizeof(p_shared_key->x), 0, ROUND_TO(length, 8)/8);

        ipp_ret = ippsRef_BN(&sgn, &length, &pdata, BN_dh_shared_y);
        ERROR_BREAK(ipp_ret);
        memset(p_shared_key->y, 0, sizeof(p_shared_key->y));
        memcpy(p_shared_key->y, pdata, ROUND_TO(length, 8)/8);
        // Clear memory securely
        memset_s(pdata, sizeof(p_shared_key->x), 0, ROUND_TO(length, 8)/8);
    }while(0);

    SAFE_FREE(point_pubA);
    if (point_R) memset_s(point_R, ecPointSize, 0, ecPointSize);
    SAFE_FREE(point_R);
    sgx_ipp_secure_free_BN(pubA_gx, sizeof(p_public_ga->gx));
    sgx_ipp_secure_free_BN(pubA_gy, sizeof(p_public_ga->gy));
    sgx_ipp_secure_free_BN(BN_dh_privB, sizeof(sgx_ec256_private_t));
    sgx_ipp_secure_free_BN(BN_dh_shared_x, sizeof(sgx_ec256_dh_shared_t));
    sgx_ipp_secure_free_BN(BN_dh_shared_y, sizeof(sgx_ec256_dh_shared_t));

    if (ipp_ret == ippStsNoMemErr || ipp_ret == ippStsMemAllocErr)
    {
        return SGX_ERROR_OUT_OF_MEMORY;
    }

    if (ipp_ret != ippStsNoErr)
    {
        return SGX_ERROR_UNEXPECTED;
    }
    return SGX_SUCCESS;
}

