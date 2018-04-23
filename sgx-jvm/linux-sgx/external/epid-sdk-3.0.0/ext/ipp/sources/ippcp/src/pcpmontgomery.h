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
//               Intel(R) Integrated Performance Primitives
//                   Cryptographic Primitives (ippcp)
// 
// 
*/

#if !defined(_CP_MONTGOMETRY_H)
#define _CP_MONTGOMETRY_H

/*
// Montgomery spec structure
*/
struct _cpMontgomery
{
   IppCtxId       idCtx;      /* Montgomery spec identifier             */
   cpSize         maxLen;     /* maximum length of modulus being stored */
   cpSize         modLen;     /* length of modulus (and R = b^modLen)   */
   BNU_CHUNK_T    m0;         /* low word of (1/modulus) mod R          */
   BNU_CHUNK_T*   pModulus;   /* modulus (of modLen BNU_CHUNK_T size)   */
   BNU_CHUNK_T*   pIdentity;  /* mont_enc(1)                            */
   BNU_CHUNK_T*   pSquare;    /* mont_enc(R^2)                          */
   BNU_CHUNK_T*   pCube;      /* mont_enc(R^3)                          */
   BNU_CHUNK_T*   pTBuffer;   /* internal buffer  modLen BNU_CHUNK_T    */
   BNU_CHUNK_T*   pSBuffer;   /* internal buffer  modLen BNU_CHUNK_T    */
   BNU_CHUNK_T*   pProduct;   /* internal product (2*modLen BNU_CHUNK_T)*/
   BNU_CHUNK_T*   pKBuffer;   /* mul/sqr buffer (Karatsuba method used) */
};

/* accessory macros */
#define MNT_ID(eng)       ((eng)->idCtx)
#define MNT_ROOM(eng)     ((eng)->maxLen)
#define MNT_SIZE(eng)     ((eng)->modLen)
#define MNT_HELPER(eng)   ((eng)->m0)
#define MNT_MODULUS(eng)  ((eng)->pModulus)
#define MNT_1(eng)        ((eng)->pIdentity)
#define MNT_IDENT_R(eng)  (MNT_1((eng)))
#define MNT_SQUARE_R(eng) ((eng)->pSquare)
#define MNT_CUBE_R(eng)   ((eng)->pCube)
#define MNT_TBUFFER(eng)  ((eng)->pTBuffer)
#define MNT_SBUFFER(eng)  ((eng)->pSBuffer)
#define MNT_PRODUCT(eng)  ((eng)->pProduct)
#define MNT_KBUFFER(eng)  ((eng)->pKBuffer)

#define MNT_VALID_ID(eng) (MNT_ID((eng))==idCtxMontgomery)

/* default methos */
#define EXPONENT_METHOD    (ippBinaryMethod)

/* alignment */
#define MONT_ALIGNMENT  ((int)(sizeof(void*)))


/*
// Pacp/unpack Montgomery context
*/
void cpPackMontCtx(const IppsMontState* pCtx, Ipp8u* pBuffer);
void cpUnpackMontCtx(const Ipp8u* pBuffer, IppsMontState* pCtx);


/*
// Montgomery reduction, multiplication and squaring
*/
//void cpMontRed_BNU(BNU_CHUNK_T* pR,
//                   BNU_CHUNK_T* pProduct,
//             const BNU_CHUNK_T* pModulus, cpSize nsM, BNU_CHUNK_T m0);
void cpMontRedAdc_BNU(BNU_CHUNK_T* pR,
                      BNU_CHUNK_T* pProduct,
                const BNU_CHUNK_T* pModulus, cpSize nsM, BNU_CHUNK_T m0);
void cpMontRedAdx_BNU(BNU_CHUNK_T* pR,
                      BNU_CHUNK_T* pProduct,
                const BNU_CHUNK_T* pModulus, cpSize nsM, BNU_CHUNK_T m0);

