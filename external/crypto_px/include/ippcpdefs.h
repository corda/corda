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

#ifndef __IPPCPDEFS_H__
#define __IPPCPDEFS_H__

#ifdef __cplusplus
extern "C" {
#endif

/*
// AES
*/
#define IPP_AES_BLOCK_BITSIZE (128) /* cipher blocksizes (bits) */

typedef enum {                      /* cipher keysizes (bits) */
   ippRijndaelKey128 = 128, IppsRijndaelKey128 = 128, /* 128-bit key */
   ippRijndaelKey192 = 192, IppsRijndaelKey192 = 192, /* 192-bit key */
   ippRijndaelKey256 = 256, IppsRijndaelKey256 = 256  /* 256-bit key */
} IppsRijndaelKeyLength;

typedef struct _cpRijndael128 IppsAESSpec;
typedef struct _cpAES_GCM     IppsAES_GCMState;
typedef struct _cpAES_CMAC    IppsAES_CMACState;


/*
// hash
*/
typedef enum {
   ippHashAlg_Unknown,
   ippHashAlg_SHA1,
   ippHashAlg_SHA256,
   ippHashAlg_SHA224,
   ippHashAlg_SHA512,
   ippHashAlg_SHA384,
   ippHashAlg_MD5,
   ippHashAlg_SHA512_224,
   ippHashAlg_SHA512_256,
   ippHashAlg_MaxNo
} IppHashAlgId;

#define IPP_ALG_HASH_UNKNOWN     (ippHashAlg_Unknown) /* unknown  */
#define IPP_ALG_HASH_SHA1        (ippHashAlg_SHA1)    /* SHA1     */
#define IPP_ALG_HASH_SHA256      (ippHashAlg_SHA256)  /* SHA256   */
#define IPP_ALG_HASH_SHA224      (ippHashAlg_SHA224)  /* SHA224 or SHA256/224 */
#define IPP_ALG_HASH_SHA512      (ippHashAlg_SHA512)  /* SHA512   */
#define IPP_ALG_HASH_SHA384      (ippHashAlg_SHA384)  /* SHA384 or SHA512/384 */
#define IPP_ALG_HASH_MD5         (ippHashAlg_MD5)     /* MD5      */
#define IPP_ALG_HASH_SHA512_224  (ippHashAlg_SHA512_224) /* SHA512/224 */
#define IPP_ALG_HASH_SHA512_256  (ippHashAlg_SHA512_256) /* SHA512/256 */
#define IPP_ALG_HASH_LIMIT       (ippHashAlg_MaxNo)   /* hash alg limiter*/

#define IPP_SHA1_DIGEST_BITSIZE        160   /* digest size (bits) */
#define IPP_SHA256_DIGEST_BITSIZE      256
#define IPP_SHA224_DIGEST_BITSIZE      224
#define IPP_SHA384_DIGEST_BITSIZE      384
#define IPP_SHA512_DIGEST_BITSIZE      512
#define IPP_MD5_DIGEST_BITSIZE         128
#define IPP_SHA512_224_DIGEST_BITSIZE  224
#define IPP_SHA512_256_DIGEST_BITSIZE  256

typedef struct _cpHashCtx  IppsHashState;
typedef struct _cpHMAC  IppsHMACState;


/*
// Big Number Integer Arithmetic
*/
#define BN_MAXBITSIZE      (16*1024)   /* bn max size (bits) */

/* operation results */
#define IPP_IS_EQ (0)
#define IPP_IS_GT (1)
#define IPP_IS_LT (2)
#define IPP_IS_NE (3)
#define IPP_IS_NA (4)

#define IPP_IS_PRIME       (5)
#define IPP_IS_COMPOSITE   (6)

#define IPP_IS_VALID       (7)
#define IPP_IS_INVALID     (8)
#define IPP_IS_INCOMPLETE  (9)
#define IPP_IS_ATINFINITY  (10)

#define IS_ZERO            IPP_IS_EQ
#define GREATER_THAN_ZERO  IPP_IS_GT
#define LESS_THAN_ZERO     IPP_IS_LT
#define IS_PRIME           IPP_IS_PRIME
#define IS_COMPOSITE       IPP_IS_COMPOSITE
#define IS_VALID_KEY       IPP_IS_VALID
#define IS_INVALID_KEY     IPP_IS_INVALID
#define IS_INCOMPLETED_KEY IPP_IS_INCOMPLETE

typedef enum {
   ippBigNumNEG = 0, IppsBigNumNEG = 0,
   ippBigNumPOS = 1, IppsBigNumPOS = 1
} IppsBigNumSGN;

typedef enum {
   ippBinaryMethod   = 0, IppsBinaryMethod = 0,
   ippSlidingWindows = 1, IppsSlidingWindows = 1
} IppsExpMethod;

typedef struct _cpBigNum      IppsBigNumState;
typedef struct _cpMontgomery  IppsMontState;
typedef struct _cpPRNG        IppsPRNGState;
typedef struct _cpPrime       IppsPrimeState;

/*  External Bit Supplier */
typedef IppStatus (__STDCALL *IppBitSupplier)(Ipp32u* pRand, int nBits, void* pEbsParams);


/*
// RSA
*/
#define MIN_RSA_SIZE (8)
#define MAX_RSA_SIZE (4096)

typedef struct _cpRSA               IppsRSAState;
typedef struct _cpRSA_public_key    IppsRSAPublicKeyState;
typedef struct _cpRSA_private_key   IppsRSAPrivateKeyState;


/*
// EC Cryptography
*/
#define EC_GFP_MAXBITSIZE   (1024)

typedef struct _cpECCP      IppsECCPState;
typedef struct _cpECCPPoint IppsECCPPointState;

/* operation result */
typedef enum {
   ippECValid,             /* validation pass successfully     */

   ippECCompositeBase,     /* field based on composite         */
   ippECComplicatedBase,   /* number of non-zero terms in the polynomial (> PRIME_ARR_MAX) */
   ippECIsZeroDiscriminant,/* zero discriminant */
   ippECCompositeOrder,    /* composite order of base point    */
   ippECInvalidOrder,      /* invalid base point order         */
   ippECIsWeakMOV,         /* weak Meneze-Okamoto-Vanstone  reduction attack */
   ippECIsWeakSSSA,        /* weak Semaev-Smart,Satoh-Araki reduction attack */
   ippECIsSupersingular,   /* supersingular curve */

   ippECInvalidPrivateKey, /* !(0 < Private < order) */
   ippECInvalidPublicKey,  /* (order*PublicKey != Infinity)    */
   ippECInvalidKeyPair,    /* (Private*BasePoint != PublicKey) */

   ippECPointOutOfGroup,   /* out of group (order*P != Infinity)  */
   ippECPointIsAtInfinite, /* point (P=(Px,Py)) at Infinity  */
   ippECPointIsNotValid,   /* point (P=(Px,Py)) out-of EC    */

   ippECPointIsEqual,      /* compared points are equal     */
   ippECPointIsNotEqual,   /* compared points are different  */

   ippECInvalidSignature   /* invalid signature */
} IppECResult;

typedef enum {
   ippECarbitrary =0x00000,        IppECCArbitrary = 0x00000,       /* arbitrary ECC */

   ippECPstd      = 0x10000,       IppECCPStd      = 0x10000,       /* random (recommended) EC over FG(p): */
   ippECPstd112r1 = ippECPstd,     IppECCPStd112r1 = IppECCPStd,    /* secp112r1 curve */
   ippECPstd112r2 = ippECPstd+1,   IppECCPStd112r2 = IppECCPStd+1,  /* secp112r2 curve */
   ippECPstd128r1 = ippECPstd+2,   IppECCPStd128r1 = IppECCPStd+2,  /* secp128r1 curve */
   ippECPstd128r2 = ippECPstd+3,   IppECCPStd128r2 = IppECCPStd+3,  /* secp128r2 curve */
   ippECPstd160r1 = ippECPstd+4,   IppECCPStd160r1 = IppECCPStd+4,  /* secp160r1 curve */
   ippECPstd160r2 = ippECPstd+5,   IppECCPStd160r2 = IppECCPStd+5,  /* secp160r2 curve */
   ippECPstd192r1 = ippECPstd+6,   IppECCPStd192r1 = IppECCPStd+6,  /* secp192r1 curve */
   ippECPstd224r1 = ippECPstd+7,   IppECCPStd224r1 = IppECCPStd+7,  /* secp224r1 curve */
   ippECPstd256r1 = ippECPstd+8,   IppECCPStd256r1 = IppECCPStd+8,  /* secp256r1 curve */
   ippECPstd384r1 = ippECPstd+9,   IppECCPStd384r1 = IppECCPStd+9,  /* secp384r1 curve */
   ippECPstd521r1 = ippECPstd+10,  IppECCPStd521r1 = IppECCPStd+10, /* secp521r1 curve */
} IppsECType, IppECCType;


#ifdef __cplusplus
}
#endif

#endif /* __IPPCPDEFS_H__ */
