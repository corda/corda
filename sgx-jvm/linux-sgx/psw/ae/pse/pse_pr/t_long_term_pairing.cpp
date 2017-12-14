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


#include <cstddef>
#include "sgx_trts.h"
#include "sgx_utils.h"
#include "sgx_tseal.h"

#include "t_long_term_pairing.h"
#include "prepare_hash_sha256.h"

#include "pse_pr_inc.h"
#include "pse_pr_common.h"

#include "pse_pr_sigma_1_1_defs.h"
#include "le2be_macros.h"
#include "t_pairing_blob.h"

#include "epid/common/1.1/types.h"

#include "Keys.h"

#include <string.h>

#include "X509Parser.h"

#include "Epid11_rl.h"

#include "sgx_tcrypto.h"

// Each of the following definitions is larger than
// should ever be encountered.
// MAX possible should be 19280 based on 150 SIGRL entries
#define MAX_ALLOWED_SIGRL_SIZE          20480
#define MAX_ALLOWED_OCSP_SIZE            8192
#define MAX_ALLOWED_CERT_SIZE            8192
// MAX of S2 is defined larger than we should ever encounter
#define MAX_ALLOWED_S2_SIZE             35840
// MAX possible should be 3280 based on 100 PRIVRL entries
#define MAX_ALLOWED_PRIVRL_SIZE          4096
// MAX of S3 is defined larger than we should ever encounter
#define MAX_ALLOWED_S3_SIZE             30720


static ae_error_t map_GenM7_error_for_return(ae_error_t status)
{
#if !defined(_DEBUG)
    // Switch to limit errors returned when building for RELEASE
    switch (status)
    {
    case AE_SUCCESS: break;
    case PSE_PR_INSUFFICIENT_MEMORY_ERROR:
    case PSE_PAIRING_BLOB_UNSEALING_ERROR:
        break;
    default:
        status = AE_FAILURE;
        break;
    }

#endif

    return status;
}

static ae_error_t map_VerifyM8_error_for_return(ae_error_t status)
{
#if !defined(_DEBUG)
    // Switch to limit errors returned when building for RELEASE
    switch (status)
    {
    case AE_SUCCESS: break;
    case PSE_PR_INSUFFICIENT_MEMORY_ERROR: break;
    case PSE_PR_PCH_EPID_NO_MEMORY_ERR:
        status = PSE_PR_INSUFFICIENT_MEMORY_ERROR;
        break;
    case PSE_PR_PCH_EPID_SIG_REVOKED_IN_GROUPRL: break;
    default:
        status = AE_FAILURE;
        break;
    }
#else
    switch (status)
    {
    case PSE_PR_PCH_EPID_OUTOFMEMORY:
        status = PSE_PR_INSUFFICIENT_MEMORY_ERROR;
        break;
    }
#endif

    return status;
}


// GUID format is DWORD-WORD-WORD-BYTES ARRAY(8)
// Current PSDA applet ID is cbede6f9-6ce4-439c-a1c7-6e2087786616
static const uint8_t PSDA_APPLET_ID[16] = {0xf9, 0xe6, 0xed, 0xcb, 0xe4, 0x6c, 0x9c, 0x43, 0xa1, 0xc7, 0x6e, 0x20, 0x87, 0x78, 0x66, 0x16};

//extern void OutputOctets(const char* pMsg, const void* pData, size_t nData);

TEpidSigma11Verifier::TEpidSigma11Verifier()
{
    m_gid = 0;

    m_pSigRL = NULL;
    m_nSigRL = 0;

    m_nSigRLVersion = 0;
    m_nPrivRLVersion = 0;
    m_nDalAppletVersion = 0;

    memset(m_pairingID, 0, sizeof(m_pairingID));
    memset(m_pairingNonce, 0, sizeof(m_pairingNonce));

    m_nextState = STATE_GENM7;
}


TEpidSigma11Verifier::~TEpidSigma11Verifier(void)
{
    if (m_pSigRL)
    {
        delete[] m_pSigRL;
        m_pSigRL = NULL;
    }
    m_nSigRL = 0;

    // Defense-in-depth: clear class members that contain Enclave secrets
    memset_s(m_pairingID, sizeof(m_pairingID), 0, sizeof(m_pairingID));
    memset_s(m_pairingNonce, sizeof(m_pairingNonce), 0, sizeof(m_pairingNonce));
    memset_s(m_verifierPrivateKey, sizeof(m_verifierPrivateKey), 0, sizeof(m_verifierPrivateKey));

}


bool TEpidSigma11Verifier::get_sigRL_info(const EPID11_SIG_RL* pSigRL, uint32_t& sigRL_entries, uint32_t& sigRL_size)
{
    if (NULL == pSigRL)
    {
        // null sigRL is acceptable
        sigRL_entries = 0;
        sigRL_size = 0;
        return true;
    }

    uint32_t entries = 0;
    memcpy(&entries, pSigRL->entries, sizeof(entries));
    entries = SwapEndian_DW(entries);
    if (entries > MAX_SIGRL_ENTRIES)            // invalid sigRL
        return false;

    sigRL_entries = entries;
    sigRL_size = (uint32_t)(sizeof(EPID11_SIG_RL) + (sigRL_entries * EPID11_SIG_RL_ENTRY_SIZE) + EPID11_SIG_RL_SIGNATURE_SIZE);

    return true;
}

bool TEpidSigma11Verifier::get_privRL_info(const EPID11_PRIV_RL* pPrivRL, uint32_t& privRL_entries, uint32_t& privRL_size)
{
    if (NULL == pPrivRL)
    {
        // null privRL is acceptable
        privRL_entries = 0;
        privRL_size = 0;
        return true;
    }

    uint32_t entries = 0;
    memcpy(&entries, pPrivRL->entries, sizeof(entries));
    entries = SwapEndian_DW(entries);
    if (entries > MAX_PRIVRL_ENTRIES)       // invalid privRL
        return false;

    privRL_entries = entries;
    privRL_size = (uint32_t)(sizeof(EPID11_PRIV_RL) + (privRL_entries * EPID11_PRIV_RL_ENTRY_SIZE) + EPID11_PRIV_RL_SIGNATURE_SIZE);

    return true;
}




