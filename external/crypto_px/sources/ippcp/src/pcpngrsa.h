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

#if !defined(_CP_NG_RSA_H)
#define _CP_NG_RSA_H

#include "pcpbn.h"
#include "pcpmontgomery.h"

struct _cpRSA_public_key {
   IppCtxId       id;            /* key ID */
   int         maxbitSizeN;
   int         maxbitSizeE;
   int            bitSizeN;      /* RSA modulus bitsize */
   int            bitSizeE;      /* RSA public exp bitsize */

   BNU_CHUNK_T*   pDataE;        /* public exp */
   IppsMontState* pMontN;        /* montgomery engine (N) */
};

/* access */
#define RSA_PUB_KEY_MAXSIZE_N(x) ((x)->maxbitSizeN)
#define RSA_PUB_KEY_MAXSIZE_E(x) ((x)->maxbitSizeE)
#define RSA_PUB_KEY_ID(x)        ((x)->id)
#define RSA_PUB_KEY_BITSIZE_N(x) ((x)->bitSizeN)
#define RSA_PUB_KEY_BITSIZE_E(x) ((x)->bitSizeE)
#define RSA_PUB_KEY_E(x)         ((x)->pDataE)
#define RSA_PUB_KEY_NMONT(x)     ((x)->pMontN)
#define RSA_PUB_KEY_VALID_ID(x)  (RSA_PUB_KEY_ID((x))==idCtxRSA_PubKey)
#define RSA_PUB_KEY_IS_SET(x)    (RSA_PUB_KEY_BITSIZE_N((x))>0)

/* alignment */
#define RSA_PUBLIC_KEY_ALIGNMENT ((int)(sizeof(void*)))

struct _cpRSA_private_key {
   IppCtxId       id;            /* key ID */
   int         maxbitSizeN;
   int         maxbitSizeD;
   int            bitSizeN;      /* RSA modulus bitsize */
   int            bitSizeD;      /* RSA private exp bitsize */
   int            bitSizeP;      /* RSA p-factor bitsize */
   int            bitSizeQ;      /* RSA q-factor bitsize */

   BNU_CHUNK_T*   pDataD;        /* private exp */
   BNU_CHUNK_T*   pDataDp;       /* dp private exp */
   BNU_CHUNK_T*   pDataDq;       /* dq private exp */
   BNU_CHUNK_T*   pDataQinv;     /* qinv coeff */

   IppsMontState* pMontP;        /* montgomery engine (P) */
   IppsMontState* pMontQ;        /* montgomery engine (Q) */
   IppsMontState* pMontN;        /* montgomery engine (N) */
};

/* access */
#define RSA_PRV_KEY_MAXSIZE_N(x) ((x)->maxbitSizeN)
#define RSA_PRV_KEY_MAXSIZE_D(x) ((x)->maxbitSizeD)
#define RSA_PRV_KEY_ID(x)        ((x)->id)
#define RSA_PRV_KEY_BITSIZE_N(x) ((x)->bitSizeN)
#define RSA_PRV_KEY_BITSIZE_D(x) ((x)->bitSizeD)
#define RSA_PRV_KEY_BITSIZE_P(x) ((x)->bitSizeP)
#define RSA_PRV_KEY_BITSIZE_Q(x) ((x)->bitSizeQ)
#define RSA_PRV_KEY_D(x)         ((x)->pDataD)
#define RSA_PRV_KEY_DP(x)        ((x)->pDataDp)
#define RSA_PRV_KEY_DQ(x)        ((x)->pDataDq)
#define RSA_PRV_KEY_INVQ(x)      ((x)->pDataQinv)
#define RSA_PRV_KEY_PMONT(x)     ((x)->pMontP)
#define RSA_PRV_KEY_QMONT(x)     ((x)->pMontQ)
#define RSA_PRV_KEY_NMONT(x)     ((x)->pMontN)
#define RSA_PRV_KEY1_VALID_ID(x) (RSA_PRV_KEY_ID((x))==idCtxRSA_PrvKey1)
#define RSA_PRV_KEY2_VALID_ID(x) (RSA_PRV_KEY_ID((x))==idCtxRSA_PrvKey2)
#define RSA_PRV_KEY_VALID_ID(x)  (RSA_PRV_KEY1_VALID_ID((x)) || RSA_PRV_KEY2_VALID_ID((x)))
#define RSA_PRV_KEY_IS_SET(x)    (RSA_PRV_KEY_BITSIZE_N((x))>0)

/* alignment */
#define RSA_PRIVATE_KEY_ALIGNMENT ((int)(sizeof(void*)))

/* pubic and private key operations */
void gsRSApub_cipher(IppsBigNumState* pY, const IppsBigNumState* pX, const IppsRSAPublicKeyState* pKey, BNU_CHUNK_T* pScratchBuffer);
void gsRSAprv_cipher(IppsBigNumState* pY, const IppsBigNumState* pX, const IppsRSAPrivateKeyState* pKey, BNU_CHUNK_T* pScratchBuffer);
void gsRSAprv_cipher_crt(IppsBigNumState* pY, const IppsBigNumState* pX, const IppsRSAPrivateKeyState* pKey, BNU_CHUNK_T* pScratchBuffer);

#endif /* _CP_NG_RSA_H */
