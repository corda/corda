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
#include "pcpeccp.h"
#include "pcppma521.h"

#if (_ECP_521_==_ECP_IMPL_SPECIFIC_) || (_ECP_521_==_ECP_IMPL_MFM_)


/*
// Specific Modulo Arithmetic
//    P521 = 2^521 -1
//    (reference secp521r1_p)
*/

/*
// Reduce modulo:
//
//  x = a1*2^521 + a0 - 521-bits values
//
// r = (s1+a0) (mod P)
*/
static
void Reduce_P521r1(BNU_CHUNK_T* pProduct)
{
   BNU_CHUNK_T  TT[LEN_P521];
   BNU_CHUNK_T* pR = pProduct;

   cpLSR_BNU(TT, pR+LEN_P521-1, LEN_P521, OPERAND_BITSIZE%BITSIZE(BNU_CHUNK_T));
   pR[LEN_P521-1] &= MASK_BNU_CHUNK(OPERAND_BITSIZE % BITSIZE(BNU_CHUNK_T));
   TT[LEN_P521-1] &= MASK_BNU_CHUNK(OPERAND_BITSIZE % BITSIZE(BNU_CHUNK_T));
   cpAdd_BNU(pR, pR, TT, LEN_P521);

   while(0 <= cpCmp_BNU(pR, LEN_P521, (BNU_CHUNK_T*)secp521r1_p, LEN_P521))
      cpSub_BNU(pR, pR, (BNU_CHUNK_T*)secp521r1_p, LEN_P521);
}

void cpSqre_521r1(IppsBigNumState* pA, IppsBigNumState* pR)
{
   BNU_CHUNK_T tmpR[2*LEN_P521];

   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   cpSqr_BNU_school(tmpR, aPtr, LEN_P521);

   Reduce_P521r1(tmpR);
   COPY_BNU(rPtr, tmpR, LEN_P521);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P521;
}

void cpMule_521r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T tmpR[2*LEN_P521];

   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   cpMul_BNU_school(tmpR, aPtr, LEN_P521, bPtr, LEN_P521);

   Reduce_P521r1(tmpR);
   COPY_BNU(rPtr, tmpR, LEN_P521);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P521;
}
#endif /* (_ECP_521_==_ECP_IMPL_SPECIFIC_) || (_ECP_521_==_ECP_IMPL_MFM_) */

#if (_ECP_521_==_ECP_IMPL_SPECIFIC_)
void cpAdde_521r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   BNU_CHUNK_T carry = cpAdd_BNU(rPtr, aPtr, bPtr, LEN_P521);
   if(carry || (0<=cpCmp_BNU(rPtr, LEN_P521, (BNU_CHUNK_T*)secp521r1_p, LEN_P521)))
      cpSub_BNU(rPtr, rPtr, (BNU_CHUNK_T*)secp521r1_p, LEN_P521);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P521;
}

void cpSube_521r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR)
{
   BNU_CHUNK_T* aPtr = BN_NUMBER(pA);
   BNU_CHUNK_T* bPtr = BN_NUMBER(pB);
   BNU_CHUNK_T* rPtr = BN_NUMBER(pR);

   BNU_CHUNK_T borrow = cpSub_BNU(rPtr, aPtr, bPtr, LEN_P521);
   if(borrow)
      cpAdd_BNU(rPtr, rPtr, (BNU_CHUNK_T*)secp521r1_p, LEN_P521);

   BN_SIGN(pR) = ippBigNumPOS;
   BN_SIZE(pR) = LEN_P521;
}
#endif /* _ECP_521_==_ECP_IMPL_SPECIFIC_ */
