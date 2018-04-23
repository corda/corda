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
    LE_GET_EINITTOKEN_KEY_ERROR              = 39,
    LE_INVALID_ATTRIBUTE                     = 40,
    LE_INVALID_PRIVILEGE_ERROR               = 41,
    LE_WHITELIST_UNINITIALIZED_ERROR         = 42,
    LE_CALC_LIC_TOKEN_ERROR                  = 43,
    /* PSE ERROR CASES*/
    PSE_PAIRING_BLOB_SEALING_ERROR           = 44,
    PSE_PAIRING_BLOB_UNSEALING_ERROR         = 45,
    PSE_PAIRING_BLOB_INVALID_ERROR           = 46,

    /* PSE_OP ERROR CASES*/
    PSE_OP_PARAMETER_ERROR                   = 47,
    PSE_OP_INTERNAL_ERROR                    = 48,
    PSE_OP_MAX_NUM_SESSION_REACHED           = 49,
    PSE_OP_SESSION_INVALID                   = 50,
    PSE_OP_SERVICE_MSG_ERROR                 = 51,
    PSE_OP_EPHEMERAL_SESSION_INVALID         = 52,
    PSE_OP_ERROR_EPH_SESSION_ESTABLISHMENT_INTEGRITY_ERROR   = 53,
    PSE_OP_UNKNWON_REQUEST_ERROR             = 54,
    PSE_OP_PSDA_BUSY_ERROR                   = 55,
    PSE_OP_LTPB_SEALING_OUT_OF_DATE          = 56,

    // PSDA ERROR CODES
    AESM_PSDA_NOT_AVAILABLE                  = 57,
    AESM_PSDA_INTERNAL_ERROR                 = 58,
    AESM_PSDA_NEED_REPAIRING                 = 59,
    AESM_PSDA_LT_SESSION_INTEGRITY_ERROR     = 60,
    AESM_PSDA_NOT_PROVISONED_ERROR           = 61,
    AESM_PSDA_PROTOCOL_NOT_SUPPORTED         = 62,
    AESM_PSDA_PLATFORM_KEYS_REVOKED          = 63,
    AESM_PSDA_SESSION_LOST                   = 64,
    AESM_PSDA_WRITE_THROTTLED                = 65,

    // PSE_Pr ERROR CASES
    PSE_PR_ERROR                             = 66,
    PSE_PR_PARAMETER_ERROR                   = 67,
    PSE_PR_ENCLAVE_EXCEPTION                 = 68,
    PSE_PR_CALL_ORDER_ERROR                  = 69,
    PSE_PR_ASN1DER_DECODING_ERROR            = 70,
    PSE_PR_PAIRING_BLOB_SIZE_ERROR           = 71,
    PSE_PR_BAD_POINTER_ERROR                 = 72,
    PSE_PR_SIGNING_CSR_ERROR                 = 73,
    PSE_PR_MSG_SIGNING_ERROR                 = 74,
    PSE_PR_INSUFFICIENT_MEMORY_ERROR         = 75,
    PSE_PR_BUFFER_TOO_SMALL_ERROR            = 76,
    PSE_PR_S3_DATA_ERROR                     = 77,
    PSE_PR_KEY_PAIR_GENERATION_ERROR         = 78,
    PSE_PR_DERIVE_SMK_ERROR                  = 79,
    PSE_PR_CREATE_REPORT_ERROR               = 80,
    PSE_PR_HASH_CALC_ERROR                   = 81,
    PSE_PR_HMAC_CALC_ERROR                   = 82,
    PSE_PR_ID_CALC_ERROR                     = 83,
    PSE_PR_HMAC_COMPARE_ERROR                = 84,
    PSE_PR_GA_COMPARE_ERROR                  = 85,
    PSE_PR_TASK_INFO_ERROR                   = 86,
    PSE_PR_MSG_COMPARE_ERROR                 = 87,
    PSE_PR_GID_MISMATCH_ERROR                = 88,
    PSE_PR_PR_CALC_ERROR                     = 89,
    PSE_PR_PARAM_CERT_SIZE_ERROR             = 90,
    PSE_PR_CERT_SIZE_ERROR                   = 91,
    PSE_PR_NO_OCSP_RESPONSE_ERROR            = 92,
    PSE_PR_X509_PARSE_ERROR                  = 93,
    PSE_PR_READ_RAND_ERROR                   = 94,
    PSE_PR_INTERNAL_ERROR                    = 95,
    PSE_PR_ENCLAVE_BRIDGE_ERROR              = 96,
    PSE_PR_ENCLAVE_LOST_ERROR                = 97,

    PSE_PR_PCH_EPID_UNKNOWN_ERROR            = 98,
    PSE_PR_PCH_EPID_NOT_IMPLEMENTED          = 99,
    PSE_PR_PCH_EPID_SIG_INVALID              =100,          
    PSE_PR_PCH_EPID_SIG_REVOKED_IN_PRIVRL    =101,         
    PSE_PR_PCH_EPID_NO_MEMORY_ERR            =102,
    PSE_PR_PCH_EPID_BAD_ARG_ERR              =103,
    PSE_PR_PCH_EPID_SIG_REVOKED_IN_VERIFIERRL=104,         
    PSE_PR_PCH_EPID_DIVIDED_BY_ZERO_ERR      =105,
    PSE_PR_PCH_EPID_MATH_ERR                 =106,
    PSE_PR_PCH_EPID_RAND_MAX_ITER_ERR        =107,
    PSE_PR_PCH_EPID_UNDERFLOW_ERR            =108,
    PSE_PR_PCH_EPID_HASH_ALGORITHM_NOT_SUPPORTED  =109,
    PSE_PR_PCH_EPID_DUPLICATE_ERR            =110,
    PSE_PR_PCH_EPID_SIG_REVOKED_IN_GROUPRL   =111,         
    PSE_PR_PCH_EPID_SIG_REVOKED_IN_SIGRL     =112,         
    PSE_PR_PCH_EPID_INCONSISTENT_BASENAME_SET_ERR =113,

    /* AESM PSE_Pr ERROR CASES*/
    AESM_PSE_PR_ERROR_GETTING_GROUP_ID_FROM_ME              =114,
    AESM_PSE_PR_INIT_QUOTE_ERROR             =115,
    AESM_PSE_PR_GET_QUOTE_ERROR              =116,
    AESM_PSE_PR_INSUFFICIENT_MEMORY_ERROR    =117,
    AESM_PSE_PR_BUFFER_TOO_SMALL             =118,
    AESM_PSE_PR_MAX_SIGRL_ENTRIES_EXCEEDED   =119,
    AESM_PSE_PR_MAX_PRIVRL_ENTRIES_EXCEEDED  =120,
    AESM_PSE_PR_GET_SIGRL_ERROR              =121,
    AESM_PSE_PR_GET_OCSPRESP_ERROR           =122,
    AESM_PSE_PR_CERT_SAVE_ERROR              =123,
    AESM_PSE_PR_CERT_LOAD_ERROR              =124,
    AESM_PSE_PR_CERT_DELETE_ERROR            =125,
    AESM_PSE_PR_PSDA_LOAD_ERROR              =126,
    AESM_PSE_PR_PSDA_PROVISION_ERROR         =127,
    AESM_PSE_PR_PSDA_NOT_PROVISIONED         =128,
    AESM_PSE_PR_PSDA_GET_GROUP_ID            =129,
    AESM_PSE_PR_PSDA_LTP_EXCHANGE_ERROR      =130,
    AESM_PSE_PR_PSDA_LTP_S1_ERROR            =131,
    AESM_PSE_PR_PERSISTENT_STORAGE_DELETE_ERROR             =132,
    AESM_PSE_PR_PERSISTENT_STORAGE_OPEN_ERROR=133,
    AESM_PSE_PR_PERSISTENT_STORAGE_WRITE_ERROR=134,
    AESM_PSE_PR_PERSISTENT_STORAGE_READ_ERROR=135,
    AESM_PSE_PR_BAD_POINTER_ERROR            =136,
    AESM_PSE_PR_CALL_ORDER_ERROR             =137,
    AESM_PSE_PR_INTERNAL_ERROR               =138,
    AESM_PRSE_HECI_INIT_ERROR                =139,
    AESM_PSE_PR_LOAD_VERIFIER_CERT_ERROR     =140,
    AESM_PSE_PR_EXCEPTION                    =141,
    AESM_PSE_PR_OCSP_RESPONSE_STATUS_MALFORMEDREQUEST       =142,
    AESM_PSE_PR_OCSP_RESPONSE_STATUS_INTERNALERROR          =143,
    AESM_PSE_PR_OCSP_RESPONSE_STATUS_TRYLATER=144,
    AESM_PSE_PR_OCSP_RESPONSE_STATUS_SIGREQUIRED            =145,
    AESM_PSE_PR_OCSP_RESPONSE_STATUS_UNAUTHORIZED           =146,
    AESM_PSE_PR_OCSP_RESPONSE_INTERNAL_ERROR =147,
    AESM_PSE_PR_OCSP_RESPONSE_NO_NONCE_ERROR =148,
    AESM_PSE_PR_OCSP_RESPONSE_NONCE_VERIFY_ERROR            =149,
    AESM_PSE_PR_OCSP_RESPONSE_VERIFY_ERROR   =150,
    AESP_PSE_PR_OCSP_RESPONSE_CERT_COUNT_ERROR              =151,
    AESM_PSE_PR_ICLS_CLIENT_MISSING_ERROR    =152,
    AESM_PSE_PR_NO_OCSP_RESPONSE_ERROR       =153,
    AESM_PSE_PR_RL_RESP_HEADER_ERROR         =154,
    AESM_PSE_PR_RL_SERVER_ERROR              =155,
    AESM_PSE_PR_BACKEND_INVALID_GID          =156,
    AESM_PSE_PR_BACKEND_GID_REVOKED          =157,
    AESM_PSE_PR_BACKEND_INVALID_QUOTE        =158,
    AESM_PSE_PR_BACKEND_INVALID_REQUEST      =159,
    AESM_PSE_PR_BACKEND_UNKNOWN_PROTOCOL_RESPONSE           =160,
    AESM_PSE_PR_BACKEND_SERVER_BUSY          =161,
    AESM_PSE_PR_BACKEND_INTEGRITY_CHECK_FAIL =162,
    AESM_PSE_PR_BACKEND_INCORRECT_SYNTAX     =163,
    AESM_PSE_PR_BACKEND_INCOMPATIBLE_VERSION =164,
    AESM_PSE_PR_BACKEND_TRANSACTION_STATE_LOST              =165,
    AESM_PSE_PR_BACKEND_PROTOCOL_ERROR       =166,
    AESM_PSE_PR_BACKEND_INTERNAL_ERROR       =167,
    AESM_PSE_PR_BACKEND_UNKNOWN_GENERAL_RESPONSE            =168,
    AESM_PSE_PR_BACKEND_MSG1_GENERATE        =169,
    AESM_PSE_PR_BACKEND_MSG2_RESPONSE_HEADER_INTEGRITY      =170,
    AESM_PSE_PR_BACKEND_MSG3_GENERATE        =171,
    AESM_PSE_PR_BACKEND_MSG4_RESPONSE_HEADER_INTEGRITY      =172,
    AESM_PSE_PR_BACKEND_MSG4_TLV_INTEGRITY   =173,
    AESM_PSE_PR_BACKEND_MSG4_PLATFORM_INFO_BLOB_SIZE        =174,
    AESM_PSE_PR_BACKEND_MSG4_LEAF_CERTIFICATE_SIZE          =175,
    AESM_PSE_PR_BACKEND_MSG4_UNEXPECTED_TLV_TYPE            =176,
    AESM_PSE_PR_BACKEND_INVALID_URL          =177,
    AESM_PSE_PR_BACKEND_NOT_INITIALIZED      =178,
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

    LE_WHITE_LIST_ALREADY_UPDATED            =213,
} ae_error_t;

#define AE_FAILED(x)    (AE_SUCCESS != (x))
#define AE_SUCCEEDED(x) (AE_SUCCESS == (x))

/* These definitions are usable to exit a loop*/
#define BREAK_IF_TRUE(x, Sts, ErrCode)  if (x)    { Sts = ErrCode; break; }
#define BREAK_IF_FALSE(x, Sts, ErrCode) if (!(x)) { Sts = ErrCode; break; }
#define BREAK_IF_FAILED(x)              if (AE_SUCCESS != (x)) { break; }
#define BREAK_IF_FAILED_ERR(x, ErrCode) if (AE_SUCCESS != (x)) { x = ErrCode; break; }


#endif/*_AE_ERROR_H_*/

