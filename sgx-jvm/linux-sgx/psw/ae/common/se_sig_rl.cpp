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


#include "stdint.h"
#include "se_sig_rl.h"
#include "byte_order.h"
#include "sgx_quote.h"
#include "se_quote_internal.h"

uint64_t se_get_sig_rl_size(const se_sig_rl_t *p_sig_rl)
{
  uint64_t n2 = (p_sig_rl) ? lv_ntohl(p_sig_rl->sig_rl.n2) : 0;
  return(sizeof(se_sig_rl_t) - sizeof(p_sig_rl->sig_rl.bk[0])
         + n2 * sizeof(p_sig_rl->sig_rl.bk[0]) + 2 * SE_ECDSA_SIGN_SIZE);
}


sgx_status_t sgx_calc_quote_size(const uint8_t *sig_rl, uint32_t sig_rl_size, uint32_t* p_quote_size)
{
    if (!p_quote_size)
        return SGX_ERROR_INVALID_PARAMETER;
    uint64_t quote_size = 0;
    uint64_t sign_size = 0;
    uint64_t n2 = 0;
    const se_sig_rl_t *p_sig_rl = reinterpret_cast<const se_sig_rl_t *>(sig_rl);

    if (sig_rl)
    {
        if (sig_rl_size < sizeof(se_sig_rl_t) ||
            se_get_sig_rl_size(p_sig_rl) != sig_rl_size)
        {
            return SGX_ERROR_INVALID_PARAMETER;
        }
        if (p_sig_rl->protocol_version != SE_EPID_SIG_RL_VERSION
            || p_sig_rl->epid_identifier != SE_EPID_SIG_RL_ID)
        {
            return SGX_ERROR_INVALID_PARAMETER;
        }
    }
    else if (sig_rl_size != 0)
        return SGX_ERROR_INVALID_PARAMETER;

    n2 = (sig_rl) ? lv_ntohl(p_sig_rl->sig_rl.n2) : 0;
    sign_size = sizeof(EpidSignature) - sizeof(NrProof) + n2 * sizeof(NrProof);
    quote_size = SE_QUOTE_LENGTH_WITHOUT_SIG + sign_size;
    if (quote_size >= 1ull << 32)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    *p_quote_size = static_cast<uint32_t>(quote_size);
    return SGX_SUCCESS;
}



sgx_status_t sgx_get_quote_size(const uint8_t *sig_rl, uint32_t* p_quote_size)
{
    if(!p_quote_size)
        return SGX_ERROR_INVALID_PARAMETER;
    uint64_t quote_size = 0;
    uint64_t sign_size = 0;
    uint64_t n2 = 0;
    const se_sig_rl_t *p_sig_rl = reinterpret_cast<const se_sig_rl_t *>(sig_rl);

    if(sig_rl)
    {
        if(p_sig_rl->protocol_version != SE_EPID_SIG_RL_VERSION
           || p_sig_rl->epid_identifier != SE_EPID_SIG_RL_ID)
        {
            return SGX_ERROR_INVALID_PARAMETER;
        }
    }

    n2 = (sig_rl) ? lv_ntohl(p_sig_rl->sig_rl.n2) : 0;
    sign_size = sizeof(EpidSignature) - sizeof(NrProof) + n2*sizeof(NrProof);
    quote_size = SE_QUOTE_LENGTH_WITHOUT_SIG + sign_size;
    if (quote_size >= 1ull<<32)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    *p_quote_size = static_cast<uint32_t>(quote_size);
    return SGX_SUCCESS;
}

