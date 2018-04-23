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
#include "helper.h"
#include "uecall_bridge.h"
#include <Buffer.h>

#include "interface_psda.h"

#include "pse_pr_sigma_1_1_defs.h"
#include "sigma_helper.h"

#include "aeerror.h"
#include <list>
#include "oal/oal.h"
#include "PSEPRClass.h"

#include "pairing_blob.h"
#include "byte_order.h"

#if defined(_DEBUG)

#include "PSDAService.h"
#include "aesm_epid_blob.h"
#include "aesm_encode.h"

#endif
#define PSEPR_LOST_ENCLAVE_RETRY_COUNT        3
extern uint32_t upse_iclsInit();


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


//*********************************************************************
// Prototypes of static functions
//*********************************************************************

static ae_error_t DoLongTermPairing(bool* p_new_pairing);
//*********************************************************************
// Main engine routine for Long-Term Pairing
//*********************************************************************
ae_error_t create_sigma_long_term_pairing(bool* p_new_pairing)
{
    ae_error_t status = AE_FAILURE;

    SGX_DBGPRINT_PRINT_ANSI_STRING("Begin Long Term Pairing");

    try
    {
        unsigned rcount = AESM_RETRY_COUNT;
        do
        {
            status = DoLongTermPairing(p_new_pairing);

            if(status == AESM_PSDA_NOT_PROVISONED_ERROR)
            {
                // retry CSE Provision
                if (upse_iclsInit() == 0)
                {
                    rcount--;
                    continue;
                }
                break;
            }

            if (status == PSE_PR_ENCLAVE_LOST_ERROR || status == AESM_PSDA_SESSION_LOST
                || status == AESM_PSDA_WRITE_THROTTLED)
            {
                //
                // went to sleep while in enclave
                // in this case (beginning of flow), we should just retry, after first destroying and then reloading
                // note that this code gets significantly more complicated if the PSE-pr ever becomes multi-threaded
                //
                if (status == PSE_PR_ENCLAVE_LOST_ERROR)
                {
                    CPSEPRClass::instance().unload_enclave();
                    if ((status = CPSEPRClass::instance().load_enclave()) != AE_SUCCESS)
                    {
                        if(status != AESM_AE_OUT_OF_EPC)
                            status = AE_FAILURE;
                        break;
                    }
                    SaveEnclaveID(CPSEPRClass::instance().GetEID());
                }
                rcount--;
                continue;
            }

            break;

        } while (rcount > 0);
    }
    catch (...)
    {
        status = AESM_PSE_PR_EXCEPTION;
    }

    //    if (AE_SUCCESS != status)
    //  {
    //      upsePersistentStorage::Delete(PSE_PR_LT_PAIRING_FID);
    //      Helper::RemoveCertificateChain();
    //  }

    SGX_DBGPRINT_PRINT_FUNCTION_AND_RETURNVAL(__FUNCTION__, status);
    SGX_DBGPRINT_PRINT_ANSI_STRING("End Long Term Pairing");

    return status;
}


