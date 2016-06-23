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

#include "owndefs.h"
#include "owncp.h"
#include "pcpscramble.h"
#include "pcpngrsa.h"
#include "pcpngrsamontstuff.h"


/*
// Montgomery engine preparation (GetSize/init/Set)
*/
void gsMontGetSize(IppsExpMethod method, int maxLen32, int* pSize)
{
   cpSize modSize = INTERNAL_BNU_LENGTH(maxLen32);

   UNREFERENCED_PARAMETER(method);

   *pSize = sizeof(IppsMontState)
           + modSize*sizeof(BNU_CHUNK_T)    /* modulus  */
           + modSize*sizeof(BNU_CHUNK_T)    /* identity */
           + modSize*sizeof(BNU_CHUNK_T)    /* square R */
           + modSize*sizeof(BNU_CHUNK_T)    /* just to compute R^2 */
           + MONT_ALIGNMENT-1;
}

void gsMontInit(IppsExpMethod method, int maxLen32, IppsMontState* pMont)
{
   UNREFERENCED_PARAMETER(method);

   MNT_ID(pMont)     = idCtxMontgomery;
   MNT_ROOM(pMont)   = INTERNAL_BNU_LENGTH(maxLen32);
   MNT_SIZE(pMont)   = 0;
   MNT_HELPER(pMont) = 0;

   MNT_CUBE_R(pMont) = NULL;
   MNT_TBUFFER(pMont) = NULL;
   MNT_SBUFFER(pMont) = NULL;
   MNT_PRODUCT(pMont) = NULL;
   MNT_KBUFFER(pMont) = NULL;

   {
      Ipp8u* ptr = (Ipp8u*)pMont;

      /* modulus length in BNU_CHUNK_T */
      cpSize modSize = MNT_ROOM(pMont);

      /* assign internal buffers */
      MNT_MODULUS(pMont) = (BNU_CHUNK_T*)( ptr += sizeof(IppsMontState) );
      MNT_1(pMont)       = (BNU_CHUNK_T*)( ptr += modSize*sizeof(BNU_CHUNK_T) );
      MNT_SQUARE_R(pMont)= (BNU_CHUNK_T*)( ptr += modSize*sizeof(BNU_CHUNK_T) );

      /* init internal buffers */
      ZEXPAND_BNU(MNT_MODULUS(pMont), 0, modSize);
      ZEXPAND_BNU(MNT_1(pMont), 0, modSize);
      ZEXPAND_BNU(MNT_SQUARE_R(pMont), 0, modSize);
   }
}

static BNU_CHUNK_T cpMontHelper(BNU_CHUNK_T m0)
{
   BNU_CHUNK_T y = 1;
   BNU_CHUNK_T x = 2;
   BNU_CHUNK_T mask = 2*x-1;

   int i;
   for(i=2; i<=BNU_CHUNK_BITS; i++, x<<=1) {
      BNU_CHUNK_T rH, rL;
      MUL_AB(rH, rL, m0, y);
      if( x < (rL & mask) ) /* x < ((m0*y) mod (2*x)) */
         y+=x;
      mask += mask + 1;
   }
   return 0-y;
}

void gsMontSet(const Ipp32u* pModulus, int len32, IppsMontState* pMont)
{
   BNU_CHUNK_T m0;
   cpSize len;

   /* store modulus */
   ZEXPAND_COPY_BNU((Ipp32u*)(MNT_MODULUS(pMont)), MNT_ROOM(pMont)*(int)(sizeof(BNU_CHUNK_T)/sizeof(Ipp32u)), pModulus, len32);
   /* store modulus length */
   len = INTERNAL_BNU_LENGTH(len32);
   MNT_SIZE(pMont) = len;

   /* pre-compute helper m0, m0*m = -1 mod R */
   m0 = cpMontHelper(MNT_MODULUS(pMont)[0]);
   MNT_HELPER(pMont) = m0;

   /* setup identity */
   ZEXPAND_BNU(MNT_1(pMont), 0, len);
   MNT_1(pMont)[len] = 1;
   cpMod_BNU(MNT_1(pMont), len+1, MNT_MODULUS(pMont), len);

   /* setup square */
   ZEXPAND_BNU(MNT_SQUARE_R(pMont), 0, len);
   COPY_BNU(MNT_SQUARE_R(pMont)+len, MNT_1(pMont), len);
   cpMod_BNU(MNT_SQUARE_R(pMont), 2*len, MNT_MODULUS(pMont), len);
}


