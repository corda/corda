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
 * File: epid_pve_type.h
 * Description: Header file to declare common types used in pve trust/untrusted code
 */
#ifndef _EPID_PVE_TYPE_H_
#define _EPID_PVE_TYPE_H_

#include "epid/common/types.h"
#include "epid/member/api.h"
#include "se_types.h"
#include "sgx_key.h"

/*Some basic macro definition*/
#define EPID_VERSION_MAJOR        2
#define EPID_VERSION_MINOR        0
#define EPID_TYPE_GROUP_CERT      12
#define IV_SIZE                   12                     /*length in bytes of iv in block cipher. */
#define SK_SIZE                   16                     /*length in bytes of SK, which is used in block cipher info*/
#define GID_SIZE                  sizeof(GroupId)        /*length in bytes of GroupID*/
#define SK_CMAC_KEY_LEN           IppsRijndaelKey128
#define XID_SIZE                  8                      /*length in bytes of transaction id*/
#define NONCE_SIZE                8                      /*length in bytes of Nonce R in ProvMsg*/
#define NONCE_2_SIZE              16                     /*length in bytes of Nonce in ProvMsg3*/
#define CHALLENGE_NONCE_SIZE      32                     /*length in bytes of Challenge nonce in ProvMsg2*/
#define PSVN_SIZE                 sizeof(psvn_t)         /*18*/
#define FLAGS_SIZE                sizeof(flags_t)        /*256*/
#define MAC_SIZE                  16                     /*length in bytes of the tag in output of AES-GCM*/
#define JOIN_PROOF_SIZE           sizeof(JoinRequest)
#define BLIND_ESCROW_SIZE         sizeof(blind_escrow_data_t)

#define PEK_PUB                   ((uint8_t)0)
#define PEK_PRIV                  ((uint8_t)1)
#define PEK_3072_PUB              ((uint8_t)3)
#define PEK_3072_PRIV             ((uint8_t)4)
#define ECDSA_SIGN_SIZE           32                     /*This is the size of biginteger for ECDSA signature appended at the end of SIG-RL and the total signature size is size of two such kind of integer*/
#define RSA_3072_KEY_BITS         3072
#define RSA_3072_KEY_BYTES        (RSA_3072_KEY_BITS/8)
#define RSA_2048_KEY_BITS         2048
#define RSA_2048_KEY_BYTES        (RSA_2048_KEY_BITS/8)
#define PVE_RSA_SEED_SIZE         32

#define XEGB_SIZE                 456                   /*hardcoded size of extended_epid_group_blob_t*/
#define XEGB_FORMAT_ID            0x0100                /*hardcoded format id in extended_epid_group_blob to be 16bits big-endian 1*/

#pragma pack(push, 1)
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

/*Data structure without alignment required. */
typedef struct _psvn_t{
    sgx_cpu_svn_t    cpu_svn;
    sgx_isv_svn_t    isv_svn; /*PvE/QE SVN*/
}psvn_t;

/*type for the optional Flags in ProvMsg1. Currently only the 1st bit is defined for performance rekey flag*/
typedef struct _flags_t{
    uint8_t flags[16];
}flags_t;

typedef struct _bk_platform_info_t{
    sgx_cpu_svn_t    cpu_svn;
    sgx_isv_svn_t    pve_svn;
    sgx_isv_svn_t    pce_svn;
    uint16_t         pce_id;
    fmsp_t           fmsp;
}bk_platform_info_t;
/*type for EpidVersion used in Epid Data which is two bytes big endian integer*/
typedef struct _epid_version_t{
    uint8_t data[2];
}epid_version_t;

/*type for EpidType used in Epid Data which is two bytes big endian integer*/
typedef struct _epid_type_t{
    uint8_t data[2];
}epid_type_t;

/*Type for signed Epid Group Public Cert*/
typedef struct _signed_epid_group_cert_t{
    epid_version_t version;
    epid_type_t type;
    GroupPubKey key;
    uint8_t     ecdsa_signature[2*ECDSA_SIGN_SIZE];
}signed_epid_group_cert_t;

#define PEK_MOD_SIZE 384
typedef struct _signed_pek_t{
    uint8_t n[PEK_MOD_SIZE];
    uint8_t e[4];
    uint8_t sha1_ne[20];
    uint8_t pek_signature[2*ECDSA_SIGN_SIZE];
    uint8_t sha1_sign[20];
}signed_pek_t;

/*Type for Blind Escrow Data which is used in provisioning message 3 and message 4.
   The data structure is only used by PvE*/
typedef struct _blind_escrow_data_t{
    uint32_t version;
    uint8_t  iv[IV_SIZE];
    FpElemStr f;
    uint8_t  mac[MAC_SIZE];
}blind_escrow_data_t;

/*The Join Proof with Escrow data in provisioning message 3*/
typedef struct _join_proof_with_escrow_t{
    JoinRequest         jr;
    blind_escrow_data_t escrow;
}join_proof_with_escrow_t;

