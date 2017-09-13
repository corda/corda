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
#include "ipp_wrapper.h"
#include "se_ecdsa_verify_internal.h"

#if !defined(SWAP_ENDIAN_DW)
#define SWAP_ENDIAN_DW(dw)	((((dw) & 0x000000ff) << 24)                    \
                            | (((dw) & 0x0000ff00) << 8)                    \
                            | (((dw) & 0x00ff0000) >> 8)                    \
                            | (((dw) & 0xff000000) >> 24))
#endif

// LE<->BE translation of 32 byte big number
#if !defined(SWAP_ENDIAN_32B)
#define SWAP_ENDIAN_32B(ptr)												\
{																			\
    unsigned int temp = 0;													\
    temp = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[0]);						\
    ((unsigned int*)(ptr))[0] = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[7]);	\
    ((unsigned int*)(ptr))[7] = temp;										\
    temp = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[1]);						\
    ((unsigned int*)(ptr))[1] = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[6]);	\
    ((unsigned int*)(ptr))[6] = temp;										\
    temp = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[2]);						\
    ((unsigned int*)(ptr))[2] = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[5]);	\
    ((unsigned int*)(ptr))[5] = temp;										\
    temp = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[3]);						\
    ((unsigned int*)(ptr))[3] = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[4]);	\
    ((unsigned int*)(ptr))[4] = temp;										\
}
#endif

#if !defined(ntohs)
#define ntohs(u16)                                      \
  ((uint16_t)(((((unsigned char*)&(u16))[0]) << 8)        \
            + (((unsigned char*)&(u16))[1])))
#endif


static const uint32_t g_nistp256_r[] = {
   0xFC632551, 0xF3B9CAC2, 0xA7179E84, 0xBCE6FAAD, 0xFFFFFFFF, 0xFFFFFFFF,
   0x00000000, 0xFFFFFFFF};

/*
 * An utility function used to verify ecc signature.
 *
 * @param p_ecp Pointer to ecc context.
 * @param p_pubkey The ecc public key.
 * @param p_signature The signature, in little endian
 * @param p_sig_rl_hash Output from sgx_sha256_get_hash
 * @param p_result Verify result
 * @return sgx_status_t Return SGX_SUCCESS if p_result is valid. Need to check
 * the p_result for detailed result. ippECValid means signature match.
 * ippECInvalidSignature means the input signature is invalid.
 */
sgx_status_t se_ecdsa_verify_internal(
    IppsECCPState *p_ecp,
    sgx_ec256_public_t *p_pubkey,
    sgx_ec256_signature_t *p_signature,
    const se_ae_ecdsa_hash_t *p_sig_rl_hash,
    IppECResult *p_result)
{
    sgx_status_t ret = SGX_SUCCESS;
    IppStatus ipp_ret = ippStsNoErr;
    Ipp32u cmpZeroResult = 0;
    IppsBigNumState* p_bn_ecp_order = NULL;
    IppsBigNumState* p_bn_sig_rl_hash = NULL;
    IppsBigNumState* p_bn_sig_rl_msg = NULL;
    IppsBigNumState* p_bn_sign_x = NULL;
    IppsBigNumState* p_bn_sign_y = NULL;
    IppsBigNumState* p_bn_p_x = NULL;
    IppsBigNumState* p_bn_p_y = NULL;
    IppsECCPPointState *p_reg_pub_key = NULL;
    int ctxSize = 0;
    IppECResult ecc_result = ippECValid;
    uint32_t sig_rl_hash_le[8] = {0};
    const uint32_t zero = 0;
    IppsBigNumState* p_bn_zero = NULL;

    if(NULL == p_ecp || NULL == p_pubkey || NULL == p_signature
       || NULL == p_sig_rl_hash || NULL == p_result)
        return SGX_ERROR_INVALID_PARAMETER;

    const int order_size = sizeof(g_nistp256_r);

    ipp_ret = newBN(g_nistp256_r, order_size, &p_bn_ecp_order);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    memcpy(sig_rl_hash_le, p_sig_rl_hash->hash, sizeof(sig_rl_hash_le));
    SWAP_ENDIAN_32B(sig_rl_hash_le);
    ipp_ret = newBN(sig_rl_hash_le, sizeof(sig_rl_hash_le),
                    &p_bn_sig_rl_hash);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    ipp_ret = newBN(0, order_size, &p_bn_sig_rl_msg);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    ipp_ret = ippsMod_BN(p_bn_sig_rl_hash, p_bn_ecp_order, p_bn_sig_rl_msg);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    ipp_ret = newBN(p_signature->x, order_size, &p_bn_sign_x);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    // Create a Big Number whose value is zero
    ipp_ret = newBN(&zero, sizeof(zero), &p_bn_zero);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    // Make sure none of the 2 signature big numbers is 0.
    ipp_ret = ippsCmp_BN(p_bn_sign_x, p_bn_zero, &cmpZeroResult);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }
    if(IS_ZERO == cmpZeroResult)
    {
        // Return SGX_SUCCESS and *p_result to be ippECInvalidSignature to report invalid signature.
        ret = SGX_SUCCESS;
        *p_result = ippECInvalidSignature;
        goto CLEANUP;
    }

    ipp_ret = newBN(p_signature->y, order_size, &p_bn_sign_y);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    ipp_ret = ippsCmp_BN(p_bn_sign_y, p_bn_zero, &cmpZeroResult);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }
    if(IS_ZERO == cmpZeroResult)
    {
        // Return SGX_SUCCESS and  *p_result to be ippECInvalidSignature to report invalid signature
        ret = SGX_SUCCESS;
        *p_result = ippECInvalidSignature;
        goto CLEANUP;
    }

    ipp_ret = newBN((Ipp32u *)p_pubkey->gx, order_size, &p_bn_p_x);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    ipp_ret = newBN((Ipp32u *)p_pubkey->gy, order_size, &p_bn_p_y);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    ipp_ret = ippsECCPPointGetSize(256, &ctxSize);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    p_reg_pub_key = (IppsECCPPointState *)malloc(ctxSize);
    if(NULL == p_reg_pub_key)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    ipp_ret = ippsECCPPointInit(256, p_reg_pub_key);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    ipp_ret = ippsECCPSetPoint(p_bn_p_x, p_bn_p_y, p_reg_pub_key, p_ecp);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    /* Set the regular pub key. */
    ipp_ret = ippsECCPSetKeyPair(NULL, p_reg_pub_key, ippTrue, p_ecp);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    ipp_ret = ippsECCPVerifyDSA(p_bn_sig_rl_msg, p_bn_sign_x, p_bn_sign_y,
                                &ecc_result, p_ecp);
    if(ipp_ret != ippStsNoErr)
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    *p_result = ecc_result;

CLEANUP:
    if(p_bn_ecp_order)
        free(p_bn_ecp_order);
    if(p_bn_sig_rl_hash)
        free(p_bn_sig_rl_hash);
    if(p_bn_sig_rl_msg)
        free(p_bn_sig_rl_msg);
    if(p_bn_sign_x)
        free(p_bn_sign_x);
    if(p_bn_sign_y)
        free(p_bn_sign_y);
    if(p_bn_p_x)
        free(p_bn_p_x);
    if(p_bn_p_y)
        free(p_bn_p_y);
    if(p_reg_pub_key)
        free(p_reg_pub_key);
    if(p_bn_zero)
        free(p_bn_zero);
    return ret;
}

