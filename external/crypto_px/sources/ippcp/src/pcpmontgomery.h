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
void cpMontRedAdc_BNU(BNU_CHUNK_T* pR,
                      BNU_CHUNK_T* pProduct,
                const BNU_CHUNK_T* pModulus, cpSize nsM, BNU_CHUNK_T m0);

__INLINE void cpMontRed_BNU(BNU_CHUNK_T* pR,
                            BNU_CHUNK_T* pProduct,
                      const BNU_CHUNK_T* pModulus, cpSize nsM, BNU_CHUNK_T m0)
{
   cpMontRedAdc_BNU(pR, pProduct, pModulus, nsM, m0);
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

/*
// Montgomery exponentiation (binary)
*/
cpSize cpMontExpBin_BNU(BNU_CHUNK_T* pY,
                  const BNU_CHUNK_T* pX, cpSize nsX,
                  const BNU_CHUNK_T* pE, cpSize nsE,
                        IppsMontState* pMont);

#endif /* _CP_MONTGOMETRY_H */
