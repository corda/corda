/*
* Copyright (C) 2016 Intel Corporation. All rights reserved.
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

#if !defined(_CP_AESAUTH_GCM_H)
#define _CP_AESAUTH_GCM_H

#include "owndefs.h"
#include "owncp.h"
#include "pcpaesm.h"

#define BLOCK_SIZE (MBS_RIJ128)

/* GCM Hash prototype: GHash = GHash*HKey mod G() */
typedef void (*MulGcm_)(Ipp8u* pGHash, const Ipp8u* pHKey, const void* pParam);

/* GCM Authentication prototype: GHash = (GHash^src[])*HKey mod G() */
typedef void (*Auth_)(Ipp8u* pHash, const Ipp8u* pSrc, int len, const Ipp8u* pHKey, const void* pParam);

/* GCM Encrypt_Authentication prototype */
typedef void (*Encrypt_)(Ipp8u* pDst, const Ipp8u* pSrc, int len, IppsAES_GCMState* pCtx);

/* GCM Authentication_Decrypt prototype */
typedef void (*Decrypt_)(Ipp8u* pDst, const Ipp8u* pSrc, int len, IppsAES_GCMState* pCtx);

typedef enum {
   GcmInit,
   GcmIVprocessing,
   GcmAADprocessing,
   GcmTXTprocessing
} GcmState;

struct _cpAES_GCM {
   IppCtxId idCtx;                  /* AES-GCM id                    */
   GcmState state;                  /* GCM state: Init, IV|AAD|TXT proccessing */
   Ipp64u   ivLen;                  /* IV length (bytes)             */
   Ipp64u   aadLen;                 /* header length (bytes)         */
   Ipp64u   txtLen;                 /* text length (bytes)           */

   int      bufLen;                 /* staff buffer length           */
   __ALIGN16                        /* aligned buffers               */
   Ipp8u    counter[BLOCK_SIZE];    /* counter                       */
   Ipp8u    ecounter0[BLOCK_SIZE];  /* encrypted initial counter     */
   Ipp8u    ecounter[BLOCK_SIZE];   /* encrypted counter             */
   Ipp8u    ghash[BLOCK_SIZE];      /* ghash accumulator             */

   MulGcm_  hashFun;                /* AES-GCM mul function          */
   Auth_    authFun;                /* authentication function       */
   Encrypt_ encFun;                 /* encryption & authentication   */
   Decrypt_ decFun;                 /* authentication & decryption   */

   __ALIGN16                        /* aligned AES context           */
   IppsAESSpec cipher;

   __ALIGN16                        /* aligned pre-computed data:    */
   Ipp8u multiplier[BLOCK_SIZE];    /* - (default) hKey                             */
                                    /* - (ase_ni)  hKey*t, (hKey*t)^2, (hKey*t)^4   */
                                    /* - (safe) hKey*(t^i), i=0,...,127             */
};

#define CTR_POS         12

/* alignment */
#define AESGCM_ALIGNMENT   (16)

#define PRECOMP_DATA_SIZE_AES_NI_AESGCM   (BLOCK_SIZE*4)
#define PRECOMP_DATA_SIZE_FAST2K          (BLOCK_SIZE*128)

/*
// Useful macros
*/
#define AESGCM_ID(stt)           ((stt)->idCtx)
#define AESGCM_STATE(stt)        ((stt)->state)

#define AESGCM_IV_LEN(stt)       ((stt)->ivLen)
#define AESGCM_AAD_LEN(stt)      ((stt)->aadLen)
#define AESGCM_TXT_LEN(stt)      ((stt)->txtLen)

#define AESGCM_BUFLEN(stt)       ((stt)->bufLen)
#define AESGCM_COUNTER(stt)      ((stt)->counter)
#define AESGCM_ECOUNTER0(stt)    ((stt)->ecounter0)
#define AESGCM_ECOUNTER(stt)     ((stt)->ecounter)
#define AESGCM_GHASH(stt)        ((stt)->ghash)

#define AESGCM_HASH(stt)         ((stt)->hashFun)
#define AESGCM_AUTH(stt)         ((stt)->authFun)
#define AESGCM_ENC(stt)          ((stt)->encFun)
#define AESGCM_DEC(stt)          ((stt)->decFun)

#define AESGCM_CIPHER(stt)       (IppsAESSpec*)(&((stt)->cipher))

#define AESGCM_HKEY(stt)         ((stt)->multiplier)
#define AESGCM_CPWR(stt)         ((stt)->multiplier)
#define AES_GCM_MTBL(stt)        ((stt)->multiplier)

#define AESGCM_VALID_ID(stt)     (AESGCM_ID((stt))==idCtxAESGCM)


__INLINE void IncrementCounter32(Ipp8u* pCtr)
{
   int i;
   for(i=BLOCK_SIZE-1; i>=CTR_POS && 0==(Ipp8u)(++pCtr[i]); i--) ;
}


void AesGcmPrecompute_table2K(Ipp8u* pPrecomputeData, const Ipp8u* pHKey);
void AesGcmMulGcm_table2K(Ipp8u* pGhash, const Ipp8u* pHkey, const void* pParam);
void AesGcmAuth_table2K(Ipp8u* pGhash, const Ipp8u* pSrc, int len, const Ipp8u* pHkey, const void* pParam);
void wrpAesGcmEnc_table2K(Ipp8u* pDst, const Ipp8u* pSrc, int len, IppsAES_GCMState* pCtx);
void wrpAesGcmDec_table2K(Ipp8u* pDst, const Ipp8u* pSrc, int len, IppsAES_GCMState* pCtx);

extern const Ipp16u AesGcmConst_table[256];            /* precomputed reduction table */

#endif /* _CP_AESAUTH_GCM_H*/
