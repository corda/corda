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
@file   SigmaCommonDefs.h 
@author Kapil Anantahraman
@brief  This file contains all the defines and data structures that are common for Sigma 1.0 and Sigma 1.1
*/

#ifndef PSE_PR_SIGMA_COMMON_DEFS_H
#define PSE_PR_SIGMA_COMMON_DEFS_H

#include "pse_pr_types.h"

//
// both of these symbols are used in untrusted, pse cert provisioning code
// the first, LEAFTOROOT, controls the order of the certs and of the ocsp responses
// in the sigma s2 message. for sgx, as of now, LEAFTOROOT true gives leaf, leaf-1, leaf-2
// LEAFTOROOT false gives leaf-2, leaf-1, leaf. LEAFTOROOT false is necessary, at least for the 
// certs. i don't think the order of ocsp responses matters.
// MORE_PADDING determines how the S2 message is padded
// MORE_PADDING false gives
// Root+1 | root+2 | root+3 | padding to next dword
// MORE_PADDING true gives
// Root+1 | padding to next dword | root+2 | padding to next dword | root+3 | padding to next dword
// we need true for the LPT emulator customized for SGX
// we need false for SPT
//
#define LEAFTOROOT		0

#include "pse_pr_padding.hh"

#define NEXT_DWORD_BOUNDARY(x)    ((x + 3) & ~3)
#define NEXT_16_BYTE_BOUNDARY(x)  ((x + 15) & ~15) 
#define REQUIRED_PADDING_DWORD_ALIGNMENT(x) ((x % 4) ? (4 - (x%4)) : 0)

//#define ECDSA_SECKEY_LENGTH            32

#define ECDSA_PUBKEY_LENGTH            64
#define ECDSA_SIG_LENGTH               64

#define SIGMA_SESSION_PRIVKEY_LENGTH   32
#define SIGMA_SESSION_PUBKEY_LENGTH    64
#define SIGMA_HMAC_LENGTH              32
#define SIGMA_HMAC_SHA256_HASH_LENGTH  32
#define SIGMA_SK_LENGTH                16
#define SIGMA_MK_LENGTH                16

#define SIGMA_IV_LENGTH                16

#define SIGMA_SMK_LENGTH               SIGMA_HMAC_SHA256_HASH_LENGTH

#define SIGMA_SESSION_STATE_LENGTH     (32+64+2*64+SIGMA_SK_LENGTH+SIGMA_MK_LENGTH) //256bytes

#define EPID_SIG_LEN                   569
#define SIGMA_NONCE_LENGTH             32
#define SIGMA_BASENAME_LENGTH          32 
#define SIG_RL_HEADER_SIZE          sizeof(UINT32) + sizeof(UINT32) + sizeof(UINT32) + sizeof(UINT32) // For Serialization Tag, Gid, RLver and Number of entries
#define NR_PROOFS_HEADER_SIZE          sizeof(UINT32) + sizeof(UINT32)   // For RLver and Number of entries

#define SIGMA_PUBCERT3P_VER0 0x0000
#define SIGMA_PUBCERT3P_VER1 0x0001

#define SIGMA_PUBCERT3P_TYPE_VER0_UNDEFINED  0xffff
#define SIGMA_PUBCERT3P_TYPE_VER1_PROTECTED_OUTPUT       0x0000
#define SIGMA_PUBCERT3P_TYPE_VER1_MV         0x0001

typedef UINT8    EphemeralPublicKey[SIGMA_SESSION_PUBKEY_LENGTH];
typedef UINT8    EPID_SIGNATURE[EPID_SIG_LEN];
typedef UINT8    SIGMA_HMAC[SIGMA_HMAC_LENGTH];
typedef UINT8    VERIFIER_SIGNATURE[ECDSA_SIG_LENGTH];
typedef UINT8    SIGMA_MAC_KEY[SIGMA_MK_LENGTH];
typedef UINT8    SIGMA_SIGN_KEY[SIGMA_SK_LENGTH];
typedef UINT8    SIGMA_INIT_VECTOR_KEY[SIGMA_IV_LENGTH];
typedef UINT8    SIGMA_NONCE[SIGMA_NONCE_LENGTH];
typedef UINT8    SIGMA_BASENAME[SIGMA_BASENAME_LENGTH];
typedef UINT8    SIGMA_SESSION_STATE[SIGMA_SESSION_STATE_LENGTH];

