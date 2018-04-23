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
#include "ippcp.h"
#include "stdlib.h"
#include "string.h"

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
    IppStatus error_code = ippStsNoErr;
    IppsAES_GCMState* pState = NULL;
    int ippStateSize = 0;

    if ((p_key == NULL) || ((src_len > 0) && (p_dst == NULL)) || ((src_len > 0) && (p_src == NULL))
        || (p_out_mac == NULL) || (iv_len != SGX_AESGCM_IV_SIZE) || ((aad_len > 0) && (p_aad == NULL))
        || (p_iv == NULL) || ((p_src == NULL) && (p_aad == NULL)))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    error_code = ippsAES_GCMGetSize(&ippStateSize);
    if (error_code != ippStsNoErr)
    {
        return SGX_ERROR_UNEXPECTED;
    }
    pState = (IppsAES_GCMState*)malloc(ippStateSize);
    if (pState == NULL)
    {
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    error_code = ippsAES_GCMInit((const Ipp8u *)p_key, SGX_AESGCM_KEY_SIZE, pState, ippStateSize);
    if (error_code != ippStsNoErr)
    {
        // Clear temp State before free.
        memset_s(pState, ippStateSize, 0, ippStateSize);
        free(pState);
        switch (error_code)
        {
        case ippStsMemAllocErr: return SGX_ERROR_OUT_OF_MEMORY;
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SGX_ERROR_INVALID_PARAMETER;
        default: return SGX_ERROR_UNEXPECTED;
        }
    }
    error_code = ippsAES_GCMStart(p_iv, SGX_AESGCM_IV_SIZE, p_aad, aad_len, pState);
    if (error_code != ippStsNoErr)
    {
        // Clear temp State before free.
        memset_s(pState, ippStateSize, 0, ippStateSize);
        free(pState);
        switch (error_code)
        {
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SGX_ERROR_INVALID_PARAMETER;
        default: return SGX_ERROR_UNEXPECTED;
        }
    }
    if (src_len > 0) {
        error_code = ippsAES_GCMEncrypt(p_src, p_dst, src_len, pState);
        if (error_code != ippStsNoErr)
        {
            // Clear temp State before free.
            memset_s(pState, ippStateSize, 0, ippStateSize);
            free(pState);
            switch (error_code)
            {
            case ippStsNullPtrErr: return SGX_ERROR_INVALID_PARAMETER;
            default: return SGX_ERROR_UNEXPECTED;
            }
        }
    }
    error_code = ippsAES_GCMGetTag((Ipp8u *)p_out_mac, SGX_AESGCM_MAC_SIZE, pState);
    if (error_code != ippStsNoErr)
    {
        // Clear temp State before free.
        memset_s(p_dst, src_len, 0, src_len);
        memset_s(pState, ippStateSize, 0, ippStateSize);
        free(pState);
        switch (error_code)
        {
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SGX_ERROR_INVALID_PARAMETER;
        default: return SGX_ERROR_UNEXPECTED;
        }
    }
    // Clear temp State before free.
    memset_s(pState, ippStateSize, 0, ippStateSize);
    free(pState);
    return SGX_SUCCESS;
}

sgx_status_t sgx_rijndael128GCM_decrypt(const sgx_aes_gcm_128bit_key_t *p_key, const uint8_t *p_src,
                                        uint32_t src_len, uint8_t *p_dst, const uint8_t *p_iv, uint32_t iv_len,
                                        const uint8_t *p_aad, uint32_t aad_len, const sgx_aes_gcm_128bit_tag_t *p_in_mac)
{
    IppStatus error_code = ippStsNoErr;
    uint8_t l_tag[SGX_AESGCM_MAC_SIZE];
    IppsAES_GCMState* pState = NULL;
    int ippStateSize = 0;

    if ((p_key == NULL) || ((src_len > 0) && (p_dst == NULL)) || ((src_len > 0) && (p_src == NULL))
        || (p_in_mac == NULL) || (iv_len != SGX_AESGCM_IV_SIZE) || ((aad_len > 0) && (p_aad == NULL))
        || (p_iv == NULL) || ((p_src == NULL) && (p_aad == NULL)))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    // Autenthication Tag returned by Decrypt to be compared with Tag created during seal
    memset(&l_tag, 0, SGX_AESGCM_MAC_SIZE);
    error_code = ippsAES_GCMGetSize(&ippStateSize);
    if (error_code != ippStsNoErr)
    {
        return SGX_ERROR_UNEXPECTED;
    }
    pState = (IppsAES_GCMState*)malloc(ippStateSize);
    if (pState == NULL)
    {
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    error_code = ippsAES_GCMInit((const Ipp8u *)p_key, SGX_AESGCM_KEY_SIZE, pState, ippStateSize);
    if (error_code != ippStsNoErr)
    {
        // Clear temp State before free.
        memset_s(pState, ippStateSize, 0, ippStateSize);
        free(pState);
        switch (error_code)
        {
        case ippStsMemAllocErr: return SGX_ERROR_OUT_OF_MEMORY;
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SGX_ERROR_INVALID_PARAMETER;
        default: return SGX_ERROR_UNEXPECTED;
        }
    }
    error_code = ippsAES_GCMStart(p_iv, SGX_AESGCM_IV_SIZE, p_aad, aad_len, pState);
    if (error_code != ippStsNoErr)
    {
        // Clear temp State before free.
        memset_s(pState, ippStateSize, 0, ippStateSize);
        free(pState);
        switch (error_code)
        {
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SGX_ERROR_INVALID_PARAMETER;
        default: return SGX_ERROR_UNEXPECTED;
        }
    }
    if (src_len > 0) {
        error_code = ippsAES_GCMDecrypt(p_src, p_dst, src_len, pState);
        if (error_code != ippStsNoErr)
        {
            // Clear temp State before free.
            memset_s(pState, ippStateSize, 0, ippStateSize);
            free(pState);
            switch (error_code)
            {
            case ippStsNullPtrErr: return SGX_ERROR_INVALID_PARAMETER;
            default: return SGX_ERROR_UNEXPECTED;
            }
        }
    }
    error_code = ippsAES_GCMGetTag((Ipp8u *)l_tag, SGX_AESGCM_MAC_SIZE, pState);
    if (error_code != ippStsNoErr)
    {
        // Clear temp State before free.
        memset_s(p_dst, src_len, 0, src_len);
        memset_s(pState, ippStateSize, 0, ippStateSize);
        free(pState);
        switch (error_code)
        {
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SGX_ERROR_INVALID_PARAMETER;
        default: return SGX_ERROR_UNEXPECTED;
        }
    }
    // Clear temp State before free.
    memset_s(pState, ippStateSize, 0, ippStateSize);
    free(pState);

    // Verify current data tag = data tag generated when sealing the data blob
    if (consttime_memequal(p_in_mac, &l_tag, SGX_AESGCM_MAC_SIZE) == 0)
    {
        memset_s(p_dst, src_len, 0, src_len);
        memset_s(&l_tag, SGX_AESGCM_MAC_SIZE, 0, SGX_AESGCM_MAC_SIZE);
        return SGX_ERROR_MAC_MISMATCH;
    }

    memset_s(&l_tag, SGX_AESGCM_MAC_SIZE, 0, SGX_AESGCM_MAC_SIZE);
    return SGX_SUCCESS;
}