__INLINE void cpMontRed_BNU(BNU_CHUNK_T* pR,
                            BNU_CHUNK_T* pProduct,
                      const BNU_CHUNK_T* pModulus, cpSize nsM, BNU_CHUNK_T m0)
{
#if 0
#if(_IPP32E < _IPP32E_L9)
   cpMontRedAdc_BNU(pR, pProduct, pModulus, nsM, m0);
#else
   IsFeatureEnabled(ADCOX_ENABLED)? cpMontRedAdx_BNU(pR, pProduct, pModulus, nsM, m0)
                                  : cpMontRedAdc_BNU(pR, pProduct, pModulus, nsM, m0);
#endif
#endif

#if(_ADCOX_NI_ENABLING_==_FEATURE_ON_)
   cpMontRedAdx_BNU(pR, pProduct, pModulus, nsM, m0);
#elif(_ADCOX_NI_ENABLING_==_FEATURE_TICKTOCK_)
   IsFeatureEnabled(ADCOX_ENABLED)? cpMontRedAdx_BNU(pR, pProduct, pModulus, nsM, m0)
                                  : cpMontRedAdc_BNU(pR, pProduct, pModulus, nsM, m0);
#else
   cpMontRedAdc_BNU(pR, pProduct, pModulus, nsM, m0);
#endif
}

__INLINE void cpMontMul_BNU(BNU_CHUNK_T* pR,
                      const BNU_CHUNK_T* pX, cpSize nsX,
                      const BNU_CHUNK_T* pY, cpSize nsY,
                      const BNU_CHUNK_T* pModulus, cpSize nsM, BNU_CHUNK_T m0,
                            BNU_CHUNK_T* pProduct, BNU_CHUNK_T* pKBuffer)
{
   cpMul_BNU(pProduct, pX,nsX, pY,nsY, pKBuffer);
   ZEXPAND_BNU(pProduct,nsX+nsY, 2*nsM);
   cpMontRed_BNU(pR, pProduct, pModulus, nsM, m0);
}

__INLINE void cpMontSqr_BNU(BNU_CHUNK_T* pR,
                      const BNU_CHUNK_T* pX, cpSize nsX,
                      const BNU_CHUNK_T* pModulus, cpSize nsM, BNU_CHUNK_T m0,
                            BNU_CHUNK_T* pProduct, BNU_CHUNK_T* pKBuffer)
{
   cpSqr_BNU(pProduct, pX,nsX, pKBuffer);
   ZEXPAND_BNU(pProduct, 2*nsX, 2*nsM);
   cpMontRed_BNU(pR, pProduct, pModulus, nsM, m0);
}

/*
// Montgomery encoding/decoding
*/
__INLINE cpSize cpMontEnc_BNU(BNU_CHUNK_T* pR,
                        const BNU_CHUNK_T* pXreg, cpSize nsX,
                              IppsMontState* pMont)
{
   cpSize nsM = MNT_SIZE(pMont);
   cpMontMul_BNU(pR,
                 pXreg, nsX, MNT_SQUARE_R(pMont), nsM,
                 MNT_MODULUS(pMont), nsM, MNT_HELPER(pMont),
                 MNT_PRODUCT(pMont), MNT_KBUFFER(pMont));

   FIX_BNU(pR, nsM);
   return nsM;
}

__INLINE cpSize cpMontDec_BNU(BNU_CHUNK_T* pR,
                        const BNU_CHUNK_T* pXmont, cpSize nsX,
                              IppsMontState* pMont)
{
   cpSize nsM = MNT_SIZE(pMont);
   ZEXPAND_COPY_BNU(MNT_PRODUCT(pMont), 2*nsM, pXmont, nsX);

   cpMontRed_BNU(pR, MNT_PRODUCT(pMont), MNT_MODULUS(pMont), nsM, MNT_HELPER(pMont));

   FIX_BNU(pR, nsM);
   return nsM;
}

__INLINE void cpMontEnc_BN(IppsBigNumState* pRbn,
                     const IppsBigNumState* pXbn,
                           IppsMontState* pMont)
{
   BNU_CHUNK_T* pR = BN_NUMBER(pRbn);
   cpSize nsM = MNT_SIZE(pMont);
   cpMontMul_BNU(pR,
                 BN_NUMBER(pXbn), BN_SIZE(pXbn),
                 MNT_SQUARE_R(pMont), nsM,
                 MNT_MODULUS(pMont), nsM, MNT_HELPER(pMont),
                 MNT_PRODUCT(pMont), MNT_KBUFFER(pMont));

   FIX_BNU(pR, nsM);
   BN_SIZE(pRbn) = nsM;
   BN_SIGN(pRbn) = ippBigNumPOS;
}

