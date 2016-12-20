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
//     Intel(R) Integrated Performance Primitives.
//     Internal Unsigned internal arithmetic
// 
// 
*/

#if !defined(_CP_BNU_ARITH_H)
#define _CP_BNU_ARITH_H

#include "pcpbnuimpl.h"
#include "pcpbnu32arith.h"
#include "pcpmulbnukara.h"

BNU_CHUNK_T cpAdd_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, cpSize ns);
BNU_CHUNK_T cpSub_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, cpSize ns);
BNU_CHUNK_T cpInc_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize ns, BNU_CHUNK_T val);
BNU_CHUNK_T cpDec_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize ns, BNU_CHUNK_T val);

#if defined(_USE_KARATSUBA_)
BNU_CHUNK_T cpAddAdd_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, const BNU_CHUNK_T* pC, cpSize size);
BNU_CHUNK_T cpAddSub_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, const BNU_CHUNK_T* pC, cpSize size);
#endif

BNU_CHUNK_T cpAddMulDgt_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize ns, BNU_CHUNK_T val);
#if 0
BNU_CHUNK_T cpMulDgt_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize ns, BNU_CHUNK_T val);
BNU_CHUNK_T cpSubMulDgt_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize ns, BNU_CHUNK_T val);
#endif


BNU_CHUNK_T cpMulAdc_BNU_school(BNU_CHUNK_T* pR,
                         const BNU_CHUNK_T* pA, cpSize nsA,
                         const BNU_CHUNK_T* pB, cpSize nsB);
BNU_CHUNK_T cpMulAdx_BNU_school(BNU_CHUNK_T* pR,
                         const BNU_CHUNK_T* pA, cpSize nsA,
                         const BNU_CHUNK_T* pB, cpSize nsB);

__INLINE BNU_CHUNK_T cpMul_BNU_school(BNU_CHUNK_T* pR,
                                const BNU_CHUNK_T* pA, cpSize nsA,
                                const BNU_CHUNK_T* pB, cpSize nsB)
{
#if(_ADCOX_NI_ENABLING_==_FEATURE_ON_)
   return cpMulAdx_BNU_school(pR, pA,nsA, pB,nsB);
#elif(_ADCOX_NI_ENABLING_==_FEATURE_TICKTOCK_)
   return IsFeatureEnabled(ADCOX_ENABLED)? cpMulAdx_BNU_school(pR, pA,nsA, pB,nsB)
                                         : cpMulAdc_BNU_school(pR, pA,nsA, pB,nsB);
#else
   return cpMulAdc_BNU_school(pR, pA,nsA, pB,nsB);
#endif
}

BNU_CHUNK_T cpSqrAdc_BNU_school(BNU_CHUNK_T * pR, const BNU_CHUNK_T * pA, cpSize nsA);
BNU_CHUNK_T cpSqrAdx_BNU_school(BNU_CHUNK_T * pR, const BNU_CHUNK_T * pA, cpSize nsA);

__INLINE BNU_CHUNK_T cpSqr_BNU_school(BNU_CHUNK_T * pR, const BNU_CHUNK_T * pA, cpSize nsA)
{
#if(_ADCOX_NI_ENABLING_==_FEATURE_ON_)
   return cpSqrAdx_BNU_school(pR, pA,nsA);
#elif(_ADCOX_NI_ENABLING_==_FEATURE_TICKTOCK_)
   return IsFeatureEnabled(ADCOX_ENABLED)? cpSqrAdx_BNU_school(pR, pA,nsA)
                                         : cpSqrAdc_BNU_school(pR, pA,nsA);
#else
   return cpSqrAdc_BNU_school(pR, pA,nsA);
#endif
}

#if(_IPP_ARCH==_IPP_ARCH_EM64T)
BNU_CHUNK_T* gf256_add(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, const BNU_CHUNK_T* pModulus);
BNU_CHUNK_T* gf256_sub(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, const BNU_CHUNK_T* pModulus);
BNU_CHUNK_T* gf256_neg(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pModulus);
BNU_CHUNK_T* gf256_mulm(BNU_CHUNK_T* pR,const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, const BNU_CHUNK_T* pModulus, BNU_CHUNK_T  m0);
BNU_CHUNK_T* gf256_sqrm(BNU_CHUNK_T* pR,const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pModulus, BNU_CHUNK_T  m0);
BNU_CHUNK_T* gf256_div2(BNU_CHUNK_T* pR,const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pModulus);
#endif

BNU_CHUNK_T cpGcd_BNU(BNU_CHUNK_T a, BNU_CHUNK_T b);

int cpModInv_BNU(BNU_CHUNK_T* pInv,
           const BNU_CHUNK_T* pA, cpSize nsA,
           const BNU_CHUNK_T* pM, cpSize nsM,
                 BNU_CHUNK_T* bufInv, BNU_CHUNK_T* bufA, BNU_CHUNK_T* bufM);


/*
// multiplication/squaring wrappers
*/
__INLINE cpSize cpMul_BNU_BufferSize(cpSize opLen)
{
#if !defined (_USE_KARATSUBA_)
   UNREFERENCED_PARAMETER(opLen);
   return 0;
#else
   return cpKaratsubaBufferSize(opLen);
#endif
}
__INLINE BNU_CHUNK_T cpMul_BNU(BNU_CHUNK_T* pR,
                         const BNU_CHUNK_T* pA, cpSize nsA,
                         const BNU_CHUNK_T* pB, cpSize nsB,
                               BNU_CHUNK_T* pBuffer)
{
#if !defined(_USE_KARATSUBA_)
   UNREFERENCED_PARAMETER(pBuffer);
   return cpMul_BNU_school(pR, pA,nsA, pB,nsB);
#else
   if(nsA!=nsB || nsA<CP_KARATSUBA_MUL_THRESHOLD || !pBuffer)
      return cpMul_BNU_school(pR, pA,nsA, pB,nsB);
   else
      return cpMul_BNU_karatsuba(pR, pA,pB,nsA, pBuffer);
#endif
}
__INLINE BNU_CHUNK_T cpSqr_BNU(BNU_CHUNK_T * pR,
                         const BNU_CHUNK_T * pA, cpSize nsA,
                               BNU_CHUNK_T* pBuffer)
{
#if !defined(_USE_KARATSUBA_)
   UNREFERENCED_PARAMETER(pBuffer);
   return cpSqr_BNU_school(pR, pA,nsA);
#else
   if(nsA<CP_KARATSUBA_SQR_THRESHOLD || !pBuffer)
      return cpSqr_BNU_school(pR, pA,nsA);
   else
      return cpSqr_BNU_karatsuba(pR, pA,nsA, pBuffer);
#endif
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

//#define cpMod_BNU(pX,sizeX, pM,sizeM) cpDiv_BNU(NULL,NULL, (pX),(sizeX), (pM),(sizeM))
__INLINE cpSize cpMod_BNU(BNU_CHUNK_T* pX, cpSize nsX, BNU_CHUNK_T* pModulus, cpSize nsM)
{
   return cpDiv_BNU(NULL,NULL, pX,nsX, pModulus, nsM);
}

#endif /* _CP_BNU_ARITH_H */
