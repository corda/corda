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
//     Internal Unsigned arithmetic
// 
//  Contents:
//     cpAdd_BNU()
//     cpSub_BNU()
//     cpInc_BNU()
//     cpDec_BNU()
// 
//     cpAddAdd_BNU()
//     cpAddSub_BNU()
// 
//     cpMuldgt_BNU()
//     cpAddMulDgt_BNU()
//     cpSubMulDgt_BNU()
// 
//     cpMulAdc_BNU_school()
//     cpSqrAdc_BNU_school()
// 
//     cpDiv_BNU()
//     cpMod_BNU()
//     cpGcd_BNU()
//     cpModInv_BNU()
// 
// 
*/

#include "owncp.h"
#include "pcpbnuarith.h"
#include "pcpbnumisc.h"


/* Function cpAdd_BNU - addition of 2 BigNumbers  */
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
      (_IPPLP64==_IPPLP64_N8) || \
      (_IPPLRB>=_IPPLRB_B1))
BNU_CHUNK_T cpAdd_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, cpSize ns)
{
   BNU_CHUNK_T carry = 0;
   cpSize i;
   for(i=0; i<ns; i++) {
      ADD_ABC(carry, pR[i], pA[i],pB[i], carry);
   }
   return carry;
}
#endif

/* Function cpSub_BNU - subtraction of 2 BigNumbers  */
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
      (_IPPLP64==_IPPLP64_N8) || \
      (_IPPLRB>=_IPPLRB_B1))
BNU_CHUNK_T cpSub_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, cpSize ns)
{
   BNU_CHUNK_T borrow = 0;
   cpSize i;
   for(i=0; i<ns; i++) {
      SUB_ABC(borrow, pR[i], pA[i], pB[i], borrow);
   }
   return borrow;
}
#endif

/* Function cpInc_BNU - increment BigNumber  */
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
BNU_CHUNK_T cpInc_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize ns, BNU_CHUNK_T val)
{
   cpSize i;
   for(i=0; i<ns && val; i++) {
      BNU_CHUNK_T carry;
      ADD_AB(carry, pR[i], pA[i], val);
      val = carry;
   }
   if(pR!=pA)
      for(; i<ns; i++)
         pR[i] = pA[i];
   return val;
}
#endif

#if !((_IPP32E==_IPP32E_M7) || \
      (_IPP32E==_IPP32E_U8) || \
      (_IPP32E==_IPP32E_Y8) || \
      (_IPP32E>=_IPP32E_E9) || \
      (_IPPLP64==_IPPLP64_N8))
BNU_CHUNK_T cpDec_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize ns, BNU_CHUNK_T val)
{
   cpSize i;
   for(i=0; i<ns && val; i++) {
      BNU_CHUNK_T borrow;
      SUB_AB(borrow, pR[i], pA[i], val);
      val = borrow;
   }
   if(pR!=pA)
      for(; i<ns; i++)
         pR[i] = pA[i];
   return val;
}
#endif

/* Function cpAddAdd_BNU */
#if defined(_USE_KARATSUBA_)
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
BNU_CHUNK_T cpAddAdd_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, const BNU_CHUNK_T* pC, cpSize ns)
{
   BNU_CHUNK_T carry1 = 0;
   BNU_CHUNK_T carry2 = 0;
   cpSize i;
   for(i=0; i<ns; i++) {
      BNU_CHUNK_T s;
      ADD_ABC(carry1, s, pA[i],pB[i],carry1);
      ADD_ABC(carry2, pR[i], s,pC[i],carry2);
   }
   return (carry1+carry2);
}
#endif
#endif

/* Function cpAddSub_BNU */
#if defined(_USE_KARATSUBA_)
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
BNU_CHUNK_T cpAddSub_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, const BNU_CHUNK_T* pC, cpSize ns)
{
   BNU_CHUNK_T carry = 0;
   BNU_CHUNK_T borrow = 0;
   cpSize i;
   for(i=0; i<ns; i++) {
      BNU_CHUNK_T d;
      SUB_ABC(borrow, d, pB[i], pC[i], borrow);
      ADD_ABC(carry,  pR[i], d, pA[i], carry);
   }
   return (carry-borrow);
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
BNU_CHUNK_T cpMulDgt_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize ns, BNU_CHUNK_T val)
{
   BNU_CHUNK_T extension = 0;
   cpSize i;
   for(i=0; i<ns; i++) {
      BNU_CHUNK_T rH, rL;

      MUL_AB(rH, rL, pA[i], val);
      rL += extension;
      extension = (rL < extension) + rH;
      pR[i] = rL;
   }
   return extension;
}
#endif
#endif

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
BNU_CHUNK_T cpAddMulDgt_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize ns, BNU_CHUNK_T val)
{
   BNU_CHUNK_T extension = 0;
   cpSize i;
   for(i=0; i<ns; i++) {
      BNU_CHUNK_T rH, rL;

      MUL_AB(rH, rL, pA[i], val);
      ADD_ABC(extension, pR[i], pR[i], rL, extension);
      extension += rH;
   }
   return extension;
}
#endif


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
      (_IPPLP64==_IPPLP64_N8) || \
      (_IPPLRB >= _IPPLRB_B1))
