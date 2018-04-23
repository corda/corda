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

#include "u_certificate_provisioning.h"
#include "helper.h"

#include "CertificateProvisioningProtocol.h"

#include "sgx_quote.h"

#include <Buffer.h>
#include <cstddef>
#include <assert.h>
#include <string.h>

#include "uecall_bridge.h"

#include "aeerror.h"

#include "epid/common/types.h"

#include "provision_msg.h"
#include "qe_logic.h"
#include "aesm_logic.h"
#include "endpoint_select_info.h"
#include "pve_logic.h"

#include "sgx_tseal.h"
#include "pairing_blob.h"
#include "helper.h"
#include "PSEPRClass.h"
#include "platform_info_blob.h"

#include "oal/oal.h"
#include "se_sig_rl.h"

#include "le2be_macros.h"
#include "pibsk_pub.hh"
#include "aesm_long_lived_thread.h"
#include "sgx_sha256_128.h"

#define PSEPR_LOST_ENCLAVE_RETRY_COUNT      3

//#define FAKE_QUOTE

#ifndef FAKE_QUOTE
uint8_t GID_TO_USE[4] = { 0x00, 0x00, 0x14, 0x01 };
#else
uint8_t GID_TO_USE[4] = { 0x00, 0x00, 0x00, 0x06 };
#endif


/* hardcoded "Public" PSE Certificate */

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

#if defined(NO_PROVISIONING_SERVER)