//****************************************************************************
//****************************************************************************
//****************************************************************************

// FLOW
//                                   Verifier              Prover         Intel Server
//  uRequestS1FromME                    |--M1: Start Pairing->|               |
//                                      |<-M2: SIGMA S1-------|               |
//  uGetR2                              |                     |               |
//                                      |                     |               |
//  uLoadPairingBlob                    |                     |               |
//                                      |                     |               |
//  uGetSigRLFromServer                 |--M3: GID_cse || R2----------------->|
//                                      |<-M4: Sig_is(RL_cse || R2)-----------|
//  uGetOCSPResponseFromServer          |--M5: OCSPReq----------------------->|
//                                      |<-M6: OCSPResp-----------------------|
//  uCheckOCSPResponseForExpiration     |                     |               |
//                                      |                     |               |
//  tGenM7 (enclave call)               Send S1, Receive S2
//                                      |                     |               |
//  uExchangeS2AndS3WithME              |--M7: SIGMA S2------>|               |
//                                      |<-M8: SIGMA S3-------|               |
//  uGetGroupIdFromME                   |                     |               |
//                                      |                     |               |
//  tVerifyM8 (enclave call)            Send S3, Receive updated pairing blob
//                                      |                     |               |
//  uSavePairingBlob                    |                     |               |


