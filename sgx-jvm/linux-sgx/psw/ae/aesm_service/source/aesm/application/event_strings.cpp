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

const char* g_event_string_table[] = {
    "SGX EPID provisioning network failure",        // SGX_EVENT_EPID_PROV_FAILURE                  //network
    "Fail to save attestation key in persistent storage",
                                                    // SGX_EVENT_EPID_BLOB_PERSISTENT_STROAGE_FAILURE
    "Platform Info Blob Signature Error",           // SGX_EVENT_PIB_SIGNATURE_FAILURE,
    "PSE certificate provisioning failure",         // SGX_EVENT_PSE_CERT_PROV_FAILURE              //network
    "OCSP failure",                                 // SGX_EVENT_OCSP_FAILURE                       //network
    "DAL failure",                                  // SGX_EVENT_DAL_COMM_FAILURE                   //communication
    "SGX disabled",                                 // SGX_EVENT_DISABLED
    "SGX Service unavailable",                      // SGX_EVENT_SERVICE_UNAVAILABLE
    "SGX AESM will exit",                           // SGX_EVENT_AESM_EXIT
    "PSE certificate revoked",                      // SGX_EVENT_PSE_CERT_REVOCATION
    "PCH EPID group revoked",                       // SGX_EVENT_ME_EPID_GROUP_REVOCATION
    "SGX EPID revocation",                          // SGX_EVENT_EPID_REVOCATION                    //SigRL fail
    "SGX EPID blob integrity error",                // SGX_EVENT_EPID_INTEGRITY_ERROR
    "LTP blob integrity error",                     // SGX_EVENT_LTP_BLOB_INTEGRITY_ERROR
    "LTP blob invalid error",                       //SGX_EVENT_LTP_BLOB_INVALID_ERROR
    "PCH EPID SigRL integrity error",               // SGX_EVENT_EPID11_SIGRL_INTEGRITY_ERROR
    "PCH EPID PrivRL integrity error",              // SGX_EVENT_EPID11_PRIVRL_INTEGRITY_ERROR
    "SGX EPID SigRL integrity error",               // SGX_EVENT_EPID20_SIGRL_INTEGRITY_ERROR
    "PCH EPID RL retrieval failure",                // SGX_EVENT_EPID11_RL_RETRIEVAL_FAILURE
    "Integrity error during SGX EPID provisioning", // SGX_EVENT_EPID_PROV_INTEGRITY_ERROR
    "Integrity error during PSE certificate provisioning",
                                                    // SGX_EVENT_PSE_CERT_PROV_INTEGRITY_ERROR
    "OCSP response error",                          // SGX_EVENT_OCSP_RESPONSE_ERROR                //based on non-crypto checks that AESM does
    "DAL SIGMA error",                              // SGX_EVENT_DAL_SIGMA_ERROR                    //DAL returns error during Sigma protocol
    "LTP failure",                                  // SGX_EVENT_LTP_FAILURE                        //other than above
    "DAL not installed or broken installation",     // SGX_EVENT_DAL_NOT_AVAILABLE_ERROR            //other than above
    "PCH EPID group certificate provisioning error",
                                                    // SGX_EVENT_EPID11_GROUP_CERT_PROV_ERROR
    "PSE certificate provisioning failure (Server Protocol Response %d)",
                                                    // SGX_EVENT_PSE_CERT_PROV_PROTOCOL_RESPONSE_FAILURE              //backend server protocol
    "PSE certificate provisioning failure (Server General Response %d)",
                                                    // SGX_EVENT_PSE_CERT_PROV_GENERAL_RESPONSE_FAILURE              //backend server status
    "PCH EPID signature revoked",                   // SGX_EVENT_ME_EPID_SIG_REVOCATION,
    "PCH EPID key revoked",                         // SGX_EVENT_ME_EPID_KEY_REVOCATION,
    "SIGMA S2 integrity error",                     // SGX_EVENT_SIGMA_S2_INTEGRITY_ERROR
    "DAL service layer error",                      // SGX_EVENT_DAL_SERVICE_ERROR
    "PSE attestation error",                        // SGX_EVENT_PSE_ATTESTATION_ERROR
    "No enough EPC to load",                        // SGX_EVENT_OUT_OF_EPC
    "Fail to read extended epid group blob. We will use default blob with ID==0",
    "Fail to load LE",
};

