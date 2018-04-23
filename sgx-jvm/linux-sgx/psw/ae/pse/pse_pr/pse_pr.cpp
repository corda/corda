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
  * File: platform_service_enclave.cpp
  * Description: Definition for interfaces provided by platform service enclave..
  *
  * Definition for interfaces provided by platform service enclave.
  */
#include <cstddef>

#include "pse_pr_t.c"
#include "pse_pr_inc.h"

#include "t_certificate_provisioning.h"
#include "t_long_term_pairing.h"
#include "le2be_macros.h"
#include "pse_pr_common.h"

#include "Epid11_rl.h"


static TEpidSigma11Verifier*  s_pVerifier = NULL;

//length to copy into enclave
size_t get_sigRL_size(const EPID11_SIG_RL* pSigRL)
{
    uint32_t nEntries = 0;
    uint32_t nSize = 0;

    if (TEpidSigma11Verifier::get_sigRL_info(pSigRL, nEntries, nSize))
        return nSize;
    else
        //invalid sigRL
        return sizeof(EPID11_SIG_RL);
}

//length to copy into enclave
size_t get_privRL_size(const EPID11_PRIV_RL* pPrivRL)
{
    uint32_t nEntries = 0;
    uint32_t nSize = 0;

    if (TEpidSigma11Verifier::get_privRL_info(pPrivRL, nEntries, nSize))
        return nSize;
    else
        //invalid privRL
        return sizeof(EPID11_PRIV_RL);
}


ae_error_t ecall_tPrepareForCertificateProvisioning
(
    /*in */ UINT64  nonce64,
    /*in */ const sgx_target_info_t*     pTargetInfo,
    /*in */ UINT16  nMaxLen_CSR_pse,
    /*out*/ UINT8*  pCSR_pse,
    /*out*/ UINT16* pnTotalLen_CSR_pse,
    /*out*/       sgx_report_t*          pREPORT,
    /*i/o*/       pairing_blob_t* pPairingBlob
)
{
    ae_error_t status = AE_FAILURE;

    status = prepare_for_certificate_provisioning( nonce64, pTargetInfo, 
        nMaxLen_CSR_pse, pCSR_pse, pnTotalLen_CSR_pse,
        pREPORT, pPairingBlob);

    return status;
}


ae_error_t ecall_tGenM7
(
    /*in */ const SIGMA_S1_MESSAGE*      pS1,
    /*in */ const EPID11_SIG_RL*         pSigRL, 
    /*in */ const uint8_t*               pOcspResp, 
    /*in */ uint32_t  nLen_OcspResp,
    /*in */ const uint8_t*               pVerifierCert,
    /*in */ uint32_t  nLen_VerifierCert,
    /*in */ const pairing_blob_t* pPairingBlob,
    /*in */ uint32_t  nMax_S2,
    /*out*/       SIGMA_S2_MESSAGE*      pS2,
    /*out*/ uint32_t* pnLen_S2
)
{
    ae_error_t status = AE_FAILURE;

    PSE_PR_SAFE_DELETE(s_pVerifier);

    do
    {
        s_pVerifier = new (std::nothrow) TEpidSigma11Verifier;
        BREAK_IF_TRUE((NULL == s_pVerifier), status, PSE_PR_INSUFFICIENT_MEMORY_ERROR);

        status = s_pVerifier->GenM7(
            pS1, 
            pSigRL, 
            pOcspResp, nLen_OcspResp, 
            pVerifierCert, nLen_VerifierCert,
            pPairingBlob,
            nMax_S2, pS2, pnLen_S2);
        BREAK_IF_FAILED(status);

    } while (0);

    if (AE_FAILED(status))
        PSE_PR_SAFE_DELETE(s_pVerifier);

    return status;
}


ae_error_t ecall_tVerifyM8
(
    /*in */ const SIGMA_S3_MESSAGE*      pS3,
    /*in */ uint32_t  nLen_S3,
    /*in */ const EPID11_PRIV_RL*        pPrivRL,
    /*out*/       pairing_blob_t* pPairingBlob,
    /*out*/ uint8_t*  puNewPairing
)
{
    ae_error_t status = AE_FAILURE;

    do
    {
        BREAK_IF_TRUE((NULL == s_pVerifier), status, PSE_PR_CALL_ORDER_ERROR);

        status = s_pVerifier->VerifyM8(
            pS3, nLen_S3, 
            pPrivRL, pPairingBlob,
            (bool*)puNewPairing);
        BREAK_IF_FAILED(status);

    } while (0);

    PSE_PR_SAFE_DELETE(s_pVerifier);

    return status;
}


#if 0
#include <stdio.h>
#include <stdarg.h>
// EDL for this function
//    untrusted
//    {
//        void ocall_OutputOctets
//        (
//            [in, sizefunc=strlen_with_null] const char*  pMsg,
//            [in, size=nData]                const void*  pData,
//                                            size_t nData
//        );
//    };
void OutputOctets(const char* pMsg, const void* pData, size_t nData)
{
    ocall_OutputOctets(pMsg, (const uint8_t*)pData, nData);
}

// EDL for this function
//    untrusted
//    {
//        void ocall_OutputString
//        (
//            [in, sizefunc=strlen_with_null] const char*  pMsg,
//        );
//    };
void OutputString(const char* format, ...)
{
    char buffer[1024];
    va_list args;
    va_start (args, format);
    vsnprintf (buffer, sizeof(buffer), format, args);
    va_end (args);
    
    ocall_OutputString(buffer);    
}

size_t strlen_with_null(const char* pMsg)
{
    size_t len = 0;
    if (pMsg)
        len = strlen(pMsg) + 1;
    return len;
}
#endif




