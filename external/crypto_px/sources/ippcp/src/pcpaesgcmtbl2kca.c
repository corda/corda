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

#include "pcpaesauthgcm.h"
#include "pcptool.h"

#include "pcprijtables.h"


/*
// AES-GCM precomputations.
*/
static void RightShiftBlock16(Ipp8u* pBlock)
{
   Ipp8u v0 = 0;
   int i;
   for(i=0; i<16; i++) {
      Ipp8u v1 = pBlock[i];
      Ipp8u tmp = (Ipp8u)( (v1>>1) | (v0<<7) );
      pBlock[i] = tmp;
      v0 = v1;
   }
}
void AesGcmPrecompute_table2K(Ipp8u* pPrecomputeData, const Ipp8u* pHKey)
{
   Ipp8u t[BLOCK_SIZE];
   int n;

   CopyBlock16(pHKey, t);

   for(n=0; n<128-24; n++) {
      /* get msb */
      int hBit = t[15]&1;

      int k = n%32;
      if(k<4) {
         CopyBlock16(t, pPrecomputeData +1024 +(n/32)*256 +(Ipp32u)(1<<(7-k)));
      }
      else if(k<8) {
         CopyBlock16(t, pPrecomputeData +(n/32)*256 +(Ipp32u)(1<<(11-k)));
      }

      /* shift */
      RightShiftBlock16(t);
      /* xor if msb=1 */
      if(hBit)
         t[0] ^= 0xe1;
   }

   for(n=0; n<4; n++) {
      int m, k;
      XorBlock16(pPrecomputeData +n*256, pPrecomputeData +n*256, pPrecomputeData +n*256);
      XorBlock16(pPrecomputeData +1024 +n*256, pPrecomputeData +1024 +n*256, pPrecomputeData +1024 +n*256);
      for(m=2; m<=8; m*=2)
         for(k=1; k<m; k++) {
            XorBlock16(pPrecomputeData +n*256+m*16, pPrecomputeData +n*256+k*16, pPrecomputeData +n*256 +(m+k)*16);
            XorBlock16(pPrecomputeData +1024 +n*256+m*16, pPrecomputeData +1024 +n*256+k*16, pPrecomputeData +1024 +n*256 +(m+k)*16);
         }
   }
}


/*
// AesGcmMulGcm_def|safe(Ipp8u* pGhash, const Ipp8u* pHKey)
//
// Ghash = Ghash * HKey mod G()
*/
void AesGcmMulGcm_table2K(Ipp8u* pGhash, const Ipp8u* pPrecomputeData, const void* pParam)
{
   __ALIGN16 Ipp8u t5[BLOCK_SIZE];
   __ALIGN16 Ipp8u t4[BLOCK_SIZE];
   __ALIGN16 Ipp8u t3[BLOCK_SIZE];
   __ALIGN16 Ipp8u t2[BLOCK_SIZE];

   int nw;
   Ipp32u a;

   UNREFERENCED_PARAMETER(pParam);

   XorBlock16(t5, t5, t5);
   XorBlock16(t4, t4, t4);
   XorBlock16(t3, t3, t3);
   XorBlock16(t2, t2, t2);

   for(nw=0; nw<4; nw++) {
      Ipp32u hashdw = ((Ipp32u*)pGhash)[nw];

      a = hashdw & 0xf0f0f0f0;
      XorBlock16(t5, pPrecomputeData+1024+EBYTE(a,1)+256*nw, t5);
      XorBlock16(t4, pPrecomputeData+1024+EBYTE(a,0)+256*nw, t4);
      XorBlock16(t3, pPrecomputeData+1024+EBYTE(a,3)+256*nw, t3);
      XorBlock16(t2, pPrecomputeData+1024+EBYTE(a,2)+256*nw, t2);

      a = (hashdw<<4) & 0xf0f0f0f0;
      XorBlock16(t5, pPrecomputeData+EBYTE(a,1)+256*nw, t5);
      XorBlock16(t4, pPrecomputeData+EBYTE(a,0)+256*nw, t4);
      XorBlock16(t3, pPrecomputeData+EBYTE(a,3)+256*nw, t3);
      XorBlock16(t2, pPrecomputeData+EBYTE(a,2)+256*nw, t2);
   }

   XorBlock(t2+1, t3, t2+1, BLOCK_SIZE-1);
   XorBlock(t5+1, t2, t5+1, BLOCK_SIZE-1);
   XorBlock(t4+1, t5, t4+1, BLOCK_SIZE-1);

   nw = t3[BLOCK_SIZE-1];
   a = (Ipp32u)AesGcmConst_table[nw];
   a <<= 8;
   nw = t2[BLOCK_SIZE-1];
   a ^= (Ipp32u)AesGcmConst_table[nw];
   a <<= 8;
   nw = t5[BLOCK_SIZE-1];
   a ^= (Ipp32u)AesGcmConst_table[nw];

   XorBlock(t4, &a, t4, sizeof(Ipp32u));
   CopyBlock16(t4, pGhash);
}


