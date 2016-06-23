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

#include "owncp.h"
#include "pcpbnumisc.h"
#include "pcpbnu32misc.h"
#include "pcpbnu32arith.h"


/*
// BNU32 addition
*/
Ipp32u cpAdd_BNU32(Ipp32u* pR, const Ipp32u* pA, const Ipp32u* pB, cpSize ns)
{
   Ipp32u carry = 0;
   cpSize i;
   for(i=0; i<ns; i++) {
      Ipp64u t = (Ipp64u)carry +pA[i] + pB[i];
      pR[i] = LODWORD(t);
      carry = HIDWORD(t);
   }
   return carry;
}

/*
// BNU32 subtraction
*/
Ipp32u cpSub_BNU32(Ipp32u* pR, const Ipp32u* pA, const Ipp32u* pB, cpSize ns)
{
   Ipp32u borrow = 0;
   cpSize i;
   for(i=0; i<ns; i++) {
      Ipp64u t = (Ipp64u)(pA[i]) - pB[i] - borrow;
      pR[i] = LODWORD(t);
      borrow = 0-HIDWORD(t);
   }
   return borrow;
}

/*
// BNU32 decrement
*/
Ipp32u cpDec_BNU32(Ipp32u* pR, const Ipp32u* pA, cpSize ns, Ipp32u v)
{
   Ipp32u borrow = v;
   int n;
   for(n=0; n<ns; n++) {
      Ipp64u t = (Ipp64u)(pA[n]) - (Ipp64u)borrow;
      pR[n] = LODWORD(t);
      borrow = HIDWORD(t)>>(32-1);
   }
   return borrow;
}

/*
// BNU32 mul_by_digit
*/
Ipp32u cpMulDgt_BNU32(Ipp32u* pR, const Ipp32u* pA, cpSize nsA, Ipp32u val)
{
   Ipp32u carry = 0;
   cpSize i;
   for(i=0; i<nsA; i++) {
      Ipp64u t = (Ipp64u)val * (Ipp64u)pA[i] + carry;
      pR[i] = LODWORD(t);
      carry = HIDWORD(t);
    }
    return carry;
}


/*
// BNU32 mul_by_digit_subtract
*/
Ipp32u cpSubMulDgt_BNU32(Ipp32u* pR, const Ipp32u* pA, cpSize nsA, Ipp32u val)
{
   Ipp32u carry = 0;
   for(; nsA>0; nsA--) {
      Ipp64u r = (Ipp64u)*pR - (Ipp64u)(*pA++) * val - carry;
      *pR++  = LODWORD(r);
      carry  = 0-HIDWORD(r);
   }
   return carry;
}

/*
// BNU32 division
*/
int cpDiv_BNU32(Ipp32u* pQ, cpSize* sizeQ,
                 Ipp32u* pX, cpSize sizeX,
                 Ipp32u* pY, cpSize sizeY)
{
   FIX_BNU(pY,sizeY);
   FIX_BNU(pX,sizeX);

   /* special case */
   if(sizeX < sizeY) {

      if(pQ) {
         pQ[0] = 0;
         *sizeQ = 1;
      }

      return sizeX;
   }

   /* special case */
   if(1 == sizeY) {
      int i;
      Ipp32u r = 0;
      for(i=(int)sizeX-1; i>=0; i--) {
         Ipp64u tmp = MAKEDWORD(pX[i],r);
         Ipp32u q = LODWORD(tmp / pY[0]);
         r = LODWORD(tmp - q*pY[0]);
         if(pQ) pQ[i] = q;
      }

      pX[0] = r;

      if(pQ) {
         FIX_BNU(pQ,sizeX);
         *sizeQ = sizeX;
      }

      return 1;
   }


   /* common case */
   {
      cpSize qs = sizeX-sizeY+1;

      cpSize nlz = cpNLZ_BNU32(pY[sizeY-1]);

      /* normalization */
      pX[sizeX] = 0;
      if(nlz) {
         cpSize ni;

         pX[sizeX] = pX[sizeX-1] >> (32-nlz);
         for(ni=sizeX-1; ni>0; ni--)
            pX[ni] = (pX[ni]<<nlz) | (pX[ni-1]>>(32-nlz));
         pX[0] <<= nlz;

         for(ni=sizeY-1; ni>0; ni--)
            pY[ni] = (pY[ni]<<nlz) | (pY[ni-1]>>(32-nlz));
         pY[0] <<= nlz;
      }

      /*
      // division
      */
      {
         Ipp32u yHi = pY[sizeY-1];

         int i;
         for(i=(int)qs-1; i>=0; i--) {
            Ipp32u extend;

            /* estimate digit of quotient */
            Ipp64u tmp = MAKEDWORD(pX[i+sizeY-1], pX[i+sizeY]);
            Ipp64u q = tmp / yHi;
            Ipp64u r = tmp - q*yHi;

            /* tune estimation above */
            for(; HIDWORD(q) || (Ipp64u)q*pY[sizeY-2] > MAKEDWORD(pX[i+sizeY-2],r); ) {
               q -= 1;
               r += yHi;
               if( HIDWORD(r) )
                  break;
            }

            /* multiply and subtract */
            extend = cpSubMulDgt_BNU32(pX+i, pY, sizeY, (Ipp32u)q);
            extend = (pX[i+sizeY] -= extend);

            if(extend) { /* subtracted too much */
               q -= 1;
               extend = cpAdd_BNU32(pX+i, pY, pX+i, sizeY);
               pX[i+sizeY] += extend;
            }

            /* store quotation digit */
            if(pQ) pQ[i] = LODWORD(q);
         }
      }

      /* de-normalization */
      if(nlz) {
         cpSize ni;
         for(ni=0; ni<sizeX; ni++)
            pX[ni] = (pX[ni]>>nlz) | (pX[ni+1]<<(32-nlz));
         for(ni=0; ni<sizeY-1; ni++)
            pY[ni] = (pY[ni]>>nlz) | (pY[ni+1]<<(32-nlz));
         pY[sizeY-1] >>= nlz;
      }

      FIX_BNU(pX,sizeX);

      if(pQ) {
         FIX_BNU(pQ,qs);
         *sizeQ = qs;
      }

      return sizeX;
   }
}