//*********************************************************************
// Do the long term pairing logic
//*********************************************************************
static ae_error_t DoLongTermPairing(bool* p_new_pairing)
{
    std::list<upse::Buffer> certChain;

    upse::Buffer certChainVLR;
    upse::Buffer keyBlob;
    upse::Buffer pairingBlob;
    upse::Buffer ocspReq;
    upse::Buffer ocspResp;
    upse::Buffer s1;
    upse::Buffer s2;
    upse::Buffer s3;
    upse::Buffer sigRL;
    upse::Buffer privRL;

    if (NULL == p_new_pairing)
        return AESM_PSE_PR_BAD_POINTER_ERROR;

    ae_error_t status = AE_FAILURE;

    pse_pr_interface_psda* pPSDA = NULL;

    do
    {
        pPSDA = new pse_pr_interface_psda();
        BREAK_IF_FALSE( (NULL != pPSDA),
            status, AESM_PSE_PR_INSUFFICIENT_MEMORY_ERROR);

        //=====================================================================
        // Start: LONG TERM PAIRING protocol
        //=====================================================================

        //*********************************************************************
        // Load the pairing blob from persistent storage.
        // Load the verifier certificate and CA certificate chain.
        // The ECDSA key pair was generated during certificate provisioning.
        //*********************************************************************
        status = Helper::read_ltp_blob(pairingBlob);
        pairing_blob_t* pairing_blob = (pairing_blob_t*)pairingBlob.getData();
        BREAK_IF_FAILED_ERR(status, AESM_NLTP_NO_LTP_BLOB);
        SGX_DBGPRINT_PRINT_ANSI_STRING("pairing blob load success");

        // Received during Certificate Chain Provisioning
        status = Helper::LoadCertificateChain(certChain);
        BREAK_IF_FAILED_ERR(status, AESM_NPC_NO_PSE_CERT);
        SGX_DBGPRINT_PRINT_ANSI_STRING("Certificate Chain load success");

        //*********************************************************************
        // Retrieve S1 from ME/CSE
        //*********************************************************************

        status = pPSDA->GetS1(pairing_blob->plaintext.pse_instance_id, s1);
        SGX_DBGPRINT_ONE_STRING_ONE_INT_LTP("Function: pPSDA->GetS1(s1), Return Value: ", status);
        BREAK_IF_FAILED(status);

        BREAK_IF_FALSE( (s1.getSize() == sizeof(SIGMA_S1_MESSAGE)),
            status, AESM_PSE_PR_INTERNAL_ERROR);

        const SIGMA_S1_MESSAGE* pS1 = (const SIGMA_S1_MESSAGE*)s1.getData();

#if 1
        upse::Buffer tGID;
        uint32_t serializedGID = (uint32_t)pS1->Gid;
        status = tGID.Alloc((uint8_t*)&serializedGID, sizeof(uint32_t));
        BREAK_IF_FAILED(status);
        SigmaHelper::SetGID(tGID);
#endif

        //*********************************************************************
        // Retrieve Sig RL and Priv RL from Intel Server (okay if it can't retrieve them)
        //*********************************************************************
        status = SigmaHelper::GetRLsFromServer(sigRL, privRL);
        if (AE_SUCCESS != status)
            AESM_LOG_WARN(g_event_string_table[SGX_EVENT_EPID11_RL_RETRIEVAL_FAILURE]);
        SGX_DBGPRINT_PRINT_ANSI_STRING("RL requested");

        //*********************************************************************
        // Retrieve OCSP Responses from Intel Server
        //*********************************************************************
        status = SigmaHelper::GetOcspResponseFromServer(certChain, pS1->OcspReq, ocspResp);
        if (AE_SUCCESS != status) {
            AESM_LOG_WARN(g_event_string_table[SGX_EVENT_OCSP_FAILURE]);
        }
		if (OAL_PROXY_SETTING_ASSIST == status) {
            SGX_DBGPRINT_PRINT_ANSI_STRING("proxy error during OCSP");
			break;
		}
        if (AESM_LTP_PSE_CERT_REVOKED == status) {
            SGX_DBGPRINT_PRINT_ANSI_STRING("OCSP server returns cert_revoked");
            break;
        }
        BREAK_IF_FALSE( (status == AE_SUCCESS), status, AESM_PSE_PR_GET_OCSPRESP_ERROR);
        SGX_DBGPRINT_PRINT_ANSI_STRING("OCSP retrieval success");

        //*********************************************************************
        // Package the Certificate Chain as a VLR
        //*********************************************************************
        status = Helper::PrepareCertificateChainVLR(certChain, certChainVLR);
        BREAK_IF_FAILED(status);
        SGX_DBGPRINT_PRINT_ANSI_STRING("Certificate Chain prepared success");

        //*********************************************************************
        // Communicate with PSE_pr enclave
        // Send:    s1, sigRL, ocspResp, verifierCert, pairingBlob
        // Receive: s2
        //*********************************************************************
        status = tGenM7(s1, sigRL, ocspResp, certChainVLR, pairingBlob, s2);
        if (PSE_PR_MSG_COMPARE_ERROR == status) {
            AESM_LOG_ERROR("%s", g_event_string_table[SGX_EVENT_EPID11_SIGRL_INTEGRITY_ERROR]);
        }
        BREAK_IF_FAILED(status);
        SGX_DBGPRINT_PRINT_ANSI_STRING("M7 success");

        //*********************************************************************
        // Communicate with ME/CSE
        // Send:    s2
        // Receive: s3
        //*********************************************************************
        status = pPSDA->ExchangeS2AndS3(pairing_blob->plaintext.pse_instance_id, s2, s3);
        SGX_DBGPRINT_ONE_STRING_ONE_INT_LTP("Function: pPSDA->ExchangeS2AndS3(s2, s3), Return Value: ", status);
        if (AESM_PSDA_LT_SESSION_INTEGRITY_ERROR == status) {
            AESM_LOG_ERROR("%s", g_event_string_table[SGX_EVENT_SIGMA_S2_INTEGRITY_ERROR]);
            SGX_DBGPRINT_PRINT_ANSI_STRING("pairing blob deleted");
            Helper::delete_ltp_blob();
        }
        BREAK_IF_FAILED(status);
        SGX_DBGPRINT_PRINT_ANSI_STRING("PSDA Exchange success");

        //*********************************************************************
        // Communicate with PSE_pr enclave
        // Send:    s3, privRL, epidGroupCert, epidParamsCert, pairingBlob
        // Receive: pairingBlob, bNewPairing flag
        //*********************************************************************
        status = tVerifyM8(s3, privRL, pairingBlob, *p_new_pairing);
        if (PSE_PR_MSG_COMPARE_ERROR == status) {
            AESM_LOG_ERROR("%s", g_event_string_table[SGX_EVENT_EPID11_PRIVRL_INTEGRITY_ERROR]);
        }
        BREAK_IF_TRUE((status == PSE_PR_PCH_EPID_SIG_REVOKED_IN_GROUPRL), status, AESM_LTP_PSE_CERT_REVOKED);
        BREAK_IF_FAILED(status);
        SGX_DBGPRINT_PRINT_ANSI_STRING("M8 success");

        //*********************************************************************
        // Save the sealed pairing blob to persistent storage.
        //*********************************************************************
        status = Helper::write_ltp_blob(pairingBlob);
        BREAK_IF_FAILED(status);
        SGX_DBGPRINT_PRINT_ANSI_STRING("pairing blob written success");

        status = AE_SUCCESS;

#if defined(_DEBUG)
        uint32_t pseSvn = certPseSvn();
        SGX_DBGPRINT_ONE_STRING_ONE_INT("certPseSvn() returns ", pseSvn);

        uint32_t sgxGid = 0;
        EPIDBlob::instance().get_sgx_gid(&sgxGid);
        SGX_DBGPRINT_ONE_STRING_ONE_INT("get_sgx_gid() returns ", sgxGid);

        uint32_t psdaSvn = 0;
        psdaSvn = Helper::ltpBlobPsdaSvn(*(pairing_blob_t*)pairingBlob.getData());
        SGX_DBGPRINT_ONE_STRING_ONE_INT("ltpBlobPsdaSvn() returns ", psdaSvn);

        unsigned currentPsdaSvn = 0;
        PSDAService::instance().current_psda_svn(&currentPsdaSvn);
        SGX_DBGPRINT_ONE_STRING_ONE_INT("current_psda_svn() returns ", currentPsdaSvn);

        uint32_t cseGid = 0;
        ae_error_t ltpBlobCseGid(uint32_t* pGid);
        ltpBlobCseGid(&cseGid);
        SGX_DBGPRINT_ONE_STRING_ONE_INT("ltpBlobCseGid() returns ", cseGid);

#endif
    } while (false);

    if (NULL != pPSDA)
    {
        delete pPSDA;
    }

    if (PSE_PAIRING_BLOB_UNSEALING_ERROR == status || PSE_PAIRING_BLOB_INVALID_ERROR == status)
    {
        SGX_DBGPRINT_PRINT_ANSI_STRING("Invalid pairing blob.");
        Helper::delete_ltp_blob();
    }

    if (AE_FAILED(status))
    {
        switch (status)
        {
        case OAL_NETWORK_UNAVAILABLE_ERROR:     AESM_LOG_FATAL(g_event_string_table[SGX_EVENT_OCSP_FAILURE]); break;
        case PSE_PAIRING_BLOB_UNSEALING_ERROR:  AESM_LOG_FATAL(g_event_string_table[SGX_EVENT_LTP_BLOB_INTEGRITY_ERROR]); break;
        case PSE_PAIRING_BLOB_INVALID_ERROR:    AESM_LOG_FATAL(g_event_string_table[SGX_EVENT_LTP_BLOB_INVALID_ERROR]); break;
        case AESM_LTP_PSE_CERT_REVOKED:
            {
                AESM_LOG_FATAL("%s", g_event_string_table[SGX_EVENT_ME_EPID_GROUP_REVOCATION]);
                break;
            }
        case PSE_PR_PCH_EPID_SIG_REVOKED_IN_PRIVRL:
            {
                AESM_LOG_FATAL("%s", g_event_string_table[SGX_EVENT_ME_EPID_KEY_REVOCATION]);
                break;
            }
        case PSE_PR_PCH_EPID_SIG_REVOKED_IN_SIGRL:
            {
                AESM_LOG_FATAL("%s", g_event_string_table[SGX_EVENT_ME_EPID_SIG_REVOCATION]);
                break;
            }
        case AE_FAILURE:
            {
                AESM_LOG_FATAL("%s", g_event_string_table[SGX_EVENT_LTP_FAILURE]);
                break;
            }
        default: break;
        }
    }



    SGX_DBGPRINT_PRINT_FUNCTION_AND_RETURNVAL(__FUNCTION__, status);
    return status;
}

//
// ltpBlobCseGid
//
// return value of CSE GID from long-term pairing blob
//
// inputs
// pGid: pointer to uint32_t that will hold GID
//
// outputs
// *pGid: CSE GID
// status
//
// different return type?
//
ae_error_t ltpBlobCseGid(uint32_t* pGid)
{
    upse::Buffer pairing_blob;
    ae_error_t retVal = AE_SUCCESS;

    if (NULL != pGid) {
        //
        // read blob
        //
        retVal = upsePersistentStorage::Read(PSE_PR_LT_PAIRING_FID, pairing_blob);
        if (AE_SUCCESS == retVal) {
            const pairing_blob_t* pb = (const pairing_blob_t*) pairing_blob.getData();
            if (NULL != pb) {
                *pGid = pb->plaintext.cse_sec_prop.ps_hw_gid;
            }
            else {
                retVal = AESM_PSE_PR_INTERNAL_ERROR;
            }
        }
        else {
            retVal = AESM_PSE_PR_PERSISTENT_STORAGE_READ_ERROR;
        }
    }
    else {
        retVal = AESM_PSE_PR_BAD_POINTER_ERROR;
    }

    return retVal;
}

