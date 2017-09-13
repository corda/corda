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
//               Intel(R) Integrated Performance Primitives
//                   Cryptographic Primitives (ippcp)
// 
*/

#include "owncp.h"
#include "pcpbnuarith.h"


#if !((_IPP==_IPP_W7) || \
      (_IPP==_IPP_T7) || \
      (_IPP==_IPP_V8) || \
      (_IPP==_IPP_P8) || \
      (_IPP>=_IPP_G9) || \
      (_IPPLP32==_IPPLP32_S8) || \
      (_IPP32E==_IPP32E_M7) || \
      (_IPP32E==_IPP32E_U8) || \
      (_IPP32E==_IPP32E_Y8) || \
      (_IPP32E>=_IPP32E_E9) || \
      (_IPPLP64==_IPPLP64_N8))

#if 0
#define MASKED_COPY_BNU(dst, mask, src1, src2, len) { \
   cpSize i; \
   for(i=0; i<(len); i++) (dst)[i] = ((mask) & (src1)[i]) | (~(mask) & (src2)[i]); \
}
#endif

void cpMontRedAdc_BNU(BNU_CHUNK_T* pR,
                      BNU_CHUNK_T* pProduct,
                const BNU_CHUNK_T* pModulus, cpSize nsM, BNU_CHUNK_T m0)
{
   BNU_CHUNK_T carry;
   BNU_CHUNK_T extension;

   cpSize n;
   for(n=0, carry = 0; n<(nsM-1); n++) {
      BNU_CHUNK_T u = pProduct[n]*m0;
      BNU_CHUNK_T t = pProduct[nsM +n +1] + carry;

      extension = cpAddMulDgt_BNU(pProduct+n, pModulus, nsM, u);
      ADD_AB(carry, pProduct[nsM+n], pProduct[nsM+n], extension);
      t += carry;

      carry = t<pProduct[nsM+n+1];
      pProduct[nsM+n+1] = t;
   }

   m0 *= pProduct[nsM-1];
   extension = cpAddMulDgt_BNU(pProduct+nsM-1, pModulus, nsM, m0);
   ADD_AB(extension, pProduct[2*nsM-1], pProduct[2*nsM-1], extension);

   carry |= extension;
   carry -= cpSub_BNU(pR, pProduct+nsM, pModulus, nsM);
   /* condition copy: R = carry? Product+mSize : R */
   MASKED_COPY_BNU(pR, carry, pProduct+nsM, pR, nsM);
}
#endif
