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
//     Message block processing according to SHA1
// 
//  Contents:
//     UpdateSHA1()
// 
// 
*/

#include "precomp.h"
#include "owncp.h"
#include "pcphash.h"
#include "pcptool.h"

//#if !defined(_ENABLE_ALG_SHA1_)
//#pragma message("IPP_ALG_HASH_SHA1 disabled")

//#else
//#pragma message("IPP_ALG_HASH_SHA1 enabled")

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

#endif
//#endif /* IPP_ALG_HASH_SHA1 */
