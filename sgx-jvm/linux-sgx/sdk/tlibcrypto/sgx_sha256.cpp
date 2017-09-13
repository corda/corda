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

#ifndef SAFE_FREE
#define SAFE_FREE(ptr) {if (NULL != (ptr)) {free(ptr); (ptr)=NULL;}}
#endif


/* Allocates and initializes sha256 state
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.h
*   Output: sgx_sha_state_handle_t *p_sha_handle - Pointer to the handle of the SHA256 state  */
sgx_status_t sgx_sha256_init(sgx_sha_state_handle_t* p_sha_handle)
{
    IppStatus ipp_ret = ippStsNoErr;
    IppsHashState* p_temp_state = NULL;

    if (p_sha_handle == NULL)
        return SGX_ERROR_INVALID_PARAMETER;

    int ctx_size = 0;
    ipp_ret = ippsHashGetSize(&ctx_size);
    if (ipp_ret != ippStsNoErr)
        return SGX_ERROR_UNEXPECTED;
    p_temp_state = (IppsHashState*)(malloc(ctx_size));
    if (p_temp_state == NULL)
        return SGX_ERROR_OUT_OF_MEMORY;
    ipp_ret = ippsHashInit(p_temp_state, IPP_ALG_HASH_SHA256);
    if (ipp_ret != ippStsNoErr)
    {
        SAFE_FREE(p_temp_state);
        *p_sha_handle = NULL;
        switch (ipp_ret)
        {
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SGX_ERROR_INVALID_PARAMETER;
        default: return SGX_ERROR_UNEXPECTED;
        }
    }

    *p_sha_handle = p_temp_state;
    return SGX_SUCCESS;
}

/* Updates sha256 has calculation based on the input message
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.
*   Input:  sgx_sha_state_handle_t sha_handle - Handle to the SHA256 state
*           uint8_t *p_src - Pointer to the input stream to be hashed
*           uint32_t src_len - Length of the input stream to be hashed  */
sgx_status_t sgx_sha256_update(const uint8_t *p_src, uint32_t src_len, sgx_sha_state_handle_t sha_handle)
{
    if ((p_src == NULL) || (sha_handle == NULL))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    IppStatus ipp_ret = ippStsNoErr;
    ipp_ret = ippsHashUpdate(p_src, src_len, (IppsHashState*)sha_handle);
    switch (ipp_ret)
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
*   Input:  sgx_sha_state_handle_t sha_handle - Handle to the SHA256 state
*   Output: sgx_sha256_hash_t *p_hash - Resultant hash from operation  */
sgx_status_t sgx_sha256_get_hash(sgx_sha_state_handle_t sha_handle, sgx_sha256_hash_t *p_hash)
{
    if ((sha_handle == NULL) || (p_hash == NULL))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    IppStatus ipp_ret = ippStsNoErr;
    ipp_ret = ippsHashGetTag((Ipp8u*)p_hash, SGX_SHA256_HASH_SIZE, (IppsHashState*)sha_handle);
    switch (ipp_ret)
    {
    case ippStsNoErr: return SGX_SUCCESS;
    case ippStsNullPtrErr:
    case ippStsLengthErr: return SGX_ERROR_INVALID_PARAMETER;
    default: return SGX_ERROR_UNEXPECTED;
    }
}

/* Cleans up sha state
* Parameters:
*   Return: sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.h
*   Input:  sgx_sha_state_handle_t sha_handle - Handle to the SHA256 state  */
sgx_status_t sgx_sha256_close(sgx_sha_state_handle_t sha_handle)
{
    if (sha_handle == NULL)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    SAFE_FREE(sha_handle);
    return SGX_SUCCESS;
}