/*
// "fast" binary montgomery exponentiation
//
// scratch buffer structure:
//    precomutation resource[0]
//    copy of base (in case of inplace operation)
//    product[nsM*2]
//    karatsubaBuffer[gsKaratsubaBufferSize()]
*/
cpSize gsMontExpBin_BNU(BNU_CHUNK_T* dataY,
                  const BNU_CHUNK_T* dataX, cpSize nsX,
                  const BNU_CHUNK_T* dataE, cpSize nsE,
                  const IppsMontState* pMont,
                  BNU_CHUNK_T* pBuffer)
{
   cpSize nsM = MNT_SIZE(pMont);

   /*
   // test for special cases:
   //    x^0 = 1
   //    0^e = 0
   */
   if( cpEqu_BNU_CHUNK(dataE, nsE, 0) ) {
      COPY_BNU(dataY, MNT_1(pMont), nsM);
   }
   else if( cpEqu_BNU_CHUNK(dataX, nsX, 0) ) {
      ZEXPAND_BNU(dataY, 0, nsM);
   }

   /* general case */
   else {
      BNU_CHUNK_T* dataM = MNT_MODULUS(pMont);
      BNU_CHUNK_T m0 = MNT_HELPER(pMont);

      /* allocate buffers */
      BNU_CHUNK_T* dataT = pBuffer;
      BNU_CHUNK_T* pProduct = dataT+nsM;
      BNU_CHUNK_T* pBufferMulK = NULL;
      BNU_CHUNK_T* pBufferSqrK = NULL;

      /* expand base and init result */
      ZEXPAND_COPY_BNU(dataT, nsM, dataX, nsX);
      COPY_BNU(dataY, dataT, nsM);

      FIX_BNU(dataE, nsE);

      /* execute most significant part pE */
      {
         BNU_CHUNK_T eValue = dataE[nsE-1];
         int n = cpNLZ_BNU(eValue)+1;

         eValue <<= n;
         for(; n<BNU_CHUNK_BITS; n++, eValue<<=1) {
            /* squaring R = R*R mod Modulus */
            cpMontSqr_BNU(dataY,
                          dataY, nsM,
                          dataM, nsM, m0,
                          pProduct, pBufferSqrK);
            /* and multiply R = R*X mod Modulus */
            if(eValue & ((BNU_CHUNK_T)1<<(BNU_CHUNK_BITS-1)))
               cpMontMul_BNU(dataY,
                             dataY, nsM,
                             dataT, nsM,
                             dataM, nsM, m0,
                             pProduct, pBufferMulK);
         }

         /* execute rest bits of E */
         for(--nsE; nsE>0; nsE--) {
            eValue = dataE[nsE-1];

            for(n=0; n<BNU_CHUNK_BITS; n++, eValue<<=1) {
               /* squaring: R = R*R mod Modulus */
               cpMontSqr_BNU(dataY,
                             dataY, nsM,
                             dataM, nsM, m0,
                             pProduct, pBufferSqrK);
               if(eValue & ((BNU_CHUNK_T)1<<(BNU_CHUNK_BITS-1)))
                  cpMontMul_BNU(dataY,
                                dataY, nsM,
                                dataT, nsM,
                                dataM, nsM, m0,
                                pProduct, pBufferMulK);
            }
         }
      }
   }

   return nsM;
}


