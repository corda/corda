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

#include "owncp.h"
#include "pcpbnumisc.h"


/*
// number of leading zeros
*/
cpSize cpNLZ_BNU(BNU_CHUNK_T x)
{
   cpSize nlz = BNU_CHUNK_BITS;
   if(x) {
      nlz = 0;
      #if (BNU_CHUNK_BITS == BNU_CHUNK_64BIT)
      if( 0==(x & 0xFFFFFFFF00000000) ) { nlz +=32; x<<=32; }
      if( 0==(x & 0xFFFF000000000000) ) { nlz +=16; x<<=16; }
      if( 0==(x & 0xFF00000000000000) ) { nlz += 8; x<<= 8; }
      if( 0==(x & 0xF000000000000000) ) { nlz += 4; x<<= 4; }
      if( 0==(x & 0xC000000000000000) ) { nlz += 2; x<<= 2; }
      if( 0==(x & 0x8000000000000000) ) { nlz++; }
      #else
      if( 0==(x & 0xFFFF0000) ) { nlz +=16; x<<=16; }
      if( 0==(x & 0xFF000000) ) { nlz += 8; x<<= 8; }
      if( 0==(x & 0xF0000000) ) { nlz += 4; x<<= 4; }
      if( 0==(x & 0xC0000000) ) { nlz += 2; x<<= 2; }
      if( 0==(x & 0x80000000) ) { nlz++; }
      #endif
   }
   return nlz;
}

/*
// number of trailing zeros
*/
cpSize cpNTZ_BNU(BNU_CHUNK_T x)
{
   cpSize ntz = BNU_CHUNK_BITS;
   if(x) {
      ntz = 0;
      #if (BNU_CHUNK_BITS==BNU_CHUNK_64BIT)
      if( 0==(x & 0x00000000FFFFFFFF) ) { ntz+=32; x>>=32; }
      if( 0==(x & 0x000000000000FFFF) ) { ntz+=16; x>>=16; }
      if( 0==(x & 0x00000000000000FF) ) { ntz+= 8; x>>= 8; }
      if( 0==(x & 0x000000000000000F) ) { ntz+= 4; x>>= 4; }
      if( 0==(x & 0x0000000000000003) ) { ntz+= 2; x>>= 2; }
      if( 0==(x & 0x0000000000000001) ) { ntz++; }
      #else
      if( 0==(x & 0x0000FFFF) )         { ntz+=16; x>>=16; }
      if( 0==(x & 0x000000FF) )         { ntz+= 8; x>>= 8; }
      if( 0==(x & 0x0000000F) )         { ntz+= 4; x>>= 4; }
      if( 0==(x & 0x00000003) )         { ntz+= 2; x>>= 2; }
      if( 0==(x & 0x00000001) )         { ntz++; }
      #endif
   }
   return ntz;
}


/*
// Logical shift right (including inplace)
//
// Returns new length
//
*/
cpSize cpLSR_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize nsA, cpSize nBits)
{
   cpSize nw = nBits/BNU_CHUNK_BITS;
   cpSize n;

   pA += nw;
   nsA -= nw;

   nBits %= BNU_CHUNK_BITS;
   if(nBits) {
      BNU_CHUNK_T hi;
      BNU_CHUNK_T lo = pA[0];

      for(n=0; n<(nsA-1); n++) {
         hi = pA[n+1];
         pR[n] = (lo>>nBits) | (hi<<(BNU_CHUNK_BITS-nBits));
         lo = hi;
      }
      pR[nsA-1] = (lo>>nBits);
   }
   else {
      for(n=0; n<nsA; n++)
         pR[n] = pA[n];
   }

   for(n=0; n<nw; n++)
      pR[nsA+n] = 0;

   return nsA+nw;
}


/*
// Convert Oct String into BNU representation
//
// Returns size of BNU in BNU_CHUNK_T chunks
*/
cpSize cpFromOctStr_BNU(BNU_CHUNK_T* pA, const Ipp8u* pStr, cpSize strLen)
{
   int nsA =0;

   /* start from the end of string */
   for(; strLen>=(int)sizeof(BNU_CHUNK_T); nsA++,strLen-=(int)(sizeof(BNU_CHUNK_T))) {
      /* pack sizeof(BNU_CHUNK_T) bytes into single BNU_CHUNK_T value*/
      *pA++ =
         #if (BNU_CHUNK_BITS==BNU_CHUNK_64BIT)
         +( (BNU_CHUNK_T)pStr[strLen-8]<<(8*7) )
         +( (BNU_CHUNK_T)pStr[strLen-7]<<(8*6) )
         +( (BNU_CHUNK_T)pStr[strLen-6]<<(8*5) )
         +( (BNU_CHUNK_T)pStr[strLen-5]<<(8*4) )
         #endif
         +( (BNU_CHUNK_T)pStr[strLen-4]<<(8*3) )
         +( (BNU_CHUNK_T)pStr[strLen-3]<<(8*2) )
         +( (BNU_CHUNK_T)pStr[strLen-2]<<(8*1) )
         +  (BNU_CHUNK_T)pStr[strLen-1];
   }

   /* convert the beginning of the string */
   if(strLen) {
      BNU_CHUNK_T x = 0;
      for(x=0; strLen>0; strLen--) {
         BNU_CHUNK_T d = *pStr++;
         x = (x<<8) + d;
       }
       *pA++ = x;
       nsA++;
   }

   return nsA;
}

/*
// Convert BNU into HexString representation
//
// Returns length of the string or 0 if no success
*/
cpSize cpToOctStr_BNU(Ipp8u* pStr, cpSize strLen, const BNU_CHUNK_T* pA, cpSize nsA)
{
   FIX_BNU(pA, nsA);
   {
      cpSize bnuBitSize = BITSIZE_BNU(pA, nsA);
      if(bnuBitSize <= strLen*BYTESIZE) {
         int cnvLen = 0;
         BNU_CHUNK_T x = pA[nsA-1];

         ZEXPAND_BNU(pStr, 0, strLen);
         pStr += strLen - BITS2WORD8_SIZE(bnuBitSize);

         if(x) {
            //int nb;
            cpSize nb;
            for(nb=cpNLZ_BNU(x)/BYTESIZE; nb<(cpSize)(sizeof(BNU_CHUNK_T)); cnvLen++, nb++)
               *pStr++ = EBYTE(x, sizeof(BNU_CHUNK_T)-1-nb);

            for(--nsA; nsA>0; cnvLen+=sizeof(BNU_CHUNK_T), nsA--) {
               x = pA[nsA-1];
               #if (BNU_CHUNK_BITS==BNU_CHUNK_64BIT)
               *pStr++ = EBYTE(x,7);
               *pStr++ = EBYTE(x,6);
               *pStr++ = EBYTE(x,5);
               *pStr++ = EBYTE(x,4);
               #endif
               *pStr++ = EBYTE(x,3);
               *pStr++ = EBYTE(x,2);
               *pStr++ = EBYTE(x,1);
               *pStr++ = EBYTE(x,0);
            }
         }
         return strLen;
      }
      else
         return 0;
   }
}
