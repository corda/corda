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
// 
*/

#include "precomp.h"
#include "owncp.h"

#if (_ECP_192_==_ECP_IMPL_SPECIFIC_)
#include "pcpeccp.h"
#include "pcppma192.h"

/*
// Specific Modulo Arithmetic
//    P192 = 2^192 -2^64 -1
//    (reference secp192r1_p)
*/

/*
// Reduce modulo:
//
//  x = c11|c10|c9|c8|c7|c6|c5|c4|c3|c2|c1|c0
//
// s1 = c05|c04|c03|c02|c01|c00
// s2 = 000|000|c07|c06|c07|c06
// s3 = c09|c08|c09|c08|000|000
//
// r = (s1+s2+s3+s4) (mod P)
*/
#if !((_IPPXSC==_IPPXSC_S1) || (_IPPXSC==_IPPXSC_S2) || (_IPPXSC==_IPPXSC_C2) || \
      (_IPP==_IPP_W7) || (_IPP==_IPP_T7) || \
      (_IPP==_IPP_V8) || (_IPP==_IPP_P8) || \
      (_IPPLP32==_IPPLP32_S8) || (_IPP>=_IPP_G9) || \
      (_IPP32E==_IPP32E_M7) || \
      (_IPP32E==_IPP32E_U8) || (_IPP32E==_IPP32E_Y8) || \
      (_IPPLP64==_IPPLP64_N8) || (_IPP32E>=_IPP32E_E9) || \
      (_IPP64==_IPP64_I7) )
void Reduce_P192r1(BNU_CHUNK_T* pProduct)
{
   Ipp32u* pR = (Ipp32u*)pProduct;

   Ipp64u
   sum   = (Ipp64u)pR[0*2+0]+(Ipp64u)pR[3*2+0]+(Ipp64u)pR[5*2+0];
   pR[0] = LODWORD(sum);
   sum   = HIDWORD(sum);

   sum  += (Ipp64u)pR[0*2+1]+(Ipp64u)pR[3*2+1]+(Ipp64u)pR[5*2+1];
   pR[1] = LODWORD(sum);
   sum   = HIDWORD(sum);

   sum  += (Ipp64u)pR[1*2+0]+(Ipp64u)pR[3*2+0]+(Ipp64u)pR[4*2+0]+(Ipp64u)pR[5*2+0];
   pR[2] = LODWORD(sum);
   sum   = HIDWORD(sum);

   sum  += (Ipp64u)pR[1*2+1]+(Ipp64u)pR[3*2+1]+(Ipp64u)pR[4*2+1]+(Ipp64u)pR[5*2+1];
   pR[3] = LODWORD(sum);
   sum   = HIDWORD(sum);

   sum  += (Ipp64u)pR[2*2+0]+(Ipp64u)pR[4*2+0]+(Ipp64u)pR[5*2+0];
   pR[4] = LODWORD(sum);
   sum   = HIDWORD(sum);

   sum  += (Ipp64u)pR[2*2+1]+(Ipp64u)pR[4*2+1]+(Ipp64u)pR[5*2+1];
   pR[5] = LODWORD(sum);
   pProduct[LEN_P192] = (BNU_CHUNK_T)(HIDWORD(sum));

   while(0<=cpCmp_BNU(pProduct, LEN_P192+1, (BNU_CHUNK_T*)secp192r1_p, LEN_P192+1))
      cpSub_BNU(pProduct, pProduct, (BNU_CHUNK_T*)secp192r1_p, LEN_P192+1);
}
#endif

void cpAdde_192r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   BNU_CHUNK_T carry = cpAdd_BNU(rPtr, aPtr, bPtr, LEN_P192);
   if(carry || (0<=cpCmp_BNU(rPtr, LEN_P192, (BNU_CHUNK_T*)secp192r1_p, LEN_P192)))
      cpSub_BNU(rPtr, rPtr, (BNU_CHUNK_T*)secp192r1_p, LEN_P192);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P192;
}

void cpSube_192r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   BNU_CHUNK_T borrow = cpSub_BNU(rPtr, aPtr, bPtr, LEN_P192);
   if(borrow)
      cpAdd_BNU(rPtr, rPtr, (BNU_CHUNK_T*)secp192r1_p, LEN_P192);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P192;
}

void cpSqre_192r1(IppsBigNumState* pA, IppsBigNumState* pR)
{
   BNU_CHUNK_T tmpR[2*LEN_P192];

   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   cpSqr_BNU_school(tmpR, aPtr, LEN_P192);

   Reduce_P192r1(tmpR);
   COPY_BNU(rPtr, tmpR, LEN_P192);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P192;
}

void cpMule_192r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T tmpR[2*LEN_P192];

   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   cpMul_BNU_school(tmpR, aPtr, LEN_P192, bPtr, LEN_P192);

   Reduce_P192r1(tmpR);
   COPY_BNU(rPtr, tmpR, LEN_P192);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P192;
}

#endif /* _ECP_192_==_ECP_IMPL_SPECIFIC_ */