ae_error_t TEpidSigma11Verifier::GenM7
    (
    /*in */ const SIGMA_S1_MESSAGE*      pS1,
    /*in */ const EPID11_SIG_RL*         pSigRL,
    /*in */ const UINT8*                 pOcspResp,
    /*in */ UINT32 nLen_OcspResp,
    /*in */ const UINT8*                 pVerifierCert,
    /*in */ UINT32 nLen_VerifierCert,
    /*in */ const pairing_blob_t* pPairingBlob,
    /*in */ UINT32 nMax_S2,
    /*out*/ SIGMA_S2_MESSAGE* pS2,
    /*out*/ UINT32* pnLen_S2
    )
{
    ae_error_t status = AE_FAILURE;
    bool bResult;


    pairing_data_t pairing_data;
    memset(&pairing_data, 0, sizeof(pairing_data));

    sgx_ecc_state_handle_t sigma_ecc_handle = NULL;

    do
    {
        ae_error_t tmp_status;

        // sigRL_size allows for the sigRL header, array of RL entries, and signature at the end
        uint32_t sigRL_entries = 0;
        uint32_t sigRL_size = 0;
        bResult = TEpidSigma11Verifier::get_sigRL_info(pSigRL, sigRL_entries, sigRL_size);
        BREAK_IF_FALSE((bResult), status, PSE_PR_BAD_POINTER_ERROR);

        BREAK_IF_TRUE((STATE_GENM7 != m_nextState), status, PSE_PR_CALL_ORDER_ERROR);


        //*********************************************************************
        // Validate pointers and sizes
        //*********************************************************************
        BREAK_IF_TRUE((NULL == pS1), status, PSE_PR_BAD_POINTER_ERROR);

        // SigRL is allowed to be NULL and will be checked in ValidateSigRL()

        BREAK_IF_TRUE((nLen_OcspResp > 0 && NULL == pOcspResp), status, PSE_PR_BAD_POINTER_ERROR);

        BREAK_IF_TRUE((NULL == pVerifierCert), status, PSE_PR_BAD_POINTER_ERROR);

        BREAK_IF_TRUE((NULL == pPairingBlob), status, PSE_PR_BAD_POINTER_ERROR);

        BREAK_IF_TRUE((nLen_VerifierCert > MAX_ALLOWED_CERT_SIZE), status, PSE_PR_PARAMETER_ERROR);
        BREAK_IF_TRUE((sigRL_size > MAX_ALLOWED_SIGRL_SIZE), status, PSE_PR_PARAMETER_ERROR);
        BREAK_IF_TRUE((nLen_OcspResp > MAX_ALLOWED_OCSP_SIZE), status, PSE_PR_PARAMETER_ERROR);

        uint32_t nNeededBytesForS2 = ::NeededBytesForS2(nLen_VerifierCert, sigRL_size, nLen_OcspResp);

        BREAK_IF_TRUE((nNeededBytesForS2 > MAX_ALLOWED_S2_SIZE), status, PSE_PR_PARAMETER_ERROR);

        BREAK_IF_TRUE((NULL == pS2 || NULL == pnLen_S2 || nMax_S2 < nNeededBytesForS2),
            status, PSE_PR_PARAMETER_ERROR);

        //*********************************************************************
        // Start SIGMA processing of S1 and generate S2
        //*********************************************************************

        //*********************************************************************
        // Extract components of Msg S1
        //    g^a || GID || OCSPReq
        //*********************************************************************
        m_sigmaAlg.set_remote_pub_key_ga_be((Ipp8u*)pS1->Ga);

        memcpy(&m_gid, &pS1->Gid, sizeof(SAFEID_GID));

        //*********************************************************************
        // Choose random value 'b' as ephemeral DH private key
        // Compute 'g^b' as ephemeral DH public key
        //*********************************************************************
        sgx_status_t sgx_status = sgx_ecc256_open_context(&sigma_ecc_handle);
        BREAK_IF_TRUE((SGX_ERROR_OUT_OF_MEMORY == sgx_status), status, PSE_PR_INSUFFICIENT_MEMORY_ERROR);
        BREAK_IF_TRUE((SGX_SUCCESS != sgx_status), status, PSE_PR_KEY_PAIR_GENERATION_ERROR);

        /* Get private key b and public key g^b, in little-endian format */
        uint8_t Publickey_little_endian[SIGMA_SESSION_PUBKEY_LENGTH];
        uint8_t Privatekey_b_little_endian[SIGMA_SESSION_PRIVKEY_LENGTH];
        if (SGX_SUCCESS != sgx_ecc256_create_key_pair((sgx_ec256_private_t *)Privatekey_b_little_endian,
                                                (sgx_ec256_public_t*)Publickey_little_endian, sigma_ecc_handle))
        {
            break;
        }
        m_sigmaAlg.set_prv_key_b_le(Privatekey_b_little_endian);
        // clear buffer containing secrets
        memset_s(Privatekey_b_little_endian, sizeof(Privatekey_b_little_endian), 0, sizeof(Privatekey_b_little_endian));

        /* Convert to big endian for m_localPublicKey_gb_big_endian */
        SwapEndian_32B(&(Publickey_little_endian[0]));
        SwapEndian_32B(&(Publickey_little_endian[32]));
        m_sigmaAlg.set_pub_key_gb_be(Publickey_little_endian);

        //        OutputOctets("::GenM7:: g^a (BE)", m_remotePublicKey_ga_big_endian, SIGMA_SESSION_PUBKEY_LENGTH);
        //        OutputOctets("::GenM7:: b (LE)", m_localPrivateKey_b_little_endian, sizeof(sgx_ec256_private_t));
        //        OutputOctets("::GenM7:: g^b (BE)", m_localPublicKey_gb_big_endian, SIGMA_SESSION_PUBKEY_LENGTH);

        //*********************************************************************
        // Compute ((g^a)^b)
        // Derive SMK
        //   SMK := HMAC-SHA256(0x00, g^(ab) || 0x00) with the HMAC key being
        //            32 bytes of 0x00 and the data element being g^(ab) little endian
        // Derive SK and MK
        //   a) Compute HMAC-SHA256(0x00, g^(ab) || 0x01)
        //        with the HMAC key being 32 bytes of 0x00, g^(ab) in little endian
        //   b) SK is taken as the first 128 bits of the HMAC result
        //   c) MK is taken as the second 128 bits of the HMAC result
        //*********************************************************************
        tmp_status = m_sigmaAlg.DeriveSkMk(sigma_ecc_handle);
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

        //        OutputOctets("::GenM7: m_Sk", m_Sk, SIGMA_SK_LENGTH);
        //        OutputOctets("::GenM7:: m_Mk", m_Mk, SIGMA_MK_LENGTH);
        //        OutputOctets("::GenM7:: m_SMK", m_SMK, SIGMA_SMK_LENGTH);

        //*********************************************************************
        // Unseal pairing blob
        //*********************************************************************
        tmp_status = UnsealPairingBlob(pPairingBlob, &pairing_data);
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

        //*************************************************************
        // Extract Private Key from pairing blob
        //*************************************************************

        //        OutputOctets("::GenM7:: Verifier PrivateKey", unsealedBlobData.VerifierPrivateKey, ECDSA_PRIVKEY_LEN);
        memcpy(m_verifierPrivateKey, pairing_data.secret_data.VerifierPrivateKey, ECDSA_PRIVKEY_LEN);

        //*************************************************************
        // Extract pairing data
        //*************************************************************
        memcpy(m_pairingID,    pairing_data.secret_data.pairingID,    sizeof(m_pairingID));
        memcpy(m_pairingNonce, pairing_data.secret_data.pairingNonce, sizeof(m_pairingNonce));

        //        OutputOctets("::GenM7:: m_pairingID", m_pairingID, sizeof(m_pairingID));
        //        OutputOctets("::GenM7:: m_pairingNonce", m_pairingNonce, sizeof(m_pairingNonce));

        //*********************************************************************
        // Prepare S2
        //*********************************************************************
        memset(pS2, 0, nMax_S2);

        // Copy Gb in big endian to S2
        memcpy(pS2->Gb, m_sigmaAlg.get_pub_key_gb_be(), SIGMA_SESSION_PUBKEY_LENGTH);

        // Copy OCSP request sent in S1
        memcpy(&pS2->OcspReq, &pS1->OcspReq, sizeof(OCSP_REQ));

        // Basename is always set to 0
        memset(pS2->Basename, 0, SIGMA_BASENAME_LENGTH);

        // Location within pS2->Data where data gets added
        size_t index = 0;

        //*********************************************************************
        // Add verifier certificate chain to S2
        //*********************************************************************
        tmp_status = AddCertificateChain(pS2, index, nMax_S2,
            pVerifierCert, nLen_VerifierCert);
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

        //*********************************************************************
        // Verify SigRL (verify Cert signature and get the RL version)
        //*********************************************************************
        tmp_status = ValidateSigRL(pSigRL, sigRL_entries, sigRL_size, &m_nSigRLVersion);
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

        //*********************************************************************
        // Add revocation list to S2
        //*********************************************************************
        tmp_status = AddRevocationList(pS2, index, nMax_S2, pSigRL, sigRL_size);
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

        //*********************************************************************
        // Add OCSP Responses to S2
        //*********************************************************************
        tmp_status = AddOcspResponses(pS2, index, nMax_S2,
            pOcspResp, nLen_OcspResp);
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

        //*********************************************************************
        // Compute the HMAC over S2 using SMK (exclude SigGbGa)
        //   [g^b || Basename || OCSPReq || Certver || SIG-RL || OCSPResp]SMK
        //*********************************************************************
        // index is portion of S2 in pS2->Data
        tmp_status = m_sigmaAlg.calc_s2_hmac(&pS2->S2Icv, pS2, index);
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

        //*********************************************************************
        // Append Pr_pse, where Pr_pse is HMAC_SHA256(MK, OLD_SK || 0x01)
        //   if OLD_SK is available, or 256-bit 0x0 if OLD_SK is not available
        //*********************************************************************
        PR_PSE_T pr = {0};
        int nSizePr = sizeof(pr);

        const Nonce128_t zeroNonce = {0};

        if (0 != memcmp(zeroNonce, m_pairingNonce, sizeof(Nonce128_t)))
        {
            // A non-zero pairing nonce indicates valid pairing info is available
            tmp_status = m_sigmaAlg.ComputePR(&m_pairingID, 0x01, (SIGMA_HMAC*)pr);
            BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);
            //            OutputOctets("::GenM7:: Pr_pse HMAC[(m_pairingID || 0x01)] using m_Mk", pr, nSizePr);
        }

        nSizePr = sizeof(pr);
        memcpy((pS2->Data + index), pr, nSizePr);
        index += nSizePr;

        //*********************************************************************
        // Sign SigGaGb
        //   Sig_pse(g^a || g^b)
        //*********************************************************************
        uint8_t combined_pubkeys[SIGMA_SESSION_PUBKEY_LENGTH * 2];
        uint8_t ecc_sig[ECDSA_SIG_LENGTH] = {0};
        /* GaGb in big endian format */
        memcpy(combined_pubkeys, m_sigmaAlg.get_remote_pub_key_ga_be(), SIGMA_SESSION_PUBKEY_LENGTH);
        memcpy(combined_pubkeys + SIGMA_SESSION_PUBKEY_LENGTH, m_sigmaAlg.get_pub_key_gb_be(), SIGMA_SESSION_PUBKEY_LENGTH);

        if (SGX_SUCCESS == sgx_ecdsa_sign(combined_pubkeys,
            sizeof(combined_pubkeys),
            (sgx_ec256_private_t *)pairing_data.secret_data.VerifierPrivateKey,
            (sgx_ec256_signature_t *)ecc_sig,
            sigma_ecc_handle))
        {
            /* Convert the signature to big endian format for pS2->SigGaGb */
            SwapEndian_32B(ecc_sig);
            SwapEndian_32B(&(ecc_sig[32]));
            memcpy(pS2->SigGaGb, ecc_sig, ECDSA_SIG_LENGTH);
        }
        else
        {
            status = PSE_PR_MSG_SIGNING_ERROR;
            break;
        }

        //*********************************************************************
        // Set the size of S2 that is being returned
        //*********************************************************************
        size_t S2IcvSize = SIGMA_S2_ICV_CONSTANT_BUFFER_SIZE + index;
        if ( UINT32_MAX - SIGMA_S2_ICV_CONSTANT_BUFFER_SIZE - ECDSA_SIG_LENGTH - SIGMA_HMAC_LENGTH < index)
        {
            status = PSE_PR_BAD_POINTER_ERROR;
            break;
        }
        *pnLen_S2 = (uint32_t)(S2IcvSize + ECDSA_SIG_LENGTH + SIGMA_HMAC_LENGTH);

        //*********************************************************************
        // WE PASSED ALL BARRIERS TO SUCCESS
        //*********************************************************************
        status = AE_SUCCESS;
        m_nextState = STATE_VERIFYM8;

    } while (false);

    /* Defense-in-depth: clear the data on stack that contains enclave secret.*/
    memset_s(&pairing_data, sizeof(pairing_data), 0, sizeof(pairing_data));

    /* close ecc context handle, the generic crypto lib will free the context memory */
    if (sigma_ecc_handle != NULL) sgx_ecc256_close_context(sigma_ecc_handle);

    return map_GenM7_error_for_return(status);
}

