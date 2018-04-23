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


#ifndef _PSE_TYPES_H_
#define _PSE_TYPES_H_

#include "sgx_ecp_types.h"
#include "sgx_report.h"

#define SAFE_FREE(ptr)     {if (NULL != (ptr)) {free(ptr); (ptr)=NULL;}}

#define EPH_SESSION_NONCE_SIZE          16
#define EPH_MESSAGE_MAC_SIZE            16
#define CSE_ID_SIZE                     32
#define EPH_SESSION_TSK_SIZE            16
#define EPH_SESSION_TMK_SIZE            16
#define SW_INSTANCE_ID_SIZE             16

#define DERIVE_MAC_KEY      0x0
#define DERIVE_SEAL_KEY     0x1

#pragma pack(push, 1)

typedef struct _pse_dh_msg1_t
{
    sgx_ec256_public_t        dh_ga;  /* the Endian-ness of Ga is Little-Endian*/
    sgx_target_info_t   pse_info;
}pse_dh_msg1_t;

typedef struct _cse_sec_prop_t
{
    uint32_t        sec_info_type; /* MBZ */
    uint32_t        gid_cse;       /* from PSE-CSE pairing blob */
    uint32_t        prvrl_version; /* from PSE-CSE pairing blob */
    uint32_t        sigrl_version; /* from PSE-CSE pairing blob */
    uint8_t         ca_id_cse[20]; /* from PSE-CSE pairing blob */ 
    uint8_t         sec_info[92];  /* from PSE-CSE pairing blob */
}cse_sec_prop_t;

typedef struct _secu_info{
    uint32_t jom_task_id;    /* must be the hardcoded value - 8 */
    uint32_t reserved;       /* MBZ */
    uint32_t psda_id;        /* must be hardcoded value - 1 */
    uint32_t psda_svn;       /* from PSE-CSE pairing blob */
    uint8_t  reserved2[76];  /* MBZ */
}secu_info_t;

typedef struct _pse_dh_msg3_t
{
    uint8_t        cmac[EPH_MESSAGE_MAC_SIZE];
    sgx_report_t   report;
    uint32_t       additional_prop_length;
    cse_sec_prop_t cse_sec_prop;
}pse_dh_msg3_t;

/***********************\
**message handling data**
\***********************/
#define PSE_TRUSTED_TIME_SERVICE  0
#define PSE_MC_SERVICE            1
/*monotonic counter*/
#define PSE_MC_CREATE             0
#define PSE_MC_READ               1
#define PSE_MC_INC                2
#define PSE_MC_DEL                3
/*trusted time*/
#define PSE_TIMER_READ            0


/*VMC creation policy*/
#define MC_POLICY_SIGNER  0x1
#define MC_POLICY_ENCLAVE 0x2

#define PAYLOAD_IV_SIZE   12
typedef struct _pse_message_t
{
    uint32_t session_id;
    uint32_t exp_resp_size;                      /* 0: response message*/
    uint8_t  payload_iv[PAYLOAD_IV_SIZE];
    uint32_t payload_size;
    uint8_t  payload_tag[SGX_AESGCM_MAC_SIZE];   /* 16: AES-GMAC of the Plain Text, Payload, and the sizes*/
    uint8_t  payload[0];                         /* encrypted payload*/
}pse_message_t;

