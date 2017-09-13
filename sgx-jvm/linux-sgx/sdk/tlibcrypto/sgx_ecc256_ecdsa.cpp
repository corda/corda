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


const uint32_t sgx_nistp256_r[] = {
    0xFC632551, 0xF3B9CAC2, 0xA7179E84, 0xBCE6FAAD, 0xFFFFFFFF, 0xFFFFFFFF,
    0x00000000, 0xFFFFFFFF };


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

    IppStatus ipp_ret = ippStsNoErr;
    IppsECCPState* p_ecc_state = (IppsECCPState*)ecc_handle;
    IppsBigNumState* p_ecp_order = NULL;
    IppsBigNumState* p_hash_bn = NULL;
    IppsBigNumState* p_msg_bn = NULL;
    IppsBigNumState* p_eph_priv_bn = NULL;
    IppsECCPPointState* p_eph_pub = NULL;
    IppsBigNumState* p_reg_priv_bn = NULL;
    IppsBigNumState* p_signx_bn = NULL;
    IppsBigNumState* p_signy_bn = NULL;
    Ipp32u *p_sigx = NULL;
    Ipp32u *p_sigy = NULL;
    int ecp_size = 0;
    const int order_size = sizeof(sgx_nistp256_r);
    uint32_t hash[8] = { 0 };

    do
    {

        ipp_ret = sgx_ipp_newBN(sgx_nistp256_r, order_size, &p_ecp_order);
        ERROR_BREAK(ipp_ret);

        // Prepare the message used to sign.
        ipp_ret = ippsHashMessage(p_data, data_size, (Ipp8u*)hash, IPP_ALG_HASH_SHA256);
        ERROR_BREAK(ipp_ret);
        /* Byte swap in creation of Big Number from SHA256 hash output */
        ipp_ret = sgx_ipp_newBN(NULL, sizeof(hash), &p_hash_bn);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsSetOctString_BN((Ipp8u*)hash, sizeof(hash), p_hash_bn);
        ERROR_BREAK(ipp_ret);

        ipp_ret = sgx_ipp_newBN(NULL, order_size, &p_msg_bn);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsMod_BN(p_hash_bn, p_ecp_order, p_msg_bn);
        ERROR_BREAK(ipp_ret);

        // Get ephemeral key pair.
        ipp_ret = sgx_ipp_newBN(NULL, order_size, &p_eph_priv_bn);
        ERROR_BREAK(ipp_ret);
        //init eccp point
        ipp_ret = ippsECCPPointGetSize(256, &ecp_size);
        ERROR_BREAK(ipp_ret);
        p_eph_pub = (IppsECCPPointState*)(malloc(ecp_size));
        if (!p_eph_pub)
        {
            ipp_ret = ippStsNoMemErr;
            break;
        }
        ipp_ret = ippsECCPPointInit(256, p_eph_pub);
        ERROR_BREAK(ipp_ret);
        // Generate ephemeral key pair for signing operation
        // Notice that IPP ensures the private key generated is non-zero
        ipp_ret = ippsECCPGenKeyPair(p_eph_priv_bn, p_eph_pub, p_ecc_state,
            (IppBitSupplier)sgx_ipp_DRNGen, NULL);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsECCPSetKeyPair(p_eph_priv_bn, p_eph_pub, ippFalse, p_ecc_state);
        ERROR_BREAK(ipp_ret);

        // Set the regular private key.
        ipp_ret = sgx_ipp_newBN((uint32_t *)p_private->r, sizeof(p_private->r),
            &p_reg_priv_bn);
        ERROR_BREAK(ipp_ret);
        ipp_ret = sgx_ipp_newBN(NULL, order_size, &p_signx_bn);
        ERROR_BREAK(ipp_ret);
        ipp_ret = sgx_ipp_newBN(NULL, order_size, &p_signy_bn);
        ERROR_BREAK(ipp_ret);

        // Sign the message.
        ipp_ret = ippsECCPSignDSA(p_msg_bn, p_reg_priv_bn, p_signx_bn, p_signy_bn,
            p_ecc_state);
        ERROR_BREAK(ipp_ret);

        IppsBigNumSGN sign;
        int length;
        ipp_ret = ippsRef_BN(&sign, &length,(Ipp32u**) &p_sigx, p_signx_bn);
        ERROR_BREAK(ipp_ret);
        memset(p_signature->x, 0, sizeof(p_signature->x));
        ipp_ret = check_copy_size(sizeof(p_signature->x), ROUND_TO(length, 8) / 8);
        ERROR_BREAK(ipp_ret);
        memcpy(p_signature->x, p_sigx, ROUND_TO(length, 8) / 8);
        memset_s(p_sigx, sizeof(p_signature->x), 0, ROUND_TO(length, 8) / 8);
        ipp_ret = ippsRef_BN(&sign, &length,(Ipp32u**) &p_sigy, p_signy_bn);
        ERROR_BREAK(ipp_ret);
        memset(p_signature->y, 0, sizeof(p_signature->y));
        ipp_ret = check_copy_size(sizeof(p_signature->y), ROUND_TO(length, 8) / 8);
        ERROR_BREAK(ipp_ret);
        memcpy(p_signature->y, p_sigy, ROUND_TO(length, 8) / 8);
        memset_s(p_sigy, sizeof(p_signature->y), 0, ROUND_TO(length, 8) / 8);
    } while (0);

    // Clear buffer before free.
    if (p_eph_pub)
        memset_s(p_eph_pub, ecp_size, 0, ecp_size);
    SAFE_FREE(p_eph_pub);
    sgx_ipp_secure_free_BN(p_ecp_order, order_size);
    sgx_ipp_secure_free_BN(p_hash_bn, sizeof(hash));
    sgx_ipp_secure_free_BN(p_msg_bn, order_size);
    sgx_ipp_secure_free_BN(p_eph_priv_bn, order_size);
    sgx_ipp_secure_free_BN(p_reg_priv_bn, sizeof(p_private->r));
    sgx_ipp_secure_free_BN(p_signx_bn, order_size);
    sgx_ipp_secure_free_BN(p_signy_bn, order_size);

    switch (ipp_ret)
    {
    case ippStsNoErr: return SGX_SUCCESS;
    case ippStsNoMemErr:
    case ippStsMemAllocErr: return SGX_ERROR_OUT_OF_MEMORY;
    case ippStsNullPtrErr:
    case ippStsLengthErr:
    case ippStsOutOfRangeErr:
    case ippStsSizeErr:
    case ippStsBadArgErr: return SGX_ERROR_INVALID_PARAMETER;
    default: return SGX_ERROR_UNEXPECTED;
    }
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

    IppStatus ipp_ret = ippStsNoErr;
    IppsECCPState* p_ecc_state = (IppsECCPState*)ecc_handle;
    IppECResult result = ippECInvalidSignature;
    *p_result = SGX_EC_INVALID_SIGNATURE;

    IppsBigNumState* p_ecp_order = NULL;
    IppsBigNumState* p_hash_bn = NULL;
    IppsBigNumState* p_msg_bn = NULL;
    IppsECCPPointState* p_reg_pub = NULL;
    IppsBigNumState* p_reg_pubx_bn = NULL;
    IppsBigNumState* p_reg_puby_bn = NULL;
    IppsBigNumState* p_signx_bn = NULL;
    IppsBigNumState* p_signy_bn = NULL;
    const int order_size = sizeof(sgx_nistp256_r);
    uint32_t hash[8] = { 0 };
    int ecp_size = 0;

    do
    {
        ipp_ret = sgx_ipp_newBN(sgx_nistp256_r, order_size, &p_ecp_order);
        ERROR_BREAK(ipp_ret);

        // Prepare the message used to sign.
        ipp_ret = ippsHashMessage(p_data, data_size, (Ipp8u*)hash, IPP_ALG_HASH_SHA256);
        ERROR_BREAK(ipp_ret);
        /* Byte swap in creation of Big Number from SHA256 hash output */
        ipp_ret = sgx_ipp_newBN(NULL, sizeof(hash), &p_hash_bn);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsSetOctString_BN((Ipp8u*)hash, sizeof(hash), p_hash_bn);
        ERROR_BREAK(ipp_ret);

        ipp_ret = sgx_ipp_newBN(NULL, order_size, &p_msg_bn);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsMod_BN(p_hash_bn, p_ecp_order, p_msg_bn);
        ERROR_BREAK(ipp_ret);

        //Init eccp point
        ipp_ret = ippsECCPPointGetSize(256, &ecp_size);
        ERROR_BREAK(ipp_ret);
        p_reg_pub = (IppsECCPPointState*)(malloc(ecp_size));
        if (!p_reg_pub)
        {
            ipp_ret = ippStsNoMemErr;
            break;
        }
        ipp_ret = ippsECCPPointInit(256, p_reg_pub);
        ERROR_BREAK(ipp_ret);
        ipp_ret = sgx_ipp_newBN((const uint32_t *)p_public->gx, sizeof(p_public->gx),
            &p_reg_pubx_bn);
        ERROR_BREAK(ipp_ret);
        ipp_ret = sgx_ipp_newBN((const uint32_t *)p_public->gy, sizeof(p_public->gy),
            &p_reg_puby_bn);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsECCPSetPoint(p_reg_pubx_bn, p_reg_puby_bn, p_reg_pub,
            p_ecc_state);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsECCPSetKeyPair(NULL, p_reg_pub, ippTrue, p_ecc_state);
        ERROR_BREAK(ipp_ret);

        ipp_ret = sgx_ipp_newBN(p_signature->x, order_size, &p_signx_bn);
        ERROR_BREAK(ipp_ret);
        ipp_ret = sgx_ipp_newBN(p_signature->y, order_size, &p_signy_bn);
        ERROR_BREAK(ipp_ret);

        // Verify the message.
        ipp_ret = ippsECCPVerifyDSA(p_msg_bn, p_signx_bn, p_signy_bn, &result,
            p_ecc_state);
        ERROR_BREAK(ipp_ret);
    } while (0);

    // Clear buffer before free.
    if (p_reg_pub)
        memset_s(p_reg_pub, ecp_size, 0, ecp_size);
    SAFE_FREE(p_reg_pub);
    sgx_ipp_secure_free_BN(p_ecp_order, order_size);
    sgx_ipp_secure_free_BN(p_hash_bn, sizeof(hash));
    sgx_ipp_secure_free_BN(p_msg_bn, order_size);
    sgx_ipp_secure_free_BN(p_reg_pubx_bn, sizeof(p_public->gx));
    sgx_ipp_secure_free_BN(p_reg_puby_bn, sizeof(p_public->gy));
    sgx_ipp_secure_free_BN(p_signx_bn, order_size);
    sgx_ipp_secure_free_BN(p_signy_bn, order_size);

    switch (result) {
    case ippECValid: *p_result = SGX_EC_VALID; break;                           /* validation pass successfully */
    case ippECInvalidSignature: *p_result = SGX_EC_INVALID_SIGNATURE; break;    /* invalid signature */
    default: *p_result = SGX_EC_INVALID_SIGNATURE; break;
    }

    switch (ipp_ret)
    {
    case ippStsNoErr: return SGX_SUCCESS;
    case ippStsNoMemErr:
    case ippStsMemAllocErr: return SGX_ERROR_OUT_OF_MEMORY;
    case ippStsNullPtrErr:
    case ippStsLengthErr:
    case ippStsOutOfRangeErr:
    case ippStsSizeErr:
    case ippStsBadArgErr: return SGX_ERROR_INVALID_PARAMETER;
    default: return SGX_ERROR_UNEXPECTED;
    }
}
