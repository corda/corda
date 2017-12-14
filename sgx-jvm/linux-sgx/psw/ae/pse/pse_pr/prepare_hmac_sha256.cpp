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

#include "prepare_hmac_sha256.h"
#include <stdlib.h>


PrepareHMACSHA256::PrepareHMACSHA256(const Ipp8u* key, size_t keyLength)
    : m_ippstatus(ippStsNoErr), m_pCtx(0)
{
    int size;

    do
    {
        if (NULL == key || keyLength < 1)
        {
            m_ippstatus = ippStsBadArgErr;
            break;
        }

        m_ippstatus = ippsHMAC_GetSize(&size);
        if (m_ippstatus != ippStsNoErr)
            break;

        m_pCtx = (IppsHMACState*) malloc(size);
        if (NULL == m_pCtx)
        {
            m_ippstatus = ippStsNoMemErr;
            break;
        }

        if (keyLength > INT32_MAX)
        {
            free(m_pCtx);
            m_pCtx = NULL;
            break;
        }

        m_ippstatus = ippsHMAC_Init(key, (int)keyLength, m_pCtx, IPP_ALG_HASH_SHA256);
        if (m_ippstatus != ippStsNoErr)
        {
            free(m_pCtx);
			m_pCtx = NULL;
            break;
        }
    } while (0);
}

PrepareHMACSHA256::~PrepareHMACSHA256(void)
{
    if (m_pCtx) {
        free(m_pCtx);
		m_pCtx = NULL;
	}
}

ae_error_t PrepareHMACSHA256::Update(const void* pData, size_t numBytes)
{
    do
    {
        if (m_ippstatus != ippStsNoErr)
            break;

        if (NULL == pData || numBytes < 1 || NULL == m_pCtx || numBytes > INT32_MAX)
        {
            m_ippstatus = ippStsBadArgErr;
            break;
        }

        m_ippstatus = ippsHMAC_Update((const Ipp8u*)pData, (int)numBytes, m_pCtx);
        if (m_ippstatus != ippStsNoErr)
            break;

    } while (0);

    ae_error_t ae_status = AE_SUCCESS;
    if (m_ippstatus != ippStsNoErr)
    {
        if (ippStsNoMemErr == m_ippstatus)
            ae_status = PSE_PR_INSUFFICIENT_MEMORY_ERROR;
        else
            ae_status = PSE_PR_HMAC_CALC_ERROR;
    }

    return ae_status;
}

// pCMAC will contain the computed CMAC if SDS_SUCCESS
ae_error_t PrepareHMACSHA256::Finalize(SIGMA_HMAC *pHMAC)
{
    do
    {
        if (m_ippstatus != ippStsNoErr)
            break;

        if (NULL == m_pCtx || NULL == pHMAC)
        {
            m_ippstatus = ippStsBadArgErr;
            break;
        }

        m_ippstatus = ippsHMAC_Final((Ipp8u*)pHMAC, sizeof(SIGMA_HMAC), m_pCtx);
        if (m_ippstatus != ippStsNoErr)
            break;

    } while (0);

    ae_error_t ae_status = AE_SUCCESS;
    if (m_ippstatus != ippStsNoErr)
    {
        if (ippStsNoMemErr == m_ippstatus)
            ae_status = PSE_PR_INSUFFICIENT_MEMORY_ERROR;
        else
            ae_status = PSE_PR_HMAC_CALC_ERROR;
    }

    return ae_status;
}
