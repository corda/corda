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


#include <string.h>

#include "se_memcpy.h"
#include "se_error_internal.h"
#include "uae_service_internal.h"
#include "launch_checker.h"


static unsigned int chk_launch_token(
    const enclave_css_t *css,
    const sgx_attributes_t *secs_attr,
    const sgx_launch_token_t *tok)
{
    const token_t *launch = reinterpret_cast<const token_t *>(tok);

    // Is it a valid launch_token? 0 = Invalid, 1 = Valid.
    if(!launch->body.valid)
    {
        // For Non-Architectural Enclaves: HWVERSION = 0
        // Anyway, EINIT will return SE_INVALID_LAUNCH_TOKEN if the Enclave
        // is not signed by intel key.
        if(0 == css->header.hw_version)
        {
            // If the licese token is invalid, only intel key signed
            // enclave can be lauched.
            return (unsigned int)SE_ERROR_INVALID_LAUNCH_TOKEN;
        }
        else
        {
            // Don't need launch token for intel key signed enclave.
            return (unsigned int)SGX_SUCCESS;
        }
    }

    // The MRENCLAVE should match the one in SIGSTRUCT.  We must check
    // it here, becaue EINIT will return SE_INVALID_MEASUREMENT for
    // this case.
    if(memcmp(&launch->body.mr_enclave, &css->body.enclave_hash, sizeof(sgx_measurement_t)))
    {
        return (unsigned int)SE_ERROR_INVALID_LAUNCH_TOKEN;
    }

    // The ATTRIBUTES should match the attributes in SECS.
    
    
    
    if(memcmp(&launch->body.attributes, secs_attr, sizeof(sgx_attributes_t)))
    {
        return (unsigned int)SE_ERROR_INVALID_LAUNCH_TOKEN;
    }

    // Other checks are done in get_secs_attr later.
    return SGX_SUCCESS;
}

SGXLaunchToken::SGXLaunchToken(
    const enclave_css_t *css,
    const sgx_attributes_t *secs_attr,
    const sgx_launch_token_t *launch)
    :m_css(css), m_secs_attr(secs_attr), m_launch_updated(false)
{
    memcpy_s(m_launch, sizeof(m_launch), launch, sizeof(m_launch));
}

bool SGXLaunchToken::is_launch_updated() const
{
    return m_launch_updated;
}

sgx_status_t SGXLaunchToken::get_launch_token(sgx_launch_token_t *tok) const
{
    if (memcpy_s(tok, sizeof(m_launch), &m_launch, sizeof(m_launch)))
        return SGX_ERROR_UNEXPECTED;
    return SGX_SUCCESS;
}

sgx_status_t SGXLaunchToken::update_launch_token(
    bool force_update_tok)
{
    sgx_status_t status = SGX_SUCCESS;

    if (force_update_tok ||
            SE_ERROR_INVALID_LAUNCH_TOKEN == chk_launch_token(m_css, m_secs_attr, &m_launch))
    {
        status = ::get_launch_token(m_css, m_secs_attr, &m_launch);

        if (status == SGX_SUCCESS)
            m_launch_updated = true;
        else
            return status;
    }

    return status;
}


