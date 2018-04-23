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

#if (_ECP_256_==_ECP_IMPL_SPECIFIC_)
#include "pcpeccp.h"
#include "pcppma256.h"


/*
// Specific Modulo Arithmetic
//    P256 = 2^256 -2^224 +2^192 +2^96 -1
//    (reference secp256r1_p)
*/

/*
// Reduce modulo:
//
//  x = c15|c14|c13|c12|c11|c10|c09|c08|c07|c06|c05|c04|c03|c02|c01|c00 - 32-bits values
//
// s1 = c07|c06|c05|c04|c03|c02|c01|c00
// s2 = c15|c14|c13|c12|c11|000|000|000
// s3 = 000|c15|c14|c13|c12|000|000|000
// s4 = c15|c14|000|000|000|c10|c09|c08
// s5 = c08|c13|c15|c14|c13|c11|c10|c09
//
// s6 = c10|c08|000|000|000|c13|c12|c11
// s7 = c11|c09|000|000|c15|c14|c13|c12
// s8 = c12|000|c10|c09|c08|c15|c14|c13
// s9 = c13|000|c11|c10|c09|000|c15|c14
//
// r = (s1+2*s2+2*s3+s4+s5-s6-s7-s8-s9) (mod P)
*/
#if !((_IPPXSC==_IPPXSC_S1) || (_IPPXSC==_IPPXSC_S2) || (_IPPXSC==_IPPXSC_C2) || \
      (_IPP==_IPP_W7) || (_IPP==_IPP_T7) || \
      (_IPP==_IPP_V8) || (_IPP==_IPP_P8) || \
      (_IPPLP32==_IPPLP32_S8) || (_IPP>=_IPP_G9) || \
      (_IPP32E==_IPP32E_M7) || \
      (_IPP32E==_IPP32E_U8) || (_IPP32E==_IPP32E_Y8) || \
      (_IPPLP64==_IPPLP64_N8) || (_IPP32E>=_IPP32E_E9) || \
      (_IPP64==_IPP64_I7) )
void Reduce_P256r1(BNU_CHUNK_T* pProduct)
{
   Ipp32u* pR = (Ipp32u*)pProduct;

   Ipp64u  c8c9 = (Ipp64u)pR[ 8] + (Ipp64u)pR[ 9];
   Ipp64u  c9c10= (Ipp64u)pR[ 9] + (Ipp64u)pR[10];
   Ipp64u c10c11= (Ipp64u)pR[10] + (Ipp64u)pR[11];
   Ipp64u c11c12= (Ipp64u)pR[11] + (Ipp64u)pR[12];
   Ipp64u c12c13= (Ipp64u)pR[12] + (Ipp64u)pR[13];
   Ipp64u c13c14= (Ipp64u)pR[13] + (Ipp64u)pR[14];
   Ipp64u c14c15= (Ipp64u)pR[14] + (Ipp64u)pR[15];

   Ipp64s
   sum   = (Ipp64u)pR[ 0] + c8c9  - c11c12 - c13c14;
   pR[0] = LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 1] + c9c10 - c12c13 - c14c15;
   pR[1] = LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 2] + c10c11- c13c14 - (Ipp64u)pR[15];
   pR[2] = LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 3] + c11c12 + c11c12 + c13c14 - c14c15 - c8c9;
   pR[3] = LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 4] + c12c13 + c12c13 + (Ipp64u)pR[14] - c9c10;
   pR[4] = LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 5] + c13c14 + c13c14 + (Ipp64u)pR[15] - c10c11;
   pR[5] = LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 6] + c14c15 +c14c15 +c13c14 - c8c9;
   pR[6] = LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 7] + (Ipp64u)pR[ 8] + (Ipp64u)pR[15] + (Ipp64u)pR[15] + (Ipp64u)pR[15] - c10c11 -c12c13;
   pR[7] = LODWORD(sum);
   sum >>= 32;
   pProduct[LEN_P256] = (BNU_CHUNK_T)(sum);

   while(((BNS_CHUNK_T)pProduct[LEN_P256]) <0)
      cpAdd_BNU(pProduct, pProduct, (BNU_CHUNK_T*)secp256r1_p, LEN_P256+1);

   while(0 <= cpCmp_BNU(pProduct, LEN_P256+1, (BNU_CHUNK_T*)secp256r1_p, LEN_P256+1))
      cpSub_BNU(pProduct, pProduct, (BNU_CHUNK_T*)secp256r1_p, LEN_P256+1);
}
#endif

void cpAdde_256r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   BNU_CHUNK_T carry = cpAdd_BNU(rPtr, aPtr, bPtr, LEN_P256);
   if(carry || (0<=cpCmp_BNU(rPtr, LEN_P256, (BNU_CHUNK_T*)secp256r1_p, LEN_P256)))
      cpSub_BNU(rPtr, rPtr, (BNU_CHUNK_T*)secp256r1_p, LEN_P256);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P256;
}

void cpSube_256r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   BNU_CHUNK_T borrow = cpSub_BNU(rPtr, aPtr, bPtr, LEN_P256);
   if(borrow)
      cpAdd_BNU(rPtr, rPtr, (BNU_CHUNK_T*)secp256r1_p, LEN_P256);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P256;
}

void cpSqre_256r1(IppsBigNumState* pA, IppsBigNumState* pR)
{
   BNU_CHUNK_T tmpR[2*LEN_P256];

   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   cpSqr_BNU_school(tmpR, aPtr, LEN_P256);

   Reduce_P256r1(tmpR);
   COPY_BNU(rPtr, tmpR, LEN_P256);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P256;
}

void cpMule_256r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T tmpR[2*LEN_P256];

   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   cpMul_BNU_school(tmpR, aPtr, LEN_P256, bPtr, LEN_P256);

   Reduce_P256r1(tmpR);
   COPY_BNU(rPtr, tmpR, LEN_P256);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P256;
}

#endif