BNU_CHUNK_T cpSubMulDgt_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize ns, BNU_CHUNK_T val)
{
   BNU_CHUNK_T extension = 0;
   cpSize i;
   for(i=0; i<ns; i++) {
      BNU_CHUNK_T rH, rL;

      MUL_AB(rH, rL, pA[i], val);
      SUB_ABC(extension, pR[i], pR[i], rL, extension);
      extension += rH;
   }
   return extension;
}
#endif

#if !((_IPP==_IPP_V8) || \
      (_IPP==_IPP_P8) || \
      (_IPP>=_IPP_G9) || \
      (_IPPLP32==_IPPLP32_S8) || \
      (_IPP32E==_IPP32E_M7) || \
      (_IPP32E==_IPP32E_U8) || \
      (_IPP32E==_IPP32E_Y8) || \
      (_IPP32E>=_IPP32E_E9) || \
      (_IPPLP64==_IPPLP64_N8) )
// || (_IPPLRB >= _IPPLRB_B1)) //dlaptev: is it renaming?
//BNU_CHUNK_T cpMul_BNU_school(BNU_CHUNK_T* pR,
//                       const BNU_CHUNK_T* pA, cpSize nsA,
//                       const BNU_CHUNK_T* pB, cpSize nsB)
BNU_CHUNK_T cpMulAdc_BNU_school(BNU_CHUNK_T* pR,
                          const BNU_CHUNK_T* pA, cpSize nsA,
                          const BNU_CHUNK_T* pB, cpSize nsB)
{
   const BNU_CHUNK_T* pa = (BNU_CHUNK_T*)pA;
   const BNU_CHUNK_T* pb = (BNU_CHUNK_T*)pB;
   BNU_CHUNK_T* pr = (BNU_CHUNK_T*)pR;

   BNU_CHUNK_T extension = 0;
   cpSize i, j;

   ZEXPAND_BNU(pr, 0, nsA+nsB);

   for(i=0; i<nsB; i++ ) {
      BNU_CHUNK_T b = pb[i];

      for(j=0, extension=0; j<nsA; j++ ) {
         BNU_CHUNK_T rH, rL;

         MUL_AB(rH, rL, pa[j], b);
         ADD_ABC(extension, pr[i+j], pr[i+j], rL, extension);
         extension += rH;
      }
      pr[i+j] = extension;
   }
   return extension;
}
#endif


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
      (_IPPLP64==_IPPLP64_N8) )
//|| (_IPPLRB >= _IPPLRB_B1)) //dlaptev: is it renaming?
//BNU_CHUNK_T cpSqr_BNU_school(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize nsA)
BNU_CHUNK_T cpSqrAdc_BNU_school(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize nsA)
{
   cpSize i;

   BNU_CHUNK_T extension;
   BNU_CHUNK_T rH, rL;

   /* init result */
   pR[0] = 0;
   for(i=1, extension=0; i<nsA; i++) {
      MUL_AB(rH, rL, pA[i], pA[0]);
      ADD_AB(extension, pR[i], rL, extension);
      extension += rH;
   }
   pR[i] = extension;

   /* add other a[i]*a[j] */
   for(i=1; i<nsA-1; i++) {
      BNU_CHUNK_T a = pA[i];
      cpSize j;
      for(j=i+1, extension=0; j<nsA; j++) {
         MUL_AB(rH, rL, pA[j], a);
         ADD_ABC(extension, pR[i+j], rL, pR[i+j], extension);
         extension += rH;
      }
      pR[i+j] = extension;
   }

   /* double a[i]*a[j] */
   for(i=1, extension=0; i<(2*nsA-1); i++) {
      ADD_ABC(extension, pR[i], pR[i], pR[i], extension);
   }
   pR[i] = extension;

   /* add a[i]^2 */
   for(i=0, extension=0; i<nsA; i++) {
      MUL_AB(rH, rL, pA[i], pA[i]);
      ADD_ABC(extension, pR[2*i], pR[2*i], rL, extension);
      ADD_ABC(extension, pR[2*i+1], pR[2*i+1], rH, extension);
   }
   return pR[2*nsA-1];
}
#endif


BNU_CHUNK_T cpGcd_BNU(BNU_CHUNK_T a, BNU_CHUNK_T b)
{
    BNU_CHUNK_T gcd, t, r;

    if(a > b){
        gcd = a;
        t = b;
    } else {
        t = a;
        gcd = b;
    }

    while (t != 0)    {
        r = gcd % t;
        gcd = t;
        t = r;
    }
    return gcd;
}

