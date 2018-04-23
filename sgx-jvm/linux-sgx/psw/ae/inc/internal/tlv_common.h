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


/**
 * File: tlv_common.h
 * Description: Header file to define TLV (the Type Length Value) related data or structure which may be commonly used by multiple components
 */

#ifndef _PVE_TLV_COMMON_H
#define _PVE_TLV_COMMON_H
#include "se_cdefs.h"
#include "se_types.h"
#include "sgx_key.h"
#include "byte_order.h"

/*enumerate all tlv types, the value of it is not defined in spec yet*/
typedef enum _tlv_enum_type_t{
    TLV_CIPHER_TEXT=0,
    TLV_BLOCK_CIPHER_TEXT,
    TLV_BLOCK_CIPHER_INFO,
    TLV_MESSAGE_AUTHENTICATION_CODE,
    TLV_NONCE,
    TLV_EPID_GID,
    TLV_EPID_SIG_RL,
    TLV_EPID_GROUP_CERT,
    /*SE Provisioning Protocol TLVs*/
    TLV_DEVICE_ID,
    TLV_PS_ID,
    TLV_EPID_JOIN_PROOF,
    TLV_EPID_SIG,
    TLV_EPID_MEMBERSHIP_CREDENTIAL,
    TLV_EPID_PSVN,
    /*PSE Provisioning Protocol TLVs*/
    TLV_QUOTE,
    TLV_X509_CERT_TLV,
    TLV_X509_CSR_TLV,
    /*End-point Selection Protocol TLVs*/
    TLV_ES_SELECTOR,
    TLV_ES_INFORMATION,
    /* EPID Provisioning Protocol TLVs Part 2*/
    TLV_FLAGS,
    /* PSE Quote Signature*/
    TLV_QUOTE_SIG,
    TLV_PLATFORM_INFO_BLOB,
    /* Generic TLVs*/
    TLV_SIGNATURE,
    /* End-point Selection Protocol TLVs*/
    TLV_PEK,
    TLV_PLATFORM_INFO,
    TLV_PWK2,
    TLV_SE_REPORT
}tlv_enum_type_t;

/*here comes general type and macro definition for AESM related Server URL which will be shared by code in other components*/
typedef enum _aesm_network_server_enum_type_t{
    SE_EPID_PROVISIONING,
    PSE_PROVISIONING,
    ENDPOINT_SELECTION,
	REVOCATION_LIST_RETRIEVAL,
    PSE_OCSP,
    SGX_WHITE_LIST_FILE
}aesm_network_server_enum_type_t;

typedef enum _pve_msg_type_t
{
     TYPE_PROV_MSG1,
     TYPE_PROV_MSG2,
     TYPE_PROV_MSG3,
     TYPE_PROV_MSG4
}pve_msg_type_t;

typedef enum _pse_msg_type_t
{
     TYPE_PSE_MSG1,
     TYPE_PSE_MSG2,
     TYPE_PSE_MSG3,
     TYPE_PSE_MSG4
}pse_msg_type_t;

typedef enum _es_msg_type_t
{
    TYPE_ES_MSG1,
    TYPE_ES_MSG2
}es_msg_type_t;

typedef enum _rlr_msg_type_t
{
    TYPE_RLR_MSG1,
    TYPE_RLR_MSG2
}rlr_msg_type_t;

#include "epid_pve_type.h"

typedef uint16_t general_response_status_t;
enum _general_response_status_t
{
    GRS_OK, 
    GRS_SERVER_BUSY, 
    GRS_INTEGRITY_CHECK_FAIL,
    GRS_INCORRECT_SYNTAX,
    GRS_INCOMPATIBLE_VERSION, 
    GRS_TRANSACTION_STATE_LOST,
    GRS_PROTOCOL_ERROR, 
    GRS_INTERNAL_ERROR
};

typedef uint16_t se_protocol_response_status_t;

enum _se_protocol_response_status_t
{
    SE_PRS_OK,
    SE_PRS_PLATFORM_REVOKED, 
    SE_PRS_STATUS_INTEGRITY_FAILED,
    SE_PRS_PERFORMANCE_REKEY_NOT_SUPPORTED,
    SE_PRS_PROVISIONING_ERROR,
    SE_PRS_INVALID_REQUEST,
    SE_PRS_PROV_ATTEST_KEY_NOT_FOUND,   
    SE_PRS_INVALID_REPORT   
};

typedef uint16_t pse_protocol_response_status_t;
enum _pse_protocol_response_status_t
{
    PSE_PRS_OK, 
    PSE_PRS_INVALID_GID,
    PSE_PRS_GID_REVOKED,
    PSE_PRS_INVALID_QUOTE,
    PSE_PRS_INVALID_REQUEST
};


#pragma pack(1)
#define NET_S_OK 0
typedef struct _provision_request_header_t{
    uint8_t protocol;
    uint8_t version;
    uint8_t xid[XID_SIZE];    /*transaction id, the unique id from ProvMsg1 to ProvMsg4*/
    uint8_t type;
    uint8_t size[4];          /*size of request body*/
}provision_request_header_t;

typedef struct _provision_response_header_t{
    uint8_t protocol;
    uint8_t version;
    uint8_t xid[XID_SIZE];
    uint8_t type;
    uint8_t gstatus[2];       
    uint8_t pstatus[2];
    uint8_t size[4];
}provision_response_header_t;

#pragma pack()

#define PROVISION_REQUEST_HEADER_SIZE sizeof(provision_request_header_t)
#define PROVISION_RESPONSE_HEADER_SIZE sizeof(provision_response_header_t)
#define GET_BODY_SIZE_FROM_PROVISION_REQUEST(req)    lv_ntohl(((const provision_request_header_t *)(req))->size)
#define GET_BODY_SIZE_FROM_PROVISION_RESPONSE(resp)  lv_ntohl(((const provision_response_header_t *)(resp))->size)
#define GET_SIZE_FROM_PROVISION_REQUEST(req)    (GET_BODY_SIZE_FROM_PROVISION_REQUEST(req)+PROVISION_REQUEST_HEADER_SIZE)
#define GET_SIZE_FROM_PROVISION_RESPONSE(resp)  (GET_BODY_SIZE_FROM_PROVISION_RESPONSE(resp)+PROVISION_RESPONSE_HEADER_SIZE)
#define GET_TYPE_FROM_PROVISION_REQUEST(req)    (((const provision_request_header_t *)(req))->type)
#define GET_TYPE_FROM_PROVISION_RESPONSE(resp)  (((const provision_response_header_t *)(resp))->type)

#define TLV_VERSION_1   1
#define TLV_VERSION_2   2

#endif

