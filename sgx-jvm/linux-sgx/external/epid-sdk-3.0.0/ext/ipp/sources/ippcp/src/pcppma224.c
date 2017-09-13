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

#if (_ECP_224_==_ECP_IMPL_SPECIFIC_)
#include "pcpeccp.h"
#include "pcppma224.h"


/*
// Specific Modulo Arithmetic
//    P224 = 2^224 -2^96 +1
//    (reference secp224r1_p)
*/

/*
// Reduce modulo:
//
//  x = c13|c12|c11|c10|c09|c08|c07|c06|c05|c04|c03|c02|c01|c00 - 32-bits values
//
// s1 = c06|c05|c04|c03|c02|c01|c00
// s2 = c10|c09|c08|c07|000|000|000
// s3 = 000|c13|c12|c11|000|000|000
//
// s4 = c13|c12|c11|c10|c09|c08|c07
// s5 = 000|000|000|000|c13|c12|c11
//
// r = (s1+s2+s3-s4-s5) (mod P)
*/
#if !((_IPPXSC==_IPPXSC_S1) || (_IPPXSC==_IPPXSC_S2) || (_IPPXSC==_IPPXSC_C2) || \
      (_IPP==_IPP_W7) || (_IPP==_IPP_T7) || \
      (_IPP==_IPP_V8) || (_IPP==_IPP_P8) || \
      (_IPPLP32==_IPPLP32_S8) || (_IPP>=_IPP_G9) || \
      (_IPP32E==_IPP32E_M7) || \
      (_IPP32E==_IPP32E_U8) || (_IPP32E==_IPP32E_Y8) || \
      (_IPPLP64==_IPPLP64_N8) || (_IPP32E>=_IPP32E_E9) || \
      (_IPP64==_IPP64_I7) )
void Reduce_P224r1(BNU_CHUNK_T* pProduct)
{
   Ipp32u* pR = (Ipp32u*)pProduct;

   Ipp64u c7c11 = (Ipp64u)pR[ 7] + (Ipp64u)pR[11];
   Ipp64u c8c12 = (Ipp64u)pR[ 8] + (Ipp64u)pR[12];
   Ipp64u c9c13 = (Ipp64u)pR[ 9] + (Ipp64u)pR[13];

   Ipp64s
   sum   = (Ipp64u)pR[ 0] - c7c11;
   pR[0] = LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 1] - c8c12;
   pR[1] = LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 2] - c9c13;
   pR[2] = LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 3] + c7c11 - (Ipp64u)pR[10];
   pR[3] = LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 4] + c8c12 - (Ipp64u)pR[11];
   pR[4] = LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 5] + c9c13 - (Ipp64u)pR[12];
   pR[5] = LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 6] + (Ipp64u)pR[10] - (Ipp64u)pR[13];
   pR[6] = LODWORD(sum);
   pR[7] = (Ipp32u)(sum>>32);

   while(((BNS_CHUNK_T)pProduct[BITS_BNU_CHUNK(OPERAND_BITSIZE+1)-1]) <0) {
      cpAdd_BNU(pProduct, pProduct, (BNU_CHUNK_T*)secp224r1_p, BITS_BNU_CHUNK(OPERAND_BITSIZE+1));
   }
   while(0 <= cpCmp_BNU(pProduct, BITS_BNU_CHUNK(OPERAND_BITSIZE+1), (BNU_CHUNK_T*)secp224r1_p, BITS_BNU_CHUNK(OPERAND_BITSIZE+1))) {
      cpSub_BNU(pProduct, pProduct, (BNU_CHUNK_T*)secp224r1_p, BITS_BNU_CHUNK(OPERAND_BITSIZE+1));
   }
}
#endif

void cpAdde_224r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   BNU_CHUNK_T carry = cpAdd_BNU(rPtr, aPtr, bPtr, LEN_P224);
   if(carry || (0<=cpCmp_BNU(rPtr, LEN_P224, (BNU_CHUNK_T*)secp224r1_p, LEN_P224)))
      cpSub_BNU(rPtr, rPtr, (BNU_CHUNK_T*)secp224r1_p, LEN_P224);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P224;
}

void cpSube_224r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   BNU_CHUNK_T borrow = cpSub_BNU(rPtr, aPtr, bPtr, LEN_P224);
   if(borrow)
      cpAdd_BNU(rPtr, rPtr, (BNU_CHUNK_T*)secp224r1_p, LEN_P224);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P224;
}

void cpSqre_224r1(IppsBigNumState* pA, IppsBigNumState* pR)
{
   BNU_CHUNK_T tmpR[2*LEN_P224];

   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   cpSqr_BNU_school(tmpR, aPtr, LEN_P224);

   Reduce_P224r1(tmpR);
   COPY_BNU(rPtr, tmpR, LEN_P224);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P224;
}

void cpMule_224r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T tmpR[2*LEN_P224];

   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   cpMul_BNU_school(tmpR, aPtr, LEN_P224, bPtr, LEN_P224);

   Reduce_P224r1(tmpR);
   COPY_BNU(rPtr, tmpR, LEN_P224);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P224;
}

#endif /* _ECP_224_==_ECP_IMPL_SPECIFIC_ */