#define PUBLIC_PSE_CERT_LEN 770
static const uint8_t PUBLIC_PSE_CERT[PUBLIC_PSE_CERT_LEN] =
{
    0x30, 0x82, 0x02, 0xFE, 0x30, 0x82, 0x02, 0xA3, 0xA0, 0x03, 0x02, 0x01, 0x02, 0x02, 0x14, 0x77,
    0xAC, 0xBD, 0xE3, 0xC4, 0xE3, 0x00, 0xC1, 0x19, 0x14, 0x70, 0xBF, 0x23, 0x76, 0x83, 0x90, 0x91,
    0x42, 0x3B, 0xEA, 0x30, 0x0A, 0x06, 0x08, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x04, 0x03, 0x02, 0x30,
    0x81, 0x8A, 0x31, 0x0B, 0x30, 0x09, 0x06, 0x03, 0x55, 0x04, 0x06, 0x13, 0x02, 0x55, 0x53, 0x31,
    0x0B, 0x30, 0x09, 0x06, 0x03, 0x55, 0x04, 0x08, 0x0C, 0x02, 0x43, 0x41, 0x31, 0x14, 0x30, 0x12,
    0x06, 0x03, 0x55, 0x04, 0x07, 0x0C, 0x0B, 0x53, 0x61, 0x6E, 0x74, 0x61, 0x20, 0x43, 0x6C, 0x61,
    0x72, 0x61, 0x31, 0x1A, 0x30, 0x18, 0x06, 0x03, 0x55, 0x04, 0x0A, 0x0C, 0x11, 0x49, 0x6E, 0x74,
    0x65, 0x6C, 0x20, 0x43, 0x6F, 0x72, 0x70, 0x6F, 0x72, 0x61, 0x74, 0x69, 0x6F, 0x6E, 0x31, 0x24,
    0x30, 0x22, 0x06, 0x03, 0x55, 0x04, 0x0B, 0x0C, 0x1B, 0x45, 0x50, 0x49, 0x44, 0x20, 0x61, 0x6E,
    0x64, 0x20, 0x53, 0x49, 0x47, 0x4D, 0x41, 0x20, 0x72, 0x6F, 0x6F, 0x74, 0x20, 0x73, 0x69, 0x67,
    0x6E, 0x69, 0x6E, 0x67, 0x31, 0x16, 0x30, 0x14, 0x06, 0x03, 0x55, 0x04, 0x03, 0x0C, 0x0D, 0x77,
    0x77, 0x77, 0x2E, 0x69, 0x6E, 0x74, 0x65, 0x6C, 0x2E, 0x63, 0x6F, 0x6D, 0x30, 0x1E, 0x17, 0x0D,
    0x31, 0x33, 0x30, 0x38, 0x31, 0x35, 0x31, 0x35, 0x34, 0x32, 0x33, 0x32, 0x5A, 0x17, 0x0D, 0x34,
    0x39, 0x31, 0x32, 0x33, 0x31, 0x32, 0x33, 0x35, 0x39, 0x35, 0x39, 0x5A, 0x30, 0x81, 0xB7, 0x31,
    0x0B, 0x30, 0x09, 0x06, 0x03, 0x55, 0x04, 0x06, 0x0C, 0x02, 0x55, 0x53, 0x31, 0x0B, 0x30, 0x09,
    0x06, 0x03, 0x55, 0x04, 0x08, 0x0C, 0x02, 0x43, 0x41, 0x31, 0x14, 0x30, 0x12, 0x06, 0x03, 0x55,
    0x04, 0x07, 0x0C, 0x0B, 0x53, 0x61, 0x6E, 0x74, 0x61, 0x20, 0x43, 0x6C, 0x61, 0x72, 0x61, 0x31,
    0x1A, 0x30, 0x18, 0x06, 0x03, 0x55, 0x04, 0x0A, 0x0C, 0x11, 0x49, 0x6E, 0x74, 0x65, 0x6C, 0x20,
    0x43, 0x6F, 0x72, 0x70, 0x6F, 0x72, 0x61, 0x74, 0x69, 0x6F, 0x6E, 0x31, 0x37, 0x30, 0x35, 0x06,
    0x03, 0x55, 0x04, 0x0B, 0x0C, 0x2E, 0x49, 0x6E, 0x74, 0x65, 0x6C, 0x20, 0x50, 0x53, 0x45, 0x20,
    0x44, 0x37, 0x33, 0x33, 0x45, 0x35, 0x32, 0x46, 0x2D, 0x43, 0x34, 0x43, 0x34, 0x2D, 0x41, 0x43,
    0x36, 0x39, 0x2D, 0x41, 0x44, 0x41, 0x46, 0x2D, 0x31, 0x42, 0x31, 0x36, 0x45, 0x32, 0x42, 0x32,
    0x31, 0x45, 0x32, 0x36, 0x31, 0x16, 0x30, 0x14, 0x06, 0x03, 0x55, 0x04, 0x03, 0x0C, 0x0D, 0x77,
    0x77, 0x77, 0x2E, 0x69, 0x6E, 0x74, 0x65, 0x6C, 0x2E, 0x63, 0x6F, 0x6D, 0x31, 0x18, 0x30, 0x16,
    0x06, 0x0A, 0x09, 0x92, 0x26, 0x89, 0x93, 0xF2, 0x2C, 0x64, 0x01, 0x01, 0x0C, 0x08, 0x46, 0x46,
    0x46, 0x46, 0x46, 0x46, 0x46, 0x46, 0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2A, 0x86, 0x48, 0xCE,
    0x3D, 0x02, 0x01, 0x06, 0x08, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
    0x04, 0x73, 0x27, 0xB9, 0x51, 0x38, 0x9A, 0x03, 0x23, 0xEC, 0xFF, 0xCA, 0xCE, 0x84, 0x51, 0x6B,
    0xB1, 0x10, 0xC1, 0x19, 0xF5, 0x11, 0xB4, 0x38, 0xAD, 0xE0, 0xAA, 0xC2, 0xFF, 0x77, 0x84, 0x49,
    0x32, 0x85, 0x9B, 0xFB, 0x21, 0x97, 0xBF, 0xA1, 0x34, 0xF7, 0x07, 0x00, 0xD3, 0xA9, 0xF5, 0x3C,
    0x8C, 0xE9, 0x9D, 0xF8, 0x62, 0xA1, 0x69, 0xA4, 0xB4, 0x06, 0xFA, 0x49, 0x91, 0x89, 0xC8, 0x6C,
    0x1C, 0xA3, 0x81, 0xB7, 0x30, 0x81, 0xB4, 0x30, 0x0E, 0x06, 0x03, 0x55, 0x1D, 0x0F, 0x01, 0x01,
    0xFF, 0x04, 0x04, 0x03, 0x02, 0x06, 0xC0, 0x30, 0x0C, 0x06, 0x03, 0x55, 0x1D, 0x13, 0x01, 0x01,
    0xFF, 0x04, 0x02, 0x30, 0x00, 0x30, 0x13, 0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF8, 0x4D, 0x01,
    0x09, 0x02, 0x01, 0x01, 0xFF, 0x04, 0x03, 0x0A, 0x01, 0x02, 0x30, 0x1D, 0x06, 0x03, 0x55, 0x1D,
    0x0E, 0x04, 0x16, 0x04, 0x14, 0xA1, 0xFF, 0x7A, 0xE1, 0xF5, 0x9D, 0x68, 0x4D, 0x84, 0x0C, 0x5A,
    0x69, 0xDA, 0xD5, 0xC2, 0x96, 0x9C, 0x32, 0x87, 0x29, 0x30, 0x3F, 0x06, 0x03, 0x55, 0x1D, 0x1F,
    0x04, 0x38, 0x30, 0x36, 0x30, 0x34, 0xA0, 0x32, 0xA0, 0x30, 0x86, 0x2E, 0x68, 0x74, 0x74, 0x70,
    0x3A, 0x2F, 0x2F, 0x75, 0x70, 0x67, 0x72, 0x61, 0x64, 0x65, 0x73, 0x2E, 0x69, 0x6E, 0x74, 0x65,
    0x6C, 0x2E, 0x63, 0x6F, 0x6D, 0x2F, 0x63, 0x6F, 0x6E, 0x74, 0x65, 0x6E, 0x74, 0x2F, 0x43, 0x52,
    0x4C, 0x2F, 0x45, 0x50, 0x49, 0x44, 0x2E, 0x63, 0x72, 0x6C, 0x30, 0x1F, 0x06, 0x03, 0x55, 0x1D,
    0x23, 0x04, 0x18, 0x30, 0x16, 0x80, 0x14, 0x66, 0xE0, 0x68, 0x4F, 0x57, 0x61, 0x49, 0x9B, 0x1F,
    0x7D, 0xFE, 0x55, 0x87, 0xE5, 0x54, 0xAB, 0xF8, 0x1B, 0x5B, 0xD9, 0x30, 0x0A, 0x06, 0x08, 0x2A,
    0x86, 0x48, 0xCE, 0x3D, 0x04, 0x03, 0x02, 0x03, 0x49, 0x00, 0x30, 0x46, 0x02, 0x21, 0x00, 0xCA,
    0x40, 0xA4, 0x60, 0xDA, 0xAD, 0x4E, 0x9E, 0xAE, 0xE9, 0x5D, 0xEB, 0x0D, 0x17, 0xD9, 0xE1, 0xFF,
    0xA3, 0xB4, 0x0F, 0x3D, 0xF2, 0x14, 0x1B, 0x89, 0x8F, 0x52, 0x2C, 0x4E, 0xEE, 0xFB, 0xE7, 0x02,
    0x21, 0x00, 0x9D, 0x7D, 0xEB, 0x47, 0xE9, 0xFA, 0xAF, 0x00, 0xA3, 0x68, 0xBC, 0xDF, 0x1C, 0x9E,
    0xB1, 0xA9, 0xA8, 0x7A, 0x0D, 0x90, 0xB2, 0xCC, 0x96, 0x2C, 0x31, 0x9B, 0x74, 0xE9, 0xBA, 0x17,
    0x28, 0xB6
};

