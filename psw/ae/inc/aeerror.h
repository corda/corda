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

#ifndef _AE_ERROR_H_
#define _AE_ERROR_H_

typedef enum _ae_error_t{
    AE_SUCCESS                               =  0,
    AE_FAILURE                               =  1,
    AE_ENCLAVE_LOST                          =  2,

    OAL_PARAMETER_ERROR                      =  3,
    OAL_PATHNAME_BUFFER_OVERFLOW_ERROR       =  4,
    OAL_FILE_ACCESS_ERROR                    =  5,
    OAL_CONFIG_FILE_ERROR                    =  6,
    OAL_NETWORK_UNAVAILABLE_ERROR            =  7,
    OAL_NETWORK_BUSY                         =  8,
    OAL_NETWORK_RESEND_REQUIRED              =  9,
    OAL_PROXY_SETTING_ASSIST                 = 10,
    OAL_THREAD_ERROR                         = 11,
    OAL_THREAD_TIMEOUT_ERROR                 = 12,

    AE_PSVN_UNMATCHED_ERROR                  = 13,
    AE_SERVER_NOT_AVAILABLE                  = 14,
    AE_INVALID_PARAMETER                     = 15,
    AE_READ_RAND_ERROR                       = 16,
    AE_OUT_OF_MEMORY_ERROR                   = 17,
    AE_INSUFFICIENT_DATA_IN_BUFFER           = 18,

    /* QUOTING ENCLAVE ERROR CASES*/
    QE_UNEXPECTED_ERROR                      = 19,
    QE_PARAMETER_ERROR                       = 20,
    QE_EPIDBLOB_ERROR                        = 21,
    QE_REVOKED_ERROR                         = 22,
    QE_SIGRL_ERROR                           = 23,

    /* PROVISIONING ENCLAVE ERROR CASES*/
    PVE_UNEXPECTED_ERROR                     = 24,
    PVE_PARAMETER_ERROR                      = 25,
    PVE_EPIDBLOB_ERROR                       = 26,
    PVE_INSUFFICIENT_MEMORY_ERROR            = 27,
    PVE_INTEGRITY_CHECK_ERROR                = 28,
    PVE_SIGRL_INTEGRITY_CHECK_ERROR          = 29,
    PVE_SERVER_REPORTED_ERROR                = 30,
    PVE_PEK_SIGN_ERROR                       = 31,
    PVE_MSG_ERROR                            = 32,
    PVE_REVOKED_ERROR                        = 33,
    PVE_SESSION_OUT_OF_ORDER_ERROR           = 34,
    PVE_SERVER_BUSY_ERROR                    = 35,
    PVE_PERFORMANCE_REKEY_NOT_SUPPORTED      = 36,

    /* LICENSING ENCLAVE ERROR CASES*/
    LE_UNEXPECTED_ERROR                      = 37,
    LE_INVALID_PARAMETER                     = 38,
    LE_GET_LICENSE_KEY_ERROR                 = 39,
    LE_INVALID_ATTRIBUTE                     = 40,
    LE_INVALID_PRIVILEGE_ERROR               = 41,
    LE_WHITELIST_UNINITIALIZED_ERROR         = 42,
    LE_CALC_LIC_TOKEN_ERROR                  = 43,
    AESM_NLTP_NO_LTP_BLOB                    =179,
    AESM_NLTP_DONT_NEED_UPDATE_PAIR_LTP      =180,
    AESM_NLTP_MAY_NEED_UPDATE_LTP            =181,
    AESM_NLTP_OLD_EPID11_RLS                 =182,
    AESM_PCP_NEED_PSE_UPDATE                 =183,
    AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_NEED_EPID_UPDATE           =184,
    AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_MIGHT_NEED_EPID_UPDATE     =185,
    AESM_PCP_SIMPLE_PSE_CERT_PROVISIONING_ERROR             =186,
    AESM_PCP_SIMPLE_EPID_PROVISION_ERROR     =187,
    AESM_NPC_DONT_NEED_PSEP                  =188,
    AESM_NPC_NO_PSE_CERT                     =189,
    AESM_NPC_DONT_NEED_UPDATE_PSEP           =190,
    AESM_NPC_MAY_NEED_UPDATE_PSEP            =191,
    AESM_NEP_DONT_NEED_EPID_PROVISIONING     =192,
    AESM_NEP_DONT_NEED_UPDATE_PVEQE          =193,
    AESM_NEP_PERFORMANCE_REKEY               =194,
    AESM_NEP_MAY_NEED_UPDATE                 =195,
    AESM_CP_ATTESTATION_FAILURE              =196,
    AESM_LTP_PSE_CERT_REVOKED                =197,
    AESM_LTP_SIMPLE_LTP_ERROR                =198,
    AESM_PSE_PR_GET_PRIVRL_ERROR             =199,
    AESM_NETWORK_TIMEOUT                     =200,

    PSW_UPDATE_REQUIRED                      =201,
    AESM_AE_OUT_OF_EPC                       =202,

    PVE_PROV_ATTEST_KEY_NOT_FOUND            =203,
    PVE_INVALID_REPORT                       =204,
    PVE_XEGDSK_SIGN_ERROR                    =205,

    // PCE ERROR CODES
    PCE_UNEXPECTED_ERROR                     =206,
    PCE_INVALID_PRIVILEGE                    =207,
    PCE_INVALID_REPORT                       =208,

    LE_WHITE_LIST_QUERY_BUSY                 =209,
    AESM_AE_NO_DEVICE                        =210,
    EXTENDED_GROUP_NOT_AVAILABLE             =211,

    // MORE PSE_OP ERROR CASES
    PSE_OP_ERROR_KDF_MISMATCH                =212,

    LE_WHITE_LIST_HAS_BEEN_UPDATED           =213,
} ae_error_t;

#define AE_FAILED(x)    (AE_SUCCESS != (x))
#define AE_SUCCEEDED(x) (AE_SUCCESS == (x))

/* These definitions are usable to exit a loop*/
#define BREAK_IF_TRUE(x, Sts, ErrCode)  if (x)    { Sts = ErrCode; break; }
#define BREAK_IF_FALSE(x, Sts, ErrCode) if (!(x)) { Sts = ErrCode; break; }
#define BREAK_IF_FAILED(x)              if (AE_SUCCESS != (x)) { break; }
#define BREAK_IF_FAILED_ERR(x, ErrCode) if (AE_SUCCESS != (x)) { x = ErrCode; break; }


#endif/*_AE_ERROR_H_*/

