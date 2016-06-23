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

#if !defined(_PCP_ECCP_H)
#define _PCP_ECCP_H

#include "pcpbnresource.h"
#include "pcppma.h"
#include "pcpeccppoint.h"
#include "pcpeccpsscm.h"


typedef struct eccp_method_st ECCP_METHOD;

/*
// ECC over prime GF(p) Context
*/
struct _cpECCP {
   IppCtxId            idCtx;      /* prime EC identifier           */

   IppsBigNumState*    pPrime;     /* specify finite field GF(p)    */
   IppsBigNumState*    pA;         /* scecify A & B of EC equation: */
   IppsBigNumState*    pB;         /* y^2 = x^3 + A*x + B (mod)p    */

   IppsBigNumState*    pGX;        /* Base Point (X coordinate)     */
   IppsBigNumState*    pGY;        /* Base Point (Y coordinate)     */
   IppsBigNumState*    pR;         /* order (r) of Base Point       */
   /*    fields above mainly for ippsECCPSet()/ippsECCPGet()        */

   Ipp32u              eccStandard;/* generic/standard ecc          */

   ECCP_METHOD*        pMethod;

   int                 gfeBitSize; /* size (bits) of field element  */
   int                 ordBitSize; /* size (bits) of BP order       */

   int                 a_3;        /* ==1 if A==-3 or A==P-3        */
   IppsBigNumState*    pAenc;      /* internal formatted pA  value  */
   IppsBigNumState*    pBenc;      /* internal formatted pB  value  */
   IppsMontState*      pMontP;     /* montromery engine (modulo p)  */

   IppsECCPPointState* pGenc;      /* internal formatted Base Point */
   IppsBigNumState*    pCofactor;  /* cofactor = #E/base_point_order*/
   IppsMontState*      pMontR;     /* montromery engine (modulo r)  */

   IppsBigNumState*    pPrivate;   /* private key                   */
   IppsECCPPointState* pPublic;    /* public key (affine)           */
   IppsBigNumState*    pPrivateE;  /* ephemeral private key         */
   IppsECCPPointState* pPublicE;   /* ephemeral public key (affine) */

   IppsPrimeState*     pPrimary;   /* prime engine                  */

   Ipp8u*              pSscmBuffer;/* pointer to sscm buffer */

   BigNumNode*         pBnList;    /* list of big numbers           */
};

/* some useful constants */
#define BNLISTSIZE      (32)  /* list size (probably less) */

/*
// Contetx Access Macros
*/
#define ECP_ID(ctx)        ((ctx)->idCtx)

#define ECP_PRIME(ctx)     ((ctx)->pPrime)
#define ECP_A(ctx)         ((ctx)->pA)
#define ECP_B(ctx)         ((ctx)->pB)

#define ECP_GX(ctx)        ((ctx)->pGX)
#define ECP_GY(ctx)        ((ctx)->pGY)
#define ECP_ORDER(ctx)     ((ctx)->pR)

#define ECP_TYPE(ctx)      ((ctx)->eccStandard)

#define ECP_METHOD(ctx)    ((ctx)->pMethod)

#define ECP_GFEBITS(ctx)   ((ctx)->gfeBitSize)
#define ECP_ORDBITS(ctx)   ((ctx)->ordBitSize)

#define ECP_AMI3(ctx)      ((ctx)->a_3)
#define ECP_AENC(ctx)      ((ctx)->pAenc)
#define ECP_BENC(ctx)      ((ctx)->pBenc)
#define ECP_PMONT(ctx)     ((ctx)->pMontP)

#define ECP_GENC(ctx)      ((ctx)->pGenc)
#define ECP_COFACTOR(ctx)  ((ctx)->pCofactor)
#define ECP_RMONT(ctx)     ((ctx)->pMontR)

#define ECP_PRIVATE(ctx)   ((ctx)->pPrivate)
#define ECP_PUBLIC(ctx)    ((ctx)->pPublic)
#define ECP_PRIVATE_E(ctx) ((ctx)->pPrivateE)
#define ECP_PUBLIC_E(ctx)  ((ctx)->pPublicE)

#define ECP_PRIMARY(ctx)   ((ctx)->pPrimary)
#define ECP_SCCMBUFF(ctx)  ((ctx)->pSscmBuffer)
#define ECP_BNCTX(ctx)     ((ctx)->pBnList)

#define ECP_VALID_ID(ctx)  (ECP_ID((ctx))==idCtxECCP)