/*
// "safe" binary montgomery exponentiation
//
// scratch buffer structure:
//    sscm[nsM]
//    dataT[nsM]
//    product[nsM*2]
//    karatsubaBuffer[gsKaratsubaBufferSize()]
*/
cpSize gsMontExpBin_BNU_sscm(BNU_CHUNK_T* dataY,
                  const BNU_CHUNK_T* dataX, cpSize nsX,
                  const BNU_CHUNK_T* dataE, cpSize nsE,
                  const IppsMontState* pMont,
                  BNU_CHUNK_T* pBuffer)
{
   cpSize nsM = MNT_SIZE(pMont);

   /*
   // test for special cases:
   //    x^0 = 1
   //    0^e = 0
   */
   if( cpEqu_BNU_CHUNK(dataE, nsE, 0) ) {
      COPY_BNU(dataY, MNT_1(pMont), nsM);
   }
      else if( cpEqu_BNU_CHUNK(dataX, nsX, 0) ) {
      ZEXPAND_BNU(dataY, 0, nsM);
   }

   /* general case */
   else {
      BNU_CHUNK_T* dataM = MNT_MODULUS(pMont);
      BNU_CHUNK_T m0 = MNT_HELPER(pMont);

      /* allocate buffers */
      BNU_CHUNK_T* sscmBuffer = pBuffer;
      BNU_CHUNK_T* dataT = sscmBuffer+nsM;

      BNU_CHUNK_T* pProduct = dataT+nsM;
      BNU_CHUNK_T* pBufferMulK = NULL;

      cpSize i;
      BNU_CHUNK_T mask_pattern;

      /* execute most significant part pE */
      BNU_CHUNK_T eValue = dataE[nsE-1];
      int j = BNU_CHUNK_BITS - cpNLZ_BNU(eValue)-1;

      int back_step = 0;

      /* expand base and init result */
      ZEXPAND_COPY_BNU(dataT, nsM, dataX, nsX);
      COPY_BNU(dataY, dataT, nsM);

      for(j-=1; j>=0; j--) {
         mask_pattern = (BNU_CHUNK_T)(back_step-1);

         /* safeBuffer = (Y[] and mask_pattern) or (X[] and ~mask_pattern) */
         for(i=0; i<nsM; i++)
            sscmBuffer[i] = (dataY[i] & mask_pattern) | (dataT[i] & ~mask_pattern);

         /* squaring/multiplication: R = R*T mod Modulus */
         cpMontMul_BNU(dataY,
                       dataY, nsM,
                       sscmBuffer, nsM,
                       dataM,  nsM, m0,
                       pProduct, pBufferMulK);

         /* update back_step and j */
         back_step = ((eValue>>j) & 0x1) & (back_step^1);
         j += back_step;
      }

      /* execute rest bits of E */
      for(--nsE; nsE>0; nsE--) {
         eValue = dataE[nsE-1];

         for(j=BNU_CHUNK_BITS-1; j>=0; j--) {
            mask_pattern = (BNU_CHUNK_T)(back_step-1);

            /* safeBuffer = (Y[] and mask_pattern) or (X[] and ~mask_pattern) */
            for(i=0; i<nsM; i++)
               sscmBuffer[i] = (dataY[i] & mask_pattern) | (dataT[i] & ~mask_pattern);

            /* squaring/multiplication: R = R*T mod Modulus */
            cpMontMul_BNU(dataY,
                          dataY, nsM,
                          sscmBuffer, nsM,
                          dataM,  nsM, m0,
                          pProduct, pBufferMulK);

            /* update back_step and j */
            back_step = ((eValue>>j) & 0x1) & (back_step^1);
            j += back_step;
         }
      }
   }

   return nsM;
}