#define RL_OFFSET	4
/*
This function will check if the S3 ICV is correct.
Then it will verify the EPID signature
*/
ae_error_t TEpidSigma11Verifier::VerifyM8
    (
    /*in */ const SIGMA_S3_MESSAGE*      pS3,
    /*in */ UINT32 nLen_S3,
    /*in */ const EPID11_PRIV_RL*        pPrivRL,
    /*in, out*/ pairing_blob_t* pPairingBlob,
    /*out*/ bool* pbNewPairing
    )
{
    // S3 -->  [TaskInfo || g^a || EpidCert || EpidSig(g^a || g^b) || SIG-RL]SMK

    ae_error_t status = AE_FAILURE;
    pairing_data_t pairing_data;

    //
    // This is misleading, PR_PSE_T isn't part of SIGMA, S3, it's part of our (sgx) m8.
    // Also, min_s3 is a very low lower bound. m8 message (not s3) is hmac || taskinfo || g**a || group cert || epid sig || sig-rl || pr_pse.
    // Group cert, EPID sig and SigRL are variable-length, but they have fixed length parts so lengths of the fixed
    // length parts could be included here.
    //
    const size_t min_s3 = sizeof(SIGMA_S3_MESSAGE) + sizeof(PR_PSE_T);

    bool bResult;
    bool bNewPairing = false;

    do
    {
        ae_error_t tmp_status;

        // privRL_size allows for the privRL header, array of RL entries, and signature at the end
        uint32_t privRL_entries = 0;
        uint32_t privRL_size = 0;
        bResult = TEpidSigma11Verifier::get_privRL_info(pPrivRL, privRL_entries, privRL_size);
        BREAK_IF_FALSE((bResult), status, PSE_PR_BAD_POINTER_ERROR);

        BREAK_IF_TRUE( (STATE_VERIFYM8 != m_nextState), status, PSE_PR_CALL_ORDER_ERROR);

        //*********************************************************************
        // Validate pointers and sizes
        //*********************************************************************
        BREAK_IF_TRUE((NULL == pS3 || nLen_S3 < min_s3), status, PSE_PR_BAD_POINTER_ERROR);

        // pPrivRL is allowed to be NULL and will be checked in ValidatePrivRL()

        BREAK_IF_TRUE((NULL == pPairingBlob), status, PSE_PR_BAD_POINTER_ERROR);

        BREAK_IF_TRUE((NULL == pbNewPairing), status, PSE_PR_BAD_POINTER_ERROR);

        BREAK_IF_TRUE((privRL_size > MAX_ALLOWED_PRIVRL_SIZE), status, PSE_PR_PARAMETER_ERROR);
        BREAK_IF_TRUE((nLen_S3 > MAX_ALLOWED_S3_SIZE), status, PSE_PR_PARAMETER_ERROR);

        BREAK_IF_FALSE(sgx_is_within_enclave(pS3, nLen_S3), status, PSE_PR_BAD_POINTER_ERROR);

        //*********************************************************************
        // Start SIGMA processing S3
        //*********************************************************************

        //*********************************************************************
        // Initialize for calculating HMAC and indexing to data
        //*********************************************************************
        size_t S3VLDataLen = nLen_S3 - (sizeof(SIGMA_S3_MESSAGE) + sizeof(PR_PSE_T));

        //*********************************************************************
        // Verify the S3 HMAC using SMK
        //   [TaskInfo || g^a || EpidCert || EpidSig(g^a || g^b) || SIG-RL]SMK
        //*********************************************************************
        SIGMA_HMAC calcHMAC;
        tmp_status = m_sigmaAlg.calc_s3_hmac(&calcHMAC, pS3, S3VLDataLen);
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

        bResult = (1 == consttime_memequal(calcHMAC, pS3->S3Icv, sizeof(SIGMA_HMAC)));
        BREAK_IF_FALSE( (bResult), status, PSE_PR_HMAC_COMPARE_ERROR);

        //*********************************************************************
        // Verify that g^a is the same that arrived in S1
        //*********************************************************************
        bResult = (0 == memcmp(m_sigmaAlg.get_remote_pub_key_ga_be(), pS3->Ga, sizeof(pS3->Ga)));
        BREAK_IF_FALSE( (bResult), status, PSE_PR_GA_COMPARE_ERROR);

        //*********************************************************************
        // Verify TaskInfo
        //*********************************************************************
        BREAK_IF_FALSE(TaskInfoIsValid(pS3->TaskInfo), status, PSE_PR_TASK_INFO_ERROR);

        //*********************************************************************
        // Check the EPID signature
        //*********************************************************************
        X509_GROUP_CERTIFICATE_VLR* X509GroupCertVlr = NULL;
        EPID_SIGNATURE_VLR* EpidSigVlr = NULL;
        tmp_status = ValidateS3DataBlock(pS3, nLen_S3, &X509GroupCertVlr, &EpidSigVlr);
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

        UINT32 S3GID;
        Epid11GroupPubKey groupPubKey;

        /* X509Parser::ParseGroupCertificate() expecting big endian format public key */
        uint8_t SerializedPublicKey[SIGMA_SESSION_PUBKEY_LENGTH];

        for (uint32_t i = 0; i < Keys::EpidVerifyKeyNum(); i++)
        {
            memcpy(SerializedPublicKey, (EcDsaPubKey*)Keys::EpidVerifyKeys()[i], SIGMA_SESSION_PUBKEY_LENGTH);
            SwapEndian_32B(SerializedPublicKey);
            SwapEndian_32B(&(SerializedPublicKey[32]));
            if (0 == X509Parser::ParseGroupCertificate( /*in */ (EcDsaPubKey*)SerializedPublicKey,
                /*in */ X509GroupCertVlr, /*out*/ &S3GID, /*out*/ &groupPubKey))
            {
                tmp_status = AE_SUCCESS;
                break;
            }
            else
            {
                tmp_status = PSE_PR_X509_PARSE_ERROR;
            }
        }
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);
        BREAK_IF_FALSE((S3GID == m_gid), status, PSE_PR_GID_MISMATCH_ERROR );

        //*********************************************************************
        // Verify PrivRL
        //*********************************************************************
        tmp_status = ValidatePrivRL(pPrivRL, privRL_entries, privRL_size, &m_nPrivRLVersion);
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

        KeysToSign_t combinedKeys;
        memset(&combinedKeys, 0, sizeof(combinedKeys));

        // Combine over g^a || g^b to the struct
        memcpy(combinedKeys.first, m_sigmaAlg.get_remote_pub_key_ga_be(),
            SIGMA_SESSION_PUBKEY_LENGTH);
        memcpy(combinedKeys.second, m_sigmaAlg.get_pub_key_gb_be(),
            SIGMA_SESSION_PUBKEY_LENGTH);

        //*********************************************************************
        // the input pPrivRL has a type definition of EPID11_PRIV_RL,
        // but the epid-sdk-3.0 library takes Epid11PrivRl as input parameter.
        // EPID11_PRIV_RL has 4 addtional bytes at the header so we offset the pointer
        // by 4 bytes . Also we need to exclude RL signature because epid-sdk3.0
        // checks the RL's size shouldn't include the signature. Similar for SigRL and GroupRL.
        //*********************************************************************
        uint8_t* pEpid11PrivRL = (pPrivRL == NULL)? NULL:(uint8_t*)pPrivRL+RL_OFFSET;
        uint32_t nEpid11PrivRLSize = (pPrivRL == NULL)? 0:privRL_size-RL_OFFSET-ECDSA_SIG_LENGTH;
        uint8_t* pEpid11SigRL = (m_pSigRL == NULL)? NULL:m_pSigRL+RL_OFFSET;
        uint32_t nEpid11SigRLSize = (m_pSigRL == NULL)? 0:static_cast<uint32_t>(m_nSigRL-RL_OFFSET-ECDSA_SIG_LENGTH);

        tmp_status = m_sigmaAlg.MsgVerifyPch((UINT8 *)&groupPubKey,
            (uint32_t)(sizeof(EpidCert) - ECDSA_SIG_LENGTH),
            NULL,             // not required for EPID SDK 3.0
            (Ipp8u*)&combinedKeys,
            (uint32_t)sizeof(combinedKeys),
            NULL,             // Bsn
            0,                // BsnLen
            (UINT8 *)EpidSigVlr->EpidSig,
            static_cast<int>(VLR_UNPADDED_PAYLOAD_SIZE(EpidSigVlr->VlrHeader)),
            pEpid11PrivRL, nEpid11PrivRLSize,       // PrivRL
            pEpid11SigRL, nEpid11SigRLSize,    // SigRL
            NULL, 0);       // GroupRL
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

        //*********************************************************************
        // Calculate Id_pse and Id_cse
        //    Id_pse = hash(sk || mk || 1)
        //    Id_cse = hash(sk || mk || 2)
        //*********************************************************************
        SHA256_HASH Id_pse = {0};
        SHA256_HASH Id_cse = {0};

        tmp_status = m_sigmaAlg.ComputeId(1, &Id_pse);
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

        tmp_status = m_sigmaAlg.ComputeId(2, &Id_cse);
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

        //*********************************************************************
        // Verify Pr_cse, where Pr_cse is HMAC_SHA256(MK, OLD_SK || 0x02)
        //   if OLD_SK is available, or 256-bit 0x0 if OLD_SK is not available
        //*********************************************************************
        size_t nSizePr = sizeof(PR_PSE_T);
        PR_PSE_T *pS3_PR_cse = (PR_PSE_T*)((const uint8_t*)pS3 + nLen_S3 - nSizePr);

        //        OutputOctets("S3", pS3, nLen_S3);

        //        OutputOctets("VerifyM8 - pS3_PR_cse", pS3_PR_cse, nSizePr);

        bNewPairing = true;
        PR_PSE_T pr_cse = {0};
        const Nonce128_t zeroNonce = {0};
        if (0 != memcmp(&pr_cse, pS3_PR_cse, sizeof(pr_cse)) && 0 != memcmp(&m_pairingNonce, &zeroNonce, sizeof(Nonce128_t)))
        {
            tmp_status = m_sigmaAlg.ComputePR(&m_pairingID, 0x02, (SIGMA_HMAC*)pr_cse);
            BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

            //            OutputOctets("::VerifyM8:: Computed Pr HMAC[(m_pairingID || 0x02)] using m_Mk", pr_cse, nSizePr);

            if (0 == memcmp(&pr_cse, pS3_PR_cse, sizeof(pr_cse)))
                bNewPairing = false;
        }

        if (bNewPairing)
        {
            memcpy(&m_pairingID, m_sigmaAlg.get_SK(), sizeof(m_pairingID));
            sgx_status_t seStatus = sgx_read_rand((uint8_t*)&m_pairingNonce, sizeof(m_pairingNonce));
            BREAK_IF_TRUE(SGX_SUCCESS != seStatus, status, PSE_PR_READ_RAND_ERROR);
            // LTPBlob.pairingNonce = 0 is used to indicate invalid pairing Info in the LTP blob.
            // Under the rare situation of a random number of 0 is returned for pairingNonce generation,
            // PSE-Pr declares pairing or re-pairing attempt failure. The next pairing/re-pairing attempt
            // most likely will generate a non-zero pairingNonce
            BREAK_IF_TRUE(memcmp(&m_pairingNonce, &zeroNonce, sizeof(Nonce128_t)) == 0, status, PSE_PR_READ_RAND_ERROR);
            //            OutputOctets("VerifyM8 - new pairing", NULL, 0);
        }
        else
        {
            //            OutputOctets("VerifyM8 - pS3_PR_cse matches pr_cse", NULL, 0);
        }

        //*********************************************************************
        // Update the unsealed pairing data
        //      [VerifierPrivateKey, id_pse || id_cse || sk || mk ||
        //       PairingNonce || SigRLVersion_cse || PrvRLVersion_cse ||
        //       DalAppletVersion]
        //*********************************************************************
        memset(&pairing_data, 0, sizeof(pairing_data));

        memcpy(pairing_data.secret_data.VerifierPrivateKey, m_verifierPrivateKey, sizeof(EcDsaPrivKey));

        memcpy(pairing_data.secret_data.Id_cse, &Id_cse, sizeof(SHA256_HASH));
        memcpy(pairing_data.secret_data.Id_pse, &Id_pse, sizeof(SHA256_HASH));
        memcpy(pairing_data.secret_data.mk, m_sigmaAlg.get_MK(), sizeof(pairing_data.secret_data.mk));
        memcpy(pairing_data.secret_data.sk, m_sigmaAlg.get_SK(), sizeof(pairing_data.secret_data.sk));

        memcpy(pairing_data.secret_data.pairingID, m_pairingID, sizeof(m_pairingID));
        memcpy(pairing_data.secret_data.pairingNonce, m_pairingNonce, sizeof(Nonce128_t));

        pairing_data.plaintext.cse_sec_prop.ps_hw_gid = m_gid;
        pairing_data.plaintext.cse_sec_prop.ps_hw_sig_rlversion = m_nSigRLVersion;
        pairing_data.plaintext.cse_sec_prop.ps_hw_privkey_rlversion = m_nPrivRLVersion;

        //NRG: Definition of ME_TASK_INFO and PS_HW_SEC_INFO is still open
        // for SunrisePoint from TaskInfo of SIGMA1.1 message:
        //      byte[ 0- 3] ME_TASK_INFO.TaskID, for SunrisePoint must be 8
        //      byte[ 4- 7] Reserved, must be 0
        //      byte[ 8-11] PSDA ID, mapped from the PSDA Applet ID in ME_TASK_INFO (1)
        //      byte[12-15] PSDA SVN from ME_TASK_INFO
        //      byte[16-31] Reserved, must be 0
        pairing_data.plaintext.cse_sec_prop.ps_hw_sec_info.taskId = pS3->TaskInfo.TaskId;
        pairing_data.plaintext.cse_sec_prop.ps_hw_sec_info.psdaId = 1;
        pairing_data.plaintext.cse_sec_prop.ps_hw_sec_info.psdaSvn = m_nDalAppletVersion;
        //NRG:

        // keep instance id
        memcpy(pairing_data.plaintext.pse_instance_id,
            pPairingBlob->plaintext.pse_instance_id,
            sizeof(pairing_data.plaintext.pse_instance_id));

        //*********************************************************************
        // Seal the pairing blob
        //*********************************************************************
        tmp_status = SealPairingBlob(&pairing_data, pPairingBlob);
        BREAK_IF_TRUE(AE_SUCCESS != tmp_status, status, tmp_status);

        *pbNewPairing = bNewPairing;

        //*********************************************************************
        // WE PASSED ALL BARRIERS TO SUCCESS
        //*********************************************************************
        status = AE_SUCCESS;
        m_nextState = STATE_DONE;

    } while (false);

    if (AE_FAILED(status))
        m_nextState = STATE_ERROR;

    /* Defense-in-depth: clear the data on stack that contains enclave secret.*/
    memset_s(&pairing_data, sizeof(pairing_data), 0, sizeof(pairing_data));

    delete[] m_pSigRL;
    m_pSigRL = NULL;
    m_nSigRL = 0;

    return map_VerifyM8_error_for_return(status);
}


