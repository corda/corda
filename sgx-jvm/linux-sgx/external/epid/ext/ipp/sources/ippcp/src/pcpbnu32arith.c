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
//  Purpose:
//     Intel(R) Integrated Performance Primitives. Cryptography Primitives.
//     Internal BNU32 arithmetic.
// 
//  Contents:
// 
//     cpAdd_BNU32()
//     cpSub_BNU32()
// 
//     cpMulDgt_BNU32()
//     cpSubMulDgt_BNU32()
// 
//     cpDiv_BNU32()
// 
// 
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
// BNU32 increment
*/
Ipp32u cpInc_BNU32(Ipp32u* pR, const Ipp32u* pA, cpSize ns, Ipp32u v)
{
   Ipp32u carry = v;
   cpSize i;
   for(i=0; i<ns && carry; i++) {
      Ipp64u t = (Ipp64u)carry +pA[i];
      pR[i] = LODWORD(t);
      carry = HIDWORD(t);
   }
   return carry;
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
// BNU32 mul_by_digit_accumulate
*/
#if 0
Ipp32u cpAddMulDgt_BNU32(Ipp32u* pR, const Ipp32u* pA, cpSize nsA, Ipp32u val)
{
   Ipp32u extension = 0;
   for(; nsA>0; nsA--) {
      Ipp64u r = (Ipp64u)*pR + (Ipp64u)(*pA++) * val + extension;
      *pR++  = LODWORD(r);
      extension = HIDWORD(r);
   }
   return extension;
}
#endif

/*
// BNU32 mul_by_digit_subtract
*/
#if !((_IPP32E==_IPP32E_M7) || \
      (_IPP32E==_IPP32E_U8) || \
      (_IPP32E==_IPP32E_Y8) || \
      (_IPP32E>=_IPP32E_E9) || \
      (_IPPLP64==_IPPLP64_N8))
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
#endif

/*
// BNU32 division
*/
#if !((_IPP32E==_IPP32E_M7) || \
      (_IPP32E==_IPP32E_U8) || \
      (_IPP32E==_IPP32E_Y8) || \
      (_IPP32E>=_IPP32E_E9) || \
      (_IPPLP64==_IPPLP64_N8))
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
            //for(; (q>=CONST_64(0x100000000)) || (Ipp64u)q*pY[sizeY-2] > MAKEDWORD(pX[i+sizeY-2],r); ) {
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
#endif

#define FE_MUL(R,A,B,LEN) { \
   int aidx, bidx; \
   \
   for(aidx=0; aidx<(LEN); aidx++) (R)[aidx] = 0; \
   \
   for(bidx=0; bidx<(LEN); bidx++) { \
      Ipp64u b = (B)[bidx]; \
      Ipp32u c = 0; \
      for(aidx=0; aidx<(LEN); aidx++) { \
         Ipp64u t = (R)[bidx+aidx] + (A)[aidx] * b + c; \
         (R)[bidx+aidx] = LODWORD(t); \
         c = HIDWORD(t); \
      } \
      (R)[bidx+aidx] = c; \
   } \
}

#if 0
#if !((_IPP==_IPP_W7) || \
      (_IPP==_IPP_T7) || \
      (_IPP==_IPP_V8) || \
      (_IPP==_IPP_P8) || \
      (_IPP>=_IPP_G9) || \
      (_IPPLP32==_IPPLP32_S8) || \
      (_IPP32E==_IPP32E_M7) || \
      (_IPP32E==_IPP32E_U8) || \
      (_IPP32E==_IPP32E_Y8) || \
      (_IPP32E>=_IPP32E_E9) || \
      (_IPPLP64==_IPPLP64_N8))
void cpMul_BNU8(const Ipp32u* pA, const Ipp32u* pB, Ipp32u* pR)
{
   FE_MUL(pR, pA, pB, 8)
}
#endif
#endif

#if 0
#if !((_IPP32E==_IPP32E_M7) || \
      (_IPP32E==_IPP32E_U8) || \
      (_IPP32E==_IPP32E_Y8) || \
      (_IPP32E>=_IPP32E_E9) || \
      (_IPPLP64==_IPPLP64_N8))
void cpMul_BNU4(const Ipp32u* pA, const Ipp32u* pB, Ipp32u* pR)
{
   FE_MUL(pR, pA, pB, 4)
}
#endif
#endif

#if 0
#if !((_IPP==_IPP_W7) || \
      (_IPP==_IPP_T7) || \
      (_IPP==_IPP_V8) || \
      (_IPP==_IPP_P8) || \
      (_IPP>=_IPP_G9) || \
      (_IPPLP32==_IPPLP32_S8) || \
      (_IPP32E==_IPP32E_M7) || \
      (_IPP32E==_IPP32E_U8) || \
      (_IPP32E==_IPP32E_Y8) || \
      (_IPP32E>=_IPP32E_E9) || \
      (_IPPLP64==_IPPLP64_N8))
void cpSqr_BNU8(const Ipp32u* pA, Ipp32u* pR)
{
   FE_MUL(pR, pA, pA, 8)
}
#endif
#endif

#if 0
#if !((_IPP==_IPP_W7) || \
      (_IPP==_IPP_T7) || \
      (_IPP==_IPP_V8) || \
      (_IPP==_IPP_P8) || \
      (_IPP>=_IPP_G9) || \
      (_IPPLP32==_IPPLP32_S8) || \
      (_IPP32E==_IPP32E_M7) || \
      (_IPP32E==_IPP32E_U8) || \
      (_IPP32E==_IPP32E_Y8) || \
      (_IPP32E>=_IPP32E_E9) || \
      (_IPPLP64==_IPPLP64_N8))
void cpSqr_BNU4(const Ipp32u* pA, Ipp32u* pR)
{
   FE_MUL(pR, pA, pA, 4)
}
#endif
#endif