typedef enum 
{
   SESSION_UNINITIATED = 0,
   SESSION_PUBKEY_CREATED,
   SESSION_3RD_PARTY_CERT_VALID,
   SESSION_3RD_PARTY_CERT_INVALID,
   SESSION_PUBKEY_GENERATION_FAILED,
#ifndef KC_TEST
//   SESSION_ESTABLISHED = SESSION_3RD_PARTY_CERT_VALID,
#endif
} SIGMA_SESSION_STATUS;

#pragma pack(1)
typedef struct _SessmgrFwVer
{
   UINT32           Reserved;
   UINT16           MajorVersion;
   UINT16           MinorVersion;
   UINT16           HotfixVersion;
   UINT16           BuildVersion;
   UINT32           SecureVersionNumber;
} SessmgrFwVer;


// length includes the size of the header also.

typedef struct _SIGMA_VLR_HEADER
{
   UINT8                     ID;
   // payload following the Sigma VLR are DWORD aligned. PaddedBytes can be 0, 1, 2 or 3 based on how many bytes were padded to make the structure DWORD aligned. 
   UINT8                     PaddedBytes;
   // Length includes the size of the VLR header.
   UINT16                    Length;
} SIGMA_VLR_HEADER;

// All variable and optional field in Sigma messages will use a VLR format
// To simplify the FW code, the length of VLR data should be DWORD aligned. The data will be padded with 0’s at the end to make the VLR’s data DWORD aligned.  


// Supported VLR IDs in the Sigma message
#define X509_GROUP_CERTIFICATE_VLR_ID       30
#define VERIFIER_CERTIFICATE_CHAIN_VLR_ID	31
#define SIGNATURE_REVOCATION_LIST_VLR_ID	32
#define OCSP_RESPONSE_VLR_ID            	33
#define EPID_SIGNATURE_VLR_ID	            34
#define NRPROOFS_VLR_ID	                    35

// length field in Sigma VLR include the size of the header also. This Macro can be used to get the size of the payload alone. Just to reduce clutter in the code.
#define VLR_UNPADDED_PAYLOAD_SIZE(VlrHdr) (VlrHdr.Length  - VlrHdr.PaddedBytes - sizeof(SIGMA_VLR_HEADER))

// Total bytes required for creating a VLR for Data of x bytes 
#define TOTAL_VLR_SIZE(x) (sizeof(SIGMA_VLR_HEADER) + NEXT_DWORD_BOUNDARY(x))

typedef struct _X509_GROUP_CERTIFICATE_VLR
{
   SIGMA_VLR_HEADER     VlrHeader;
   UINT8                X509GroupCertData[0];
} X509_GROUP_CERTIFICATE_VLR;

typedef struct _VERIFIER_CERT_CHAIN_VLR
{
   SIGMA_VLR_HEADER     VlrHeader;
   UINT8                VerifierCertificateChain[0];
} VERIFIER_CERT_CHAIN_VLR;

typedef struct _SIGNATURE_REV_LIST_VLR
{
   SIGMA_VLR_HEADER     VlrHeader;
   UINT8                SigRl[0];
}SIGNATURE_REV_LIST_VLR;

typedef struct _OCSP_RESPONSE_VLR
{
   SIGMA_VLR_HEADER     VlrHeader;
   UINT8                OcspResponse[0];
}OCSP_RESPONSE_VLR;

typedef struct _EPID_SIGNATURE_VLR
{
   SIGMA_VLR_HEADER     VlrHeader;
   UINT8                EpidSig[0];
}EPID_SIGNATURE_VLR;

#define NONCE_LENGTH	32

// OCSP Request Info
typedef struct _OCSPRequestInfo
{
  char          *urlOcspResponder;		// OCSP Responder URL
  char			*certName;				// Verifier Certificate Name
  char			*issuerName;			// Verifier Issuer Certificate Name
  //char			*ocspResponderCertName;	// OCSP Responder Certificate Name to verify OCSP response
  unsigned char	ocspNonce[NONCE_LENGTH];
} OCSPRequestInfo;


#pragma pack()

#endif
