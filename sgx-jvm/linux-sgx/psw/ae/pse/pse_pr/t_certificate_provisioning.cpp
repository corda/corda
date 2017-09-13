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
 
 
#include "t_certificate_provisioning.h"
#include <cstddef>

#include "sgx_trts.h"
#include "sgx_tseal.h"
#include "sgx_report.h"
#include "sgx_utils.h"

#include <string.h>

#include "sign_csr.h"
#include "prepare_hash_sha256.h"

#include "pse_pr_common.h"
#include "t_pairing_blob.h"

#include "sgx_tcrypto.h" 

//extern void OutputOctets(const char* pMsg, const void* pData, size_t nData);

static ae_error_t map_error_for_return(ae_error_t status)
{
#if !defined(_DEBUG)
    // Switch to limit errors returned when building for RELEASE
    switch (status)
    {
    case AE_SUCCESS: break;
    case PSE_PR_INSUFFICIENT_MEMORY_ERROR: break;
    default:
        status = AE_FAILURE;
        break;
    }

#endif

    return status;
}

ae_error_t prepare_for_certificate_provisioning
(
    /*in */ UINT64  nonce64,
    /*in */ const sgx_target_info_t*     pTargetInfo,
    /*in */ UINT16  nMax_CSR_pse,
    /*out*/ UINT8*  pCSR_pse,
    /*out*/ UINT16* pnLen_CSR_pse,
    /*out*/ sgx_report_t*          pREPORT,
    /*i/o*/ pairing_blob_t* pPairingBlob
)
{
    // Flow:  1) Check pointers for buffer data sizes
    //        2) If buffers are too small, return and tell caller size required
    //        3) Validate pointers and ensure buffers are within the enclave
    //        4) Generate a new private/public ECDSA key pair
    //        5) Request signed CSR template
    //        6) Calculate HASH_pse of (CSR_pse || nonce64)
    //        7) Generate REPORT with HASH_pse as the REPORTDATA, targeting QE
    //        8) Copy private key and public key into unsealed_pairing buffer
    //        9) Seal pairing blob
    //       10) Return Sealed pairing blob, generated CSR, REPORT, and status

    ae_error_t status = AE_FAILURE;

    pairing_data_t pairingData;
    EcDsaPrivKey privateKey;
    EcDsaPubKey publicKey;
    uint8_t     temp_instance_id[16];

    SignCSR CSR;

    size_t nMaxSizeCSR = CSR.GetMaxSize();
    sgx_ecc_state_handle_t csr_ecc_handle = NULL;

    memset(&pairingData, 0, sizeof(pairingData));

    /////////////////////////////////////////////////////////////////

    do
    {
        //*********************************************************************
        // Validate pointers and sizes
        //*********************************************************************
        BREAK_IF_TRUE((NULL == pPairingBlob),
            status, PSE_PR_BAD_POINTER_ERROR);

        // save SW_INSTANCE_ID
        memcpy(temp_instance_id, pPairingBlob->plaintext.pse_instance_id, sizeof(temp_instance_id));

        {

            BREAK_IF_TRUE((NULL == pTargetInfo),
                status, PSE_PR_BAD_POINTER_ERROR);

            BREAK_IF_TRUE((NULL == pREPORT),
                status, PSE_PR_BAD_POINTER_ERROR);

            BREAK_IF_TRUE((NULL == pCSR_pse || NULL == pnLen_CSR_pse),
                status, PSE_PR_BAD_POINTER_ERROR);

            BREAK_IF_TRUE((nMax_CSR_pse < nMaxSizeCSR),
                status, PSE_PR_PARAMETER_ERROR);

            BREAK_IF_FALSE(sgx_is_within_enclave(pCSR_pse, nMaxSizeCSR), status, PSE_PR_BAD_POINTER_ERROR);

            //*********************************************************************
            // Generate a new ECDSA Key Pair
            //*********************************************************************
            
            sgx_status_t sgx_status = sgx_ecc256_open_context(&csr_ecc_handle);
            BREAK_IF_TRUE((SGX_ERROR_OUT_OF_MEMORY == sgx_status), status, PSE_PR_INSUFFICIENT_MEMORY_ERROR);
            BREAK_IF_TRUE((SGX_SUCCESS != sgx_status), status, PSE_PR_KEY_PAIR_GENERATION_ERROR);

            sgx_status = sgx_ecc256_create_key_pair((sgx_ec256_private_t *)privateKey, (sgx_ec256_public_t*)publicKey, csr_ecc_handle);
            BREAK_IF_TRUE((SGX_SUCCESS != sgx_status), status, PSE_PR_KEY_PAIR_GENERATION_ERROR);

            *pnLen_CSR_pse = (uint16_t)nMaxSizeCSR;

            //*********************************************************************
            // Get a signed Certificate Signing Request from the template
            //*********************************************************************
            status = CSR.GetSignedTemplate(&privateKey, &publicKey, csr_ecc_handle,  pCSR_pse, pnLen_CSR_pse);
            BREAK_IF_FAILED(status);

            //*********************************************************************
            // Calculate HASH_pse of (CSR_pse || nonce64)
            //*********************************************************************
            PrepareHashSHA256 hash;
            SHA256_HASH computedHash;
            status = hash.Update(pCSR_pse, *pnLen_CSR_pse);
            BREAK_IF_FAILED(status);
            status = hash.Update(&nonce64, sizeof(nonce64));
            BREAK_IF_FAILED(status);
            status = hash.Finalize(&computedHash);
            BREAK_IF_FAILED(status);

            //*********************************************************************
            // Generate a REPORT with HASH_pse
            //*********************************************************************
            sgx_report_data_t report_data = {{0}};
            memcpy(&report_data, &computedHash, sizeof(computedHash));
            if (SGX_SUCCESS != sgx_create_report(const_cast<sgx_target_info_t*>(pTargetInfo), 
                &report_data, (sgx_report_t*)pREPORT))
            {
                status = PSE_PR_CREATE_REPORT_ERROR;
                break;
            }

            //*********************************************************************
            // Try to unseal the pairing data
            //*********************************************************************
            status = UnsealPairingBlob(pPairingBlob, &pairingData);
            if (AE_FAILED(status))
                memset_s(&pairingData, sizeof(pairingData), 0, sizeof(pairingData));

            //*********************************************************************
            // Seal ECDSA Verifier Private Key into blob
            //*********************************************************************

            memcpy(pairingData.secret_data.VerifierPrivateKey, &privateKey, sizeof(EcDsaPrivKey));
        
        } // "Public" PSE Cert

        // Set pairingData.plaintext.pse_instance_id using saved temp_instance_id
        memcpy(pairingData.plaintext.pse_instance_id, temp_instance_id, sizeof(pairingData.plaintext.pse_instance_id));

        status = SealPairingBlob(&pairingData, pPairingBlob);
        BREAK_IF_FAILED(status);

        //*********************************************************************
        // WE PASSED ALL BARRIERS TO SUCCESS
        //*********************************************************************

        status = AE_SUCCESS;

//        OutputOctets("::tPrepareForCertificateProvisioning:: New CSR generated", NULL, 0);

    } while (false);

    // Defense-in-depth: clear the data on stack that contains enclave secret.
    memset_s(&pairingData, sizeof(pairingData), 0, sizeof(pairingData));
    memset_s(&privateKey, sizeof(privateKey), 0, sizeof(privateKey));

    if (csr_ecc_handle != NULL) sgx_ecc256_close_context(csr_ecc_handle);

    return map_error_for_return(status);

}


