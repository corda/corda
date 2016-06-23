/*
* Copyright (C) 2016 Intel Corporation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions
* are met:
*
*   * Redistributions of source code must retain the above copyright
*     notice, this list of conditions and the following disclaimer.
*   * Redistributions in binary form must reproduce the above copyright
*     notice, this list of conditions and the following disclaimer in
*     the documentation and/or other materials provided with the
*     distribution.
*   * Neither the name of Intel Corporation nor the names of its
*     contributors may be used to endorse or promote products derived
*     from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
* "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
* LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
* A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
* OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
* SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
* DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
* THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
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

/* fix actual length */
#define FIX_BNU(src,srcLen) \
   for(; ((srcLen)>1) && (0==(src)[(srcLen)-1]); (srcLen)--)


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

/* least and most significant BNU bit */
int cpMSBit_BNU(const BNU_CHUNK_T* pA, cpSize nsA);

/* BNU <-> hex-string conversion */
int cpToOctStr_BNU(Ipp8u* pStr, cpSize strLen, const BNU_CHUNK_T* pA, cpSize nsA);
int cpFromOctStr_BNU(BNU_CHUNK_T* pA, const Ipp8u* pStr, cpSize strLen);

#endif /* _PCP_BNUMISC_H */
