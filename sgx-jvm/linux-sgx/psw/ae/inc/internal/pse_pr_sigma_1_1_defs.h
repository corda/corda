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
@file   Sigma_1_1_defs.h 
@author Kapil Anantharaman
@brief  This file contains the data structures for sigma 1.1 protocol
*/

#ifndef PSE_PR_SIGMA_1_1_DEFS_H
#define PSE_PR_SIGMA_1_1_DEFS_H

#include "pse_pr_sigma_common_defs.h"

#define SIGMA_MAX_SIG_RL_ENTRY 100
#define MAX_WORK_BUFFER_SIZE   400
#define MAX_VERIFIER_CERT_SIZE 600

#ifndef C_ASSERT
#define C_ASSERT(e) typedef char __C_ASSERT__[(e)?1:-1]
//#define C_ASSERT(e) /* nothing */
#endif

#pragma pack(1)

typedef UINT32 EPID_GID;

typedef UINT8       EphemeralPublicKey[SIGMA_SESSION_PUBKEY_LENGTH];
typedef UINT8       SIGMA_HMAC[SIGMA_HMAC_LENGTH];
typedef UINT8       VERIFIER_SIGNATURE[ECDSA_SIG_LENGTH];
typedef UINT8       SIGMA_BASENAME[SIGMA_BASENAME_LENGTH];


/*!  \brief The beginning of the Signature based Revocation List.
 * 
 *   SIG_RL header is present even if the revocation list is empty.
 *
 */
typedef struct _SIG_RL_HEADER
{
   UINT8 Sver[2];
   // SafeId BlobId
   UINT8 Blobid[2];
   EPID_GID Gid;
   UINT32 RLver;
   UINT32 n2;
}SIG_RL_HEADER;
C_ASSERT(sizeof(SIG_RL_HEADER) == 16);
#pragma pack()

//calculate size of SIG-RL based on n2 - number of entries)
#define GET_SIG_RL_SIZE(SIG_RL_ENTRIES) \
(sizeof(SIG_RL_HEADER) + (1024 * SIG_RL_ENTRIES + 512)/8)



typedef enum _TASK_INFO_TYPE {
        ME_TASK = 0,
        SE_TASK,
        MAX_TASK,
}TASK_INFO_TYPE;

typedef struct _SIGMA_TASK_INFO_HDR {
        TASK_INFO_TYPE   Type;
        unsigned int     TaskInfoLen;
}SIGMA_TASK_INFO_HDR;
   
typedef struct _ME_TASK_INFO {
    SIGMA_TASK_INFO_HDR   Hdr;
    unsigned int		  TaskId;
    unsigned int		  SubTaskId;
    unsigned char		  RsvdMECore[32];
    unsigned char		  RsvdforApp[32];
} ME_TASK_INFO;

#define DAL_APPLET_ID_LEN 16
#define DAL_APPLET_SVN_LEN 4
#define JOM_TASK_ID 8

/* 
OCSP Request and Response
*/
typedef enum _OCSP_REQ_TYPE { 
    NO_OCSP = 0,
    CACHED = 1,
    NON_CACHED = 2,
    MAX_OCSP_TYPE= 3,
} OCSP_REQ_TYPE;

#pragma pack(1)
typedef struct _OCSP_REQ {
    OCSP_REQ_TYPE     ReqType;
    SIGMA_NONCE   	  OcspNonce;
} OCSP_REQ;

/**
\defgroup SigmaMessages SIGMA MESSAGES
*/

/**
\ingroup SigmaMessages
\brief S1 message sent from ME FW to verifier
*/
typedef struct _SIGMA_S1_MESSAGE
{
   EphemeralPublicKey  Ga;
   EPID_GID            Gid;
   OCSP_REQ            OcspReq;
}SIGMA_S1_MESSAGE;

/**
\ingroup SigmaMessages
\brief S2 message sent from verifier to ME FW. 
*/
typedef struct _SIGMA_S2_MESSAGE
{
   
   VERIFIER_SIGNATURE     SigGaGb;
   
   SIGMA_HMAC             S2Icv;
   
   EphemeralPublicKey     Gb;
   
   SIGMA_BASENAME         Basename;
   OCSP_REQ               OcspReq;
   UINT8                Data[0];
}SIGMA_S2_MESSAGE;

/**
\ingroup SigmaMessages
\brief S3 message sent from ME FW to verifier
*/
typedef struct _SIGMA_S3_MESSAGE
{
   SIGMA_HMAC           S3Icv;
   ME_TASK_INFO         TaskInfo;
   EphemeralPublicKey   Ga;
   
   UINT8                Data[0];
}SIGMA_S3_MESSAGE;

#pragma pack()

// This is the constant size portion of the S2 message that is part of the ICV
#define SIGMA_S2_ICV_CONSTANT_BUFFER_SIZE (sizeof(EphemeralPublicKey) + sizeof(SIGMA_BASENAME) + sizeof(OCSP_REQ))

#endif
