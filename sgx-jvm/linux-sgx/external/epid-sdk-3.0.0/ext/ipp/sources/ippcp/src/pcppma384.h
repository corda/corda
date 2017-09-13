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
//     Internal Definitions and
//     Internal Prime Modulo Arithmetic Function Prototypes
// 
// 
*/

#if !defined(_PCP_PMA384_H)
#define _PCP_PMA384_H


#include "pcpbn.h"
//#include "pcppmafix.h"


/* length of operand in bits and BNU32_CHUNK_T */
#define OPERAND_BITSIZE (384)
#define LEN_P384        (BITS_BNU_CHUNK(OPERAND_BITSIZE))

/*
// Modular Arithmetic for secp384r1 ECC
*/
void Reduce_P384r1(BNU_CHUNK_T* pProduct);

void cpAdde_384r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR);
void cpSube_384r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR);
void cpSqre_384r1(IppsBigNumState* pA, IppsBigNumState* pR);
void cpMule_384r1(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR);

#define PMA384_add(r,a,b) \
   cpAdde_384r1((a),(b), (r))

#define PMA384_sub(r,a,b) \
   cpSube_384r1((a),(b), (r))

#define PMA384_sqr(r,a) \
   cpSqre_384r1((a),(r))

#define PMA384_mul(r,a,b) \
   cpMule_384r1((a),(b), (r))

#define PMA384_div2(r,a) \
{ \
   if( IsOdd_BN((a)) ) { \
      cpInc_BNU(BN_NUMBER((r)), BN_NUMBER((a)), LEN_P384, 1); \
      cpLSR_BNU(BN_NUMBER((r)), BN_NUMBER((r)), LEN_P384, 1); \
      cpAdd_BNU(BN_NUMBER((r)), BN_NUMBER((r)), (BNU_CHUNK_T*)h_secp384r1_p, LEN_P384); \
   } \
   else \
      cpLSR_BNU(BN_NUMBER((r)), BN_NUMBER((a)), LEN_P384, 1); \
   BN_SIGN((r)) = ippBigNumPOS; \
   BN_SIZE((r)) = LEN_P384; \
}

#define PMA384_inv(r,a,modulo) \
{ \
   ippsModInv_BN((a),(modulo),(r)); \
   ZEXPAND_BNU(BN_NUMBER((r)),BN_SIZE((r)), LEN_P384); \
   BN_SIGN((r)) = ippBigNumPOS; \
   BN_SIZE((r)) = LEN_P384; \
}

#endif /* _PCP_PMA384_H */
