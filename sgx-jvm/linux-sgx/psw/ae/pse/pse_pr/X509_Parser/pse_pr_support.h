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


// NOTE: This file should only be included in X509_Parser.cpp

#ifndef __PSE_PR_SUPPORT_H__
#define __PSE_PR_SUPPORT_H__

#include <string.h>
#include <ctype.h>

#include "le2be_macros.h"

typedef struct _G1Point {
    unsigned char x[32];
    unsigned char y[32];
} G1Point;


typedef G1Point G3Point;



#include "sgx_tcrypto.h" 
#include "prepare_hash_sha1.h"
#include "typedef.h"

#define STATUS_SUCCESS  (0)
#define STATUS_INVALID_PARAMS   X509_GENERAL_ERROR

#define  DBG_ASSERT(a)
#define C_ASSERT(e) typedef char __C_ASSERT__[(e)?1:-1]

#define SESSMGR_MEMCPY_S(Dst, DstSize, Src, MaxCount)   memcpy(Dst, Src, MaxCount)

#define SESSMGR_STATUS_INTERNAL_ERROR   X509_GENERAL_ERROR

typedef enum _CRYPTO_STATUS
{
    CRYPTO_STATUS_SUCCESS = 0,
    CRYPTO_STATUS_INVALID_PARAMS,
    CRYPTO_MEMORY_ERROR,
    CRYPTO_IPP_ERROR,
    CRYPTO_STATUS_INTERNAL_ERROR,
} crypto_status_t;

// Dummy values for CryptoCreateHash and SafeIdSigmaEcDsaVerify calls
#define CRYPTO_HASH_TYPE_SHA1   0
#define CRYPTO_HASH_TYPE_SHA256 1
#define SINGLE_BLOCK            0

// Replace CryptoCreateHash with version that uses IPP for SHA1
#define CryptoCreateHash(a1, pSrcBuffer, pDigest, a2, a3, a4)  CreateSha1Hash(pSrcBuffer, pDigest)

// Replace SafeIdSigmaEcDsaVerifyPriv with version that uses IPP
#define SafeIdSigmaEcDsaVerifyPriv(a1, msgBuffer, msgLen, EcdsaKey, sigBuffer, a2, a3, a4, VerifRes) \
    EcDsa_VerifySignature(msgBuffer, msgLen, (EcDsaPubKey*)EcdsaKey, (EcDsaSig*)sigBuffer, (bool*)VerifRes)


// Dummy values for SESSMGR_MEM_ALLOC_BUFFER
#define MM_DATA_HEAP_SHARED_RW  0
#define TX_WAIT_FOREVER         0

#define SESSMGR_MEM_ALLOC_BUFFER(workBuffer, a1, elementSize, bufferSize, a2) \
    { workBuffer = (UINT8*)malloc(bufferSize*elementSize); \
    if (nullptr == workBuffer) { return X509_STATUS_MEMORY_ALLOCATION_ERROR; } }

#define SESSMGR_MEM_FREE(workBuffer)  { if (workBuffer) free(workBuffer); }


static EcDsaPubKey SerializedPublicKey = {0};
#define INTEL_ECDSA_PUBKEY_PROD_BE  SerializedPublicKey

STATUS SetPublicEcDsaKey( const EcDsaPubKey* pPublicKey)
{
    memcpy(SerializedPublicKey, pPublicKey, sizeof(EcDsaPubKey));
    return STATUS_SUCCESS;
}
  



#endif
