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

#if !defined(_CP_NG_RSA_MONT_STUFF_H)
#define _CP_NG_RSA_MONT_STUFF_H

#include "pcpbn.h"
#include "pcpmontgomery.h"

/*
// Montgomery engine preparation (GetSize/init/Set)
*/
void gsMontGetSize(IppsExpMethod method, int length, int* pSize);
void gsMontInit(IppsExpMethod method, int length, IppsMontState* pCtx);
void gsMontSet(const Ipp32u* pModulo, int size, IppsMontState* pCtx);


/*
// optimal size of fixed window exponentiation
*/
__INLINE cpSize gsMontExp_WinSize(cpSize bitsize)
{
   return
         bitsize> 4096? 6 :    /* 4096- .. .  */
         bitsize> 2666? 5 :    /* 2666 - 4095 */
         bitsize>  717? 4 :    /*  717 - 2665 */
         bitsize>  178? 3 :    /*  178 - 716  */
         bitsize>   41? 2 : 1; /*   41 - 177  */
}

/*
// Montgomery encoding/decoding
*/
__INLINE cpSize gsMontEnc_BNU(BNU_CHUNK_T* pR,
                        const BNU_CHUNK_T* pXreg, cpSize nsX,
                        const IppsMontState* pMont,
                              BNU_CHUNK_T* pBuffer)
{
   cpSize nsM = MNT_SIZE(pMont);
   BNU_CHUNK_T* pProduct = pBuffer;
   BNU_CHUNK_T* pBufferKmul = NULL;

   cpMontMul_BNU(pR,
                 pXreg, nsX, MNT_SQUARE_R(pMont), nsM,
                 MNT_MODULUS(pMont), nsM, MNT_HELPER(pMont),
                 pProduct, pBufferKmul);
   return nsM;
}

__INLINE cpSize gsMontDec_BNU(BNU_CHUNK_T* pR,
                        const BNU_CHUNK_T* pXmont, cpSize nsX,
                        const IppsMontState* pMont,
                              BNU_CHUNK_T* pBuffer)
{
   cpSize nsM = MNT_SIZE(pMont);
   ZEXPAND_COPY_BNU(pBuffer, 2*nsM, pXmont, nsX);

   cpMontRed_BNU(pR, pBuffer, MNT_MODULUS(pMont), nsM, MNT_HELPER(pMont));
   return nsM;
}

__INLINE void gsMontEnc_BN(IppsBigNumState* pRbn,
                     const IppsBigNumState* pXbn,
                     const IppsMontState* pMont,
                           BNU_CHUNK_T* pBuffer)
{
   BNU_CHUNK_T* pR = BN_NUMBER(pRbn);
   cpSize nsM = MNT_SIZE(pMont);

   gsMontEnc_BNU(pR, BN_NUMBER(pXbn), BN_SIZE(pXbn), pMont, pBuffer);

   FIX_BNU(pR, nsM);
   BN_SIZE(pRbn) = nsM;
   BN_SIGN(pRbn) = ippBigNumPOS;
}

__INLINE void gsMontDec_BN(IppsBigNumState* pRbn,
                     const IppsBigNumState* pXbn,
                     const IppsMontState* pMont,
                           BNU_CHUNK_T* pBuffer)
{
   BNU_CHUNK_T* pR = BN_NUMBER(pRbn);
   cpSize nsM = MNT_SIZE(pMont);

   gsMontDec_BNU(pR, BN_NUMBER(pXbn), BN_SIZE(pXbn), pMont, pBuffer);

   FIX_BNU(pR, nsM);
   BN_SIZE(pRbn) = nsM;
   BN_SIGN(pRbn) = ippBigNumPOS;
}


/*
// binary montgomery exponentiation ("fast" version)
*/
cpSize gsMontExpBin_BNU(BNU_CHUNK_T* dataY,
                  const BNU_CHUNK_T* dataX, cpSize nsX,
                  const BNU_CHUNK_T* dataE, cpSize nsE,
                  const IppsMontState* pMont,
                  BNU_CHUNK_T* pBuffer);

