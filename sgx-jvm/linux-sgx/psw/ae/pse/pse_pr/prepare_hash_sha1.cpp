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

#include "prepare_hash_sha1.h"
#include <stdlib.h>

#include "ippcp.h"


PrepareHashSHA1::PrepareHashSHA1()
    : m_status(false), m_pCtx(0)
{
    IppStatus ippstatus;
    int size;

    do
    {
        ippstatus = ippsSHA1GetSize(&size);
        if (ippstatus != ippStsNoErr)
            break;

        m_pCtx = (IppsSHA1State*) malloc(size);
        if (NULL == m_pCtx)
            break;

        ippstatus = ippsSHA1Init((IppsSHA1State*)m_pCtx);
        if (ippstatus != ippStsNoErr)
        {
            free(m_pCtx);
			m_pCtx = NULL;
            break;
        }

        m_status = true;

    } while (0);
}

PrepareHashSHA1::~PrepareHashSHA1(void)
{
    if (m_pCtx) {
        free(m_pCtx);
		m_pCtx = NULL;
	}
}

bool PrepareHashSHA1::Update(const void* pData, size_t numBytes)
{
    do
    {
        if (!m_status)
            break;

        m_status = false;

        if (NULL == pData || numBytes < 1 ||
            NULL == m_pCtx)
            break;

        if (numBytes > INT32_MAX)
            break;

        if (ippStsNoErr != ippsSHA1Update((const Ipp8u*)pData, (int)numBytes, (IppsSHA1State*)m_pCtx))
            break;

        m_status = true;

    } while (0);

    return m_status;
}

// pCMAC will contain the computed CMAC if SDS_SUCCESS
bool PrepareHashSHA1::Finalize(SHA1_HASH *pHash)
{
    do
    {
        if (!m_status)
            break;

        m_status = false;

        if (NULL == m_pCtx || NULL == pHash)
            break;

        if (ippStsNoErr != ippsSHA1Final((Ipp8u*)pHash, (IppsSHA1State*)m_pCtx))
            break;

        m_status = true;

    } while (0);

    return m_status;
}