bool TEpidSigma11Verifier::TaskInfoIsValid( const ME_TASK_INFO& taskInfo)
{
    uint32_t taskInfoType = SwapEndian_DW(taskInfo.Hdr.Type);
    if (taskInfoType != ME_TASK) return false;

    //check TaskID and Applet ID according to SunrisePoint specification
    /* Check the TaskId matches the hardcoded JVM-On-ME Task ID */
    if (taskInfo.TaskId != JOM_TASK_ID) return false;

    /* Check the first 16 bytes of RsvdforApp matches the hardcoded PSDA Applet ID */
    if (memcmp(taskInfo.RsvdforApp, PSDA_APPLET_ID, DAL_APPLET_ID_LEN))
    {
        return false;
    }

    /* retrieve the PSDA SVN */
    memcpy(&m_nDalAppletVersion, (const_cast<uint8_t *>(taskInfo.RsvdforApp) + DAL_APPLET_ID_LEN), DAL_APPLET_SVN_LEN);

    return true;
}


ae_error_t TEpidSigma11Verifier::ValidateS3DataBlock(const SIGMA_S3_MESSAGE* pS3, uint32_t nLen_S3, X509_GROUP_CERTIFICATE_VLR** X509GroupCertVlr, EPID_SIGNATURE_VLR** EpidSigVlr)
{
    X509_GROUP_CERTIFICATE_VLR* pX;
    EPID_SIGNATURE_VLR* pE;

    uint32_t data_offset = offsetof(SIGMA_S3_MESSAGE, Data);

    if (NULL == pS3 || NULL == X509GroupCertVlr || NULL == EpidSigVlr)
        return AESM_PSE_PR_BAD_POINTER_ERROR;

    // Make sure certificate is within bounds of S3 message allocated in trusted memory
    if (data_offset + sizeof(X509_GROUP_CERTIFICATE_VLR) >= nLen_S3)
        return PSE_PR_S3_DATA_ERROR;

    pX = (X509_GROUP_CERTIFICATE_VLR *)(((uint8_t*)pS3) + data_offset);

    // Make sure epid signature VLR is within bounds of S3 message allocated in trusted memory
    if ((data_offset + sizeof(EPID_SIGNATURE_VLR) + pX->VlrHeader.Length) >= nLen_S3)
        return PSE_PR_S3_DATA_ERROR;

    pE = (EPID_SIGNATURE_VLR*)((UINT8*)(pX) + pX->VlrHeader.Length);

    // Make sure epid signature data is within bounds of S3 message allocated in trusted memory
    if ((data_offset + pX->VlrHeader.Length + pE->VlrHeader.Length) >= nLen_S3)
        return PSE_PR_S3_DATA_ERROR;

    *X509GroupCertVlr = pX;
    *EpidSigVlr = pE;

    return AE_SUCCESS;
}