typedef enum _pse_op_error_t
{
    OP_SUCCESS = 0,
    OP_ERROR_INTERNAL,                          /*  Internal errors */
    OP_ERROR_INVALID_PARAMETER,                 /*  Invalid input parameter */
    OP_ERROR_MALLOC,                            /*  malloc() fails */
    OP_ERROR_UNKNOWN_REQUEST,                   /*  Unknown request sent to CSE  */
    OP_ERROR_CAP_NOT_AVAILABLE,                 /*  The required service is not available  */
    OP_ERROR_MAX_NUM_SESSION_REACHED,           /*  All session slots are in use and
                                                    the least active session does not exceed predefined time  */
    OP_ERROR_INVALID_SESSION,                   /*  Create APP Enclave - PSE session failed
                                                    or the session indicated by SID is invalid  */
    OP_ERROR_DATABASE_FULL,                     /*  No empty vmc nodes left in VMC DB  */
    OP_ERROR_DATABASE_OVER_QUOTA,               /*  The quota for the MRSIGNER is exceeded  */
    OP_ERROR_INVALID_EPH_SESSION,               /*  Ephemeral session is not valid or sequence no overflows.
                                                    Need do repairing. */
    OP_ERROR_PSDA_SESSION_LOST,                 /*  CSME session is lost during OCALL  */
    OP_ERROR_PSDA_BUSY,                         /*  CSME is busy  */
    OP_ERROR_EPH_SESSION_ESTABLISHMENT_INTEGRITY_ERROR,         /*  Integrity error of ephemeral session message  */
    OP_ERROR_SQLITE_INTERNAL,                   /*  SQLite internal errors  */
    OP_ERROR_COPY_PREBUILD_DB,                  /*  Copy of prebuilt DB failed  */
    OP_ERROR_BACKUP_CURRENT_DB,                 /*  Copy of current DB failed  */
    OP_ERROR_INVALID_HW_MC,                     /*  CC_MC > WR_MC  or  WR_MC > CC_MC + 2  */
    OP_ERROR_INVALID_COUNTER,                   /*  The VMC counter ID passed in is not valid  */
    OP_ERROR_INVALID_OWNER,                     /*  owner ID or isv_svn doesn't match  */
    OP_ERROR_UNSEAL_PAIRING_BLOB,               /*  Unsealing LT pairing blob failed  */
    OP_ERROR_INVALID_POLICY,                    /*  Invalid owner policy  */
    OP_ERROR_INVALID_PAIRING_BLOB,              /*  LT pairing blob is invalid  */

    /* Errors for internal use. Won't be returned to AESM*/
    OP_ERROR_INVALID_VMC_DB,                    /*  Verification of VMC DB failed. Should re-initialize DB  */
    OP_ERROR_DATABASE_FATAL,                    /*  Fatal error returned when opening VMC DB, Should re-initialize DB  */
    OP_ERROR_SQLITE_NOT_FOUND,                  /*  Record not found. */
    OP_ERROR_CACHE_MISS,                        /*  The related nodes of a leaf node are not cached */
    OP_ERROR_KDF_MISMATCH,                      /*  Key derivation function doesn't match during exchange report */
    OP_ERROR_LTPB_SEALING_OUT_OF_DATE,          /*  The ISV SVN in the LTP blob doesn't match PSE ISV SVN */
}pse_op_error_t;

typedef enum _pse_service_resp_status_t
{
    PSE_SUCCESS = 0,
    PSE_ERROR_UNKNOWN_REQ,
    PSE_ERROR_CAP_NOT_AVAILABLE,
    PSE_ERROR_INVALID_PARAM,
    PSE_ERROR_BUSY,
    PSE_ERROR_INTERNAL,
    PSE_ERROR_INVALID_POLICY,
    PSE_ERROR_QUOTA_EXCEEDED,
    PSE_ERROR_MC_NOT_FOUND,
    PSE_ERROR_MC_NO_ACCESS_RIGHT,
    PSE_ERROR_MC_USED_UP,
    PSE_ERROR_MC_OVER_QUOTA
} pse_service_resp_status_t;

typedef struct _pse_req_hdr_t
{
    uint32_t seq_num;
    uint16_t service_id;
    uint16_t service_cmd;
}pse_req_hdr_t;

typedef struct _pse_resp_hdr_t
{
    uint32_t seq_num;
    uint16_t service_id;
    uint16_t service_cmd;
    pse_service_resp_status_t status;
}pse_resp_hdr_t;

typedef struct _pse_mc_create_req_t
{
    pse_req_hdr_t req_hdr;
    uint16_t policy;
    uint8_t attr_mask[16];
}pse_mc_create_req_t;

typedef struct _pse_mc_create_resp_t
{
    pse_resp_hdr_t resp_hdr;
    uint8_t counter_id[3];
    uint8_t nonce[13];
}pse_mc_create_resp_t;

typedef struct _pse_mc_read_req_t
{
    pse_req_hdr_t req_hdr;
    uint8_t counter_id[3];
    uint8_t nonce[13];
}pse_mc_read_req_t;

typedef struct _pse_mc_inc_req_t
{
    pse_req_hdr_t req_hdr;
    uint8_t counter_id[3];
    uint8_t nonce[13];
}pse_mc_inc_req_t;

typedef struct _pse_mc_del_req_t
{
    pse_req_hdr_t req_hdr;
    uint8_t counter_id[3];
    uint8_t nonce[13];
}pse_mc_del_req_t;

typedef struct _pse_mc_read_resp_t
{
    pse_resp_hdr_t resp_hdr;
    uint32_t  counter_value;
    uint16_t  pse_svn;
}pse_mc_read_resp_t;

typedef struct _pse_mc_inc_resp_t
{
    pse_resp_hdr_t resp_hdr;
    uint32_t  counter_value;
    uint16_t  pse_svn;
}pse_mc_inc_resp_t;

typedef struct _pse_mc_del_resp_t
{
    pse_resp_hdr_t resp_hdr;
}pse_mc_del_resp_t;