__INLINE void cpMontDec_BN(IppsBigNumState* pRbn,
                     const IppsBigNumState* pXbn,
                           IppsMontState* pMont)
{
   BNU_CHUNK_T* pR = BN_NUMBER(pRbn);
   cpSize nsM = MNT_SIZE(pMont);
   ZEXPAND_COPY_BNU(MNT_PRODUCT(pMont), 2*nsM, BN_NUMBER(pXbn), BN_SIZE(pXbn));

   cpMontRed_BNU(pR, MNT_PRODUCT(pMont), MNT_MODULUS(pMont), nsM, MNT_HELPER(pMont));

   FIX_BNU(pR, nsM);
   BN_SIZE(pRbn) = nsM;
   BN_SIGN(pRbn) = ippBigNumPOS;
}

#if 0
/*
// Size of scratch buffer, involved in MontExp operation
*/
cpSize cpMontExpScratchBufferSize(cpSize modulusBitSize,
                                  cpSize expBitSize,
                                  cpSize nExponents);
#endif

/*
// Montgomery exponentiation (binary) "fast" and "safe" versions
*/
cpSize cpMontExpBin_BNU_sscm(BNU_CHUNK_T* pY,
                       const BNU_CHUNK_T* pX, cpSize nsX,
                       const BNU_CHUNK_T* pE, cpSize nsE,
                       IppsMontState* pMont);

cpSize cpMontExpBin_BNU(BNU_CHUNK_T* pY,
                  const BNU_CHUNK_T* pX, cpSize nsX,
                  const BNU_CHUNK_T* pE, cpSize nsE,
                        IppsMontState* pMont);

__INLINE void cpMontExpBin_BN_sscm(IppsBigNumState* pYbn,
                             const IppsBigNumState* pXbn,
                             const IppsBigNumState* pEbn,
                                   IppsMontState* pMont)
{
   BNU_CHUNK_T* pX = BN_NUMBER(pXbn);
   cpSize nsX = BN_SIZE(pXbn);
   BNU_CHUNK_T* pE = BN_NUMBER(pEbn);
   cpSize nsE = BN_SIZE(pEbn);
   BNU_CHUNK_T* pY = BN_NUMBER(pYbn);
   cpSize nsY = cpMontExpBin_BNU_sscm(pY, pX,nsX, pE,nsE, pMont);
   FIX_BNU(pY, nsY);
   BN_SIZE(pYbn) = nsY;
   BN_SIGN(pYbn) = ippBigNumPOS;
}

__INLINE void cpMontExpBin_BN(IppsBigNumState* pYbn,
                        const IppsBigNumState* pXbn,
                        const IppsBigNumState* pEbn,
                              IppsMontState* pMont)
{
   BNU_CHUNK_T* pX = BN_NUMBER(pXbn);
   cpSize nsX = BN_SIZE(pXbn);
   BNU_CHUNK_T* pE = BN_NUMBER(pEbn);
   cpSize nsE = BN_SIZE(pEbn);
   BNU_CHUNK_T* pY = BN_NUMBER(pYbn);
   cpSize nsY = cpMontExpBin_BNU(pY, pX,nsX, pE,nsE, pMont);
   FIX_BNU(pY, nsY);
   BN_SIZE(pYbn) = nsY;
   BN_SIGN(pYbn) = ippBigNumPOS;
}


/*
// Montgomery exponentiation (fixed window)
*/
cpSize cpMontExp_WinSize(int bitsize);

#if defined(_USE_WINDOW_EXP_)
void cpMontExpWin_BN_sscm(IppsBigNumState* pY,
                    const IppsBigNumState* pX, const IppsBigNumState* pE,
                          IppsMontState* pMont,
                          BNU_CHUNK_T* pPrecompResource);

void cpMontExpWin_BN(IppsBigNumState* pY,
               const IppsBigNumState* pX, const IppsBigNumState* pE,
                     IppsMontState* pMont,
                     BNU_CHUNK_T* pPrecompResource);
#endif

/*
// Montgomery multi-exponentiation
*/
/* precompute table for multi-exponentiation */
void cpMontMultiExpInitArray(BNU_CHUNK_T* pPrecomTbl,
              const BNU_CHUNK_T** ppX, cpSize xItemBitSize, cpSize numItems,
              IppsMontState* pMont);

/* multi-exponentiation */
void cpFastMontMultiExp(BNU_CHUNK_T* pY,
                  const BNU_CHUNK_T* pPrecomTbl,
                  const Ipp8u** ppE, cpSize eItemBitSize, cpSize numItems,
                  IppsMontState* pMont);

#endif /* _CP_MONTGOMETRY_H */
