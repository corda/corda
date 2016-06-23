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
// Magic functions defined in FIPS 180-1
//
*/
#define MAGIC_F0(B,C,D) (((B) & (C)) | ((~(B)) & (D)))
#define MAGIC_F1(B,C,D) ((B) ^ (C) ^ (D))
#define MAGIC_F2(B,C,D) (((B) & (C)) | ((B) & (D)) | ((C) & (D)))
#define MAGIC_F3(B,C,D) ((B) ^ (C) ^ (D))

#define SHA1_STEP(A,B,C,D,E, MAGIC_FUN, W,K) \
   (E)+= ROL32((A),5) + MAGIC_FUN((B),(C),(D)) + (W) + (K); \
   (B) = ROL32((B),30)

#define COMPACT_SHA1_STEP(A,B,C,D,E, MAGIC_FUN, W,K, t) { \
   Ipp32u _T = ROL32((A),5) + MAGIC_FUN((t)/20, (B),(C),(D)) + (E) + (W)[(t)] + (K)[(t)/20]; \
   (E) = (D); \
   (D) = (C); \
   (C) = ROL32((B),30); \
   (B) = (A); \
   (A) = _T; \
}

#if defined(_ALG_SHA1_COMPACT_)
__INLINE Ipp32u MagicFun(int s, Ipp32u b, Ipp32u c, Ipp32u d)
{
   switch(s) {
      case 0: return MAGIC_F0(b,c,d);
      case 2: return MAGIC_F2(b,c,d);
      default:return MAGIC_F1(b,c,d);
   }
}
#endif


/*F*
//    Name: UpdateSHA1
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
#if defined(_ALG_SHA1_COMPACT_)
#pragma message("SHA1 compact")
#endif

void UpdateSHA1(void* uinHash, const Ipp8u* mblk, int mlen, const void *uniParam)
{
   Ipp32u* data = (Ipp32u*)mblk;

   Ipp32u* digest = (Ipp32u*)uinHash;
   Ipp32u* SHA1_cnt_loc = (Ipp32u*)uniParam;

   for(; mlen>=MBS_SHA1; data += MBS_SHA1/sizeof(Ipp32u), mlen -= MBS_SHA1) {
      int    t;

      /*
      // expand message block
      */
      Ipp32u W[80];
      /* initialize the first 16 words in the array W (remember about endian) */
      for(t=0; t<16; t++) {
         #if (IPP_ENDIAN == IPP_BIG_ENDIAN)
         W[t] = data[t];
         #else
         W[t] = ENDIANNESS(data[t]);
         #endif
      }
      /* schedule another 80-16 words in the array W */
      for(; t<80; t++) {
         W[t] = ROL32(W[t-3] ^ W[t-8] ^ W[t-14] ^ W[t-16], 1);
      }

      /*
      // update hash
      */
      {
         /* init A, B, C, D, E by the the input hash */
         Ipp32u A = digest[0];
         Ipp32u B = digest[1];
         Ipp32u C = digest[2];
         Ipp32u D = digest[3];
         Ipp32u E = digest[4];

         #if defined(_ALG_SHA1_COMPACT_)
         /* steps 0-79 */
         for(t=0; t<80; t++)
            COMPACT_SHA1_STEP(A,B,C,D,E, MagicFun, W, SHA1_cnt_loc, t);

         #else
         /* perform 0-19 steps */
         for(t=0; t<20; t+=5) {
            SHA1_STEP(A,B,C,D,E, MAGIC_F0, W[t  ],SHA1_cnt_loc[0]);
            SHA1_STEP(E,A,B,C,D, MAGIC_F0, W[t+1],SHA1_cnt_loc[0]);
            SHA1_STEP(D,E,A,B,C, MAGIC_F0, W[t+2],SHA1_cnt_loc[0]);
            SHA1_STEP(C,D,E,A,B, MAGIC_F0, W[t+3],SHA1_cnt_loc[0]);
            SHA1_STEP(B,C,D,E,A, MAGIC_F0, W[t+4],SHA1_cnt_loc[0]);
         }
         /* perform 20-39 steps */
         for(; t<40; t+=5) {
            SHA1_STEP(A,B,C,D,E, MAGIC_F1, W[t  ],SHA1_cnt_loc[1]);
            SHA1_STEP(E,A,B,C,D, MAGIC_F1, W[t+1],SHA1_cnt_loc[1]);
            SHA1_STEP(D,E,A,B,C, MAGIC_F1, W[t+2],SHA1_cnt_loc[1]);
            SHA1_STEP(C,D,E,A,B, MAGIC_F1, W[t+3],SHA1_cnt_loc[1]);
            SHA1_STEP(B,C,D,E,A, MAGIC_F1, W[t+4],SHA1_cnt_loc[1]);
         }
         /* perform 40-59 steps */
         for(; t<60; t+=5) {
            SHA1_STEP(A,B,C,D,E, MAGIC_F2, W[t  ],SHA1_cnt_loc[2]);
            SHA1_STEP(E,A,B,C,D, MAGIC_F2, W[t+1],SHA1_cnt_loc[2]);
            SHA1_STEP(D,E,A,B,C, MAGIC_F2, W[t+2],SHA1_cnt_loc[2]);
            SHA1_STEP(C,D,E,A,B, MAGIC_F2, W[t+3],SHA1_cnt_loc[2]);
            SHA1_STEP(B,C,D,E,A, MAGIC_F2, W[t+4],SHA1_cnt_loc[2]);
         }
         /* perform 60-79 steps */
         for(; t<80; t+=5) {
            SHA1_STEP(A,B,C,D,E, MAGIC_F3, W[t  ],SHA1_cnt_loc[3]);
            SHA1_STEP(E,A,B,C,D, MAGIC_F3, W[t+1],SHA1_cnt_loc[3]);
            SHA1_STEP(D,E,A,B,C, MAGIC_F3, W[t+2],SHA1_cnt_loc[3]);
            SHA1_STEP(C,D,E,A,B, MAGIC_F3, W[t+3],SHA1_cnt_loc[3]);
            SHA1_STEP(B,C,D,E,A, MAGIC_F3, W[t+4],SHA1_cnt_loc[3]);
         }
         #endif

         /* update digest */
         digest[0] += A;
         digest[1] += B;
         digest[2] += C;
         digest[3] += D;
         digest[4] += E;
      }
   }
}
