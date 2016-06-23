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

#if !defined(_CP_BNU_ARITH_H)
#define _CP_BNU_ARITH_H

#include "pcpbnuimpl.h"
#include "pcpbnu32arith.h"

BNU_CHUNK_T cpAdd_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, cpSize ns);
BNU_CHUNK_T cpSub_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, cpSize ns);
BNU_CHUNK_T cpInc_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize ns, BNU_CHUNK_T val);
BNU_CHUNK_T cpDec_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize ns, BNU_CHUNK_T val);

BNU_CHUNK_T cpAddMulDgt_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize ns, BNU_CHUNK_T val);

BNU_CHUNK_T cpMulAdc_BNU_school(BNU_CHUNK_T* pR,
                         const BNU_CHUNK_T* pA, cpSize nsA,
                         const BNU_CHUNK_T* pB, cpSize nsB);

__INLINE BNU_CHUNK_T cpMul_BNU_school(BNU_CHUNK_T* pR,
                                const BNU_CHUNK_T* pA, cpSize nsA,
                                const BNU_CHUNK_T* pB, cpSize nsB)
{
   return cpMulAdc_BNU_school(pR, pA,nsA, pB,nsB);
}

BNU_CHUNK_T cpSqrAdc_BNU_school(BNU_CHUNK_T * pR, const BNU_CHUNK_T * pA, cpSize nsA);

__INLINE BNU_CHUNK_T cpSqr_BNU_school(BNU_CHUNK_T * pR, const BNU_CHUNK_T * pA, cpSize nsA)
{
   return cpSqrAdc_BNU_school(pR, pA,nsA);
}

BNU_CHUNK_T cpGcd_BNU(BNU_CHUNK_T a, BNU_CHUNK_T b);

int cpModInv_BNU(BNU_CHUNK_T* pInv,
           const BNU_CHUNK_T* pA, cpSize nsA,
           const BNU_CHUNK_T* pM, cpSize nsM,
                 BNU_CHUNK_T* bufInv, BNU_CHUNK_T* bufA, BNU_CHUNK_T* bufM);


/*
// multiplication/squaring wrappers
*/
__INLINE BNU_CHUNK_T cpMul_BNU(BNU_CHUNK_T* pR,
                         const BNU_CHUNK_T* pA, cpSize nsA,
                         const BNU_CHUNK_T* pB, cpSize nsB,
                               BNU_CHUNK_T* pBuffer)
{
   UNREFERENCED_PARAMETER(pBuffer);
   return cpMul_BNU_school(pR, pA,nsA, pB,nsB);
}
__INLINE BNU_CHUNK_T cpSqr_BNU(BNU_CHUNK_T * pR,
                         const BNU_CHUNK_T * pA, cpSize nsA,
                               BNU_CHUNK_T* pBuffer)
{
   UNREFERENCED_PARAMETER(pBuffer);
   return cpSqr_BNU_school(pR, pA,nsA);
}

/*
// division/reduction wrappers
*/
__INLINE cpSize cpDiv_BNU(BNU_CHUNK_T* pQ, cpSize* pnsQ, BNU_CHUNK_T* pA, cpSize nsA, BNU_CHUNK_T* pB, cpSize nsB)
{
   int nsR = cpDiv_BNU32((Ipp32u*)pQ, pnsQ,
                         (Ipp32u*)pA, nsA*(sizeof(BNU_CHUNK_T)/sizeof(Ipp32u)),
                         (Ipp32u*)pB, nsB*(sizeof(BNU_CHUNK_T)/sizeof(Ipp32u)));
   #if (BNU_CHUNK_BITS == BNU_CHUNK_64BIT)
   if(nsR&1) ((Ipp32u*)pA)[nsR] = 0;
   nsR = INTERNAL_BNU_LENGTH(nsR);
   if(pQ) {
      if(*pnsQ&1) ((Ipp32u*)pQ)[*pnsQ] = 0;
      *pnsQ = INTERNAL_BNU_LENGTH(*pnsQ);
   }
   #endif
   return nsR;
}

__INLINE cpSize cpMod_BNU(BNU_CHUNK_T* pX, cpSize nsX, BNU_CHUNK_T* pModulus, cpSize nsM)
{
   return cpDiv_BNU(NULL,NULL, pX,nsX, pModulus, nsM);
}

#endif /* _CP_BNU_ARITH_H */