#endif

//*********************************************************************
// Prototypes of static functions
//*********************************************************************

static ae_error_t do_quote_initialization
    (
    /*out */ upse::Buffer& targetInfo,
    /*out */ GroupId* pGID
    );
static ae_error_t do_get_quote
    (
    /*in */ upse::Buffer& report,
    /*in */ upse::Buffer& sigRL,
    /*out*/ upse::Buffer& quote
    );

static ae_error_t do_certificate_chain_provisioning
    (
    /*in */ const endpoint_selection_infos_t& es_info,
    /*out*/ platform_info_blob_wrapper_t* pib_wrapper
    );


ae_error_t ConvertBackendStatus(CertificateProvisioningProtocol& cpp, ae_error_t status)
{
    if (AE_FAILED(status))
    {
        if (PSE_PRS_OK != cpp.GetProtocolResponseStatus())
        {
            SGX_DBGPRINT_ONE_STRING_ONE_INT("Backend ProtocolResponseStatus", cpp.GetProtocolResponseStatus());
            switch (cpp.GetProtocolResponseStatus())
            {
            case PSE_PRS_INVALID_GID:       status = AESM_PSE_PR_BACKEND_INVALID_GID;               break;
            case PSE_PRS_GID_REVOKED:       status = AESM_PSE_PR_BACKEND_GID_REVOKED;               break;
            case PSE_PRS_INVALID_QUOTE:     status = AESM_PSE_PR_BACKEND_INVALID_QUOTE;             break;
            case PSE_PRS_INVALID_REQUEST:   status = AESM_PSE_PR_BACKEND_INVALID_REQUEST;           break;
            default:                        status = AESM_PSE_PR_BACKEND_UNKNOWN_PROTOCOL_RESPONSE; break;
            }
            AESM_DBG_ERROR(g_event_string_table[SGX_EVENT_PSE_CERT_PROV_PROTOCOL_RESPONSE_FAILURE], status);
            AESM_LOG_ERROR(g_event_string_table[SGX_EVENT_PSE_CERT_PROV_PROTOCOL_RESPONSE_FAILURE], status);
        }
        else if (GRS_OK != cpp.GetGeneralResponseStatus())
        {
            SGX_DBGPRINT_ONE_STRING_ONE_INT("Backend GeneralResponseStatus", cpp.GetGeneralResponseStatus());
            switch (cpp.GetGeneralResponseStatus())
            {
            case GRS_SERVER_BUSY:               status = AESM_PSE_PR_BACKEND_SERVER_BUSY;               break;
            case GRS_INTEGRITY_CHECK_FAIL:      status = AESM_PSE_PR_BACKEND_INTEGRITY_CHECK_FAIL;      break;
            case GRS_INCORRECT_SYNTAX:          status = AESM_PSE_PR_BACKEND_INCORRECT_SYNTAX;          break;
            case GRS_INCOMPATIBLE_VERSION:      status = PSW_UPDATE_REQUIRED;                           break;
            case GRS_TRANSACTION_STATE_LOST:    status = AESM_PSE_PR_BACKEND_TRANSACTION_STATE_LOST;    break;
            case GRS_PROTOCOL_ERROR:            status = AESM_PSE_PR_BACKEND_PROTOCOL_ERROR;            break;
            case GRS_INTERNAL_ERROR:            status = AESM_PSE_PR_BACKEND_INTERNAL_ERROR;            break;
            default:                            status = AESM_PSE_PR_BACKEND_UNKNOWN_PROTOCOL_RESPONSE; break;
            }
            AESM_DBG_ERROR(g_event_string_table[SGX_EVENT_PSE_CERT_PROV_GENERAL_RESPONSE_FAILURE], status);
            AESM_LOG_ERROR(g_event_string_table[SGX_EVENT_PSE_CERT_PROV_GENERAL_RESPONSE_FAILURE], status);
        }
        else
        {
            switch (status)
            {
            case OAL_NETWORK_UNAVAILABLE_ERROR:
                {
                    AESM_LOG_ERROR(g_event_string_table[SGX_EVENT_PSE_CERT_PROV_FAILURE]);
                    break;
                }
            case PSE_PAIRING_BLOB_UNSEALING_ERROR:
                {
                    AESM_LOG_ERROR(g_event_string_table[SGX_EVENT_LTP_BLOB_INTEGRITY_ERROR]);
                    break;
                }
            case PSE_PAIRING_BLOB_INVALID_ERROR:
                {
                    AESM_LOG_ERROR(g_event_string_table[SGX_EVENT_LTP_BLOB_INVALID_ERROR]);
                    break;
                }
            case AESM_PSE_PR_BACKEND_MSG4_PLATFORM_INFO_BLOB_SIZE:
                {
                    //
                    // happens if pib returned is not the right size
                    //
                    AESM_LOG_ERROR(g_event_string_table[SGX_EVENT_PSE_CERT_PROV_PROTOCOL_RESPONSE_FAILURE]);
                    break;
                }
            case AE_FAILURE:
                {
                    //
                    // happens if problem with proxy setting
                    //
                    AESM_LOG_ERROR(g_event_string_table[SGX_EVENT_PSE_CERT_PROV_FAILURE]);
                    break;
                }
            case AESM_CP_ATTESTATION_FAILURE:
                {
                    AESM_LOG_ERROR(g_event_string_table[SGX_EVENT_PSE_ATTESTATION_ERROR]);
                    break;
                }
            default:
                {
                    AESM_DBG_ERROR("Error in ConvertBackendStatus(status) : status = %d (%xh)", status, status);
                    break;
                }
            }
        }
    }

    return status;
}