/*
// Recommended (Standard) Domain Parameters
*/
extern const Ipp32u secp112r1_p[]; // (2^128 -3)/76439
extern const Ipp32u secp112r1_a[];
extern const Ipp32u secp112r1_b[];
extern const Ipp32u secp112r1_gx[];
extern const Ipp32u secp112r1_gy[];
extern const Ipp32u secp112r1_r[];
extern       Ipp32u secp112r1_h;

extern const Ipp32u secp112r2_p[]; // (2^128 -3)/76439
extern const Ipp32u secp112r2_a[];
extern const Ipp32u secp112r2_b[];
extern const Ipp32u secp112r2_gx[];
extern const Ipp32u secp112r2_gy[];
extern const Ipp32u secp112r2_r[];
extern       Ipp32u secp112r2_h;

extern const Ipp32u secp128r1_p[]; // 2^128 -2^97 -1
extern const Ipp32u secp128r1_a[];
extern const Ipp32u secp128r1_b[];
extern const Ipp32u secp128r1_gx[];
extern const Ipp32u secp128r1_gy[];
extern const Ipp32u secp128r1_r[];
extern       Ipp32u secp128r1_h;

extern const Ipp32u* secp128_mx[];

extern const Ipp32u secp128r2_p[]; // 2^128 -2^97 -1
extern const Ipp32u secp128r2_a[];
extern const Ipp32u secp128r2_b[];
extern const Ipp32u secp128r2_gx[];
extern const Ipp32u secp128r2_gy[];
extern const Ipp32u secp128r2_r[];
extern       Ipp32u secp128r2_h;

extern const Ipp32u secp160r1_p[]; // 2^160 -2^31 -1
extern const Ipp32u secp160r1_a[];
extern const Ipp32u secp160r1_b[];
extern const Ipp32u secp160r1_gx[];
extern const Ipp32u secp160r1_gy[];
extern const Ipp32u secp160r1_r[];
extern       Ipp32u secp160r1_h;

extern const Ipp32u secp160r2_p[]; // 2^160 -2^32 -2^14 -2^12 -2^9 -2^8 -2^7 -2^2 -1
extern const Ipp32u secp160r2_a[];
extern const Ipp32u secp160r2_b[];
extern const Ipp32u secp160r2_gx[];
extern const Ipp32u secp160r2_gy[];
extern const Ipp32u secp160r2_r[];
extern       Ipp32u secp160r2_h;

extern const Ipp32u secp192r1_p[]; // 2^192 -2^64 -1
extern const Ipp32u secp192r1_a[];
extern const Ipp32u secp192r1_b[];
extern const Ipp32u secp192r1_gx[];
extern const Ipp32u secp192r1_gy[];
extern const Ipp32u secp192r1_r[];
extern       Ipp32u secp192r1_h;

extern const Ipp32u secp224r1_p[]; // 2^224 -2^96 +1
extern const Ipp32u secp224r1_a[];
extern const Ipp32u secp224r1_b[];
extern const Ipp32u secp224r1_gx[];
extern const Ipp32u secp224r1_gy[];
extern const Ipp32u secp224r1_r[];
extern       Ipp32u secp224r1_h;

extern const Ipp32u secp256r1_p[]; // 2^256 -2^224 +2^192 +2^96 -1
extern const Ipp32u secp256r1_a[];
extern const Ipp32u secp256r1_b[];
extern const Ipp32u secp256r1_gx[];
extern const Ipp32u secp256r1_gy[];
extern const Ipp32u secp256r1_r[];
extern       Ipp32u secp256r1_h;

extern const Ipp32u secp384r1_p[]; // 2^384 -2^128 -2^96 +2^32 -1
extern const Ipp32u secp384r1_a[];
extern const Ipp32u secp384r1_b[];
extern const Ipp32u secp384r1_gx[];
extern const Ipp32u secp384r1_gy[];
extern const Ipp32u secp384r1_r[];
extern       Ipp32u secp384r1_h;

extern const Ipp32u secp521r1_p[]; // 2^521 -1
extern const Ipp32u secp521r1_a[];
extern const Ipp32u secp521r1_b[];
extern const Ipp32u secp521r1_gx[];
extern const Ipp32u secp521r1_gy[];
extern const Ipp32u secp521r1_r[];
extern       Ipp32u secp521r1_h;

/* half of some std  modulus */
extern const Ipp32u h_secp128r1_p[];
extern const Ipp32u h_secp192r1_p[];
extern const Ipp32u h_secp224r1_p[];
extern const Ipp32u h_secp256r1_p[];
extern const Ipp32u h_secp384r1_p[];
extern const Ipp32u h_secp521r1_p[];

#endif /* _PCP_ECCP_H */