ae_error_t TEpidSigma11Verifier::AddCertificateChain(SIGMA_S2_MESSAGE* pS2,
                                                     size_t& index, size_t nMaxS2, const UINT8* pCertChain, size_t nCertChain)
{
    ae_error_t status = PSE_PR_INTERNAL_ERROR;

    do
    {
        if (nMaxS2 < ((pS2->Data - (uint8_t*)pS2) + index + nCertChain))
            break;

        memcpy((pS2->Data + index), pCertChain, nCertChain);

        index += nCertChain;

        status = AE_SUCCESS;

    } while (false);

    return status;
}


ae_error_t TEpidSigma11Verifier::AddRevocationList(SIGMA_S2_MESSAGE* pS2,
                                                   size_t& index, size_t nMaxS2, const EPID11_SIG_RL* pRL, uint32_t nSigRL)
{
    ae_error_t status = PSE_PR_INTERNAL_ERROR;

    do
    {
        if (NULL != m_pSigRL)
            delete [] m_pSigRL;
        m_nSigRL = 0;
        m_pSigRL = NULL;

        if (nSigRL > 0)
        {

            m_nSigRL = nSigRL;
            m_pSigRL = new (std::nothrow) UINT8[m_nSigRL];
            BREAK_IF_TRUE( (NULL == m_pSigRL), status,
                PSE_PR_INSUFFICIENT_MEMORY_ERROR);

            int nPaddedBytes =  static_cast<int>(REQUIRED_PADDING_DWORD_ALIGNMENT(m_nSigRL));
            memcpy(m_pSigRL, pRL , m_nSigRL);

            SIGNATURE_REV_LIST_VLR sigRL_VLR;
            sigRL_VLR.VlrHeader.ID = SIGNATURE_REVOCATION_LIST_VLR_ID;
            sigRL_VLR.VlrHeader.PaddedBytes = (uint8_t)nPaddedBytes;
            if (sizeof(SIGMA_VLR_HEADER) + nPaddedBytes + m_nSigRL > UINT16_MAX)
                break;
            sigRL_VLR.VlrHeader.Length = (uint16_t)(sizeof(SIGMA_VLR_HEADER) + nPaddedBytes + m_nSigRL);

            if (nMaxS2 < ((pS2->Data - (uint8_t*)pS2) + index + nSigRL + sizeof(SIGNATURE_REV_LIST_VLR)))
                break;

            memcpy((pS2->Data + index), &sigRL_VLR, sizeof(SIGNATURE_REV_LIST_VLR));
            index += sizeof(SIGNATURE_REV_LIST_VLR);
            memcpy((pS2->Data + index), m_pSigRL, m_nSigRL);
            index += m_nSigRL;
            // must skip nPaddedBytes for alignment
            index += nPaddedBytes;
        }

        status = AE_SUCCESS;

    } while (false);

    return status;
}


