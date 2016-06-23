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
#include "pcprij.h"
#include "pcprijtables.h"
#include "pcptool.h"


/*
// RconTbl[] contains [x**(i),{00},{00},{00}], i=0,..,10 GF(256)
//
// Note:
//    Reference sec 4.2 of FIPS-197 for calculation
*/
static const Ipp32u RconTbl[] = {
   BYTE0_TO_WORD(0x01), BYTE0_TO_WORD(0x02), BYTE0_TO_WORD(0x04), BYTE0_TO_WORD(0x08),
   BYTE0_TO_WORD(0x10), BYTE0_TO_WORD(0x20), BYTE0_TO_WORD(0x40), BYTE0_TO_WORD(0x80),
   BYTE0_TO_WORD(0x1B), BYTE0_TO_WORD(0x36), BYTE0_TO_WORD(0x6C), BYTE0_TO_WORD(0xD8),
   BYTE0_TO_WORD(0xAB), BYTE0_TO_WORD(0x4D), BYTE0_TO_WORD(0x9A), BYTE0_TO_WORD(0x2F),
   BYTE0_TO_WORD(0x5E), BYTE0_TO_WORD(0xBC), BYTE0_TO_WORD(0x63), BYTE0_TO_WORD(0xC6),
   BYTE0_TO_WORD(0x97), BYTE0_TO_WORD(0x35), BYTE0_TO_WORD(0x6A), BYTE0_TO_WORD(0xD4),
   BYTE0_TO_WORD(0xB3), BYTE0_TO_WORD(0x7D), BYTE0_TO_WORD(0xFA), BYTE0_TO_WORD(0xEF),
   BYTE0_TO_WORD(0xC5)
};

/* precomputed table for InvMixColumn() operation */
static const Ipp32u InvMixCol_Tbl[4][256] = {
   { LINE(inv_t0) },
   { LINE(inv_t1) },
   { LINE(inv_t2) },
   { LINE(inv_t3) }
};

#define InvMixColumn(x, tbl) \
   ( (tbl)[0][ EBYTE((x),0) ] \
    ^(tbl)[1][ EBYTE((x),1) ] \
    ^(tbl)[2][ EBYTE((x),2) ] \
    ^(tbl)[3][ EBYTE((x),3) ] )


/*
// Expansion of key for Rijndael's Encryption
*/
void ExpandRijndaelKey(const Ipp8u* pKey, int NK, int NB, int NR, int nKeys,
                       Ipp8u* pEncKeys, Ipp8u* pDecKeys)
{
   Ipp32u* enc_keys = (Ipp32u*)pEncKeys;
   Ipp32u* dec_keys = (Ipp32u*)pDecKeys;
   /* convert security key to WORD and save into the enc_key array */
   int n;
   for(n=0; n<NK; n++)
      enc_keys[n] = BYTES_TO_WORD(pKey[4*n+0], pKey[4*n+1], pKey[4*n+2], pKey[4*n+3]);

   /* 128-bits Key */
   if(NK128 == NK) {
      const Ipp32u* rtbl = RconTbl;
      Ipp32u k0 = enc_keys[0];
      Ipp32u k1 = enc_keys[1];
      Ipp32u k2 = enc_keys[2];
      Ipp32u k3 = enc_keys[3];

      for(n=NK128; n<nKeys; n+=NK128) {
         /* key expansion: extract bytes, substitute via Sbox and rotate */
         k0 ^= BYTES_TO_WORD( RijEncSbox[EBYTE(k3,1)],
                              RijEncSbox[EBYTE(k3,2)],
                              RijEncSbox[EBYTE(k3,3)],
                              RijEncSbox[EBYTE(k3,0)] ) ^ *rtbl++;

         k1 ^= k0;
         k2 ^= k1;
         k3 ^= k2;

         /* add key expansion */
         enc_keys[n  ] = k0;
         enc_keys[n+1] = k1;
         enc_keys[n+2] = k2;
         enc_keys[n+3] = k3;
      }
   }

   /* 192-bits Key */
   else if(NK192 == NK) {
      const Ipp32u* rtbl = RconTbl;
      Ipp32u k0 = enc_keys[0];
      Ipp32u k1 = enc_keys[1];
      Ipp32u k2 = enc_keys[2];
      Ipp32u k3 = enc_keys[3];
      Ipp32u k4 = enc_keys[4];
      Ipp32u k5 = enc_keys[5];

      for(n=NK192; n<nKeys; n+=NK192) {
         /* key expansion: extract bytes, substitute via Sbox and rorate */
         k0 ^= BYTES_TO_WORD( RijEncSbox[EBYTE(k5,1)],
                              RijEncSbox[EBYTE(k5,2)],
                              RijEncSbox[EBYTE(k5,3)],
                              RijEncSbox[EBYTE(k5,0)] ) ^ *rtbl++;
         k1 ^= k0;
         k2 ^= k1;
         k3 ^= k2;
         k4 ^= k3;
         k5 ^= k4;

         /* add key expansion */
         enc_keys[n  ] = k0;
         enc_keys[n+1] = k1;
         enc_keys[n+2] = k2;
         enc_keys[n+3] = k3;
         enc_keys[n+4] = k4;
         enc_keys[n+5] = k5;
      }
   }

   /* 256-bits Key */
   else {
      const Ipp32u* rtbl = RconTbl;
      Ipp32u k0 = enc_keys[0];
      Ipp32u k1 = enc_keys[1];
      Ipp32u k2 = enc_keys[2];
      Ipp32u k3 = enc_keys[3];
      Ipp32u k4 = enc_keys[4];
      Ipp32u k5 = enc_keys[5];
      Ipp32u k6 = enc_keys[6];
      Ipp32u k7 = enc_keys[7];

      for(n=NK256; n<nKeys; n+=NK256) {
         /* key expansion: extract bytes, substitute via Sbox and rorate */
         k0 ^= BYTES_TO_WORD( RijEncSbox[EBYTE(k7,1)],
                              RijEncSbox[EBYTE(k7,2)],
                              RijEncSbox[EBYTE(k7,3)],
                              RijEncSbox[EBYTE(k7,0)] ) ^ *rtbl++;
         k1 ^= k0;
         k2 ^= k1;
         k3 ^= k2;

         k4 ^= BYTES_TO_WORD( RijEncSbox[EBYTE(k3,0)],
                              RijEncSbox[EBYTE(k3,1)],
                              RijEncSbox[EBYTE(k3,2)],
                              RijEncSbox[EBYTE(k3,3)] );
         k5 ^= k4;
         k6 ^= k5;
         k7 ^= k6;

         /* add key expansion */
         enc_keys[n  ] = k0;
         enc_keys[n+1] = k1;
         enc_keys[n+2] = k2;
         enc_keys[n+3] = k3;
         enc_keys[n+4] = k4;
         enc_keys[n+5] = k5;
         enc_keys[n+6] = k6;
         enc_keys[n+7] = k7;
      }
   }


   /*
   // Key Expansion for Decryption
   */

   /* copy keys */
   CopyBlock(enc_keys, dec_keys, sizeof(Ipp32u)*nKeys);

   /* update decryption keys */
   for(n=NB; n<NR*NB; n++)
      dec_keys[n] = InvMixColumn(dec_keys[n], InvMixCol_Tbl);
}