//*********************************************************************
// Main engine routine for Certificate Chain Provisioning
//*********************************************************************
ae_error_t certificate_chain_provisioning(const endpoint_selection_infos_t& es_info, platform_info_blob_wrapper_t* pib_wrapper)
{
    ae_error_t status = AE_FAILURE;
    AESM_DBG_TRACE("enter fun");

    try
    {
        do
        {

            status = do_certificate_chain_provisioning(es_info, pib_wrapper);

            if (status == PSE_PR_ENCLAVE_LOST_ERROR)
            {
                //
                // went to sleep while in enclave
                // in this case (beginning of flow), we should just retry, after first destroying and then reloading
                // note that this code gets significantly more complicated if the PSE-pr ever becomes multi-threaded
                //
                for (unsigned rcount = 0; rcount < PSEPR_LOST_ENCLAVE_RETRY_COUNT; rcount++)
                {
                    CPSEPRClass::instance().unload_enclave();
                    if (0 != CPSEPRClass::instance().load_enclave())
                    {
                        status = AE_FAILURE;
                        break;
                    }
                    SaveEnclaveID(CPSEPRClass::instance().GetEID());

                    status = do_certificate_chain_provisioning(es_info, pib_wrapper);
                    if (status != PSE_PR_ENCLAVE_LOST_ERROR)
                        break;
                }
            }

            BREAK_IF_FAILED(status);

            status = AE_SUCCESS;

        } while (0);
    }
    catch (...)
    {
        status = AESM_PSE_PR_EXCEPTION;
    }

    SGX_DBGPRINT_PRINT_FUNCTION_AND_RETURNVAL(__FUNCTION__, status);
    SGX_DBGPRINT_PRINT_ANSI_STRING("End Certificate Chain Provisioning");
    return status;
}


