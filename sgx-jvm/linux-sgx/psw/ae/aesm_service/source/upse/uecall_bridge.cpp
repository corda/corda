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

#include "u_long_term_pairing.h"
#include <cstddef>
#include "u_certificate_provisioning.h"
#include "uecall_bridge.h"

#include "pse_pr_u.h"
#include "pse_pr_u.c"

#include "pse_pr_common.h"

#include <Buffer.h>
#include "pse_pr_sigma_1_1_defs.h"
#include "le2be_macros.h"

#include "aeerror.h"

//
// need to fix this
//
#define __FUNCTIONW__ __FUNCTION__

#include "oal/oal.h"

static sgx_enclave_id_t _enclaveID;

static ae_error_t check_sigrl_entries_max(const EPID11_SIG_RL* pSigRL)
{
    if (NULL != pSigRL)
    {
        const uint32_t* p = reinterpret_cast<const uint32_t*>(pSigRL->entries);
        uint32_t nEntries = SwapEndian_DW(*p);
        if (nEntries > MAX_SIGRL_ENTRIES)
            return AESM_PSE_PR_MAX_SIGRL_ENTRIES_EXCEEDED;
    }

    return AE_SUCCESS;
}

static ae_error_t check_privrl_entries_max(const EPID11_PRIV_RL* pPrivRL)
{
    if (NULL != pPrivRL)
    {
        const uint32_t* p = reinterpret_cast<const uint32_t*>(pPrivRL->entries);
        uint32_t nEntries = SwapEndian_DW(*p);
        if (nEntries > MAX_PRIVRL_ENTRIES)
            return AESM_PSE_PR_MAX_PRIVRL_ENTRIES_EXCEEDED;
    }

    return AE_SUCCESS;
}



void SaveEnclaveID(sgx_enclave_id_t eid)
{
    _enclaveID = eid;
}



ae_error_t tPrepareForCertificateProvisioning
(
    /*in */ upse::Buffer& nonce,
    /*in */ upse::Buffer& target_info,
    /*out*/ upse::Buffer& csr_pse,
    /*out*/ upse::Buffer& report,
    /*out*/ upse::Buffer& pairingBlob
)
{
    ae_error_t retval;
    UINT8* pCsr = NULL;

    do
    {
        sgx_status_t seStatus;

        UINT64* pNonce64 = (UINT64*)const_cast<UINT8*>(nonce.getData());
        UINT32 nNonce64 = nonce.getSize();

        UINT8* pTargetInfo = const_cast<UINT8*>(target_info.getData());
        UINT32 nTargetInfo = target_info.getSize();

        BREAK_IF_TRUE( (NULL == pNonce64 || NULL == pTargetInfo), 
            retval, PSE_PR_ASN1DER_DECODING_ERROR);
        BREAK_IF_FALSE( (nNonce64 == sizeof(UINT64)), retval, PSE_PR_INTERNAL_ERROR);

        UINT16 nCsrPse = (UINT16)MaxBytesForCSR();
        UINT32 nReport = NeededBytesForREPORT();
        UINT32 nPairingBlob = NeededBytesForPairingBlob();

        // Allocate memory for output buffers
        upse::Buffer tmpReport;
        retval = tmpReport.Alloc(nReport);
        if (AE_FAILED(retval))
            break;
        upse::Buffer tmpPairingBlob;

        if (nPairingBlob == pairingBlob.getSize())
            retval = tmpPairingBlob.Clone(pairingBlob);
        else
        {
            // If pairingBlob is not the correct size, start with empty blob
            retval = tmpPairingBlob.Alloc(nPairingBlob);
        }
        if (AE_FAILED(retval))
            break;

        upse::BufferWriter bwReport(tmpReport);

        upse::BufferWriter bwPairingBlob(tmpPairingBlob);

        pCsr = (UINT8*)calloc(1, nCsrPse);
        BREAK_IF_FALSE((NULL != pCsr), retval, PSE_PR_INSUFFICIENT_MEMORY_ERROR);

        UINT8* pReport;
        retval = bwReport.reserve(nReport, &pReport);
        if (AE_FAILED(retval))
            break;
        UINT8* pPairingBlob;
        retval = bwPairingBlob.reserve(nPairingBlob, &pPairingBlob);
        if (AE_FAILED(retval))
            break;

        BREAK_IF_FALSE((nTargetInfo == sizeof(sgx_target_info_t)), retval, PSE_PR_INTERNAL_ERROR);
        BREAK_IF_FALSE((nReport == sizeof(sgx_report_t)), retval, PSE_PR_INTERNAL_ERROR);

        if (nPairingBlob != pairingBlob.getSize())
        {
            // generate new sw_instance_id only when no valid LTP blob
            BREAK_IF_FAILED(generate_pse_instance_id(((pairing_blob_t*)pPairingBlob)->plaintext.pse_instance_id));
        }
        
        // Call to get size required of output buffers
        seStatus = ecall_tPrepareForCertificateProvisioning(_enclaveID, &retval,
            *pNonce64,
            (sgx_target_info_t*)pTargetInfo, 
            nCsrPse, pCsr, &nCsrPse,
            (sgx_report_t*)pReport,
            (pairing_blob_t*)pPairingBlob);

        BREAK_IF_TRUE( (SGX_ERROR_ENCLAVE_LOST == seStatus), retval, PSE_PR_ENCLAVE_LOST_ERROR);
        BREAK_IF_TRUE( (SGX_SUCCESS != seStatus), retval, PSE_PR_ENCLAVE_BRIDGE_ERROR);

        BREAK_IF_FAILED(retval);

        retval = report.Clone(tmpReport);
        BREAK_IF_FAILED(retval);
        retval = pairingBlob.Clone(tmpPairingBlob);
        BREAK_IF_FAILED(retval);
        retval = csr_pse.Alloc(pCsr, nCsrPse);
        BREAK_IF_FAILED(retval);

    } while (0);

    if (pCsr != NULL)
        free(pCsr);

	SGX_DBGPRINT_PRINT_FUNCTION_AND_RETURNVAL(__FUNCTIONW__, retval);

    return retval;
}

