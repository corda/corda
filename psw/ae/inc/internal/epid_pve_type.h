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


/**
 * File: epid_pve_type.h
 * Description: Header file to declare common types used in pve trust/untrusted code
 */
#ifndef _EPID_PVE_TYPE_H_
#define _EPID_PVE_TYPE_H_

#include "epid_types.h"
#include "se_types.h"
#include "sgx_key.h"

/*Some basic macro definition*/
#define EPID_VERSION_MAJOR        2
#define EPID_VERSION_MINOR        0
#define EPID_TYPE_GROUP_CERT      12
#define IV_SIZE                   12                     /*length in bytes of iv in block cipher. */
#define SK_SIZE                   16                     /*length in bytes of SK, which is used in block cipher info*/
#define GID_SIZE                  sizeof(GroupID)        /*length in bytes of GroupID*/
#define SK_CMAC_KEY_LEN           IppsRijndaelKey128
#define XID_SIZE                  8                      /*length in bytes of transaction id*/
#define NONCE_SIZE                8                      /*length in bytes of Nonce R in ProvMsg*/
#define CHALLENGE_NONCE_SIZE      32                     /*length in bytes of Challenge nonce in ProvMsg2*/
#define PPID_SIZE                 sizeof(ppid_t)         /*16*/
#define PSVN_SIZE                 sizeof(psvn_t)         /*18*/
#define FMSP_SIZE                 sizeof(fmsp_t)         /*4*/
#define FLAGS_SIZE                sizeof(flags_t)        /*256*/
#define MAC_SIZE                  16                     /*length in bytes of the tag in output of AES-GCM*/
#define PSID_SIZE                 sizeof(psid_t)         /*64*/
#define JOIN_PROOF_SIZE           sizeof(JoinRequest)    
#define BLIND_ESCROW_SIZE         sizeof(blind_escrow_data_t)
#define EPID_KEY_MEMBER_SIZE      16                     /*the length in bytes of X,A,F in private key*/

#define PEK_PUB                   ((uint8_t)0)
#define PEK_PRIV                  ((uint8_t)1)
#define PWK_KEY                   ((uint8_t)2)
#define ECDSA_SIGN_SIZE           32                     /*This is the size of biginteger for ECDSA signature appended at the end of SIG-RL and the total signature size is size of two such kind of integer*/
#define PVE_RSA_KEY_BITS          2048
#define PVE_RSA_KEY_BYTES         (PVE_RSA_KEY_BITS/8)

#pragma pack(1)
/*Define some structure will be used in TLV payload. Make sure the alignment of all of them is 1 since they'll be used in an unaligned buffer
  type for Platform Provisioning Identifier, it could be calculated inside PvE*/
typedef struct _ppid_t{
    uint8_t ppid[16];
}ppid_t;

typedef struct _fmsp_t{
    uint8_t fmsp[4];
}fmsp_t;

/*type for provisioning server Identifier, the hash of provisioning server public key*/
typedef struct _psid_t{
    uint8_t psid[32];
}psid_t;

/*type for Platform Security Version Numbers. Data structure without alignment required. */
typedef struct _psvn_t{
    sgx_cpu_svn_t    cpu_svn;
    sgx_isv_svn_t    isv_svn; //PvE SVN
}psvn_t;

/*type for the optional Flags in ProvMsg1. Currently only the 1st bit is defined for performance rekey flag*/
typedef struct _flags_t{
    uint8_t flags[16];
}flags_t;

/*type for EpidVersion used in Epid Data which is two bytes big endian integer*/
typedef struct _epid_version_t{
    uint8_t data[2];
}epid_version_t;

/*type for EpidType used in Epid Data which is two bytes big endian integer*/
typedef struct _epid_type_t{
    uint8_t data[2];
}epid_type_t;

/*Type for Epid Group Public Cert*/
typedef struct _signed_epid_group_cert_t{
    epid_version_t version;
    epid_type_t type;
    GroupPubKey key;
    uint8_t    intel_signature[2*ECDSA_SIGN_SIZE];
}signed_epid_group_cert_t;

typedef struct _signed_pek_t{
    uint8_t n[256];
    uint8_t e[4];
    uint8_t sha1_ne[20];
    uint8_t pek_signature[2*ECDSA_SIGN_SIZE];
    uint8_t sha1_sign[20];
}signed_pek_t;

/*Type for an Blind Escrow Data which is used in provisioning message 3 and message 4.
   The data structure is only used by PvE*/
typedef struct _blind_escrow_data_t{
    uint32_t version;
    uint8_t iv[IV_SIZE];
    PElemStr f;
    uint8_t mac[MAC_SIZE];
}blind_escrow_data_t;

/*The Join Proof with Escrow data in provisioning message 3*/
typedef struct _join_proof_with_escrow_t{
    JoinRequest jr;
    blind_escrow_data_t escrow;
}join_proof_with_escrow_t;

/*The Membership Credential with Escrow Data used in provisioning message 4*/
typedef struct _membertship_credential_with_escrow_t{
    PElemStr x;
    G1ElemStr A;
    blind_escrow_data_t escrow;
}membertship_credential_with_escrow_t;

/*The Device ID structure used in Provisioning message*/
typedef struct _device_id_t{
    ppid_t ppid;
    psvn_t psvn;
    fmsp_t fmsp;
}device_id_t;

#define EPID_KEY_BLOB_VERSION     1
#define PVE_SEAL_EPID_KEY_BLOB    0

#pragma pack(push, 1)
typedef struct _se_secret_epid_data_t {
    PrivKey         epid_private_key;
}se_secret_epid_data_t;

typedef struct _se_plaintext_epid_data_t {
    uint8_t          seal_blob_type; /*Encalve-specific Sealblob Type, for 2015 PvE/QE, only one Sealblob type defined: PVE_SEAL_EPID_KEY_BLOB=0*/
    uint8_t          epid_key_version;/*epid key version should be EPID_KEY_BLOB_VERSION=1*/
    sgx_cpu_svn_t    equiv_cpu_svn;
    sgx_isv_svn_t    equiv_isv_svn;
    EPID2Params      epid_param_cert;
    GroupPubKey      epid_group_cert;
}se_plaintext_epid_data_t;

#pragma pack(pop)

#define SGX_TRUSTED_EPID_BLOB_SIZE  (sgx_calc_sealed_data_size( \
                             sizeof(se_plaintext_epid_data_t),   \
                             sizeof(se_secret_epid_data_t)))

#pragma pack()
#endif

