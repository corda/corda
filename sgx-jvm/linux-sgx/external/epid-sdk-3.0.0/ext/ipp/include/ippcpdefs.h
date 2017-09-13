/*############################################################################
  # Copyright 2016 Intel Corporation
  #
  # Licensed under the Apache License, Version 2.0 (the "License");
  # you may not use this file except in compliance with the License.
  # You may obtain a copy of the License at
  #
  #     http://www.apache.org/licenses/LICENSE-2.0
  #
  # Unless required by applicable law or agreed to in writing, software
  # distributed under the License is distributed on an "AS IS" BASIS,
  # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  # See the License for the specific language governing permissions and
  # limitations under the License.
  ############################################################################*/

/* 
//              Intel(R) Integrated Performance Primitives
//              Cryptographic Primitives (ippCP) definitions.
// 
// 
*/


#ifndef __IPPCPDEFS_H__
#define __IPPCPDEFS_H__

#ifdef __cplusplus
extern "C" {
#endif

#if !defined( _OWN_BLDPCS )

typedef Ipp32u IppAlgId;

/*
// =========================================================
// Symmetric Ciphers
// =========================================================
*/
typedef enum {
   ippPaddingNONE  = 0, /*NONE  = 0,*/ IppsCPPaddingNONE  = 0,
   ippPaddingPKCS7 = 1, /*PKCS7 = 1,*/ IppsCPPaddingPKCS7 = 1,
   ippPaddingZEROS = 2, /*ZEROS = 2,*/ IppsCPPaddingZEROS = 2
} IppsPadding, IppsCPPadding;

typedef struct _cpDES         IppsDESSpec;
typedef struct _cpRijndael128 IppsAESSpec;
typedef struct _cpRijndael128 IppsRijndael128Spec;
typedef struct _cpSMS4        IppsSMS4Spec;

/* TDES */
#define  DES_BLOCKSIZE  (64)  /* cipher blocksize (bits) */
#define TDES_BLOCKSIZE  DES_BLOCKSIZE

#define  DES_KEYSIZE    (64) /*     cipher keysize (bits) */
#define TDES_KEYSIZE    DES_KEYSIZE

/* AES */
#define IPP_AES_BLOCK_BITSIZE (128)    /* cipher blocksizes (bits) */

/* Rijndael */
typedef enum {
   ippRijndaelKey128 = 128, IppsRijndaelKey128 = 128, /* 128-bit key */
   ippRijndaelKey192 = 192, IppsRijndaelKey192 = 192, /* 192-bit key */
   ippRijndaelKey256 = 256, IppsRijndaelKey256 = 256  /* 256-bit key */
} IppsRijndaelKeyLength;

/* AES based  authentication & confidence */
typedef struct _cpRijndael128GCM IppsRijndael128GCMState;
typedef struct _cpAES_CCM        IppsAES_CCMState;
typedef struct _cpRijndael128GCM IppsAES_GCMState;

/*
// =========================================================
// ARCFOUR Stream Cipher
// =========================================================
*/
typedef struct _cpARCfour  IppsARCFourState;

#define IPP_ARCFOUR_KEYMAX_SIZE  (256)  /* max key length (bytes) */
#define MAX_ARCFOUR_KEY_LEN   IPP_ARCFOUR_KEYMAX_SIZE /* obsolete */

/*
// =========================================================
// One-Way Hash Functions
// =========================================================
*/
typedef enum {
   ippHashAlg_Unknown,
   ippHashAlg_SHA1,
   ippHashAlg_SHA256,
   ippHashAlg_SHA224,
   ippHashAlg_SHA512,
   ippHashAlg_SHA384,
   ippHashAlg_MD5,
   ippHashAlg_SM3,
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
#define IPP_ALG_HASH_SM3         (ippHashAlg_SM3)     /* SM3      */
#define IPP_ALG_HASH_SHA512_224  (ippHashAlg_SHA512_224) /* SHA512/224 */
#define IPP_ALG_HASH_SHA512_256  (ippHashAlg_SHA512_256) /* SHA512/256 */
#define IPP_ALG_HASH_LIMIT       (ippHashAlg_MaxNo)   /* hash alg limiter*/

typedef struct _cpSHA1     IppsSHA1State;
typedef struct _cpSHA256   IppsSHA256State;
typedef struct _cpSHA256   IppsSHA224State;
typedef struct _cpSHA512   IppsSHA512State;
typedef struct _cpSHA512   IppsSHA384State;
typedef struct _cpMD5      IppsMD5State;
typedef struct _cpSM3      IppsSM3State;
typedef struct _cpHashCtx  IppsHashState;


/* MGF */
typedef IppStatus (__STDCALL *IppMGF)(const Ipp8u* pSeed, int seedLen, Ipp8u* pMask, int maskLen);
/* HASH function */
typedef IppStatus (__STDCALL *IppHASH)(const Ipp8u* pMsg, int len, Ipp8u* pMD);

#define   IPP_SHA1_DIGEST_BITSIZE  160   /* digest size (bits) */
#define IPP_SHA256_DIGEST_BITSIZE  256
#define IPP_SHA224_DIGEST_BITSIZE  224
#define IPP_SHA384_DIGEST_BITSIZE  384
#define IPP_SHA512_DIGEST_BITSIZE  512
#define    IPP_MD5_DIGEST_BITSIZE  128
#define    IPP_SM3_DIGEST_BITSIZE  256
#define IPP_SHA512_224_DIGEST_BITSIZE  224
#define IPP_SHA512_256_DIGEST_BITSIZE  256

/*
// =========================================================
// Keyed-Hash Message Authentication Codes
// =========================================================
*/
typedef struct _cpHMAC  IppsHMACState;
typedef struct _cpHMAC  IppsHMACSHA1State;
typedef struct _cpHMAC  IppsHMACSHA256State;
typedef struct _cpHMAC  IppsHMACSHA224State;
typedef struct _cpHMAC  IppsHMACSHA384State;
typedef struct _cpHMAC  IppsHMACSHA512State;
typedef struct _cpHMAC  IppsHMACMD5State;

/*
// =========================================================
// Data Authentication Codes
// =========================================================
*/
typedef struct _cpAES_CMAC          IppsAES_CMACState;

/*
// =========================================================
// Big Number Integer Arithmetic
// =========================================================
*/
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

/*
// =========================================================
// RSA Cryptography
// =========================================================
*/
typedef struct _cpRSA IppsRSAState;

/* key types */
typedef enum {
   ippRSApublic  = 0x20000000, IppRSApublic  = 0x20000000,
   ippRSAprivate = 0x40000000, IppRSAprivate = 0x40000000
} IppRSAKeyType;

/* key component's tag */
typedef enum {
   ippRSAkeyN    = 0x01,  IppRSAkeyN    = 0x01,
   ippRSAkeyE    = 0x02,  IppRSAkeyE    = 0x02,
   ippRSAkeyD    = 0x04,  IppRSAkeyD    = 0x04,
   ippRSAkeyP    = 0x08,  IppRSAkeyP    = 0x08,
   ippRSAkeyQ    = 0x10,  IppRSAkeyQ    = 0x10,
   ippRSAkeyDp   = 0x20,  IppRSAkeyDp   = 0x20,
   ippRSAkeyDq   = 0x40,  IppRSAkeyDq   = 0x40,
   ippRSAkeyQinv = 0x80,  IppRSAkeyQinv = 0x80
} IppRSAKeyTag;

typedef struct _cpRSA_public_key   IppsRSAPublicKeyState;
typedef struct _cpRSA_private_key  IppsRSAPrivateKeyState;

#define MIN_RSA_SIZE (8)
#define MAX_RSA_SIZE (4096)

/*
// =========================================================
// DL Cryptography
// =========================================================
*/
typedef struct _cpDLP IppsDLPState;

/* domain parameter tags */
typedef enum {
   ippDLPkeyP = 0x01, IppDLPkeyP = 0x01,
   ippDLPkeyR = 0x02, IppDLPkeyR = 0x02,
   ippDLPkeyG = 0x04, IppDLPkeyG = 0x04
} IppDLPKeyTag;

typedef enum {
   ippDLValid,                /* validation pass successfully  */

   ippDLBaseIsEven,           /* !(P is odd)                   */
   ippDLOrderIsEven,          /* !(R is odd)                   */
   ippDLInvalidBaseRange,     /* !(2^(L-1) < P < 2^L)          */
   ippDLInvalidOrderRange,    /* !(2^(M-1) < R < 2^M)          */
   ippDLCompositeBase,
   ippDLCompositeOrder,
   ippDLInvalidCofactor,      /* !( R|(P-1) )                  */
   ippDLInvalidGenerator,     /* !( G^R == 1 (mod P) )         */
                              /* !(1 < G < (P-1))              */
   ippDLInvalidPrivateKey,    /* !(1 < private < (R-1))        */
   ippDLInvalidPublicKey,     /* !(1 < public  <=(P-1))        */
   ippDLInvalidKeyPair,       /* !(G^private == public         */

   ippDLInvalidSignature       /* invalid signature             */
} IppDLResult;

#define MIN_DLP_BITSIZE      (512)
#define MIN_DLP_BITSIZER     (160)

#define MIN_DLPDH_BITSIZE    (512)
#define MIN_DLPDH_BITSIZER   (160)
#define DEF_DLPDH_BITSIZER   (160)

#define MIN_DLPDSA_BITSIZE   (512)
#define MAX_DLPDSA_BITSIZE  (1024)
#define MIN_DLPDSA_BITSIZER  (160)
#define DEF_DLPDSA_BITSIZER  (160)
#define MAX_DLPDSA_BITSIZER  (160)
#define MIN_DLPDSA_SEEDSIZE  (160)

/*
// =========================================================
// EC Cryptography
// =========================================================
*/
typedef struct _cpECCP      IppsECCPState;
typedef struct _cpECCB      IppsECCBState;
typedef struct _cpECCPPoint IppsECCPPointState;
typedef struct _cpECCBPoint IppsECCBPointState;

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

/* domain parameter set/get flags */
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
   ippECPstdSM2   = ippECPstd+11,  IppECCPStdSM2   = IppECCPStd+11, /* TMP SM2   curve */
   ippEC_TPM_SM2_P256= ippECPstd+11,
   ippEC_TPM_BN_P256 = ippECPstd+12,                                /* TPM BN_P256 curve */

   /* curves over binary finit fields are not supported in IPP 9.0 */
   IppECCBStd      = 0x20000,       /* random (recommended) EC over FG(2^m): */
   IppECCBStd113r1 = IppECCBStd,    /* sect113r1 curve */
   IppECCBStd113r2 = IppECCBStd+1,  /* sect113r2 curve */
   IppECCBStd131r1 = IppECCBStd+2,  /* sect131r1 curve */
   IppECCBStd131r2 = IppECCBStd+3,  /* sect131r2 curve */
   IppECCBStd163r1 = IppECCBStd+4,  /* sect163r1 curve */
   IppECCBStd163r2 = IppECCBStd+5,  /* sect163r2 curve */
   IppECCBStd193r1 = IppECCBStd+6,  /* sect193r1 curve */
   IppECCBStd193r2 = IppECCBStd+7,  /* sect193r2 curve */
   IppECCBStd233r1 = IppECCBStd+8,  /* sect233r1 curve */
   IppECCBStd283r1 = IppECCBStd+9,  /* sect283r1 curve */
   IppECCBStd409r1 = IppECCBStd+10, /* sect409r1 curve */
   IppECCBStd571r1 = IppECCBStd+11, /* sect571r1 curve */

   IppECCKStd      = 0x40000,       /* Koblitz (recommended) EC over FG(2^m): */
   IppECCBStd163k1 = IppECCKStd,    /* Koblitz 163 curve */
   IppECCBStd233k1 = IppECCKStd+1,  /* Koblitz 233 curve */
   IppECCBStd239k1 = IppECCKStd+2,  /* Koblitz 239 curve */
   IppECCBStd283k1 = IppECCKStd+3,  /* Koblitz 283 curve */
   IppECCBStd409k1 = IppECCKStd+4,  /* Koblitz 409 curve */
   IppECCBStd571k1 = IppECCKStd+5   /* Koblitz 571 curve */
} IppsECType, IppECCType;

#endif /* _OWN_BLDPCS */

#ifdef __cplusplus
}
#endif

#endif /* __IPPCPDEFS_H__ */