#if defined(NO_PROVISIONING_SERVER)
ae_error_t tPrepareForCertificateProvisioning_hardcoded_privatekey
(

    /*out*/ upse::Buffer& pairingBlob
)
{
    ae_error_t retval;

    do
    {
        sgx_status_t seStatus;

        UINT32 nPairingBlob = NeededBytesForPairingBlob();

        // Allocate memory for output buffers
        upse::Buffer tmpPairingBlob;

        if (nPairingBlob == pairingBlob.getSize())
            retval = tmpPairingBlob.Clone(pairingBlob);
        else
            retval = tmpPairingBlob.Alloc(nPairingBlob);
        if (AE_FAILED(retval))
            break;

        upse::BufferWriter bwPairingBlob(tmpPairingBlob);

        UINT8* pPairingBlob;
        retval = bwPairingBlob.reserve(nPairingBlob, &pPairingBlob);
        BREAK_IF_FAILED(retval);

		// calculate platform instance id
		BREAK_IF_FAILED(generate_pse_instance_id(((pairing_blob_t*)pPairingBlob)->plaintext.pse_instance_id));

        // Call to get size required of output buffers
        seStatus = ecall_tPrepareForCertificateProvisioning(_enclaveID, &retval,
            0,
            NULL, 
            0, NULL, NULL,
            NULL,
            (pairing_blob_t*)pPairingBlob);
        BREAK_IF_TRUE( (SGX_ERROR_ENCLAVE_LOST == seStatus), retval, PSE_PR_ENCLAVE_LOST_ERROR);
        BREAK_IF_TRUE( (SGX_SUCCESS != seStatus), retval, PSE_PR_ENCLAVE_BRIDGE_ERROR);

        BREAK_IF_FAILED(retval);

        retval = pairingBlob.Clone(tmpPairingBlob);
        BREAK_IF_FAILED(retval);

    } while (0);

    return retval;
}
#endif

