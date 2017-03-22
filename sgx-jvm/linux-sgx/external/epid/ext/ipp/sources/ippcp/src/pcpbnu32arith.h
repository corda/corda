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
//     Internal BNU32 arithmetic
// 
// 
*/

#if !defined(_CP_BNU32_ARITH_H)
#define _CP_BNU32_ARITH_H

Ipp32u cpAdd_BNU32(Ipp32u* pR, const Ipp32u* pA, const Ipp32u* pB, int ns);
Ipp32u cpSub_BNU32(Ipp32u* pR, const Ipp32u* pA, const Ipp32u* pB, int ns);
Ipp32u cpInc_BNU32(Ipp32u* pR, const Ipp32u* pA, cpSize ns, Ipp32u val);
Ipp32u cpDec_BNU32(Ipp32u* pR, const Ipp32u* pA, cpSize ns, Ipp32u val);

Ipp32u cpMulDgt_BNU32(Ipp32u* pR, const Ipp32u* pA, int ns, Ipp32u val);
Ipp32u cpSubMulDgt_BNU32(Ipp32u* pR, const Ipp32u* pA, int nsA, Ipp32u val);
#if 0
Ipp32u cpAddMulDgt_BNU32(Ipp32u* pR, const Ipp32u* pA, int nsA, Ipp32u val);
#endif

int cpDiv_BNU32(Ipp32u* pQ, int* nsQ, Ipp32u* pX, int nsX, Ipp32u* pY, int nsY);
#define cpMod_BNU32(pX,sizeX, pM,sizeM) cpDiv_BNU32(NULL,NULL, (pX),(sizeX), (pM),(sizeM))

int cpFromOS_BNU32(Ipp32u* pBNU, const Ipp8u* pOctStr, int strLen);
int cpToOS_BNU32(Ipp8u* pStr, int strLen, const Ipp32u* pBNU, int bnuSize);

void cpMul_BNU8(const Ipp32u* pA, const Ipp32u* pB, Ipp32u* pR);
void cpMul_BNU4(const Ipp32u* pA, const Ipp32u* pB, Ipp32u* pR);
void cpSqr_BNU8(const Ipp32u* pA, Ipp32u* pR);
void cpSqr_BNU4(const Ipp32u* pA, Ipp32u* pR);

#endif /* _CP_BNU32_ARITH_H */
