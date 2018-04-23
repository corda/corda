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

#if !defined(_PCP_PMA_H)
#define _PCP_PMA_H


#include "pcpbn.h"
#include "pcpmontgomery.h"


/*
// unsigned BN set/get
*/
#define SET_BN(pBN,bnu,len) \
   BN_SIGN((pBN)) = ippBigNumPOS; \
   BN_SIZE((pBN)) = ((len)); \
   Cpy_BNU((bnu), BN_NUMBER((pBN)), (len))

#define GET_BN(pBN,bnu,len) \
   Set_BNU(0, (bnu), (len)); \
   Cpy_BNU(BN_NUMBER((pBN)), (bnu), BN_SIZE((pBN)))


/*
// Prime Modulo Arithmetic
*/
#define PMA_set(r,a) \
   BN_SIGN((r)) = BN_SIGN((a)); \
   BN_SIZE((r)) = BN_SIZE((a)); \
   ZEXPAND_COPY_BNU(BN_NUMBER((r)),BN_ROOM((r)), BN_NUMBER((a)),BN_SIZE((a))) \

#define PMA_mod(r,a,modulo) \
   ippsMod_BN((a),(modulo),(r))

#define PMA_inv(r,a,modulo) \
   ippsModInv_BN((a),(modulo),(r))

#define PMA_neg(r,a,modulo) \
   ippsSub_BN((modulo),(a),(r))

#define PMA_lsr(r,a,modulo) \
   BN_SIZE((r)) = cpLSR_BNU(BN_NUMBER((a)), BN_NUMBER((r)), (int)BN_SIZE((a)), 1)

#define PMA_div2(r,a,modulo) { \
   if( IsOdd_BN((a)) ) { \
      ippsAdd_BN((a), (modulo), (a)); \
   } \
   BN_SIZE((r)) = cpLSR_BNU(BN_NUMBER((r)), BN_NUMBER((a)), (int)BN_SIZE((a)), 1); \
   cpBN_fix((r)); \
}

#define PMA_sqr(r,a,modulo) \
   PMA_mul(r,a,a,modulo)

#define PMA_add(r,a,b,modulo) \
   ippsAdd_BN((a),(b),(r));   \
   if( cpCmp_BNU(BN_NUMBER((r)),BN_SIZE((r)),BN_NUMBER((modulo)),BN_SIZE(modulo)) >= 0 ) \
      ippsSub_BN((r),(modulo),(r))

#define PMA_sub(r,a,b,modulo) \
   ippsSub_BN((a),(b),(r));   \
   if( BN_NEGATIVE((r)) )     \
      ippsAdd_BN((r),(modulo),(r))

#define PMA_mul(r,a,b,modulo) \
   ippsMul_BN((a),(b),(r));   \
   if( cpCmp_BNU(BN_NUMBER((r)),BN_SIZE((r)),BN_NUMBER((modulo)),BN_SIZE(modulo)) >= 0 ) \
      ippsMod_BN((r),(modulo),(r))

#define PMA_enc(r,a,mont) \
   cpMontEnc_BN((r), (a), (mont))

#define PMA_dec(r,a,mont) \
   cpMontDec_BN((r), (a), (mont))

#define PMA_sqre(r,a,mont) \
   ippsMontMul((a),(a), (mont),(r))

#define PMA_mule(r,a,b,mont) \
   ippsMontMul((a),(b), (mont),(r))

#endif /* _PCP_PMA_H */