//*********************************************************************
// Do the certificate chain provisioning logic
//*********************************************************************
static ae_error_t do_certificate_chain_provisioning
    (
    /*in */ const endpoint_selection_infos_t& es_info,
    /*out*/ platform_info_blob_wrapper_t* pib_wrapper
    )
{
    if (NULL == pib_wrapper)
        return AESM_PSE_PR_BAD_POINTER_ERROR;

    ae_error_t status = AE_FAILURE;
    upse::Buffer target_info;

    uint32_t gid = 0;
    //    GroupId gid = {0};                  // Send to Server in M1

    upse::Buffer nonce;                 // Receive from Server in M2
    upse::Buffer sig_rl;                // Receive from Server in M2
    upse::Buffer csr_pse;               // Send to Server in M3
    upse::Buffer quote;                 // Send to Server in M3
    std::list<upse::Buffer> certChain;  // Receive from Server in M4

    upse::Buffer report;                // Received from PSE_pr
    upse::Buffer pairing_blob;          // Received from PSE_pr
    upse::Buffer ocsp_req;              // Created here from cert chain

    const char *szURL = EndpointSelectionInfo::instance().get_pse_provisioning_url(es_info);

    memset(pib_wrapper, 0, sizeof(platform_info_blob_wrapper_t));   // Receive from Server in M4

    CertificateProvisioningProtocol cpp;

    SGX_DBGPRINT_PRINT_ANSI_STRING("Begin Certificate (PSE) Provisioning");
    do
    {
        Helper::RemoveCertificateChain();
        Helper::delete_ocsp_response_vlr();

#if defined(NO_PROVISIONING_SERVER)

        {
            //*********************************************************************
            // Use hardcoded Cert.
            //*********************************************************************
            SGX_DBGPRINT_PRINT_ANSI_STRING("Using Hard Coded Cert");
            status = tPrepareForCertificateProvisioning_hardcoded_privatekey(pairing_blob);
            BREAK_IF_FAILED(status);

            /* Use the hardcoded "Public" Cert */
            upse::Buffer Cert;
            Cert.Alloc(PUBLIC_PSE_CERT_LEN);
            upse::BufferWriter bwCert(Cert);
            uint8_t* pCert;
            status = bwCert.reserve(PUBLIC_PSE_CERT_LEN, &pCert);
            BREAK_IF_FAILED(status);
            memcpy_s(pCert, PUBLIC_PSE_CERT_LEN, PUBLIC_PSE_CERT, PUBLIC_PSE_CERT_LEN);
            certChain.push_back(Cert);

        }

#else

        {

            status = cpp.init(szURL, es_info.pek);
            BREAK_IF_FAILED(status);

            //=====================================================================
            // Start: CERTIFICATE CHAIN PROVISIONING  (3.6.7.1.1.2.1)
            //=====================================================================

            //*********************************************************************
            // Retrieve GID_SE from the QE
            SGX_DBGPRINT_PRINT_ANSI_STRING("quote init?");
            //*********************************************************************
            status = do_quote_initialization(target_info, (GroupId*)&gid);
            BREAK_IF_FAILED(status);//keep reason for quoting failure including UPDATE required
            SGX_DBGPRINT_PRINT_ANSI_STRING("quote init success");

            //*********************************************************************
            // Retrieve SIG_RL and Nonce from Intel Server.
            //*********************************************************************
            status = cpp.SendM1_ReceiveM2(*(uint32_t*)&gid, nonce, sig_rl);
            BREAK_IF_FAILED(status);
            SGX_DBGPRINT_PRINT_ANSI_STRING("send m1, receive m2 success");

            Helper::read_ltp_blob(pairing_blob);
            // Note: failure during read_ltp_blob is okay, pairing_blob will be empty and get filled in by enclave

            //*********************************************************************
            // Generate ECDSA key pair, CSR_pse, and REPORT in enclave (PSE_Pr).
            //*********************************************************************
            status = tPrepareForCertificateProvisioning(nonce, target_info, csr_pse,
                report, pairing_blob);
            BREAK_IF_FAILED(status);
            SGX_DBGPRINT_PRINT_ANSI_STRING("prepare for cert pv success");

            //*********************************************************************
            // Call QE to convert REPORT to name-based QUOTE using SIG_RL
            //*********************************************************************
            status = do_get_quote(report, sig_rl, quote);
            BREAK_IF_TRUE((AESM_AE_OUT_OF_EPC == status), status, AESM_AE_OUT_OF_EPC);
            BREAK_IF_FAILED_ERR(status, AESM_CP_ATTESTATION_FAILURE);
            SGX_DBGPRINT_PRINT_ANSI_STRING("get quote success");

            //*********************************************************************
            // Retrieve the Certificate Chain from Intel Server.
            //*********************************************************************
            status = cpp.SendM3_ReceiveM4(csr_pse, quote, certChain, *pib_wrapper);
            BREAK_IF_TRUE((PSE_PRS_OK != cpp.GetProtocolResponseStatus()), status, AESM_CP_ATTESTATION_FAILURE);
            BREAK_IF_FAILED(status);
            SGX_DBGPRINT_PRINT_ANSI_STRING("send m3, receive m4 success");
        }

#endif

        //*********************************************************************
        // Save the Certificate Chain to persistent storage.
        //*********************************************************************
        status = Helper::SaveCertificateChain(certChain);
        BREAK_IF_FAILED(status);
        SGX_DBGPRINT_PRINT_ANSI_STRING("save cert success");

        //*********************************************************************
        // Save the sealed pairing blob to persistent storage.
        //*********************************************************************
        status = Helper::write_ltp_blob(pairing_blob);
        BREAK_IF_FAILED(status);
        SGX_DBGPRINT_PRINT_ANSI_STRING("write blob success");

        status = AE_SUCCESS;

        SGX_DBGPRINT_PRINT_ANSI_STRING("End of Certificate (PSE) Provisioning");
    } while (0);

    status = ConvertBackendStatus(cpp, status);
    return status;
}


