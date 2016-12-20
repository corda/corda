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

#if !defined(_PCP_PMASM2_H)
#define _PCP_PMASM2_H


#include "pcpbn.h"
//#include "pcppmafix.h"


/* length of operand in bits and BNU32_CHUNK_T */
#define OPERAND_BITSIZE (256)
#define LEN_P256        (BITS_BNU_CHUNK(OPERAND_BITSIZE))


/*
// Modular Arithmetic for secp256r1 ECC
*/
void Reduce_SM2(BNU_CHUNK_T* pR);
void cpAdde_SM2(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR);
void cpSube_SM2(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR);
void cpSqre_SM2(IppsBigNumState* pA, IppsBigNumState* pR);
void cpMule_SM2(IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR);

#define PMAsm2_add(r,a,b) \
   cpAdde_SM2((a),(b), (r))

#define PMAsm2_sub(r,a,b) \
   cpSube_SM2((a),(b), (r))

#define PMAsm2_sqr(r,a) \
   cpSqre_SM2((a),(r))

#define PMAsm2_mul(r,a,b) \
   cpMule_SM2((a),(b), (r))

__INLINE void maskMov(BNU_CHUNK_T dst[LEN_P256+1], const BNU_CHUNK_T src[LEN_P256+1], BNU_CHUNK_T moveFlag)
{
   BNU_CHUNK_T maskSrc = 0-moveFlag;
   BNU_CHUNK_T maskDst = ~maskSrc;

   dst[0] = (src[0] & maskSrc) ^  (dst[0] & maskDst);
   dst[1] = (src[1] & maskSrc) ^  (dst[1] & maskDst);
   dst[2] = (src[2] & maskSrc) ^  (dst[2] & maskDst);
   dst[3] = (src[3] & maskSrc) ^  (dst[3] & maskDst);
   dst[4] = (src[4] & maskSrc) ^  (dst[4] & maskDst);
   #if (_IPP_ARCH ==_ARCH_IA32)
   dst[5] = (src[5] & maskSrc) ^  (dst[5] & maskDst);
   dst[6] = (src[6] & maskSrc) ^  (dst[6] & maskDst);
   dst[7] = (src[7] & maskSrc) ^  (dst[7] & maskDst);
   dst[8] = (src[8] & maskSrc) ^  (dst[8] & maskDst);
   #endif
}

#if 0
__INLINE void PMAsm2_div2(IppsBigNumState* r, IppsBigNumState* a)
{
   BNU_CHUNK_T t[LEN_P256+1];

   BNU_CHUNK_T* aData = BN_NUMBER(a);
   BNU_CHUNK_T aIsEeven = 1 - aData[0]&1;

   /* expand a value */
   ZEXPAND_BNU(aData, BN_SIZE(a), LEN_P256+1);
   /* add modulus */
   cpAdd_BNU(t, aData, (BNU_CHUNK_T*)tpmSM2_p256_p, LEN_P256+1);
   /* if a value is even then assign t to a */
   maskMov(t, aData, aIsEeven);

   /* div by 2 */
   cpLSR_BNU(BN_NUMBER((r)), t, LEN_P256+1, 1);
   BN_SIGN((r)) = ippBigNumPOS;
   BN_SIZE((r)) = LEN_P256;
}
#endif
#define PMAsm2_div2(r,a) \
{ \
   if( IsOdd_BN((a)) ) { \
      cpInc_BNU(BN_NUMBER((r)), BN_NUMBER((a)), LEN_P256, 1); \
      cpLSR_BNU(BN_NUMBER((r)), BN_NUMBER((r)), LEN_P256, 1); \
      cpAdd_BNU(BN_NUMBER((r)), BN_NUMBER((r)), (BNU_CHUNK_T*)h_tpmSM2_p256_p, LEN_P256); \
   } \
   else \
      cpLSR_BNU(BN_NUMBER((r)), BN_NUMBER((a)), LEN_P256, 1); \
   BN_SIGN((r)) = ippBigNumPOS; \
   BN_SIZE((r)) = LEN_P256; \
}

#define PMAsm2_inv(r,a,modulo) \
{ \
   ippsModInv_BN((a),(modulo),(r)); \
   ZEXPAND_BNU(BN_NUMBER((r)),BN_SIZE((r)), LEN_P256); \
   BN_SIGN((r)) = ippBigNumPOS; \
   BN_SIZE((r)) = LEN_P256; \
}

#endif /* _PCP_PMASM2_H */