/*
 * Enterprise Customer targeted event string table for ADMIN log events. Don't change these
 * strings unless there is a need, since customers may be looking for specific event strings in
 * the Windows Event Log.
 */
const char* g_admin_event_string_table[] = {
    "AESM Service started",                                         // SGX_ADMIN_EVENT_AESM_STARTED
    "AESM Service stopped or paused",                               // SGX_ADMIN_EVENT_AESM_STOPPED
    "SGX is Enabled at AESM Service startup",                       // SGX_ADMIN_EVENT_SGX_IS_ENABLED
    "SGX is Disabled at AESM Service startup",                      // SGX_ADMIN_EVENT_SGX_IS_DISABLED
    "AESM Enable SGX request",                                      // SGX_ADMIN_EVENT_SGX_ENABLE_REQUEST
    "EPID Provisioning initiated",                                  // SGX_ADMIN_EVENT_EPID_PROV_START
    "EPID Provisioning successful",                                 // SGX_ADMIN_EVENT_EPID_PROV_SUCCESS
    "EPID Provisioning failed due to network error",                // SGX_ADMIN_EVENT_EPID_PROV_FAIL_NW
    "EPID Provisioning failed. PSW update is required",             // SGX_ADMIN_EVENT_EPID_PROV_FAIL_PSWVER
    "EPID Provisioning failed",                                     // SGX_ADMIN_EVENT_EPID_PROV_FAIL
    "Platform Services initializing",                               // SGX_ADMIN_EVENT_PS_INIT_START
    "Platform Services initialized successfully",                   // SGX_ADMIN_EVENT_PS_INIT_SUCCESS
    "Platform Services initialization failed due to network error", // SGX_ADMIN_EVENT_PS_INIT_FAIL_NW
    "Platform Services initialization failed. PSW update is required", // SGX_ADMIN_EVENT_PS_INIT_FAIL_PSWVER
    "Platform Services initialization failed due to certificate provisioning failure", // SGX_ADMIN_EVENT_PS_INIT_FAIL_CERT
    "Platform Services initialization failed due to Long Term Pairing failure", // SGX_ADMIN_EVENT_PS_INIT_FAIL_LTP
    "Platform Services initialization failed due to DAL error",     // SGX_ADMIN_EVENT_PS_INIT_FAIL_DAL
    "Platform Services initialization failed",                      // SGX_ADMIN_EVENT_PS_INIT_FAIL
    "Platform Services error, DAL related error",                   // SGX_ADMIN_EVENT_PS_DAL_ERROR
    "Platform Services error, certificate error",                   // SGX_ADMIN_EVENT_PS_CERT_ERROR
    "Platform Services error",                                      // SGX_ADMIN_EVENT_PS_ERROR
    "White List update requested",                                  // SGX_ADMIN_EVENT_WL_UPDATE_START
    "White list update request successful",                         // SGX_ADMIN_EVENT_WL_UPDATE_SUCCESS
    "White List update failed",                                     // SGX_ADMIN_EVENT_WL_UPDATE_FAIL
    "White List update failed due to network error",                // SGX_ADMIN_EVENT_WL_UPDATE_NETWORK_FAIL
    "Failed to get extended epid group ID, defaulting to ID=0",     // SGX_ADMIN_EVENT_PCD_NOT_AVAILABLE
    "EPID Provisioning failed. Platform is revoked.",               // SGX_ADMIN_EVENT_EPID_PROV_FAIL_REVOKED
    "Platform Services error, resource limit reached",              // SGX_ADMIN_EVENT_PS_RESOURCE_ERROR
    "EPID Provisioning protocol error reported by Backend",         // SGX_ADMIN_EVENT_EPID_PROV_BACKEND_PROTOCOL_ERROR
};