ae_error_t tGenM7
(
    /*in */ upse::Buffer& s1,
    /*in */ upse::Buffer& sigRL, 
    /*in */ upse::Buffer& ocspResp, 
    /*in */ upse::Buffer& verifierCert,
    /*in */ upse::Buffer& pairingBlob,
    /*out*/ upse::Buffer& s2 
)
{
    ae_error_t retval;

    do
    {
        sgx_status_t seStatus;

        BREAK_IF_TRUE( (s1.getSize() < sizeof(SIGMA_S1_MESSAGE)), retval,     AESM_PSE_PR_INTERNAL_ERROR);
        const SIGMA_S1_MESSAGE* pS1 = (const SIGMA_S1_MESSAGE*)(s1.getData());

        const UINT8* pSigRL = sigRL.getSize() ? const_cast<uint8_t*>(sigRL.getData()) : NULL;
        retval = check_sigrl_entries_max((const EPID11_SIG_RL*)pSigRL);
        BREAK_IF_FAILED(retval);

        const UINT8* pOcspResp = const_cast<uint8_t*>(ocspResp.getData());
        UINT32 nOcspResp = ocspResp.getSize();

        const UINT8* pVCert = const_cast<uint8_t*>(verifierCert.getData());
        UINT32 nVCert = verifierCert.getSize();

        UINT32 nPairingBlob = pairingBlob.getSize();
        BREAK_IF_FALSE((nPairingBlob == NeededBytesForPairingBlob()), retval,
            PSE_PAIRING_BLOB_INVALID_ERROR);

        UINT8* pPairingBlob = const_cast<uint8_t*>(pairingBlob.getData());

        UINT32 nS2 = NeededBytesForS2(nVCert, sigRL.getSize(), nOcspResp);

        // Allocate memory for output buffer
        retval = s2.Alloc(nS2);
        BREAK_IF_FAILED(retval);
        upse::BufferWriter bwS2(s2);

        UINT8* p;
        retval = bwS2.reserve(nS2, &p);
        BREAK_IF_FAILED(retval);
        SIGMA_S2_MESSAGE* pS2 = (SIGMA_S2_MESSAGE*)p;
        AESM_DBG_INFO("start gen M7 ...");
        // Call to get size required of output buffers
        seStatus = ecall_tGenM7(_enclaveID, &retval, pS1, 
            (const EPID11_SIG_RL*)pSigRL, pOcspResp, nOcspResp, 
            pVCert, nVCert, (pairing_blob_t*)pPairingBlob,
            nS2, pS2, &nS2);
        BREAK_IF_TRUE( (SGX_ERROR_ENCLAVE_LOST == seStatus), retval, PSE_PR_ENCLAVE_LOST_ERROR);
        BREAK_IF_TRUE( (SGX_SUCCESS != seStatus), retval, PSE_PR_ENCLAVE_BRIDGE_ERROR);

        BREAK_IF_FAILED(retval);

    } while (0);
	
	SGX_DBGPRINT_PRINT_FUNCTION_AND_RETURNVAL(__FUNCTIONW__, retval);
    
    return retval;
}


ae_error_t tVerifyM8
(
    /*in */ upse::Buffer& s3, 
    /*in */ upse::Buffer& privRL,
    /*out*/ upse::Buffer& pairingBlob,
    /*out*/ bool& new_pairing
)
{
    ae_error_t retval;

    do
    {
        sgx_status_t seStatus;

        UINT8 uNewPairing = 0;

        UINT32 nS3 = s3.getSize();
        BREAK_IF_TRUE( (nS3 < sizeof(SIGMA_S3_MESSAGE)), retval,     AESM_PSE_PR_INTERNAL_ERROR);
        const SIGMA_S3_MESSAGE* pS3 = (const SIGMA_S3_MESSAGE*)(s3.getData());

        UINT8* pPrivRL = privRL.getSize() ? const_cast<uint8_t*>(privRL.getData()) : NULL;
        retval = check_privrl_entries_max((const EPID11_PRIV_RL*)pPrivRL);
        BREAK_IF_FAILED(retval);

        UINT32 nPairingBlob = NeededBytesForPairingBlob();
        BREAK_IF_FALSE((nPairingBlob == pairingBlob.getSize()), retval,
            PSE_PAIRING_BLOB_INVALID_ERROR);

        upse::BufferWriter bw(pairingBlob);

        pairing_blob_t* pPairingBlob = NULL; 
        retval = bw.reserve(nPairingBlob, (uint8_t**)&pPairingBlob);

        // Call to get size required of output buffers
        seStatus = ecall_tVerifyM8(_enclaveID, &retval, pS3, nS3,
            (EPID11_PRIV_RL*)pPrivRL, pPairingBlob, &uNewPairing);
        BREAK_IF_TRUE( (SGX_ERROR_ENCLAVE_LOST == seStatus), retval, PSE_PR_ENCLAVE_LOST_ERROR);
        BREAK_IF_TRUE( (SGX_SUCCESS != seStatus), retval, PSE_PR_ENCLAVE_BRIDGE_ERROR);

        BREAK_IF_FAILED(retval);

        new_pairing = (uNewPairing == 0) ? false : true;

    } while (0);
	
	SGX_DBGPRINT_PRINT_FUNCTION_AND_RETURNVAL(__FUNCTIONW__, retval);
    
    return retval;
}


#if 0
void ocall_OutputOctets(const char*  pMsg, const void*  pData, size_t nData)
{
}

void ocall_OutputString(const char* pMsg)
{
}
#endif



