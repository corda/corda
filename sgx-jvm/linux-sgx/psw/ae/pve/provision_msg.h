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
  * File: provision_msg.h
  * Description: Definition for data structure and provision protocol
  *
  * Definition for data structure and provision protocol
  */

#ifndef _PVE_MSG_H_
#define _PVE_MSG_H_

#include "epid_pve_type.h"
#include "sgx_tseal.h"
#include "sgx_report.h"

/*error code definition*/
typedef enum _pve_status_t
{
     PVEC_SUCCESS = 0,
     PVEC_PARAMETER_ERROR,
     PVEC_INSUFFICIENT_MEMORY_ERROR,
     PVEC_READ_RAND_ERROR,
     PVEC_SIGRL_INTEGRITY_CHECK_ERROR,
     PVEC_MALLOC_ERROR,
     PVEC_EPID_BLOB_ERROR,
     PVEC_SE_ERROR,
     PVEC_IPP_ERROR,
     PVEC_MSG_ERROR,
     PVEC_PEK_SIGN_ERROR,
     PVEC_XEGDSK_SIGN_ERROR,
     PVEC_INTEGER_OVERFLOW_ERROR,
     PVEC_SEAL_ERROR,
     PVEC_EPID_ERROR,
     PVEC_REVOKED_ERROR,
     PVEC_UNSUPPORTED_VERSION_ERROR,
     PVEC_INVALID_CPU_ISV_SVN,
     PVEC_INVALID_EPID_KEY,
     PVEC_UNEXPECTED_ERROR            /*unknown error which should never happen, it indicates there're internal logical error in PvE's code*/
}pve_status_t;

/*State inside PvE*/
typedef enum _prov_stage_t
{
     PVE_STAGE_IDLE,                  /*waiting for ProvMsg1*/
     PVE_STAGE_WAIT_FOR_GET_EK2,      /*waiting for get ek2 after processing msg1*/
     PVE_STAGE_WAIT_FOR_MSG2_OR_MSG4, /*waiting for ProvMsg2 or ProvMsg4 after getting ek2*/
     PVE_STAGE_WAIT_FOR_MSG4,         /*waiting for ProvMsg4 only*/
}prov_stage_t;

/*macro definition for RSA-OAEP algorithm
  SHA-256 will be used for the hash generation*/
#define PVE_RSAOAEP_ENCRYPT_MAXLEN (RSA_3072_KEY_BYTES - 2*SHA_SIZE_BIT/8 - 2) /*190 bytes at most*/

#define SHA_SIZE_BIT  256

#define pointer_diff_u32(p1, p2) static_cast<uint32_t>(p1-p2)
#pragma pack(1)

/*input information for PvE to decode data from ProvMsg2*/
typedef struct _proc_prov_msg2_blob_input_t{
    signed_epid_group_cert_t   group_cert;         /*ECDSA signed EPID Group Public Certificate decoded from ProvMsg2*/
    extended_epid_group_blob_t xegb;
    signed_pek_t               pek;
    sgx_target_info_t          pce_target_info;
    uint8_t challenge_nonce[CHALLENGE_NONCE_SIZE]; /*The challenge nonce from ProvMsg2*/
    bk_platform_info_t         equiv_pi;           /*The Equivalent platform_info*/
    bk_platform_info_t         previous_pi;        /*an optional platform_info for Sigrl correpondent to previous EPID (if we upgrade TCB or performance rekey)*/
    GroupId                    previous_gid;       /*optional previous_gid if previous_psvn is provided*/
    uint8_t old_epid_data_blob[SGX_TRUSTED_EPID_BLOB_SIZE_SDK]; /*optional old epid data blob correpondent to previous EPID*/
    uint8_t                    is_previous_pi_provided;         /*both previous_platform_info and old_epid_data_blob should be provided if it is true and prev gid must be provided too*/
}proc_prov_msg2_blob_input_t;

#define HARD_CODED_JOIN_PROOF_WITH_ESCROW_TLV_SIZE (4+sizeof(join_proof_with_escrow_t))
#define HARD_CODED_EPID_MEMBER_WITH_ESCROW_TLV_SIZE (4+sizeof(membership_credential_with_escrow_t))

/*output information from PvE for AESM to generate ProvMsg3*/
typedef struct _gen_prov_msg3_output_t{
    uint8_t field1_iv[IV_SIZE];    /*The random generated IV for aes-gcm encryption of join proof and escrow data*/
    uint8_t field1_data[HARD_CODED_JOIN_PROOF_WITH_ESCROW_TLV_SIZE]; /*The encrypted join proof and escrow data TLV by aes-gcm*/
    uint8_t field1_mac[MAC_SIZE];  /*The corresponding mac value of previous encrypted data*/
    uint8_t n2[NONCE_2_SIZE];
    uint8_t epid_sig_iv[IV_SIZE];  /*The random generated IV for aes-gcm encryption of EPIDSignature if available*/
    uint8_t epid_sig_mac[MAC_SIZE];/*The corresponding mac value for encrypted EPIDSignature if available*/
    uint8_t encrypted_pwk2[PEK_MOD_SIZE];
    sgx_report_t pwk2_report;
    uint32_t epid_sig_output_size; /*The size of EPIDSignature if available*/
    uint8_t is_join_proof_generated;/*boolean value to tell whether join pro[of and escrow data is generated. The first three fields in this structure will be invalid if this field is false*/
    uint8_t is_epid_sig_generated;  /*boolean value to tell whether EpidSignature is generated*/
                                    /*If it is false, the epid_sig_iv/mac/output_size are all invalid*/
}gen_prov_msg3_output_t;

/*input information for PvE to decode data from ProvMsg4 and generate EPID Data Blob*/
typedef struct _proc_prov_msg4_input_t{
    extended_epid_group_blob_t xegb;
    uint8_t member_credential_iv[IV_SIZE];  /*The random IV to decode member credential and escrow data TLV*/
    uint8_t encrypted_member_credential[HARD_CODED_EPID_MEMBER_WITH_ESCROW_TLV_SIZE]; /*The encrypted member credential and escrow data TLV by aes-gcm*/
    uint8_t member_credential_mac[MAC_SIZE];/*The mac value of previous field*/
    uint8_t n2[NONCE_2_SIZE];
    psvn_t equivalent_psvn;                 /*An equivalent PSVN including ISVN and equivalent CPUSVN*/
    fmsp_t fmsp;                            /*The fmsp from provisioning backend server*/
    signed_epid_group_cert_t group_cert;    /*ECDSA signed EPID Group Public Certificate from Intel decoded from ProvMsg4*/
}proc_prov_msg4_input_t;

/*The EPID Data Blob generated by PvE in processing ProvMsg4 data*/
typedef struct _proc_prov_msg4_output_t{
    uint8_t truested_epid_blob[SGX_TRUSTED_EPID_BLOB_SIZE_SDK];
}proc_prov_msg4_output_t;

/*output data of PvE to generate End Point Selection TLV*/
typedef struct _gen_endpoint_selection_output_t{
    uint8_t xid[XID_SIZE];
    uint8_t selector_id;
}gen_endpoint_selection_output_t;

#pragma pack()

#define PSVN_START_IN_DEVICE_ID sizeof(ppid_t)
#define PPID_START_IN_DEVICE_ID 0
#define FMSP_START_IN_DEVICE_ID (sizeof(ppid_t)+sizeof(psvn_t))
#endif
