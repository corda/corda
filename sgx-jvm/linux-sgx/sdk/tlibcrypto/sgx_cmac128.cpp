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
    IppsAES_CMACState* pState = NULL;
    int ippStateSize = 0;
    IppStatus error_code = ippStsNoErr;

    if ((p_key == NULL) || (p_src == NULL) || (p_mac == NULL))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    error_code = ippsAES_CMACGetSize(&ippStateSize);
    if (error_code != ippStsNoErr)
    {
        return SGX_ERROR_UNEXPECTED;
    }
    pState = (IppsAES_CMACState*)malloc(ippStateSize);
    if (pState == NULL)
    {
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    error_code = ippsAES_CMACInit((const Ipp8u *)p_key, SGX_CMAC_KEY_SIZE, pState, ippStateSize);
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
    error_code = ippsAES_CMACUpdate((const Ipp8u *)p_src, src_len, pState);
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
    error_code = ippsAES_CMACFinal((Ipp8u *)p_mac, SGX_CMAC_MAC_SIZE, pState);
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
    // Clear temp State before free.
    memset_s(pState, ippStateSize, 0, ippStateSize);
    free(pState);
    return SGX_SUCCESS;
}

static void sgx_secure_free_cmac128_state(IppsAES_CMACState *pState)
{
    if (pState == NULL)
        return;
    int ippStateSize = 0;
    IppStatus error_code = ippStsNoErr;
    error_code = ippsAES_CMACGetSize(&ippStateSize);
    if (error_code != ippStsNoErr)
    {
        free(pState);
        return;
    }
    memset_s(pState, ippStateSize, 0, ippStateSize);
    free(pState);
    return;
}

/* Allocates and initializes CMAC state
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.h
*   Inputs: sgx_cmac_128bit_key_t *p_key - Pointer to the key used in encryption/decryption operation
*   Output: sgx_cmac_state_handle_t *p_cmac_handle - Pointer to the handle of the CMAC state  */
sgx_status_t sgx_cmac128_init(const sgx_cmac_128bit_key_t *p_key, sgx_cmac_state_handle_t* p_cmac_handle)
{
    if ((p_key == NULL) || (p_cmac_handle == NULL))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    IppsAES_CMACState* pState = NULL;
    int ippStateSize = 0;
    IppStatus error_code = ippStsNoErr;
    error_code = ippsAES_CMACGetSize(&ippStateSize);
    if (error_code != ippStsNoErr)
    {
        return SGX_ERROR_UNEXPECTED;
    }
    pState = (IppsAES_CMACState*)malloc(ippStateSize);
    if (pState == NULL)
    {
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    error_code = ippsAES_CMACInit((const Ipp8u *)p_key, SGX_CMAC_KEY_SIZE, pState, ippStateSize);
    if (error_code != ippStsNoErr)
    {
        // Clear state before free.
        memset_s(pState, ippStateSize, 0, ippStateSize);
        free(pState);
        *p_cmac_handle = NULL;
        switch (error_code)
        {
        case ippStsMemAllocErr: return SGX_ERROR_OUT_OF_MEMORY;
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SGX_ERROR_INVALID_PARAMETER;
        default: return SGX_ERROR_UNEXPECTED;
        }
    }
    *p_cmac_handle = pState;
    return SGX_SUCCESS;
}

/* Updates CMAC hash calculation based on the input message
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.
*   Input:  sgx_cmac_state_handle_t cmac_handle - Handle to the CMAC state
*           uint8_t *p_src - Pointer to the input stream to be hashed
*           uint32_t src_len - Length of the input stream to be hashed  */
sgx_status_t sgx_cmac128_update(const uint8_t *p_src, uint32_t src_len, sgx_cmac_state_handle_t cmac_handle)
{
    if ((p_src == NULL) || (cmac_handle == NULL))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    IppStatus error_code = ippStsNoErr;
    error_code = ippsAES_CMACUpdate(p_src, src_len, (IppsAES_CMACState*)cmac_handle);
    switch (error_code)
    {
    case ippStsNoErr: return SGX_SUCCESS;
    case ippStsNullPtrErr:
    case ippStsLengthErr: return SGX_ERROR_INVALID_PARAMETER;
    default: return SGX_ERROR_UNEXPECTED;
    }
}

/* Returns Hash calculation
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.h
*   Input:  sgx_cmac_state_handle_t cmac_handle - Handle to the CMAC state
*   Output: sgx_cmac_128bit_tag_t *p_hash - Resultant hash from operation  */
sgx_status_t sgx_cmac128_final(sgx_cmac_state_handle_t cmac_handle, sgx_cmac_128bit_tag_t *p_hash)
{
    if ((cmac_handle == NULL) || (p_hash == NULL))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    IppStatus error_code = ippStsNoErr;
    error_code = ippsAES_CMACFinal((Ipp8u *)p_hash, SGX_CMAC_MAC_SIZE, (IppsAES_CMACState*)cmac_handle);
    switch (error_code)
    {
    case ippStsNoErr: return SGX_SUCCESS;
    case ippStsNullPtrErr:
    case ippStsLengthErr: return SGX_ERROR_INVALID_PARAMETER;
    default: return SGX_ERROR_UNEXPECTED;
    }
}


/* Clean up the CMAC state
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.h
*   Input:  sgx_cmac_state_handle_t cmac_handle  - Handle to the CMAC state  */
sgx_status_t sgx_cmac128_close(sgx_cmac_state_handle_t cmac_handle)
{
    if (cmac_handle == NULL)
        return SGX_ERROR_INVALID_PARAMETER;
    sgx_secure_free_cmac128_state((IppsAES_CMACState*)cmac_handle);
    return SGX_SUCCESS;
}