//*********************************************************************
// Call quoting enclave to get target info
//*********************************************************************
static ae_error_t do_quote_initialization
    (
    /*out */ upse::Buffer& targetInfo,
    /*out */ GroupId* pGID
    )
{
    ae_error_t status = AE_FAILURE;


    do
    {
        BREAK_IF_TRUE( (NULL == pGID), status, PSE_PR_BAD_POINTER_ERROR);

#ifndef FAKE_QUOTE
        if (AE_FAILED(targetInfo.Alloc(sizeof(sgx_target_info_t))))
            break;
        upse::BufferWriter bwTargetInfo(targetInfo);
        uint8_t* p;
        status = bwTargetInfo.reserve(sizeof(sgx_target_info_t), &p);
        if (AE_FAILED(status))
            break;
        sgx_target_info_t* pTargetInfo = (sgx_target_info_t*)p;

        aesm_error_t result;
        SGX_DBGPRINT_PRINT_ANSI_STRING("aesmLogic.init_quote?");

        result = AESMLogic::init_quote(
            (uint8_t*)pTargetInfo, sizeof(sgx_target_info_t),
            (uint8_t*)pGID, sizeof(*pGID));
        if (result == AESM_BUSY)
        {
            //EPID_PROVISION triggered, make sure previous EPID provision has finished
            ae_error_t temp_ret = wait_pve_thread();
            BREAK_IF_TRUE(AE_SUCCESS != temp_ret , status, PSE_PR_PCH_EPID_UNKNOWN_ERROR);
            //redo init_quote
            result = AESMLogic::init_quote(
                (uint8_t*)pTargetInfo, sizeof(sgx_target_info_t),
                (uint8_t*)pGID, sizeof(*pGID));
        }
        BREAK_IF_TRUE(AESM_UPDATE_AVAILABLE == result, status, PSW_UPDATE_REQUIRED);
        BREAK_IF_TRUE(AESM_OUT_OF_EPC == result, status, AESM_AE_OUT_OF_EPC);
        BREAK_IF_TRUE(AESM_SUCCESS != result, status, AESM_PSE_PR_INIT_QUOTE_ERROR);
#else

        //NRG: m_tmpGID = 0;
        upse::Buffer m_tmpGID;
        if (AE_FAILED(m_tmpGID.Alloc(GID_TO_USE, sizeof(GID_TO_USE))))
            break;

        //      m_tmpGID = 1244;
        //      upse::BufferWriter(m_tmpGID).writeRaw(GID_TO_USE, sizeof(GID_TO_USE));
        SigmaData::SetGID(m_tmpGID);
        memcpy_s(pGID, sizeof(GroupId), m_tmpGID.getData(), sizeof(GroupId));
        if (AE_FAILED(targetInfo.Alloc(sizeof(sgx_target_info_t))))
            break;
#endif

        SGX_DBGPRINT_PRINT_ANSI_STRING("aesmLogic.init_quote success");
        status = AE_SUCCESS;

    } while (0);

    SGX_DBGPRINT_PRINT_FUNCTION_AND_RETURNVAL(__FUNCTION__, status);

    return status;
}


