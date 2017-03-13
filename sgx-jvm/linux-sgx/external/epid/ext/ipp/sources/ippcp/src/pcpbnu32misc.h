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
//     Intel(R) Integrated Performance Primitives. Cryptography Primitives.
//     Internal Miscellaneous BNU 32 bit Definitions & Function Prototypes
// 
// 
*/

#if !defined(_CP_BNU32_MISC_H)
#define _CP_BNU32_MISC_H


/* bit operations */
#define BITSIZE_BNU32(p,ns)  ((ns)*BNU_CHUNK_32BIT-cpNLZ_BNU32((p)[(ns)-1]))

/* number of leading/trailing zeros */
cpSize cpNLZ_BNU32(Ipp32u x);
#if 0
cpSize cpNTZ_BNU32(Ipp32u x);
#endif

/* most significant BNU bit */
__INLINE int cpMSBit_BNU32(const Ipp32u* pA, cpSize nsA)
{
   FIX_BNU(pA, nsA);
   return nsA*BITSIZE(Ipp32u) - cpNLZ_BNU32(pA[nsA-1]) -1;
}


__INLINE int cpCmp_BNU32(const Ipp32u* pA, cpSize nsA, const Ipp32u* pB, cpSize nsB)
{
   if(nsA!=nsB)
      return nsA>nsB? 1 : -1;
   else {
      for(; nsA>0; nsA--) {
         if(pA[nsA-1] > pB[nsA-1])
            return 1;
         else if(pA[nsA-1] < pB[nsA-1])
            return -1;
      }
      return 0;
   }
}

/* logical shift left/right */
#if 0
cpSize cpLSL_BNU32(Ipp32u* pR, const Ipp32u* pA, cpSize nsA, cpSize nBits);
cpSize cpLSR_BNU32(Ipp32u* pR, const Ipp32u* pA, cpSize nsA, cpSize nBits);
#endif

/* to/from oct string conversion */
cpSize cpToOctStr_BNU32(Ipp8u* pStr, cpSize strLen, const Ipp32u* pBNU, cpSize bnuSize);
cpSize cpFromOctStr_BNU32(Ipp32u* pBNU, const Ipp8u* pOctStr, cpSize strLen);

#endif /* _CP_BNU32_MISC_H */
