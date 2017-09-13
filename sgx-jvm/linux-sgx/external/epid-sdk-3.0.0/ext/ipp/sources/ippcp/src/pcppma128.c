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
// 
//  Purpose:
//     Cryptography Primitive.
//     Internal Prime Modulo Arithmetic Function
// 
//  Contents:
// 
// 
*/

#include "precomp.h"
#include "owncp.h"

#if (_ECP_128_==_ECP_IMPL_SPECIFIC_)
#include "pcpeccp.h"
#include "pcppma128.h"


/*
// Specific Modulo Arithmetic
//    P128 = 2^128 -2^97 -1
//    (reference secp128r1_p)
*/

/*
// Reduce modulo:
//
//  x = c7|c6|c5|c4|c3|c2|c1|c0
//
// s1 =  c3| c2| c1| c0
// s2 = 2c4| 00| 00| c4
// s3 = 4c5| 00| c5|2c5
// s4 = 8c6| c6|2c6|4c6
// s5 =17c7|2c7|4c7|8c7
//
// r = (s1+s2+s3+s4+s5) (mod P)
*/
#if !((_IPPXSC==_IPPXSC_S1) || (_IPPXSC==_IPPXSC_S2) || (_IPPXSC==_IPPXSC_C2) || \
      (_IPP==_IPP_W7) || (_IPP==_IPP_T7) || \
      (_IPP==_IPP_V8) || (_IPP==_IPP_P8) || \
      (_IPPLP32==_IPPLP32_S8) || (_IPP>=_IPP_G9) || \
      (_IPP32E==_IPP32E_M7) || \
      (_IPP32E==_IPP32E_U8) || (_IPP32E==_IPP32E_Y8) || \
      (_IPPLP64==_IPPLP64_N8) || (_IPP32E>=_IPP32E_E9) || \
      (_IPP64==_IPP64_I7) )
void Reduce_P128r1(BNU_CHUNK_T* pProduct)
{
   Ipp32u* pR = (Ipp32u*)pProduct;

   Ipp64u c7x2 = (Ipp64u)pR[7] + (Ipp64u)pR[7];
   Ipp64u c7x4 = c7x2 + c7x2;
   Ipp64u c7x8 = c7x4 + c7x4;

   Ipp64u c6x2 = (Ipp64u)pR[6] + (Ipp64u)pR[6];
   Ipp64u c6x4 = c6x2 + c6x2;
   Ipp64u c6x8 = c6x4 + c6x4;

   Ipp64u c5x2 = (Ipp64u)pR[5] + (Ipp64u)pR[5];
   Ipp64u c5x4 = c5x2 + c5x2;

   Ipp64u c4x2 = (Ipp64u)pR[4] + (Ipp64u)pR[4];

   Ipp64u
   sum   = (Ipp64u)pR[0] +  (Ipp64u)pR[4] + c5x2 + c6x4 + c7x8;
   pR[0] = LODWORD(sum);
   sum   = HIDWORD(sum);

   sum  += (Ipp64u)pR[1] + (Ipp64u)pR[5] + c6x2 + c7x4;
   pR[1] = LODWORD(sum);
   sum   = HIDWORD(sum);

   sum  += (Ipp64u)pR[2] + (Ipp64u)pR[6] + c7x2;
   pR[2] = LODWORD(sum);
   sum   = HIDWORD(sum);

   sum  += (Ipp64u)pR[3] + c4x2 + c5x4 + c6x8 + c7x8+c7x8+(Ipp64u)pR[7];
   pR[3] = LODWORD(sum);
   pProduct[LEN_P128] = (BNU_CHUNK_T)(HIDWORD(sum));

   if(pProduct[LEN_P128])
      cpSub_BNU(pProduct, pProduct, ((BNU_CHUNK_T**)secp128_mx)[pProduct[LEN_P128]], LEN_P128+1);

   while((BNS_CHUNK_T)pProduct[LEN_P128] <0)
      cpAdd_BNU(pProduct, pProduct, (BNU_CHUNK_T*)secp128r1_p, LEN_P128+1);

   while(0 <= cpCmp_BNU(pProduct, LEN_P128+1, (BNU_CHUNK_T*)secp128r1_p, LEN_P128+1))
      cpSub_BNU(pProduct, pProduct, (BNU_CHUNK_T*)secp128r1_p, LEN_P128+1);
}
#endif

void cpAdde_128r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   BNU_CHUNK_T carry = cpAdd_BNU(rPtr, aPtr, bPtr, LEN_P128);
   if(carry || (0<=cpCmp_BNU(rPtr, LEN_P128, (BNU_CHUNK_T*)secp128r1_p, LEN_P128)))
      cpSub_BNU(rPtr, rPtr, (BNU_CHUNK_T*)secp128r1_p, LEN_P128);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P128;
}

void cpSube_128r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   BNU_CHUNK_T borrow = cpSub_BNU(rPtr, aPtr, bPtr, LEN_P128);
   if(borrow)
      cpAdd_BNU(rPtr, rPtr, (BNU_CHUNK_T*)secp128r1_p, LEN_P128);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P128;
}

void cpSqre_128r1(IppsBigNumState* pA, IppsBigNumState* pR)
{
   BNU_CHUNK_T tmpR[2*LEN_P128];

   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   cpSqr_BNU_school(tmpR, aPtr, LEN_P128);

   Reduce_P128r1(tmpR);
   COPY_BNU(rPtr, tmpR, LEN_P128);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P128;
}

void cpMule_128r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T tmpR[2*LEN_P128];

   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   cpMul_BNU_school(tmpR, aPtr, LEN_P128, bPtr, LEN_P128);

   Reduce_P128r1(tmpR);
   COPY_BNU(rPtr, tmpR, LEN_P128);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P128;
}

#endif /* _ECP_128_==_ECP_IMPL_SPECIFIC_ */