ae_error_t TEpidSigma11Verifier::AddOcspResponses(SIGMA_S2_MESSAGE* pS2,
                                                  size_t& index, size_t nMaxS2, const UINT8* pOcspResp, size_t nOcspResp)
{
    ae_error_t status = PSE_PR_INTERNAL_ERROR;

    do
    {
        if (pS2->OcspReq.ReqType == NO_OCSP)
        {
            status = AE_SUCCESS;
            break;
        }

        BREAK_IF_TRUE( (0 == nOcspResp), status ,
            PSE_PR_NO_OCSP_RESPONSE_ERROR);

        if (nMaxS2 < ((pS2->Data - (uint8_t*)pS2) + index + nOcspResp))
            break;

        memcpy((pS2->Data+index), pOcspResp, nOcspResp);

        index += nOcspResp;

        status = AE_SUCCESS;

    } while (false);

    return status;
}


ae_error_t TEpidSigma11Verifier::ValidateSigRL(const EPID11_SIG_RL* pSigRL, uint32_t sigRL_entries, uint32_t sigRL_size, uint32_t* pVersion)
{
    sgx_ecc_state_handle_t ivk_ecc_handle = NULL;
    uint8_t result;
    ae_error_t status = PSE_PR_MSG_COMPARE_ERROR;

    if (NULL == pVersion)
        return PSE_PR_BAD_POINTER_ERROR;

    *pVersion = 0;

    if (0 == sigRL_size || NULL == pSigRL)
        return AE_SUCCESS;

    do
    {
        uint32_t nBaseSigRL_size = sigRL_size - EPID11_SIG_RL_SIGNATURE_SIZE;

        if (sigRL_entries > MAX_SIGRL_ENTRIES)
            break;

        uint8_t* p_rl_version = const_cast<uint8_t*>(pSigRL->rl_version);
        *pVersion = SwapEndian_DW(*reinterpret_cast<UINT32*>(p_rl_version));

        sgx_status_t sgx_status = sgx_ecc256_open_context(&ivk_ecc_handle);
        BREAK_IF_TRUE((SGX_ERROR_OUT_OF_MEMORY == sgx_status), status, PSE_PR_INSUFFICIENT_MEMORY_ERROR);
        BREAK_IF_TRUE((SGX_SUCCESS != sgx_status), status, PSE_PR_MSG_COMPARE_ERROR);

        //Convert the big endian signature in the Cert to little endian
        uint8_t ecc_sig[ECDSA_SIG_LENGTH ];
        memcpy(ecc_sig, (uint8_t*)pSigRL + nBaseSigRL_size, ECDSA_SIG_LENGTH );
        SwapEndian_32B(ecc_sig);
        SwapEndian_32B(&(ecc_sig[32]));

        const uint8_t** pEpidVerifyKeys = Keys::EpidVerifyKeys();
        for (uint32_t i = 0; i < Keys::EpidVerifyKeyNum(); i++)
        {
            sgx_status = sgx_ecdsa_verify((uint8_t*)pSigRL,
                nBaseSigRL_size,
                (sgx_ec256_public_t *)(pEpidVerifyKeys[i]), /* requiring little endian format */
                (sgx_ec256_signature_t *)ecc_sig,
                &result,
                ivk_ecc_handle);
            if (sgx_status == SGX_SUCCESS && result == SGX_EC_VALID)
                break;
        }
        BREAK_IF_TRUE((SGX_ERROR_OUT_OF_MEMORY == sgx_status), status, PSE_PR_INSUFFICIENT_MEMORY_ERROR);
        BREAK_IF_TRUE((SGX_SUCCESS != sgx_status), status, PSE_PR_MSG_COMPARE_ERROR);
        BREAK_IF_TRUE((SGX_EC_VALID != result), status, PSE_PR_MSG_COMPARE_ERROR);

        status = AE_SUCCESS;

    } while (false);

    if (ivk_ecc_handle != NULL) sgx_ecc256_close_context(ivk_ecc_handle);

    return status;
}