typedef struct _pse_timer_read_req_t
{
    pse_req_hdr_t req_hdr;
}pse_timer_read_req_t;

typedef struct _pse_timer_read_resp_t
{
    pse_resp_hdr_t resp_hdr;
    uint64_t timestamp;
    uint8_t time_source_nonce[32];
}pse_timer_read_resp_t;

/*message length*/
#define PSE_CREATE_MC_REQ_SIZE      static_cast<uint32_t>(sizeof(pse_message_t) + sizeof(pse_mc_create_req_t))
#define PSE_CREATE_MC_RESP_SIZE     static_cast<uint32_t>(sizeof(pse_message_t) + sizeof(pse_mc_create_resp_t))
#define PSE_READ_MC_REQ_SIZE        static_cast<uint32_t>(sizeof(pse_message_t) + sizeof(pse_mc_read_req_t))
#define PSE_READ_MC_RESP_SIZE       static_cast<uint32_t>(sizeof(pse_message_t) + sizeof(pse_mc_read_resp_t))
#define PSE_INC_MC_REQ_SIZE         static_cast<uint32_t>(sizeof(pse_message_t) + sizeof(pse_mc_inc_req_t))
#define PSE_INC_MC_RESP_SIZE        static_cast<uint32_t>(sizeof(pse_message_t) + sizeof(pse_mc_inc_resp_t))
#define PSE_DEL_MC_REQ_SIZE         static_cast<uint32_t>(sizeof(pse_message_t) + sizeof(pse_mc_del_req_t))
#define PSE_DEL_MC_RESP_SIZE        static_cast<uint32_t>(sizeof(pse_message_t) + sizeof(pse_mc_del_resp_t))
#define PSE_TIMER_READ_REQ_SIZE     static_cast<uint32_t>(sizeof(pse_message_t) + sizeof(pse_timer_read_req_t))
#define PSE_TIMER_READ_RESP_SIZE    static_cast<uint32_t>(sizeof(pse_message_t) + sizeof(pse_timer_read_resp_t))

/*********************************************\
** Define macros for CSE session and messages**
\*********************************************/
#define PSDA_API_VERSION            1
#define BE_PSDA_API_VERSION         0x01000000

#define PSDA_COMMAND_INFO           0
#define PSDA_COMMAND_EP             2
#define PSDA_COMMAND_SERVICE        3

#define PSDA_MSG_TYPE_CAP_QUERY     0
#define PSDA_MSG_TYPE_CAP_RESULT    1
#define PSDA_MSG_TYPE_CERT_INFO_QUERY       2
#define PSDA_MSG_TYPE_CERT_INFO_RESULT      3
#define PSDA_MSG_TYPE_LT_M1         0
#define PSDA_MSG_TYPE_LT_M2         1
#define PSDA_MSG_TYPE_LT_M7         2
#define PSDA_MSG_TYPE_LT_M8         3
#define PSDA_MSG_TYPE_EP_M1         0
#define PSDA_MSG_TYPE_EP_M2         1
#define PSDA_MSG_TYPE_EP_M3         2
#define PSDA_MSG_TYPE_EP_M4         3
#define PSDA_MSG_TYPE_SERV_REQ      0
#define PSDA_MSG_TYPE_SERV_RESP     1
#define BE_PSDA_MSG_TYPE_SERV_REQ   0x00000000
#define BE_PSDA_MSG_TYPE_SERV_RESP  0x01000000

typedef struct _psda_msg_hdr_t
{
    uint8_t             pse_instance_id[SW_INSTANCE_ID_SIZE];
    uint32_t            msg_type;
    uint32_t            msg_len;
}psda_msg_hdr_t;

typedef struct _psda_info_query_msg_t
{
    psda_msg_hdr_t msg_hdr;
}psda_info_query_msg_t;

typedef struct _psda_cap_result_msg_t
{
    psda_msg_hdr_t msg_hdr;
    uint32_t       cap_descriptor_version;
    uint32_t       cap_descriptor0;
    uint32_t       cap_descriptor1;
}psda_cap_result_msg_t;

typedef struct _psda_cert_result_msg_t
{
    psda_msg_hdr_t msg_hdr;
    uint8_t        cert_info[24];
}psda_cert_result_msg_t;

/* messages used for pse-cse ephemeral session establishment */
typedef struct _pse_cse_msg2_t
{
    uint8_t     id_cse[CSE_ID_SIZE];
    uint8_t     nonce_r_cse[EPH_SESSION_NONCE_SIZE];
}pse_cse_msg2_t;