//*********************************************************************
// Call quoting enclave to convert report to name-based quote
//*********************************************************************
static ae_error_t do_get_quote
    (
    /*in */ upse::Buffer& reportBuffer,
    /*in */ upse::Buffer& sigRLBuffer,
    /*out*/ upse::Buffer& quoteBuffer
    )
{
    // Call QE to convert REPORT to a name-based QUOTE
    ae_error_t status = AE_FAILURE;
    ae_error_t tmp_status = AE_SUCCESS;

    do
    {
#ifndef FAKE_QUOTE
        uint32_t nQuote;                                 // in     - Quote buffer size

        sgx_report_t enclaveReport;                      // in
        sgx_quote_sign_type_t quote_type;                // in
        sgx_spid_t spid = {{0}};                           // in
        uint8_t* pSigRL = NULL;                          // in
        uint32_t nSigRL = 0;                               // in     - Sig RL buffer size

        memset(&enclaveReport, 0, sizeof(enclaveReport));

        nSigRL = sigRLBuffer.getSize();

        if (0 != nSigRL)
            pSigRL = const_cast<uint8_t*>(sigRLBuffer.getData());

        if (SGX_SUCCESS != sgx_calc_quote_size(pSigRL, nSigRL, &nQuote))
            break;

        tmp_status = quoteBuffer.Alloc(nQuote);
        if (AE_FAILED(tmp_status))
            break;
        upse::BufferWriter bwQuote(quoteBuffer);
        uint8_t* pQuote;
        tmp_status = bwQuote.reserve(nQuote, &pQuote);      // out
        if (AE_FAILED(tmp_status))
            break;

        quote_type = SGX_UNLINKABLE_SIGNATURE; // or SGX_LINKABLE_SIGNATURE

        // LSB16(SHA256("SGX PSE PROVISIONING SERVER"))
        // const char* SPID_VALUE = "SGX PSE PROVISIONING SERVER";
        // sgx_sha256_hash_t spid_hash;
        // sgx_sha256_msg((const uint8_t*)SPID_VALUE, strlen(SPID_VALUE), &spid_hash);
        // memcpy_s(spid.id, sizeof(spid.id), &spid_hash[0], 16);
        static uint8_t spid_hash[] = { 0x32, 0x81, 0xE5, 0x9E, 0xB1, 0x23, 0xFA, 0xCD,
            0x56, 0xDB, 0x62, 0x1E, 0x3B, 0x37, 0xFB, 0xE2 };
        memcpy_s(spid.id, sizeof(spid.id), spid_hash, sizeof(spid_hash));

        if (reportBuffer.getSize() != sizeof(enclaveReport))
            break;

        memcpy_s(&enclaveReport, reportBuffer.getSize(), reportBuffer.getData(), reportBuffer.getSize());
        aesm_error_t result;
        result = AESMLogic::get_quote(
            (uint8_t*)&enclaveReport, sizeof(enclaveReport),
            quote_type,
            (uint8_t*)&spid, sizeof(spid),
            NULL, 0,
            pSigRL, nSigRL,
            NULL, 0,
            (uint8_t*)pQuote, nQuote);
        if (result == AESM_BUSY)
        {
            //EPID_PROVISION triggered, make sure previous EPID provision has finished
            ae_error_t temp_ret = wait_pve_thread();
            BREAK_IF_TRUE(AE_SUCCESS != temp_ret , status, PSE_PR_PCH_EPID_UNKNOWN_ERROR);
            //redo get_quote
            result = AESMLogic::get_quote(
                (uint8_t*)&enclaveReport, sizeof(enclaveReport),
                quote_type,
                (uint8_t*)&spid, sizeof(spid),
                NULL, 0,
                pSigRL, nSigRL,
                NULL, 0,
                (uint8_t*)pQuote, nQuote);
        }
        BREAK_IF_TRUE(AESM_OUT_OF_EPC == result, status, AESM_AE_OUT_OF_EPC);
        BREAK_IF_TRUE(AESM_SUCCESS != result, status, AESM_PSE_PR_GET_QUOTE_ERROR);
#else
        const uint16_t SIGNATURE_LENGTH = 32;

        tmp_status = quoteBuffer.Alloc(sizeof(sgx_quote_t) + SIGNATURE_LENGTH);
        if (AE_FAILED(tmp_status))
            break;

        sgx_quote_t* pQuote;
        tmp_status = upse::BufferWriter(quoteBuffer).reserve(quoteBuffer.getSize(), (uint8_t**)&pQuote);
        if (AE_FAILED(tmp_status))
            break;

        uint16_t CPUSVN = 1;
        pQuote->version = 1;
        memcpy_s(pQuote->epid_group_id, sizeof(pQuote->epid_group_id), &GID_TO_USE, sizeof(GID_TO_USE));
        pQuote->report_body.isv_prod_id = 0x0002; //0x8086;
        pQuote->report_body.isv_svn = 1;
        memcpy_s(pQuote->report_body.cpu_svn, sizeof(pQuote->report_body.cpu_svn), &CPUSVN, sizeof(CPUSVN));

        const sgx_report_t* pReport = (sgx_report_t*)reportBuffer.getData();

        memcpy_s(pQuote->report_body.report_data, sizeof(pQuote->report_body.report_data), pReport->body.report_data, sizeof(pQuote->report_body.report_data));

        pQuote->signature_len = SIGNATURE_LENGTH;

        //NOTE: The signature is not valid when doing a FAKE_QUOTE
#endif

        status = AE_SUCCESS;

    } while (0);

    if ((AE_FAILURE == status) && AE_FAILED(tmp_status))
        status = tmp_status;

    SGX_DBGPRINT_PRINT_FUNCTION_AND_RETURNVAL(__FUNCTION__, status);

    return status;
}


