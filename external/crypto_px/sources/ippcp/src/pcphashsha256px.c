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
// SHA256 Specific Macros (reference proposal 256-384-512)
*/
#define CH(x,y,z)    (((x) & (y)) ^ (~(x) & (z)))
#define MAJ(x,y,z)   (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))

#define SUM0(x)   (ROR32((x), 2) ^ ROR32((x),13) ^ ROR32((x),22))
#define SUM1(x)   (ROR32((x), 6) ^ ROR32((x),11) ^ ROR32((x),25))

#define SIG0(x)   (ROR32((x), 7) ^ ROR32((x),18) ^ LSR32((x), 3))
#define SIG1(x)   (ROR32((x),17) ^ ROR32((x),19) ^ LSR32((x),10))

#define SHA256_UPDATE(i) \
   wdat[i & 15] += SIG1(wdat[(i+14)&15]) + wdat[(i+9)&15] + SIG0(wdat[(i+1)&15])

#define SHA256_STEP(i,j)  \
   v[(7 - i) & 7] += (j ? SHA256_UPDATE(i) : wdat[i&15])    \
                  + SHA256_cnt_loc[i + j]                       \
                  + SUM1(v[(4-i)&7])                        \
                  + CH(v[(4-i)&7], v[(5-i)&7], v[(6-i)&7]); \
   v[(3-i)&7] += v[(7-i)&7];                                \
   v[(7-i)&7] += SUM0(v[(0-i)&7]) + MAJ(v[(0-i)&7], v[(1-i)&7], v[(2-i)&7])

#define COMPACT_SHA256_STEP(A,B,C,D,E,F,G,H, W,K, r) { \
   Ipp32u _T1 = (H) + SUM1((E)) + CH((E),(F),(G)) + (W)[(r)] + (K)[(r)]; \
   Ipp32u _T2 = SUM0((A)) + MAJ((A),(B),(C)); \
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
//    Name: UpdateSHA256
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
#if defined(_ALG_SHA256_COMPACT_)
#pragma message("SHA256 compact")

void UpdateSHA256(void* uniHash, const Ipp8u* mblk, int mlen, const void* uniParam)
{
   Ipp32u* data = (Ipp32u*)mblk;

   Ipp32u* digest = (Ipp32u*)uniHash;
   Ipp32u* SHA256_cnt_loc = (Ipp32u*)uniParam;

   for(; mlen>=MBS_SHA256; data += MBS_SHA256/sizeof(Ipp32u), mlen -= MBS_SHA256) {
      int t;

      /*
      // expand message block
      */
      Ipp32u W[64];
      /* initialize the first 16 words in the array W (remember about endian) */
      for(t=0; t<16; t++) {
         #if (IPP_ENDIAN == IPP_BIG_ENDIAN)
         W[t] = data[t];
         #else
         W[t] = ENDIANNESS( data[t] );
         #endif
      }
      for(; t<64; t++)
         W[t] = SIG1(W[t-2]) + W[t-7] + SIG0(W[t-15]) + W[t-16];

      /*
      // update hash
      */
      {
         /* init A, B, C, D, E, F, G, H by the input hash */
         Ipp32u A = digest[0];
         Ipp32u B = digest[1];
         Ipp32u C = digest[2];
         Ipp32u D = digest[3];
         Ipp32u E = digest[4];
         Ipp32u F = digest[5];
         Ipp32u G = digest[6];
         Ipp32u H = digest[7];

         for(t=0; t<64; t++)
         COMPACT_SHA256_STEP(A,B,C,D,E,F,G,H, W,SHA256_cnt_loc, t);

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
void UpdateSHA256(void* uniHash, const Ipp8u* mblk, int mlen, const void* uniParam)
{
   Ipp32u* data = (Ipp32u*)mblk;

   Ipp32u* digest = (Ipp32u*)uniHash;
   Ipp32u* SHA256_cnt_loc = (Ipp32u*)uniParam;

   for(; mlen>=MBS_SHA256; data += MBS_SHA256/sizeof(Ipp32u), mlen -= MBS_SHA256) {
      Ipp32u wdat[16];
      int j;

      /* copy digest */
      Ipp32u v[8];
      CopyBlock(digest, v, IPP_SHA256_DIGEST_BITSIZE/BYTESIZE);

      /* initialize the first 16 words in the array W (remember about endian) */
      for(j=0; j<16; j++) {
         #if (IPP_ENDIAN == IPP_BIG_ENDIAN)
         wdat[j] = data[j];
         #else
         wdat[j] = ENDIANNESS( data[j] );
         #endif
      }

      for(j=0; j<64; j+=16) {
         SHA256_STEP( 0, j);
         SHA256_STEP( 1, j);
         SHA256_STEP( 2, j);
         SHA256_STEP( 3, j);
         SHA256_STEP( 4, j);
         SHA256_STEP( 5, j);
         SHA256_STEP( 6, j);
         SHA256_STEP( 7, j);
         SHA256_STEP( 8, j);
         SHA256_STEP( 9, j);
         SHA256_STEP(10, j);
         SHA256_STEP(11, j);
         SHA256_STEP(12, j);
         SHA256_STEP(13, j);
         SHA256_STEP(14, j);
         SHA256_STEP(15, j);
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