/*The Membership Credential with Escrow Data used in provisioning message 4*/
typedef struct _membership_credential_with_escrow_t{
    FpElemStr            x;
    G1ElemStr           A;
    blind_escrow_data_t escrow;
}membership_credential_with_escrow_t;

/*The Device ID structure used in Provisioning message*/
typedef struct _device_id_t{
    ppid_t ppid;
    psvn_t psvn;
    fmsp_t fmsp;
}device_id_t;

#define EPID_KEY_BLOB_VERSION_SIK   2
#define EPID_KEY_BLOB_VERSION_SDK   3
#define PVE_SEAL_EPID_KEY_BLOB      0

typedef struct _se_secret_epid_data_sik_t {
    PrivKey         epid_private_key;
}se_secret_epid_data_sik_t;

typedef struct _se_secret_epid_data_sdk_t{
    PrivKey         epid_private_key;  /*This field must be the first field of the structure so that offset of epid_private_key is same in both se_secret_epid_data_sik_t and se_secret_epid_data_sdk_t*/
    MemberPrecomp   member_precomp_data;
}se_secret_epid_data_sdk_t;

/*The first two fields are same for plaintext part of both EPID Blob Data*/
typedef struct _se_plaintext_epid_data_sik_t {
    uint8_t         seal_blob_type;             /*Enclave-specific Sealblob Type, currently only one Sealblob type defined: PVE_SEAL_EPID_KEY_BLOB=0*/
    uint8_t         epid_key_version;           /*epid_key_version specifies version number, should be EPID_KEY_BLOB_VERSION_SIK*/
    sgx_cpu_svn_t   equiv_cpu_svn;
    sgx_isv_svn_t   equiv_pve_isv_svn;
    Epid2Params     epid_param_cert;
    GroupPubKey     epid_group_cert;
    uint8_t         qsdk_exp[4];                /*little endian*/
    uint8_t         qsdk_mod[RSA_2048_KEY_BYTES];/*little endian*/
    uint8_t         epid_sk[2*ECDSA_SIGN_SIZE]; /*little endian*/
    uint32_t        xeid;                       /*ExtEPIDGroup ID, little endian*/
}se_plaintext_epid_data_sik_t;

typedef struct _se_plaintext_epid_data_sdk_t{
    uint8_t         seal_blob_type;             /*Enclave-specific Sealblob Type, currently only one Sealblob type defined: PVE_SEAL_EPID_KEY_BLOB=0*/
    uint8_t         epid_key_version;           /*epid_key_versione specifies version number, should be EPID_KEY_BLOB_VERSION_SDK*/
    sgx_cpu_svn_t   equiv_cpu_svn;
    sgx_isv_svn_t   equiv_pve_isv_svn;
    GroupPubKey     epid_group_cert;
    uint8_t         qsdk_exp[4];                /*little endian*/
    uint8_t         qsdk_mod[RSA_2048_KEY_BYTES];/*little endian*/
    uint8_t         epid_sk[2*ECDSA_SIGN_SIZE]; /*little endian*/
    uint32_t        xeid;                       /*ExtEPIDGroup ID, little endian*/
}se_plaintext_epid_data_sdk_t;

typedef struct _extended_epid_group_blob_t{
    uint16_t        format_id;                   /*must be 1 in big-endian*/
    uint16_t        data_length;                 /*big-endian length for fields after it but not including signature*/
    uint32_t        xeid;                        /*ExtEPIDGroup ID, little endian*/
    uint8_t         epid_sk[2*ECDSA_SIGN_SIZE];  /*ecdsa public key for EPID sign Key in little endian*/
    uint8_t         pek_sk[2*ECDSA_SIGN_SIZE];   /*ecdsa public key for PEKSK in little endian*/
    uint8_t         qsdk_exp[4];                 /*exponient of RSA key for QSDK, little endian*/
    uint8_t         qsdk_mod[RSA_2048_KEY_BYTES]; /*Modulus of RSA key for QSDK. current it is 2048 bits, little endian*/
    uint8_t         signature[2*ECDSA_SIGN_SIZE];/*ECDSA signature of the data, big endian*/
}extended_epid_group_blob_t;

#define EXTENDED_EPID_GROUP_BLOB_DATA_LEN    ((uint32_t)(sizeof(uint32_t)+4*(ECDSA_SIGN_SIZE)+4+(RSA_2048_KEY_BYTES)))

#define SGX_TRUSTED_EPID_BLOB_SIZE_SIK  ((uint32_t)(sizeof(sgx_sealed_data_t)+sizeof(se_secret_epid_data_sik_t)+sizeof(se_plaintext_epid_data_sik_t)))
#define SGX_TRUSTED_EPID_BLOB_SIZE_SDK  ((uint32_t)(sizeof(sgx_sealed_data_t)+sizeof(se_secret_epid_data_sdk_t)+sizeof(se_plaintext_epid_data_sdk_t)))
#pragma pack(pop)
#endif

