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
//     Internal Miscellaneous BNU Definitions & Function Prototypes
// 
// 
*/

#if !defined(_PCP_BNUMISC_H)
#define _PCP_BNUMISC_H

#include "pcpbnuimpl.h"


/* bit operations */
#define BITSIZE_BNU(p,ns)  ((ns)*BNU_CHUNK_BITS-cpNLZ_BNU((p)[(ns)-1]))
#define BIT_BNU(bnu, ns,nbit) ((((nbit)>>BNU_CHUNK_LOG2) < (ns))? ((((bnu))[(nbit)>>BNU_CHUNK_LOG2] >>((nbit)&(BNU_CHUNK_BITS))) &1) : 0)
#define TST_BIT(bnu, nbit)    ((((bnu))[(nbit)>>BNU_CHUNK_LOG2]) &  ((BNU_CHUNK_T)1<<((nbit)&(BNU_CHUNK_BITS-1))))
#define SET_BIT(bnu, nbit)    ((((bnu))[(nbit)>>BNU_CHUNK_LOG2]) |= ((BNU_CHUNK_T)1<<((nbit)&(BNU_CHUNK_BITS-1))))
#define CLR_BIT(bnu, nbit)    ((((bnu))[(nbit)>>BNU_CHUNK_LOG2]) &=~((BNU_CHUNK_T)1<<((nbit)&(BNU_CHUNK_BITS-1))))

/* convert bitsize nbits into  the number of BNU_CHUNK_T */
#define BITS_BNU_CHUNK(nbits) (((nbits)+BNU_CHUNK_BITS-1)/BNU_CHUNK_BITS)

/* mask for top BNU_CHUNK_T */
#define MASK_BNU_CHUNK(nbits) ((BNU_CHUNK_T)(-1) >>((BNU_CHUNK_BITS- ((nbits)&(BNU_CHUNK_BITS-1))) &(BNU_CHUNK_BITS-1)))

/* copy BNU content */
#define COPY_BNU(dst, src, len) \
{ \
   cpSize __idx; \
   for(__idx=0; __idx<(len); __idx++) (dst)[__idx] = (src)[__idx]; \
}

/* expand by zeros */
#define ZEXPAND_BNU(srcdst,srcLen, dstLen) \
{ \
   cpSize __idx; \
   for(__idx=(srcLen); __idx<(dstLen); __idx++) (srcdst)[__idx] = 0; \
}

/* copy and expand by zeros */
#define ZEXPAND_COPY_BNU(dst,dstLen, src,srcLen) \
{ \
   cpSize __idx; \
   for(__idx=0; __idx<(srcLen); __idx++) (dst)[__idx] = (src)[__idx]; \
   for(; __idx<(dstLen); __idx++)    (dst)[__idx] = 0; \
}

/* compare */
#if 0
#define CMP_BNU(sign, pA, pB, len) \
{ \
   for((sign)=(len); (sign)>0; (sign)--) { \
      if( (pA)[(sign)-1] != (pB)[(sign)-1] ) \
         break; \
   } \
   (sign) = (sign)? ((pA)[(sign)-1] > (pB)[(sign)-1])? 1:-1 : 0; \
}
#endif

/* fix actual length; {} is required to build with GCC6 */
#define FIX_BNU(src,srcLen) \
   for(; ((srcLen)>1) && (0==(src)[(srcLen)-1]); (srcLen)--){}


/* copy and set */
__INLINE void cpCpy_BNU(BNU_CHUNK_T* pDst, const BNU_CHUNK_T* pSrc, cpSize ns)
{  COPY_BNU(pDst, pSrc, ns); }

__INLINE void cpSet_BNU(BNU_CHUNK_T* pDst, cpSize ns, BNU_CHUNK_T val)
{
   ZEXPAND_BNU(pDst, 0, ns);
   pDst[0] = val;
}

/* fix up */
__INLINE int cpFix_BNU(const BNU_CHUNK_T* pA, int nsA)
{
   FIX_BNU(pA, nsA);
   return nsA;
}

/* comparison
//
// returns
//    negative, if A < B
//           0, if A = B
//    positive, if A > B
*/
__INLINE int cpCmp_BNU(const BNU_CHUNK_T* pA, cpSize nsA, const BNU_CHUNK_T* pB, cpSize nsB)
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
__INLINE int cpEqu_BNU_CHUNK(const BNU_CHUNK_T* pA, cpSize nsA, BNU_CHUNK_T b)
{
   return (pA[0]==b && 1==cpFix_BNU(pA, nsA));
}

/*
// test
//
// returns
//     0, if A = 0
//    >0, if A > 0
//    <0, looks like impossible (or error) case
*/
__INLINE int cpTst_BNU(const BNU_CHUNK_T* pA, int nsA)
{
   for(; (nsA>0) && (0==pA[nsA-1]); nsA--) ;
   return nsA;
}

/* number of leading/trailing zeros */
cpSize cpNLZ_BNU(BNU_CHUNK_T x);
cpSize cpNTZ_BNU(BNU_CHUNK_T x);

/* logical shift left/right */
int cpLSR_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize nsA, cpSize nBits);
#if 0
int cpLSL_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize nsA, cpSize nBits);
#endif

/* least and most significant BNU bit */
#if 0
int cpLSBit_BNU(const BNU_CHUNK_T* pA, cpSize nsA);
#endif
int cpMSBit_BNU(const BNU_CHUNK_T* pA, cpSize nsA);

/* BNU <-> hex-string conversion */
int cpToOctStr_BNU(Ipp8u* pStr, cpSize strLen, const BNU_CHUNK_T* pA, cpSize nsA);
int cpFromOctStr_BNU(BNU_CHUNK_T* pA, const Ipp8u* pStr, cpSize strLen);

#endif /* _PCP_BNUMISC_H */