/*
// "fast" fixed-size window montgomery exponentiation
//
// scratch buffer structure:
//    precomutation resource[(1<<w)nsM*sizeof(BNU_CHUNK_T)]
//    dataE[nsM]
//    product[nsM*2]
//    karatsubaBuffer[gsKaratsubaBufferSize()]
*/
cpSize gsMontExpWin_BNU(BNU_CHUNK_T* dataY,
                  const BNU_CHUNK_T* dataX, cpSize nsX,
                  const BNU_CHUNK_T* dataExp, cpSize nsE, cpSize wBitSize,
                  const IppsMontState* pMont,
                        BNU_CHUNK_T* pBuffer)
{
   cpSize nsM = MNT_SIZE(pMont);

   /*
   // test for special cases:
   //    x^0 = 1
   //    0^e = 0
   */
   if( cpEqu_BNU_CHUNK(dataExp, nsE, 0) ) {
      COPY_BNU(dataY, MNT_1(pMont), nsM);
   }
   else if( cpEqu_BNU_CHUNK(dataX, nsX, 0) ) {
      ZEXPAND_BNU(dataY, 0, nsM);
   }

   /* general case */
   else {
      BNU_CHUNK_T* dataM = MNT_MODULUS(pMont);
      BNU_CHUNK_T m0 = MNT_HELPER(pMont);

      cpSize nPrecomute= 1<<wBitSize;

      /* allocate buffers */
      BNU_CHUNK_T* pResource = pBuffer;
      BNU_CHUNK_T* dataE = pResource + nPrecomute*nsM;
      BNU_CHUNK_T* pProduct = dataE+nsM;
      BNU_CHUNK_T* pBufferMulK = NULL;
      BNU_CHUNK_T* pBufferSqrK = NULL;

      /* fixed window param */
      cpSize bitsizeE = BITSIZE_BNU(dataExp, nsE);
      BNU_CHUNK_T mask = nPrecomute -1;
      int n;

      /* expand base */
      ZEXPAND_COPY_BNU(dataY, nsM, dataX, nsX);

      /* initialize recource */
      COPY_BNU(pResource+0, MNT_1(pMont), nsM);
      COPY_BNU(pResource+nsM, dataY, nsM);
      for(n=2; n<nPrecomute; n++) {
         cpMul_BNU(pProduct, pResource+(n-1)*nsM, nsM, dataY, nsM, pBufferMulK);
         cpMontRed_BNU(pResource+n*nsM, pProduct, dataM, nsM, m0);
      }

      /* expand exponent*/
      ZEXPAND_COPY_BNU(dataE, nsE+1, dataExp, nsE);
      bitsizeE = ((bitsizeE+wBitSize-1)/wBitSize) *wBitSize;

      /* exponentiation */
      {
         /* position of the 1-st (left) window */
         int eBit = bitsizeE-wBitSize;

         /* extract 1-st window value */
         Ipp32u eChunk = *((Ipp32u*)((Ipp16u*)dataE + eBit/BITSIZE(Ipp16u)));
         int shift = eBit & 0xF;
         Ipp32u windowVal = (eChunk>>shift) &mask;

         /* initialize result */
         COPY_BNU(dataY, pResource+windowVal*nsM, nsM);

         for(eBit-=wBitSize; eBit>=0; eBit-=wBitSize) {
            /* do square window times */
            for(n=0,windowVal=0; n<wBitSize; n++) {
               cpSqr_BNU(pProduct, dataY, nsM, pBufferSqrK);
               cpMontRed_BNU(dataY, pProduct, dataM, nsM, m0);
            }

            /* extract next window value */
            eChunk = *((Ipp32u*)((Ipp16u*)dataE + eBit/BITSIZE(Ipp16u)));
            shift = eBit & 0xF;
            windowVal = (eChunk>>shift) &mask;

            if(windowVal) {
               /* extract precomputed value and muptiply */
               cpMul_BNU(pProduct, dataY, nsM, pResource+windowVal*nsM, nsM, pBufferMulK);
               cpMontRed_BNU(dataY, pProduct, dataM, nsM, m0);
            }
         }
      }
   }

   return nsM;
}