ae_error_t TEpidSigma11Verifier::ValidatePrivRL(const EPID11_PRIV_RL* pPrivRL, uint32_t privRL_entries, uint32_t privRL_size, uint32_t* pVersion)
{
    sgx_ecc_state_handle_t ivk_ecc_handle = NULL;
    uint8_t result;

    ae_error_t status = PSE_PR_MSG_COMPARE_ERROR;

    if (NULL == pVersion)
        return PSE_PR_BAD_POINTER_ERROR;

    *pVersion = 0;

    if (0 == privRL_size || NULL == pPrivRL)
        return AE_SUCCESS;


    do
    {
        uint32_t nBasePrivRL_size = privRL_size - EPID11_PRIV_RL_SIGNATURE_SIZE;

        if (privRL_entries > MAX_SIGRL_ENTRIES)
            break;

        uint8_t* p_rl_version = const_cast<uint8_t*>(pPrivRL->rl_version);
        *pVersion = SwapEndian_DW(*reinterpret_cast<UINT32*>(p_rl_version));

        sgx_status_t sgx_status = sgx_ecc256_open_context(&ivk_ecc_handle);
        BREAK_IF_TRUE((SGX_ERROR_OUT_OF_MEMORY == sgx_status), status, PSE_PR_INSUFFICIENT_MEMORY_ERROR);
        BREAK_IF_TRUE((SGX_SUCCESS != sgx_status), status, PSE_PR_MSG_COMPARE_ERROR);

        //Convert the big endian signature in the Cert to little endian
        uint8_t ecc_sig[ECDSA_SIG_LENGTH];
        memcpy(ecc_sig, (uint8_t*)pPrivRL + nBasePrivRL_size, ECDSA_SIG_LENGTH);
        SwapEndian_32B(ecc_sig);
        SwapEndian_32B(&(ecc_sig[32]));

        const uint8_t** pEpidVerifyKeys = Keys::EpidVerifyKeys();
        for (uint32_t i = 0; i < Keys::EpidVerifyKeyNum(); i++)
        {
            sgx_status = sgx_ecdsa_verify((uint8_t*)pPrivRL,
                nBasePrivRL_size,
                (sgx_ec256_public_t *)(pEpidVerifyKeys[i]), /* requiring little endian format */
                (sgx_ec256_signature_t *)ecc_sig,
                &result,
                ivk_ecc_handle);
            if (sgx_status == SGX_SUCCESS && result == SGX_EC_VALID)
                break;
        }
        BREAK_IF_TRUE((SGX_ERROR_OUT_OF_MEMORY == sgx_status), status, PSE_PR_INSUFFICIENT_MEMORY_ERROR);
        BREAK_IF_TRUE((SGX_SUCCESS != sgx_status), status, PSE_PR_MSG_COMPARE_ERROR);
        BREAK_IF_TRUE((SGX_EC_VALID != result), status, PSE_PR_MSG_COMPARE_ERROR);
        status = AE_SUCCESS;

    } while (false);

    if (ivk_ecc_handle != NULL) sgx_ecc256_close_context(ivk_ecc_handle);
    return status;
}