/*
// authenticates n*BLOCK_SIZE bytes
*/
void AesGcmAuth_table2K(Ipp8u* pHash, const Ipp8u* pSrc, int len, const Ipp8u* pHKey, const void* pParam)
{
   UNREFERENCED_PARAMETER(pParam);

   while(len>=BLOCK_SIZE) {
      /* add src */
      XorBlock16(pSrc, pHash, pHash);
      /* hash it */
      AesGcmMulGcm_table2K(pHash, pHKey, AesGcmConst_table);

      pSrc += BLOCK_SIZE;
      len -= BLOCK_SIZE;
   }
}


/*
// encrypts and authenticates n*BLOCK_SIZE bytes
*/
void wrpAesGcmEnc_table2K(Ipp8u* pDst, const Ipp8u* pSrc, int len, IppsAES_GCMState* pState)
{
   Ipp8u* pHashedData = pDst;
   int hashedDataLen = len;

   Ipp8u* pCounter = AESGCM_COUNTER(pState);
   Ipp8u* pECounter = AESGCM_ECOUNTER(pState);

   IppsAESSpec* pAES = AESGCM_CIPHER(pState);
   RijnCipher encoder = RIJ_ENCODER(pAES);

   while(len>=BLOCK_SIZE) {
      /* encrypt whole AES block */
      XorBlock16(pSrc, pECounter, pDst);

      pSrc += BLOCK_SIZE;
      pDst += BLOCK_SIZE;
      len -= BLOCK_SIZE;

      /* increment counter block */
      IncrementCounter32(pCounter);
      /* and encrypt counter */
      encoder(pCounter, pECounter, RIJ_NR(pAES), RIJ_EKEYS(pAES), RijEncSbox);
   }

   AesGcmAuth_table2K(AESGCM_GHASH(pState), pHashedData, hashedDataLen, AESGCM_HKEY(pState), AesGcmConst_table);
}


/*
// authenticates and decrypts n*BLOCK_SIZE bytes
*/
void wrpAesGcmDec_table2K(Ipp8u* pDst, const Ipp8u* pSrc, int len, IppsAES_GCMState* pState)
{
   AesGcmAuth_table2K(AESGCM_GHASH(pState), pSrc, len, AESGCM_HKEY(pState), AesGcmConst_table);

   {
      Ipp8u* pCounter = AESGCM_COUNTER(pState);
      Ipp8u* pECounter = AESGCM_ECOUNTER(pState);

      IppsAESSpec* pAES = AESGCM_CIPHER(pState);
      RijnCipher encoder = RIJ_ENCODER(pAES);

      while(len>=BLOCK_SIZE) {
         /* encrypt whole AES block */
         XorBlock16(pSrc, pECounter, pDst);

         pSrc += BLOCK_SIZE;
         pDst += BLOCK_SIZE;
         len -= BLOCK_SIZE;

         /* increment counter block */
         IncrementCounter32(pCounter);
         /* and encrypt counter */
         encoder(pCounter, pECounter, RIJ_NR(pAES), RIJ_EKEYS(pAES), RijEncSbox);
      }
   }
}
