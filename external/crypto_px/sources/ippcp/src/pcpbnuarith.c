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
#include "pcpbnuarith.h"
#include "pcpbnumisc.h"


/* Function cpAdd_BNU - addition of 2 BigNumbers  */
BNU_CHUNK_T cpAdd_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, cpSize ns)
{
   BNU_CHUNK_T carry = 0;
   cpSize i;
   for(i=0; i<ns; i++) {
      ADD_ABC(carry, pR[i], pA[i],pB[i], carry);
   }
   return carry;
}

/* Function cpSub_BNU - subtraction of 2 BigNumbers  */
BNU_CHUNK_T cpSub_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, cpSize ns)
{
   BNU_CHUNK_T borrow = 0;
   cpSize i;
   for(i=0; i<ns; i++) {
      SUB_ABC(borrow, pR[i], pA[i], pB[i], borrow);
   }
   return borrow;
}

/* Function cpInc_BNU - increment BigNumber  */
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

      for(;;) {
         nsM = cpDiv_BNU(Q, &nsQ, (BNU_CHUNK_T*)pM, nsM, bufA, nsA);
         nsX1 = cpMac_BNU(X1,moduloSize, Q,nsQ, X2,nsX2);

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
         nsX2 = cpMac_BNU(X2,moduloSize, Q,nsQ, X1,nsX1);

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
