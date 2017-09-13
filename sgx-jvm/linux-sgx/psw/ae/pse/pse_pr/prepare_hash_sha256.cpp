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

#include "prepare_hash_sha256.h"
#include <cstddef>


PrepareHashSHA256::PrepareHashSHA256()
    : m_sgx_status(SGX_SUCCESS), m_pShaState(NULL)
{
    m_sgx_status = sgx_sha256_init(&m_pShaState);
}

PrepareHashSHA256::~PrepareHashSHA256(void)
{
    if (NULL != m_pShaState)
        sgx_sha256_close(m_pShaState);
}

ae_error_t PrepareHashSHA256::Update(const void* pData, size_t numBytes)
{
    ae_error_t ae_status = PSE_PR_HASH_CALC_ERROR;
    do
    {
        if (SGX_SUCCESS != m_sgx_status) {
            break;
        }

        if (NULL == pData || numBytes < 1 || NULL == m_pShaState || numBytes > UINT32_MAX)
        {
            m_sgx_status = SGX_ERROR_INVALID_PARAMETER;
            break;
        }

        m_sgx_status = sgx_sha256_update((const uint8_t*)pData, (uint32_t)numBytes, m_pShaState);
        if (SGX_SUCCESS != m_sgx_status) {
            break;
        }
        ae_status = AE_SUCCESS;

    } while (0);

    if (SGX_ERROR_OUT_OF_MEMORY == m_sgx_status)
    {
        ae_status = PSE_PR_INSUFFICIENT_MEMORY_ERROR;
    }

    return ae_status;
}

ae_error_t PrepareHashSHA256::Finalize(SHA256_HASH *pHash)
{
    ae_error_t ae_status = PSE_PR_HASH_CALC_ERROR;
    do
    {
        if (SGX_SUCCESS != m_sgx_status)
            break;

        if (NULL == m_pShaState || NULL == pHash)
        {
            m_sgx_status = SGX_ERROR_INVALID_PARAMETER;
            break;
        }

        if (sizeof(*pHash) != sizeof(sgx_sha256_hash_t))
        {
            m_sgx_status = SGX_ERROR_INVALID_PARAMETER;
            break;
        }

        m_sgx_status = sgx_sha256_get_hash(m_pShaState, (sgx_sha256_hash_t*)pHash);
        if (SGX_SUCCESS != m_sgx_status) {
            break;
        }

        ae_status = AE_SUCCESS;
    } while (0);

    if (SGX_ERROR_OUT_OF_MEMORY == m_sgx_status) {
        ae_status = PSE_PR_INSUFFICIENT_MEMORY_ERROR;
    }

    return ae_status;
}