/*
// "safe" fixed-size window montgomery exponentiation
//
// scratch buffer structure:
//    precomutation resource[(1<<w)nsM*sizeof(BNU_CHUNK_T)]
//    dataT[nsM]
//    dataE[nsM]
//    product[nsM*2]
//    karatsubaBuffer[gsKaratsubaBufferSize()]
*/
cpSize gsMontExpWin_BNU_sscm(BNU_CHUNK_T* dataY,
                       const BNU_CHUNK_T* dataX, cpSize nsX,
                       const BNU_CHUNK_T* dataExp, cpSize nsE, cpSize bitsizeEwin,
                       const IppsMontState* pMont,
                             BNU_CHUNK_T* pBuffer)
{
   cpSize nsM = MNT_SIZE(pMont);

   /*
   // test for special cases:
   //    x^0 = 1
   //    0^e = 0
   */
   if( cpEqu_BNU_CHUNK(dataExp, nsE, 0) ) {
      COPY_BNU(dataY, MNT_1(pMont), nsM);
   }
   else if( cpEqu_BNU_CHUNK(dataX, nsX, 0) ) {
      ZEXPAND_BNU(dataY, 0, nsM);
   }

   /* general case */
   else {
      BNU_CHUNK_T* dataM = MNT_MODULUS(pMont);
      BNU_CHUNK_T m0 = MNT_HELPER(pMont);

      cpSize nPrecomute= 1<<bitsizeEwin;
      cpSize chunkSize = CACHE_LINE_SIZE/nPrecomute;

      /* allocate buffers */
      BNU_CHUNK_T* pResource = pBuffer;
      BNU_CHUNK_T* dataE = pResource + gsPrecompResourcelen(nPrecomute, nsM);//+nPrecomute*nsM;
      BNU_CHUNK_T* dataT = dataE+nsM;
      BNU_CHUNK_T* pProduct = dataT+nsM;
      BNU_CHUNK_T* pBufferMulK = NULL;
      BNU_CHUNK_T* pBufferSqrK = NULL;

      /* fixed window param */
      cpSize bitsizeE = BITSIZE_BNU(dataExp, nsE);
      BNU_CHUNK_T mask = nPrecomute -1;
      int n;

      /* expand base */
      ZEXPAND_COPY_BNU(dataY, nsM, dataX, nsX);

      /* initialize recource */
      cpScramblePut(((Ipp8u*)pResource)+0, chunkSize, (Ipp32u*)MNT_1(pMont), nsM*sizeof(BNU_CHUNK_T)/sizeof(Ipp32u));
      ZEXPAND_COPY_BNU(dataT, nsM, dataX, nsX);
      cpScramblePut(((Ipp8u*)pResource)+chunkSize, chunkSize, (Ipp32u*)dataT, nsM*sizeof(BNU_CHUNK_T)/sizeof(Ipp32u));
      for(n=2; n<nPrecomute; n++) {
         cpMul_BNU(pProduct, dataT, nsM, dataY, nsM, pBufferMulK);
         cpMontRed_BNU(dataT, pProduct, dataM, nsM, m0);
         cpScramblePut(((Ipp8u*)pResource)+n*chunkSize, chunkSize, (Ipp32u*)dataT, nsM*sizeof(BNU_CHUNK_T)/sizeof(Ipp32u));
      }

      /* expand exponent*/
      ZEXPAND_COPY_BNU(dataE, nsE+1, dataExp, nsE);
      bitsizeE = ((bitsizeE+bitsizeEwin-1)/bitsizeEwin) *bitsizeEwin;

      /* exponentiation */
      {
         /* position of the 1-st (left) window */
         int eBit = bitsizeE-bitsizeEwin;

         /* extract 1-st window value */
         Ipp32u eChunk = *((Ipp32u*)((Ipp16u*)dataE + eBit/BITSIZE(Ipp16u)));
         int shift = eBit & 0xF;
         Ipp32u windowVal = (eChunk>>shift) &mask;

         /* initialize result */
         cpScrambleGet((Ipp32u*)dataY, nsM*sizeof(BNU_CHUNK_T)/sizeof(Ipp32u), ((Ipp8u*)pResource)+windowVal*chunkSize, chunkSize);

         for(eBit-=bitsizeEwin; eBit>=0; eBit-=bitsizeEwin) {
            /* do square window times */
            for(n=0,windowVal=0; n<bitsizeEwin; n++) {
               cpSqr_BNU(pProduct, dataY, nsM, pBufferSqrK);
               cpMontRed_BNU(dataY, pProduct, dataM, nsM, m0);
            }

            /* extract next window value */
            eChunk = *((Ipp32u*)((Ipp16u*)dataE + eBit/BITSIZE(Ipp16u)));
            shift = eBit & 0xF;
            windowVal = (eChunk>>shift) &mask;

            /* exptact precomputed value and muptiply */
            cpScrambleGet((Ipp32u*)dataT, nsM*sizeof(BNU_CHUNK_T)/sizeof(Ipp32u), ((Ipp8u*)pResource)+windowVal*chunkSize, chunkSize);

            cpMul_BNU(pProduct, dataY, nsM, dataT, nsM, pBufferMulK);
            cpMontRed_BNU(dataY, pProduct, dataM, nsM, m0);
         }
      }
   }

   return nsM;
}
