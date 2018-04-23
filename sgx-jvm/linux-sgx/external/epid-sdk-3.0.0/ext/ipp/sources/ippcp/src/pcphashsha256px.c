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
// 
//  Purpose:
//     Cryptography Primitive.
//     Message block processing according to SHA256
// 
//  Contents:
//     UpdateSHA256()
// 
// 
*/

#include "precomp.h"
#include "owncp.h"
#include "pcphash.h"
#include "pcptool.h"

#if !defined(_ENABLE_ALG_SHA256_) && !defined(_ENABLE_ALG_SHA224_)
#pragma message("IPP_ALG_HASH_SHA256 disabled")

#else
#pragma message("IPP_ALG_HASH_SHA256 enabled")

#if !((_IPPXSC==_IPPXSC_S1) || (_IPPXSC==_IPPXSC_S2) || (_IPPXSC==_IPPXSC_C2) || \
      (_IPP==_IPP_M5) || \
      (_IPP==_IPP_W7) || (_IPP==_IPP_T7) || \
      (_IPP==_IPP_V8) || (_IPP==_IPP_P8) || \
      (_IPPLP32==_IPPLP32_S8) || (_IPP>=_IPP_G9) || \
      (_IPP32E==_IPP32E_M7) || \
      (_IPP32E==_IPP32E_U8) || (_IPP32E==_IPP32E_Y8) || \
      (_IPPLP64==_IPPLP64_N8) || (_IPP32E>=_IPP32E_E9) || \
      (_IPP64==_IPP64_I7) )

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

#endif
#endif /* IPP_ALG_HASH_SHA256 */