typedef struct _pse_cse_msg3_t
{
    uint8_t     id_pse[CSE_ID_SIZE];
    uint8_t     id_cse[CSE_ID_SIZE];
    uint8_t     nonce_r_cse[EPH_SESSION_NONCE_SIZE];
    uint8_t     nonce_r_pse[EPH_SESSION_NONCE_SIZE];
    uint8_t     mac[SGX_SHA256_HASH_SIZE];
}pse_cse_msg3_t;

typedef struct _pse_cse_msg4_t
{
    uint8_t     id_cse[CSE_ID_SIZE];
    uint8_t     nonce_r_pse[EPH_SESSION_NONCE_SIZE];
    uint8_t     mac[SGX_SHA256_HASH_SIZE];
}pse_cse_msg4_t;

typedef struct _eph_session_m1_t
{
    psda_msg_hdr_t msg_hdr;
}eph_session_m1_t;

typedef struct _eph_session_m2_t
{
    psda_msg_hdr_t msg_hdr;
    pse_cse_msg2_t msg2;
}eph_session_m2_t;

typedef struct _eph_session_m3_t
{
    psda_msg_hdr_t msg_hdr;
    pse_cse_msg3_t msg3;
}eph_session_m3_t;

typedef struct _eph_session_m4_t
{
    psda_msg_hdr_t msg_hdr;
    pse_cse_msg4_t msg4;
}eph_session_m4_t;

/*********************************\
**PSDA service message definition**
\*********************************/
#define PSDA_MC_READ    1                       /* Read MC command*/
#define PSDA_MC_INC     2                       /* Incroment MC command*/
#define PSDA_MESSAGE_IV_SIZE  16                /* IV size*/
#define PSDA_MESSAGE_MAC_SIZE  32               /* MAC size*/


#define SGX_RPDATA_SIZE 16                      /* RPDATA size*/

typedef struct _service_message_t
{
    uint32_t version;
    uint32_t session_id;
    uint32_t msg_type_exp_resp_size;
    uint32_t payload_size;
    uint8_t  payload_iv[PSDA_MESSAGE_IV_SIZE];
    uint8_t  payload_mac[PSDA_MESSAGE_MAC_SIZE];
    uint8_t  payload[0];                        /*encrypted payload*/
}service_message_t;

typedef struct _psda_service_message_t
{
    psda_msg_hdr_t msg_hdr;
    service_message_t service_message;
}psda_service_message_t;

typedef struct _psda_req_hdr_t
{
    uint32_t seqnum;
    uint16_t service_id;
    uint16_t service_cmd;
}psda_req_hdr_t;

typedef struct _psda_resp_hdr_t
{
    uint32_t seqnum;
    uint16_t service_id;
    uint16_t service_cmd;
    uint32_t status;
}psda_resp_hdr_t;

typedef struct _cse_mc_read_req_t
{
    psda_req_hdr_t req_hdr;
    uint8_t  counter_id;
}cse_mc_read_req_t;

typedef struct _cse_mc_inc_req_t
{
    psda_req_hdr_t req_hdr;
    uint8_t  counter_id;
    uint8_t  increase_amount;
}cse_mc_inc_req_t;

typedef struct _cse_mc_resp_t
{
    psda_resp_hdr_t resp_hdr;
    uint32_t counter_value;
    uint32_t mc_epoch;
}cse_mc_resp_t;

typedef struct _cse_rpdata_read_req_t
{
    psda_req_hdr_t req_hdr;
}cse_rpdata_read_req_t;

typedef struct _cse_rpdata_update_req_t
{
    psda_req_hdr_t req_hdr;
    uint8_t rpdata_cur[SGX_RPDATA_SIZE];
    uint8_t rpdata_new[SGX_RPDATA_SIZE];
}cse_rpdata_update_req_t;

typedef struct _cse_rpdata_reset_req_t
{
    psda_req_hdr_t req_hdr;
    uint8_t rpdata_cur[SGX_RPDATA_SIZE];
}cse_rpdata_reset_req_t;

typedef struct _cse_rpdata_resp_t
{
    psda_resp_hdr_t resp_hdr;
    uint8_t rpdata[SGX_RPDATA_SIZE];
    uint32_t rp_epoch;
}cse_rpdata_resp_t;

typedef struct _cse_timer_read_req_t
{
    psda_req_hdr_t req_hdr;
}cse_timer_read_req_t;

typedef struct _cse_timer_read_resp_t
{
    psda_resp_hdr_t resp_hdr;
    uint64_t timestamp;
    uint32_t epoch;
}cse_timer_read_resp_t;

/* Because PSDA requires buffer size to be a multiple of AES_BLOCK_SIZE, add an extra AES_BLOCK_SIZE here
 * to make sure response message can be stored in the buffer*/
#define AES_BLOCK_SIZE 16

#pragma pack(pop)
#endif

