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

#ifndef _TYPES_H_
#define _TYPES_H_

#include <stdint.h>

#ifndef UINT64
#define UINT64   uint64_t
#endif
#ifndef UINT32
#define UINT32   uint32_t
#endif
#ifndef UINT16
#define UINT16  uint16_t
#endif
#ifndef UINT8
#define UINT8   uint8_t
#endif
#ifndef BYTE
#define BYTE    uint8_t
#endif

#define SIGMA_HMAC_LENGTH       32

#define ECDSA_PRIVKEY_LEN       32
#define ECDSA_PUBKEY_LEN        64
#define ECDSA_SIGNATURE_LEN     64

#define SIGMA_PRIVKEY_LEN       32
#define SIGMA_PUBKEY_LEN        64

#define EPID_PUBKEY_LEN        328

#define PRIV_RL_ENTRY   32

typedef uint32_t   SHA256_HASH[8];
typedef uint8_t    SIGMA_HMAC[SIGMA_HMAC_LENGTH];

typedef uint8_t Nonce128_t[16];


typedef UINT8 PR_PSE_T[32];

typedef UINT32 STATUS;

#define SAFEID_CRYPTO_CONTEXT_LEN    sizeof(SAFEID_CRYPTO_CONTEXT)


typedef uint8_t EcDsaPrivKey[ECDSA_PRIVKEY_LEN];
typedef uint8_t EcDsaPubKey[ECDSA_PUBKEY_LEN];
typedef uint8_t EcDsaSig[ECDSA_SIGNATURE_LEN];

typedef uint32_t SAFEID_GID;

#pragma pack(push, 1)
typedef struct
{
    UINT8 first[ECDSA_PUBKEY_LEN];
    UINT8 second[ECDSA_PUBKEY_LEN];
} KeysToSign_t;

/*
**    3.1.1.2.1    EPID Certificate
*/
typedef struct _EpidCert 
{
    unsigned char   PubKeyEpid[EPID_PUBKEY_LEN];
    EcDsaSig        SignIntel;
} EpidCert;

typedef struct _SAFEID_CRYPTO_CONTEXT
{
   /// SafeId serialization tags
   // SafeId version
   UINT8 Sver[2];
   // SafeId BlobId
   UINT8 Blobid[2]; 

   UINT8 p[32];
   UINT8 q[32];
   UINT8 h[4];
   UINT8 a[32];
   UINT8 b[32];
   UINT8 coeff0[32];
   UINT8 coeff1[32];
   UINT8 coeff2[32];
   UINT8 qnr[32];
   UINT8 orderG2[96];
   UINT8 pp[32];
   UINT8 qp[32];
   UINT8 hp[4];
   UINT8 ap[32];
   UINT8 bp[32];
   UINT8 g1[64];
   UINT8 g2[192];
   UINT8 g3[64];

   // Intel root signature
   UINT8 eccDsaSignature[ECDSA_SIGNATURE_LEN];
} SAFEID_CRYPTO_CONTEXT;


#pragma pack(pop)

#endif
