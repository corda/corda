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

#if (_ECP_384_==_ECP_IMPL_SPECIFIC_) || (_ECP_384_==_ECP_IMPL_MFM_)
#include "pcpeccp.h"
#include "pcppma384.h"


/*
// Specific Modulo Arithmetic
//    P384 = 2^384 -2^128 -2^96 +2^32 -1
//    (reference secp384r1_p)
*/

/*
// Reduce modulo:
//
//  x = c23|c22|c21|c20|c19|c18|c17|c16|c15|c14|c13|c12|c11|c10|c09|c08|c07|c06|c05|c04|c03|c02|c01|c00 - 32-bits values
//
// s1 = c11|c10|c09|c08|c07|c06|c05|c04|c03|c02|c01|c00
// s2 = 000|000|000|000|000|c23|c22|c21|000|000|000|000
// s3 = c23|c22|c21|c20|c19|c18|c17|c16|c15|c14|c13|c12
// s4 = c20|c19|c18|c17|c16|c15|c14|c13|c12|c23|c22|c21
// s5 = c19|c18|c17|c16|c15|c14|c13|c12|c20|000|c23|000
// s6 = 000|000|000|000|c23|c22|c21|c20|000|000|000|000
// s7 = 000|000|000|000|000|000|c23|c22|c21|000|000|c20
//
// s8 = c22|c21|c20|c19|c18|c17|c16|c15|c14|c13|c12|c23
// s9 = 000|000|000|000|000|000|000|c23|c22|c21|c20|000
// s10= 000|000|000|000|000|000|000|c23|c23|000|000|000
//
// r = (s1+2*s2+s3+s4+s5+s6+s7-s8-s9-10) (mod P)
*/

//static
void Reduce_P384r1(BNU_CHUNK_T* pProduct)
{
   #define CHUNK_LEN_P384  (BITS_BNU_CHUNK(OPERAND_BITSIZE))

   Ipp32u* pR = (Ipp32u*)pProduct;

   Ipp64u c12c21 = (Ipp64u)pR[12] + (Ipp64u)pR[21];
   Ipp64u c13c22 = (Ipp64u)pR[13] + (Ipp64u)pR[22];
   Ipp64u c14c23 = (Ipp64u)pR[14] + (Ipp64u)pR[23];

   Ipp64s
   sum   = (Ipp64u)pR[ 0] + c12c21 + (Ipp64u)pR[20] - (Ipp64u)pR[23];
   pR[ 0]= LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 1] + c13c22 + (Ipp64u)pR[23] - (Ipp64u)pR[12] -  (Ipp64u)pR[20];
   pR[ 1]= LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 2] + c14c23 - (Ipp64u)pR[13] - (Ipp64u)pR[21];
   pR[ 2]= LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 3] + c12c21 + (Ipp64u)pR[15] + (Ipp64u)pR[20] - c14c23 - (Ipp64u)pR[22];
   pR[ 3]= LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 4] + (Ipp64u)pR[21] + c12c21 + c13c22 + (Ipp64u)pR[16] + (Ipp64u)pR[20] - (Ipp64u)pR[15] - (Ipp64u)pR[23] - (Ipp64u)pR[23];
   pR[ 4]= LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 5] + (Ipp64u)pR[22] + c13c22 + c14c23 + (Ipp64u)pR[17] + (Ipp64u)pR[21] - (Ipp64u)pR[16];
   pR[ 5]= LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 6] + (Ipp64u)pR[23] + c14c23 + (Ipp64u)pR[15] + (Ipp64u)pR[18] + (Ipp64u)pR[22] - (Ipp64u)pR[17];
   pR[ 6]= LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 7] + (Ipp64u)pR[15] + (Ipp64u)pR[16] + (Ipp64u)pR[19] + (Ipp64u)pR[23] - (Ipp64u)pR[18];
   pR[ 7]= LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 8] + (Ipp64u)pR[16] + (Ipp64u)pR[17] + (Ipp64u)pR[20] - (Ipp64u)pR[19];
   pR[ 8]= LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[ 9] + (Ipp64u)pR[17] + (Ipp64u)pR[18] + (Ipp64u)pR[21] - (Ipp64u)pR[20];
   pR[ 9]= LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[10] + (Ipp64u)pR[18] + (Ipp64u)pR[19] + (Ipp64u)pR[22] - (Ipp64u)pR[21];
   pR[10]= LODWORD(sum);
   sum >>= 32;

   sum  += (Ipp64u)pR[11] + (Ipp64u)pR[19] + (Ipp64u)pR[20] + (Ipp64u)pR[23] - (Ipp64u)pR[22];
   pR[11]= LODWORD(sum);
   sum >>= 32;
   pProduct[LEN_P384] = (BNU_CHUNK_T)sum;

   while(((BNS_CHUNK_T)pProduct[LEN_P384]) <0)
      cpAdd_BNU(pProduct, pProduct, (BNU_CHUNK_T*)secp384r1_p, LEN_P384+1);

   while(0 <= cpCmp_BNU(pProduct, LEN_P384+1, (BNU_CHUNK_T*)secp384r1_p, LEN_P384+1))
      cpSub_BNU(pProduct, pProduct, (BNU_CHUNK_T*)secp384r1_p, LEN_P384+1);
}


void cpSqre_384r1(IppsBigNumState* pA, IppsBigNumState* pR)
{
   BNU_CHUNK_T tmpR[2*LEN_P384];

   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   cpSqr_BNU_school(tmpR, aPtr, LEN_P384);

   Reduce_P384r1(tmpR);
   COPY_BNU(rPtr, tmpR, LEN_P384);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P384;
}

void cpMule_384r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T tmpR[2*LEN_P384];

   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   cpMul_BNU_school(tmpR, aPtr, LEN_P384, bPtr, LEN_P384);

   Reduce_P384r1(tmpR);
   COPY_BNU(rPtr, tmpR, LEN_P384);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P384;
}
#endif /* (_ECP_384_==_ECP_IMPL_SPECIFIC_) || (_ECP_384_==_ECP_IMPL_MFM_) */

#if (_ECP_384_==_ECP_IMPL_SPECIFIC_)
void cpAdde_384r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   BNU_CHUNK_T carry = cpAdd_BNU(rPtr, aPtr, bPtr, LEN_P384);
   if(carry || (0<=cpCmp_BNU(rPtr, LEN_P384, (BNU_CHUNK_T*)secp384r1_p, LEN_P384)))
      cpSub_BNU(rPtr, rPtr, (BNU_CHUNK_T*)secp384r1_p, LEN_P384);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P384;
}

void cpSube_384r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   BNU_CHUNK_T borrow = cpSub_BNU(rPtr, aPtr, bPtr, LEN_P384);
   if(borrow)
      cpAdd_BNU(rPtr, rPtr, (BNU_CHUNK_T*)secp384r1_p, LEN_P384);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P384;
}
#endif /* _ECP_384_==_ECP_IMPL_SPECIFIC_ */
