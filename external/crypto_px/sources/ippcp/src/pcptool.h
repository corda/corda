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

#if !defined(_PC_TOOL_H)
#define _CP_TOOL_H

/* copy data block */
__INLINE void CopyBlock(const void* pSrc, void* pDst, cpSize numBytes)
{
   const Ipp8u* s  = (Ipp8u*)pSrc;
   Ipp8u* d  = (Ipp8u*)pDst;
   cpSize k;
   for(k=0; k<numBytes; k++ )
      d[k] = s[k];
}
__INLINE void CopyBlock8(const void* pSrc, void* pDst)
{
   int k;
   for(k=0; k<8; k++ )
      ((Ipp8u*)pDst)[k] = ((Ipp8u*)pSrc)[k];
}
__INLINE void CopyBlock16(const void* pSrc, void* pDst)
{
   int k;
   for(k=0; k<16; k++ )
      ((Ipp8u*)pDst)[k] = ((Ipp8u*)pSrc)[k];
}
__INLINE void CopyBlock24(const void* pSrc, void* pDst)
{
   int k;
   for(k=0; k<24; k++ )
      ((Ipp8u*)pDst)[k] = ((Ipp8u*)pSrc)[k];
}
__INLINE void CopyBlock32(const void* pSrc, void* pDst)
{
   int k;
   for(k=0; k<32; k++ )
      ((Ipp8u*)pDst)[k] = ((Ipp8u*)pSrc)[k];
}

/*
// padding data block
*/
__INLINE void PaddBlock(Ipp8u paddingByte, void* pDst, cpSize numBytes)
{
   Ipp8u* d  = (Ipp8u*)pDst;
   cpSize k;
   for(k=0; k<numBytes; k++ )
      d[k] = paddingByte;
}

__INLINE void PurgeBlock(void* pDst, int len)
{
   int n;
   for(n=0; n<len; n++) ((Ipp8u*)pDst)[n] = 0;
}

/* fill block */
__INLINE void FillBlock16(Ipp8u filler, const void* pSrc, void* pDst, int len)
{
   int n;
   for(n=0; n<len; n++) ((Ipp8u*)pDst)[n] = ((Ipp8u*)pSrc)[n];
   for(; n<16; n++) ((Ipp8u*)pDst)[n] = filler;
}

/* xor block */
__INLINE void XorBlock(const void* pSrc1, const void* pSrc2, void* pDst, int len)
{
   const Ipp8u* p1 = (const Ipp8u*)pSrc1;
   const Ipp8u* p2 = (const Ipp8u*)pSrc2;
   Ipp8u* d  = (Ipp8u*)pDst;
   int k;
   for(k=0; k<len; k++)
      d[k] = (Ipp8u)(p1[k] ^p2[k]);
}
__INLINE void XorBlock8(const void* pSrc1, const void* pSrc2, void* pDst)
{
   const Ipp8u* p1 = (const Ipp8u*)pSrc1;
   const Ipp8u* p2 = (const Ipp8u*)pSrc2;
   Ipp8u* d  = (Ipp8u*)pDst;
   int k;
   for(k=0; k<8; k++ )
      d[k] = (Ipp8u)(p1[k] ^p2[k]);
}
__INLINE void XorBlock16(const void* pSrc1, const void* pSrc2, void* pDst)
{
   const Ipp8u* p1 = (const Ipp8u*)pSrc1;
   const Ipp8u* p2 = (const Ipp8u*)pSrc2;
   Ipp8u* d  = (Ipp8u*)pDst;
   int k;
   for(k=0; k<16; k++ )
      d[k] = (Ipp8u)(p1[k] ^p2[k]);
}
__INLINE void XorBlock24(const void* pSrc1, const void* pSrc2, void* pDst)
{
   const Ipp8u* p1 = (const Ipp8u*)pSrc1;
   const Ipp8u* p2 = (const Ipp8u*)pSrc2;
   Ipp8u* d  = (Ipp8u*)pDst;
   int k;
   for(k=0; k<24; k++ )
      d[k] = (Ipp8u)(p1[k] ^p2[k]);
}
__INLINE void XorBlock32(const void* pSrc1, const void* pSrc2, void* pDst)
{
   const Ipp8u* p1 = (const Ipp8u*)pSrc1;
   const Ipp8u* p2 = (const Ipp8u*)pSrc2;
   Ipp8u* d  = (Ipp8u*)pDst;
   int k;
   for(k=0; k<32; k++ )
      d[k] = (Ipp8u)(p1[k] ^p2[k]);
}


/* compare (equivalence) */
__INLINE int EquBlock(const void* pSrc1, const void* pSrc2, int len)
{
   const Ipp8u* p1 = (const Ipp8u*)pSrc1;
   const Ipp8u* p2 = (const Ipp8u*)pSrc2;
   int k;
   int isEqu;
   for(k=0, isEqu=1; k<len && isEqu; k++)
      isEqu = (p1[k] == p2[k]);
   return isEqu;
}


/* addition functions for CTR mode of diffenent block ciphers */
__INLINE void StdIncrement(Ipp8u* pCounter, int blkSize, int numSize)
{
   int maskPosition = (blkSize-numSize)/8;
   Ipp8u mask = (Ipp8u)( 0xFF >> (blkSize-numSize)%8 );

   /* save crytical byte */
   Ipp8u save  = (Ipp8u)( pCounter[maskPosition] & ~mask );

   int len = BITS2WORD8_SIZE(blkSize);
   Ipp32u carry = 1;
   for(; (len>maskPosition) && carry; len--) {
      Ipp32u x = pCounter[len-1] + carry;
      pCounter[len-1] = (Ipp8u)x;
      carry = (x>>8) & 0xFF;
   }

   /* update crytical byte */
   pCounter[maskPosition] &= mask;
   pCounter[maskPosition] |= save;
}

#endif /* _CP_TOOL_H */
