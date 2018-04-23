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
//     Internal Unsigned BNU misc functionality
// 
//  Contents:
//     cpNLZ_BNU()
//     cpNTZ_BNU()
// 
//     cpLSL_BNU()
//     cpLSR_BNU()
// 
//     cpLSBit_BNU()
//     cpMSBit_BNU()
// 
//     cpFromOctStr_BNU()
//     cpToOctStrS_BNU()
// 
// 
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
// Logical shift left  (including inplace)
//
// Returns new length
//
*/
#if 0
cpSize cpLSL_BNU(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, cpSize nsA, cpSize nBits)
{
   cpSize nlz = cpNLZ_BNU(pA[nsA-1]);
   cpSize nw = nBits/BNU_CHUNK_BITS;
   cpSize n;

   pR += nw;

   nBits %= BNU_CHUNK_BITS;
   if(nBits) {
      BNU_CHUNK_T hi,lo;

      if(nlz < nBits )
         hi = 0;
      else
         hi = pA[--nsA];

      for(n=nsA; n>0; n--) {
         lo = pA[n-1];
         pR[n] = (hi<<nBits) | (lo>>(BNU_CHUNK_BITS-nBits));
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
// Returns Last Significant Bit of the BNU
// Note:
//    if BNU==0, than 32*size will return
*/
#if 0
int cpLSBit_BNU(const BNU_CHUNK_T* pA, cpSize nsA)
{
   int lsb = 0;

   cpSize n;
   for(n=0; n<nsA; n++) {
      if(0==pA[n])
         lsb += BNU_CHUNK_BITS;
      else {
         lsb += cpNTZ_BNU( pA[n] );
         return lsb;
      }
   }
   return lsb;
}
#endif

/*
// Returns Most Significant Bit of the BNU
// Note:
//    if BNU==0, -1 will return
*/
int cpMSBit_BNU(const BNU_CHUNK_T* pA, cpSize nsA)
{
   int msb;
   FIX_BNU(pA, nsA);
   msb  = nsA*BNU_CHUNK_BITS - cpNLZ_BNU(pA[nsA-1]) -1;
   return msb;
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
