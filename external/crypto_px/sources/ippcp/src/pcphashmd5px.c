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

#pragma message("IPP_ALG_HASH_MD5 enabled")


/*
// Magic functions defined in RFC 1321
//
*/
#define F(X,Y,Z)  ((Z) ^ ((X) & ((Y) ^ (Z))))   /* sightly optimized form of (((X) & (Y)) | ((~(X) & (Z)))*/
#define G(X,Y,Z)  F((Z),(X),(Y))                /* replace the original      (((X) & (Z)) | ((Y) & ~(Z))) */
#define H(X,Y,Z)  ((X) ^ (Y) ^ (Z))
#define I(X,Y,Z)  ((Y) ^ ((X) | ~(Z)))

/*
// MD5 step
*/
#define MD5_STEP(MAGIC, A,B,C,D, data, constant, nrot) \
   (A = B +ROL32((A +MAGIC(B,C,D) +data +constant), nrot))

/*
// MD5 left rotations (number of bits)
// depends on round type
*/
#define F1  7
#define F2 12
#define F3 17
#define F4 22

#define G1  5
#define G2  9
#define G3 14
#define G4 20

#define H1  4
#define H2 11
#define H3 16
#define H4 23

#define I1  6
#define I2 10
#define I3 15
#define I4 21

/*F*
//    Name: UpdateMD5
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
void UpdateMD5(void* uinHash, const Ipp8u* mblk, int mlen, const void* uniParam)
{
   Ipp32u* digest = (Ipp32u*)uinHash;
   Ipp32u* MD5_cnt_loc = (Ipp32u*)uniParam;

   for(; mlen>=MBS_MD5; mblk += MBS_MD5, mlen -= MBS_MD5) {

      /* allocate data */
      #if (IPP_ENDIAN == IPP_BIG_ENDIAN)
      Ipp32u data[MBS_MD5/sizeof(Ipp32u)];
      #else
      /* or just word alias */
      Ipp32u* data = (Ipp32u*)mblk;
      #endif

      /* init variables */
      Ipp32u a = digest[0];
      Ipp32u b = digest[1];
      Ipp32u c = digest[2];
      Ipp32u d = digest[3];

      #if (IPP_ENDIAN == IPP_BIG_ENDIAN)
      int t;
      for(t=0; t<16; t++) {
         data[t] = ENDIANNESS(((Ipp32u*)mblk)[t]);
      }
      #endif

      /* rounds type F */
      MD5_STEP(F, a,b,c,d, data[ 0], MD5_cnt_loc[ 0], F1);
      MD5_STEP(F, d,a,b,c, data[ 1], MD5_cnt_loc[ 1], F2);
      MD5_STEP(F, c,d,a,b, data[ 2], MD5_cnt_loc[ 2], F3);
      MD5_STEP(F, b,c,d,a, data[ 3], MD5_cnt_loc[ 3], F4);
      MD5_STEP(F, a,b,c,d, data[ 4], MD5_cnt_loc[ 4], F1);
      MD5_STEP(F, d,a,b,c, data[ 5], MD5_cnt_loc[ 5], F2);
      MD5_STEP(F, c,d,a,b, data[ 6], MD5_cnt_loc[ 6], F3);
      MD5_STEP(F, b,c,d,a, data[ 7], MD5_cnt_loc[ 7], F4);
      MD5_STEP(F, a,b,c,d, data[ 8], MD5_cnt_loc[ 8], F1);
      MD5_STEP(F, d,a,b,c, data[ 9], MD5_cnt_loc[ 9], F2);
      MD5_STEP(F, c,d,a,b, data[10], MD5_cnt_loc[10], F3);
      MD5_STEP(F, b,c,d,a, data[11], MD5_cnt_loc[11], F4);
      MD5_STEP(F, a,b,c,d, data[12], MD5_cnt_loc[12], F1);
      MD5_STEP(F, d,a,b,c, data[13], MD5_cnt_loc[13], F2);
      MD5_STEP(F, c,d,a,b, data[14], MD5_cnt_loc[14], F3);
      MD5_STEP(F, b,c,d,a, data[15], MD5_cnt_loc[15], F4);

      /* rounds type G */
      MD5_STEP(G, a,b,c,d, data[ 1], MD5_cnt_loc[16], G1);
      MD5_STEP(G, d,a,b,c, data[ 6], MD5_cnt_loc[17], G2);
      MD5_STEP(G, c,d,a,b, data[11], MD5_cnt_loc[18], G3);
      MD5_STEP(G, b,c,d,a, data[ 0], MD5_cnt_loc[19], G4);
      MD5_STEP(G, a,b,c,d, data[ 5], MD5_cnt_loc[20], G1);
      MD5_STEP(G, d,a,b,c, data[10], MD5_cnt_loc[21], G2);
      MD5_STEP(G, c,d,a,b, data[15], MD5_cnt_loc[22], G3);
      MD5_STEP(G, b,c,d,a, data[ 4], MD5_cnt_loc[23], G4);
      MD5_STEP(G, a,b,c,d, data[ 9], MD5_cnt_loc[24], G1);
      MD5_STEP(G, d,a,b,c, data[14], MD5_cnt_loc[25], G2);
      MD5_STEP(G, c,d,a,b, data[ 3], MD5_cnt_loc[26], G3);
      MD5_STEP(G, b,c,d,a, data[ 8], MD5_cnt_loc[27], G4);
      MD5_STEP(G, a,b,c,d, data[13], MD5_cnt_loc[28], G1);
      MD5_STEP(G, d,a,b,c, data[ 2], MD5_cnt_loc[29], G2);
      MD5_STEP(G, c,d,a,b, data[ 7], MD5_cnt_loc[30], G3);
      MD5_STEP(G, b,c,d,a, data[12], MD5_cnt_loc[31], G4);

      /* rounds type H */
      MD5_STEP(H, a,b,c,d, data[ 5], MD5_cnt_loc[32], H1);
      MD5_STEP(H, d,a,b,c, data[ 8], MD5_cnt_loc[33], H2);
      MD5_STEP(H, c,d,a,b, data[11], MD5_cnt_loc[34], H3);
      MD5_STEP(H, b,c,d,a, data[14], MD5_cnt_loc[35], H4);
      MD5_STEP(H, a,b,c,d, data[ 1], MD5_cnt_loc[36], H1);
      MD5_STEP(H, d,a,b,c, data[ 4], MD5_cnt_loc[37], H2);
      MD5_STEP(H, c,d,a,b, data[ 7], MD5_cnt_loc[38], H3);
      MD5_STEP(H, b,c,d,a, data[10], MD5_cnt_loc[39], H4);
      MD5_STEP(H, a,b,c,d, data[13], MD5_cnt_loc[40], H1);
      MD5_STEP(H, d,a,b,c, data[ 0], MD5_cnt_loc[41], H2);
      MD5_STEP(H, c,d,a,b, data[ 3], MD5_cnt_loc[42], H3);
      MD5_STEP(H, b,c,d,a, data[ 6], MD5_cnt_loc[43], H4);
      MD5_STEP(H, a,b,c,d, data[ 9], MD5_cnt_loc[44], H1);
      MD5_STEP(H, d,a,b,c, data[12], MD5_cnt_loc[45], H2);
      MD5_STEP(H, c,d,a,b, data[15], MD5_cnt_loc[46], H3);
      MD5_STEP(H, b,c,d,a, data[ 2], MD5_cnt_loc[47], H4);

      /* rounds type I */
      MD5_STEP(I, a,b,c,d, data[ 0], MD5_cnt_loc[48], I1);
      MD5_STEP(I, d,a,b,c, data[ 7], MD5_cnt_loc[49], I2);
      MD5_STEP(I, c,d,a,b, data[14], MD5_cnt_loc[50], I3);
      MD5_STEP(I, b,c,d,a, data[ 5], MD5_cnt_loc[51], I4);
      MD5_STEP(I, a,b,c,d, data[12], MD5_cnt_loc[52], I1);
      MD5_STEP(I, d,a,b,c, data[ 3], MD5_cnt_loc[53], I2);
      MD5_STEP(I, c,d,a,b, data[10], MD5_cnt_loc[54], I3);
      MD5_STEP(I, b,c,d,a, data[ 1], MD5_cnt_loc[55], I4);
      MD5_STEP(I, a,b,c,d, data[ 8], MD5_cnt_loc[56], I1);
      MD5_STEP(I, d,a,b,c, data[15], MD5_cnt_loc[57], I2);
      MD5_STEP(I, c,d,a,b, data[ 6], MD5_cnt_loc[58], I3);
      MD5_STEP(I, b,c,d,a, data[13], MD5_cnt_loc[59], I4);
      MD5_STEP(I, a,b,c,d, data[ 4], MD5_cnt_loc[60], I1);
      MD5_STEP(I, d,a,b,c, data[11], MD5_cnt_loc[61], I2);
      MD5_STEP(I, c,d,a,b, data[ 2], MD5_cnt_loc[62], I3);
      MD5_STEP(I, b,c,d,a, data[ 9], MD5_cnt_loc[63], I4);

      /* update digest */
      digest[0] += a;
      digest[1] += b;
      digest[2] += c;
      digest[3] += d;
   }
}
