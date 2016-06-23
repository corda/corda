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

#include "owndefs.h"
#include "owncp.h"
#include "pcphash.h"
#include "pcptool.h"


/*
// SHA512 Specific Macros (reference proposal 256-384-512)
//
// Note: All operations act on DWORDs (64-bits)
*/
#define CH(x,y,z)    (((x) & (y)) ^ (~(x) & (z)))
#define MAJ(x,y,z)   (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))

#define SUM0(x)   (ROR64((x),28) ^ ROR64((x),34) ^ ROR64((x),39))
#define SUM1(x)   (ROR64((x),14) ^ ROR64((x),18) ^ ROR64((x),41))

#define SIG0(x)   (ROR64((x), 1) ^ ROR64((x), 8) ^ LSR64((x), 7))
#define SIG1(x)   (ROR64((x),19) ^ ROR64((x),61) ^ LSR64((x), 6))

#define SHA512_UPDATE(i) \
   wdat[i&15] += SIG1(wdat[(i+14)&15]) + wdat[(i+9)&15] + SIG0(wdat[(i+1)&15])

#define SHA512_STEP(i,j)  \
    v[(7-i)&7] += (j ? SHA512_UPDATE(i) : wdat[i&15])    \
               + SHA512_cnt_loc[i+j]                         \
               + SUM1(v[(4-i)&7])                        \
               + CH(v[(4-i)&7], v[(5-i)&7], v[(6-i)&7]); \
    v[(3-i)&7] += v[(7-i)&7];                            \
    v[(7-i)&7] += SUM0(v[(0-i)&7]) + MAJ(v[(0-i)&7], v[(1-i)&7], v[(2-i)&7])

#define COMPACT_SHA512_STEP(A,B,C,D,E,F,G,H, W,K, r) { \
   Ipp64u _T1 = (H) + SUM1((E)) + CH((E),(F),(G)) + (W)[(r)] + (K)[(r)]; \
   Ipp64u _T2 = SUM0((A)) + MAJ((A),(B),(C)); \
   (H) = (G); \
   (G) = (F); \
   (F) = (E); \
   (E) = (D)+_T1; \
   (D) = (C); \
   (C) = (B); \
   (B) = (A); \
   (A) = _T1+_T2; \
}

/*F*
//    Name: UpdateSHA512
//
// Purpose: Update internal hash according to input message stream.
//
// Parameters:
//    uniHash  pointer to in/out hash
//    mblk     pointer to message stream
//    mlen     message stream length (multiple by message block size)
//    uniParam pointer to the optional parameter
//
*F*/
#if defined(_ALG_SHA512_COMPACT_)
#pragma message("SHA512 compact")

void UpdateSHA512(void* uniHash, const Ipp8u* mblk, int mlen, const void* uniPraram)
{
   Ipp32u* data = (Ipp32u*)mblk;

   Ipp64u* digest = (Ipp64u*)uniHash;
   Ipp64u* SHA512_cnt_loc = (Ipp64u*)uniPraram;


   for(; mlen>=MBS_SHA512; data += MBS_SHA512/sizeof(Ipp32u), mlen -= MBS_SHA512) {
      int t;
      Ipp64u W[80];

      /*
      // expand message block
      */
      /* initialize the first 16 words in the array W (remember about endian) */
      for(t=0; t<16; t++) {
         Ipp32u hiX = data[2*t];
         Ipp32u loX = data[2*t+1];
         #if (IPP_ENDIAN == IPP_BIG_ENDIAN)
         W[t] = MAKEDWORD(loX, hiX);
         #else
         W[t] = MAKEDWORD( ENDIANNESS(loX), ENDIANNESS(hiX) );
         #endif
      }
      for(; t<80; t++)
         W[t] = SIG1(W[t-2]) + W[t-7] + SIG0(W[t-15]) + W[t-16];

      /*
      // update hash
      */
      {
         /* init A, B, C, D, E, F, G, H by the input hash */
         Ipp64u A = digest[0];
         Ipp64u B = digest[1];
         Ipp64u C = digest[2];
         Ipp64u D = digest[3];
         Ipp64u E = digest[4];
         Ipp64u F = digest[5];
         Ipp64u G = digest[6];
         Ipp64u H = digest[7];

         for(t=0; t<80; t++)
            COMPACT_SHA512_STEP(A,B,C,D,E,F,G,H, W,SHA512_cnt_loc, t);

         /* update hash*/
         digest[0] += A;
         digest[1] += B;
         digest[2] += C;
         digest[3] += D;
         digest[4] += E;
         digest[5] += F;
         digest[6] += G;
         digest[7] += H;
      }
   }
}

#else
void UpdateSHA512(void* uniHash, const Ipp8u* mblk, int mlen, const void* uniPraram)
{
   Ipp32u* data = (Ipp32u*)mblk;

   Ipp64u* digest = (Ipp64u*)uniHash;
   Ipp64u* SHA512_cnt_loc = (Ipp64u*)uniPraram;

   for(; mlen>=MBS_SHA512; data += MBS_SHA512/sizeof(Ipp32u), mlen -= MBS_SHA512) {
      Ipp64u wdat[16];
      int j;

      Ipp64u v[8];

      /* initialize the first 16 words in the array W (remember about endian) */
      for(j=0; j<16; j++) {
         Ipp32u hiX = data[2*j];
         Ipp32u loX = data[2*j+1];
         #if (IPP_ENDIAN == IPP_BIG_ENDIAN)
         wdat[j] = MAKEDWORD(loX, hiX);
         #else
         wdat[j] = MAKEDWORD( ENDIANNESS(loX), ENDIANNESS(hiX) );
         #endif
      }

      /* copy digest */
      CopyBlock(digest, v, IPP_SHA512_DIGEST_BITSIZE/BYTESIZE);

      for(j=0; j<80; j+=16) {
         SHA512_STEP( 0, j);
         SHA512_STEP( 1, j);
         SHA512_STEP( 2, j);
         SHA512_STEP( 3, j);
         SHA512_STEP( 4, j);
         SHA512_STEP( 5, j);
         SHA512_STEP( 6, j);
         SHA512_STEP( 7, j);
         SHA512_STEP( 8, j);
         SHA512_STEP( 9, j);
         SHA512_STEP(10, j);
         SHA512_STEP(11, j);
         SHA512_STEP(12, j);
         SHA512_STEP(13, j);
         SHA512_STEP(14, j);
         SHA512_STEP(15, j);
      }

      /* update digest */
      digest[0] += v[0];
      digest[1] += v[1];
      digest[2] += v[2];
      digest[3] += v[3];
      digest[4] += v[4];
      digest[5] += v[5];
      digest[6] += v[6];
      digest[7] += v[7];
   }
}
#endif