/*
// cpMAC_BNU
//
// Multiply with ACcumulation
// Computes r <- r + a * b, returns real size of the r in the size_r variable
// Returns 0 if there are no enought buffer size to write to r[MAX(size_r + 1, size_a + size_b) - 1]
// Returns 1 if no error
//
// Note:
//  DO NOT run in inplace mode
//  The minimum buffer size for the r must be (size_a + size_b - 1)
//      the maximum buffer size for the r is MAX(size_r + 1, size_a + size_b)
*/
static int cpMac_BNU(BNU_CHUNK_T* pR, cpSize nsR,
        const BNU_CHUNK_T* pA, cpSize nsA,
        const BNU_CHUNK_T* pB, cpSize nsB)
{
   /* cleanup the rest of destination buffer */
   ZEXPAND_BNU(pR, nsR, nsA+nsB-1);
   //nsR = IPP_MAX(nsR, nsA+nsB);

   {
      BNU_CHUNK_T expansion = 0;
      cpSize i;
      for(i=0; i<nsB && !expansion; i++) {
         expansion = cpAddMulDgt_BNU(pR+i, pA, nsA, pB[i]);
         if(expansion)
            expansion = cpInc_BNU(pR+i+nsA, pR+i+nsA, nsR-i-nsA, expansion);
      }

      if(expansion)
         return 0;
      else {   /* compute real size */
         FIX_BNU(pR, nsR);
         return nsR;
      }
   }
}

#if 0
#include <stdio.h>
static void Print_BNU(const char* note, const BNU_CHUNK_T* pData, int len)
{
   if(note)
      printf("%s", note);
   {
      int n;
      Ipp32u* pDataT = (Ipp32u*)pData;
      len *= (sizeof(BNU_CHUNK_T)/sizeof(Ipp32u));
      for(n=len; n>0; n--) {
         Ipp32u x = pDataT[n-1];
         printf("%08x ", x);
      }
      printf("\n");
   }
}
#endif

int cpModInv_BNU(BNU_CHUNK_T* pInv,
            const BNU_CHUNK_T* pA, cpSize nsA,
            const BNU_CHUNK_T* pM, cpSize nsM,
                  BNU_CHUNK_T* bufInv, BNU_CHUNK_T* bufA, BNU_CHUNK_T* bufM)
{
    FIX_BNU(pA, nsA);
    FIX_BNU(pM, nsM);

   /* inv(1) = 1 */
   if(nsA==1 && pA[0]==1) {
      pInv[0] = 1;
      return 1;
   }

   {
      cpSize moduloSize = nsM;

      BNU_CHUNK_T* X1 = pInv;
      BNU_CHUNK_T* X2 = bufM;
      BNU_CHUNK_T* Q = bufInv;
      cpSize nsX1 = 1;
      cpSize nsX2 = 1;
      cpSize nsQ;

      COPY_BNU(bufA, pA, nsA);

      ZEXPAND_BNU(X1, 0, moduloSize);
      ZEXPAND_BNU(X2, 0, moduloSize);
      X2[0] = 1;

      //printf("\n");
      for(;;) {
         nsM = cpDiv_BNU(Q, &nsQ, (BNU_CHUNK_T*)pM, nsM, bufA, nsA);
         //Print_BNU(" q: ", Q, nsQ);
         //Print_BNU(" m: ", pM, nsM);
         nsX1 = cpMac_BNU(X1,moduloSize, Q,nsQ, X2,nsX2);
         //Print_BNU("X1: ", X1, nsX1);

         if (nsM==1 && pM[0]==1) {
            ////ZEXPAND_BNU(X2, nsX2, moduloSize);
            nsX2 = cpMac_BNU(X2,moduloSize, X1,nsX1, bufA, nsA);
            COPY_BNU((BNU_CHUNK_T*)pM, X2, moduloSize);
            cpSub_BNU(pInv, pM, X1, moduloSize);
            FIX_BNU(pInv, moduloSize);
            return moduloSize;
         }
         else if (nsM==1 && pM[0]==0) {
            cpMul_BNU_school((BNU_CHUNK_T*)pM, X1,nsX1, bufA, nsA);
            /* gcd = buf_a */
            return 0;
         }

         nsA = cpDiv_BNU(Q, &nsQ, bufA, nsA, (BNU_CHUNK_T*)pM, nsM);
         //Print_BNU(" q: ", Q, nsQ);
         //Print_BNU(" a: ", bufA, nsA);
         nsX2 = cpMac_BNU(X2,moduloSize, Q,nsQ, X1,nsX1);
         //Print_BNU("X2: ", X2, nsX2);

         if(nsA==1 && bufA[0]==1) {
            ////ZEXPAND_BNU(X1, nsX1, moduloSize);
            nsX1 = cpMac_BNU(X1, moduloSize, X2, nsX2, pM, nsM);
            COPY_BNU((BNU_CHUNK_T*)pM, X1, moduloSize);
            COPY_BNU(pInv, X2, nsX2);
            return nsX2;
         }
         else if (nsA==1 && bufA[0]==0) {
            /* gcd = m */
            COPY_BNU(X1, pM, nsM);
            cpMul_BNU_school((BNU_CHUNK_T*)pM, X2, nsX2, X1, nsM);
            return 0;
         }
      }
   }
}
