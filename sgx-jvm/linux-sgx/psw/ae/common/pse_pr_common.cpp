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


/**
 * File: pse_pr_common.cpp
 * Description: Common code needed between untrusted and enclave
 */

#include "pse_pr_common.h"
#include "pse_pr_sigma_1_1_defs.h"
#include "pse_pr_sigma_common_defs.h"
#include "sgx_tseal.h"
#include "sgx_report.h"
#include "pairing_blob.h"

#include "Epid11_rl.h"

#define MAX_CSR_BYTES   1024


uint32_t NeededBytesForPairingBlob()
{
    return sgx_calc_sealed_data_size(sizeof(se_plaintext_pairing_data_t), sizeof(se_secret_pairing_data_t));
}


uint32_t NeededBytesForREPORT()
{
    return sizeof(sgx_report_t); 
}


uint32_t NeededBytesForS2(uint32_t nCertChain, uint32_t nRL, uint32_t nOcspResp)
{
	uint32_t nRL_VLR = 0;
	
	if (nRL > 0)
	{
        uint32_t nPaddedBytes =  REQUIRED_PADDING_DWORD_ALIGNMENT(nRL);
        nRL_VLR = static_cast<uint32_t>(sizeof(SIGMA_VLR_HEADER)) + nPaddedBytes + nRL;
    }

    uint32_t nFixed = sizeof(SIGMA_S2_MESSAGE);   // Fixed portion of S2
    uint32_t nNeededPr = sizeof(PR_PSE_T);

    return nFixed + nCertChain + nRL_VLR + nOcspResp + nNeededPr; 
}


uint32_t MaxBytesForCSR()
{
    return 1024; 
}
