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
//     Unsigned internal BNU32 misc functionality
// 
//  Contents:
//     cpNLZ_BNU32()
//     cpNTZ_BNU32()
// 
//     cpLSL_BNU32()
//     cpLSR_BNU32()
// 
//     cpFromOctStr_BNU32()
//     cpToOctStr_BNU32()
// 
// 
*/

#include "owncp.h"
#include "pcpbnuimpl.h"
#include "pcpbnumisc.h"
#include "pcpbnu32misc.h"


/*
// number of leading zeros
*/
cpSize cpNLZ_BNU32(Ipp32u x)
{
   cpSize nlz = BITSIZE(Ipp32u);
   if(x) {
      nlz = 0;
      if( 0==(x & 0xFFFF0000) ) { nlz +=16; x<<=16; }
      if( 0==(x & 0xFF000000) ) { nlz += 8; x<<= 8; }
      if( 0==(x & 0xF0000000) ) { nlz += 4; x<<= 4; }
      if( 0==(x & 0xC0000000) ) { nlz += 2; x<<= 2; }
      if( 0==(x & 0x80000000) ) { nlz++; }
   }
   return nlz;
}

/*
// number of trailing zeros
*/
#if 0
cpSize cpNTZ_BNU32(Ipp32u x)
{
   cpSize ntz = BITSIZE(Ipp32u);
   if(x) {
      ntz = 0;
      if( 0==(x & 0x0000FFFF) )         { ntz+=16; x>>=16; }
      if( 0==(x & 0x000000FF) )         { ntz+= 8; x>>= 8; }
      if( 0==(x & 0x0000000F) )         { ntz+= 4; x>>= 4; }
      if( 0==(x & 0x00000003) )         { ntz+= 2; x>>= 2; }
      if( 0==(x & 0x00000001) )         { ntz++; }
   }
   return ntz;
}
#endif

/*
// Logical shift left (including inplace)
//
// Returns new length
*/
#if 0
cpSize cpLSL_BNU32(Ipp32u* pR, const Ipp32u* pA, cpSize nsA, cpSize nBits)
{
   cpSize nlz = cpNLZ_BNU32(pA[nsA-1]);
   cpSize nw = nBits/BNU_CHUNK_32BIT;
   cpSize n;

   pR += nw;

   nBits %= BNU_CHUNK_32BIT;
   if(nBits) {
      Ipp32u hi,lo;

      if(nlz < nBits )
         hi = 0;
      else
         hi = pA[--nsA];

      for(n=nsA; n>0; n--) {
         lo = pA[n-1];
         pR[n] = (hi<<nBits) | (lo>>(BNU_CHUNK_32BIT-nBits));
         hi = lo;
      }
      pR[0] = (hi<<nBits);
   }

   else {
      for(n=nsA; n>0; n--)
         pR[n-1] = pA[n-1];
   }

   pR--;
   for(n=0; n<nw; n++)
      pR[n] = 0;

   return nsA+nw;
}
#endif

/*
// Logical shift right (including inplace)
//
// Returns new length
*/
#if 0
cpSize cpLSR_BNU32(Ipp32u* pR, const Ipp32u* pA, cpSize nsA, cpSize nBits)
{
   cpSize cnz = BNU_CHUNK_32BIT - cpNLZ_BNU32(pA[nsA-1]);
   cpSize nw = nBits/BNU_CHUNK_32BIT;
   cpSize n;

   pA += nw;
   nsA -= nw;

   nBits %= BNU_CHUNK_32BIT;
   if(nBits) {
      Ipp32u hi;
      Ipp32u lo = pA[0];

      for(n=0; n<(nsA-1); n++) {
         hi = pA[n+1];
         pR[n] = (lo>>nBits) | (hi<<(BNU_CHUNK_32BIT-nBits));
         lo = hi;
      }
      if(cnz > nBits)
         pR[nsA-1] = (lo>>nBits);
      else
         nsA--;
   }

   else {
      for(n=0; n<nsA; n++)
         pR[n] = pA[n];
   }

   return nsA;
}
#endif

/*
// Convert Oct String into BNU representation
//
// Returns length of BNU in Ipp32u chunks
*/
cpSize cpFromOctStr_BNU32(Ipp32u* pBNU, const Ipp8u* pOctStr, cpSize strLen)
{
   cpSize bnuSize=0;
   *pBNU = 0;

   /* start from the end of string */
   for(; strLen>=4; bnuSize++,strLen-=4) {
      /* pack 4 bytes into single Ipp32u value*/
      *pBNU++ = ( pOctStr[strLen-4]<<(8*3) )
               +( pOctStr[strLen-3]<<(8*2) )
               +( pOctStr[strLen-2]<<(8*1) )
               +  pOctStr[strLen-1];
   }

   /* convert the beginning of the string */
   if(strLen) {
      Ipp32u x;
      for(x=0; strLen>0; strLen--) {
         Ipp32u d = *pOctStr++;
         x = x*256 + d;
       }
       *pBNU++ = x;
       bnuSize++;
   }

   return bnuSize? bnuSize : 1;
}


/*
// Convert BNU into Octet String representation
//
// Returns strLen or 0 if no success
*/
cpSize cpToOctStr_BNU32(Ipp8u* pStr, cpSize strLen, const Ipp32u* pBNU, cpSize bnuSize)
{
   FIX_BNU(pBNU, bnuSize);
   {
      int bnuBitSize = BITSIZE_BNU32(pBNU, bnuSize);
      if(bnuBitSize <= strLen*BYTESIZE) {
         Ipp32u x = pBNU[bnuSize-1];

         ZEXPAND_BNU(pStr, 0, strLen);
         pStr += strLen - BITS2WORD8_SIZE(bnuBitSize);

         if(x) {
            int nb;
            for(nb=cpNLZ_BNU32(x)/BYTESIZE; nb<4; nb++)
               *pStr++ = EBYTE(x,3-nb);

            for(--bnuSize; bnuSize>0; bnuSize--) {
               x = pBNU[bnuSize-1];
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
