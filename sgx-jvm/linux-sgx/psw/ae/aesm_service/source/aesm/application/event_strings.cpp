/*
 * Copyright (C) 2011-2016 Intel Corporation. All rights reserved.
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
	"PSE attestation error"							// SGX_EVENT_PSE_ATTESTATION_ERROR

};