//
// fwiw
// hex-encoded private part
//
//std::string prvPibSK = "942BCC166737FCF62CBF39668C6980F42A69A6828BEAB912362FEC0E21A2A61D";

ae_error_t pib_verify_signature(platform_info_blob_wrapper_t& piBlobWrapper)
{
    ae_error_t ae_err = AE_FAILURE;
    sgx_ecc_state_handle_t ecc_handle = NULL;

    uint8_t result = SGX_EC_INVALID_SIGNATURE;

    const uint32_t data_size = static_cast<uint32_t>(sizeof(piBlobWrapper.platform_info_blob) - sizeof(piBlobWrapper.platform_info_blob.signature));


    piBlobWrapper.valid_info_blob = false;
    do
    {
        sgx_ec256_public_t publicKey;
        sgx_ec256_signature_t signature;
        sgx_status_t sgx_status;

        //BREAK_IF_TRUE((sizeof(publicKey) != sizeof(s_pib_pub_key_big_endian)), ae_err, AE_FAILURE);
        //BREAK_IF_TRUE((sizeof(signature) != sizeof(piBlobWrapper.platform_info_blob.signature)), ae_err, AE_FAILURE);

        // convert the public key to little endian
        if(0!=memcpy_s(&publicKey, sizeof(publicKey), s_pib_pub_key_big_endian, sizeof(s_pib_pub_key_big_endian))){
            ae_err = AE_FAILURE;
            break;
        }
        SwapEndian_32B(((uint8_t*)&publicKey) +  0);
        SwapEndian_32B(((uint8_t*)&publicKey) + 32);

        // convert the signature to little endian
        if(0!=memcpy_s(&signature, sizeof(signature), &piBlobWrapper.platform_info_blob.signature, sizeof(piBlobWrapper.platform_info_blob.signature))){
            ae_err = AE_FAILURE;
            break;
        }
        SwapEndian_32B(((uint8_t*)&signature) +  0);
        SwapEndian_32B(((uint8_t*)&signature) + 32);

        sgx_status = sgx_ecc256_open_context(&ecc_handle);
        BREAK_IF_TRUE((SGX_SUCCESS != sgx_status), ae_err, AE_FAILURE);

        sgx_status = sgx_ecdsa_verify((uint8_t*)&piBlobWrapper.platform_info_blob, data_size, &publicKey, &signature, &result, ecc_handle);
        BREAK_IF_TRUE((SGX_SUCCESS != sgx_status), ae_err, AE_FAILURE);

        if (SGX_EC_VALID != result)
        {
            AESM_LOG_WARN(g_event_string_table[SGX_EVENT_PID_SIGNATURE_FAILURE]);
            break;
        }

        piBlobWrapper.valid_info_blob = true;

        ae_err = AE_SUCCESS;

    } while (0);
    if (ecc_handle != NULL) {
        sgx_ecc256_close_context(ecc_handle);
    }

    return ae_err;
}

ae_error_t generate_pse_instance_id(uint8_t* instance_id)
{
    memset(instance_id, 0, 16);
    return AE_SUCCESS;
}