__INLINE void gsMontExpBin_BN(IppsBigNumState* pY,
                        const IppsBigNumState* pX,
                        const BNU_CHUNK_T* dataE, cpSize nsE,
                        const IppsMontState* pMont,
                              BNU_CHUNK_T* pBuffer)
{
   BNU_CHUNK_T* dataY = BN_NUMBER(pY);
   cpSize nsY = gsMontExpBin_BNU(dataY,
                                 BN_NUMBER(pX), BN_SIZE(pX),
                                 dataE, nsE,
                                 pMont, pBuffer);
   FIX_BNU(dataY, nsY);
   BN_SIZE(pY) = nsY;
   BN_SIGN(pY) = ippBigNumPOS;
}

/*
// fixed-size window montgomery exponentiation ("fast" version)
*/
cpSize gsMontExpWin_BNU(BNU_CHUNK_T* pY,
                 const BNU_CHUNK_T* pX, cpSize nsX,
                 const BNU_CHUNK_T* dataE, cpSize nsE, cpSize bitsieW,
                 const IppsMontState* pMont,
                       BNU_CHUNK_T* pBuffer);

__INLINE void gsMontExpWin_BN(IppsBigNumState* pY,
                        const IppsBigNumState* pX,
                        const BNU_CHUNK_T* dataE, cpSize nsE, cpSize bitsieW,
                        const IppsMontState* pMont,
                              BNU_CHUNK_T* pBuffer)
{
   BNU_CHUNK_T* dataY = BN_NUMBER(pY);
   cpSize nsY = gsMontExpWin_BNU(dataY,
                                 BN_NUMBER(pX), BN_SIZE(pX),
                                 dataE, nsE, bitsieW,
                                 pMont, pBuffer);
   FIX_BNU(dataY, nsY);
   BN_SIZE(pY) = nsY;
   BN_SIGN(pY) = ippBigNumPOS;
}

/*
// binary montgomery exponentiation ("safe" version)
*/
__INLINE cpSize gsPrecompResourcelen(int n, cpSize nsM)
{
   cpSize nsR = sizeof(BNU_CHUNK_T)*nsM*n + (CACHE_LINE_SIZE-1);
   nsR /=CACHE_LINE_SIZE;  /* num of cashe lines */
   nsR *= (CACHE_LINE_SIZE/sizeof(BNU_CHUNK_T));
   return nsR;
}

cpSize gsMontExpBin_BNU_sscm(BNU_CHUNK_T* pY,
                       const BNU_CHUNK_T* pX, cpSize nsX,
                       const BNU_CHUNK_T* pE, cpSize nsE,
                       const IppsMontState* pMont,
                             BNU_CHUNK_T* pBuffer);

__INLINE void gsMontExpBin_BN_sscm(IppsBigNumState* pY,
                             const IppsBigNumState* pX,
                             const BNU_CHUNK_T* dataE, cpSize nsE,
                             const IppsMontState* pMont,
                                   BNU_CHUNK_T* pBuffer)
{
   BNU_CHUNK_T* dataY = BN_NUMBER(pY);
   cpSize nsY = gsMontExpBin_BNU_sscm(dataY,
                                      BN_NUMBER(pX), BN_SIZE(pX),
                                      dataE, nsE,
                                      pMont, pBuffer);
   FIX_BNU(dataY, nsY);
   BN_SIZE(pY) = nsY;
   BN_SIGN(pY) = ippBigNumPOS;
}

/*
// fixed-size window montgomery exponentiation ("safe" version)
*/
cpSize gsMontExpWin_BNU_sscm(BNU_CHUNK_T* dataY,
                       const BNU_CHUNK_T* dataX, cpSize nsX,
                       const BNU_CHUNK_T* dataE, cpSize nsE, cpSize bitsieEwin,
                       const IppsMontState* pMont,
                             BNU_CHUNK_T* pBuffer);

__INLINE void gsMontExpWin_BN_sscm(IppsBigNumState* pY,
                    const IppsBigNumState* pX,
                    const BNU_CHUNK_T* dataE, cpSize nsE, cpSize bitsieEwin,
                    const IppsMontState* pMont,
                          BNU_CHUNK_T* pBuffer)
{
   BNU_CHUNK_T* dataY = BN_NUMBER(pY);
   cpSize nsY = gsMontExpWin_BNU_sscm(dataY,
                                      BN_NUMBER(pX), BN_SIZE(pX),
                                      dataE, nsE, bitsieEwin,
                                      pMont, pBuffer);
   FIX_BNU(dataY, nsY);
   BN_SIZE(pY) = nsY;
   BN_SIGN(pY) = ippBigNumPOS;
}

#endif /* _CP_NG_RSA_MONT_STUFF_H */
