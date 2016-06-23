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
#include "pcpcmac.h"
#include "pcpaesm.h"
#include "pcptool.h"

#include "pcprijtables.h"

/*F*
//    Name: ippsAES_CMACGetSize
//
// Purpose: Returns size of AES-CMAC context (in bytes).
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSzie == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pSize    pointer to the AES-CMAC size of context
//
*F*/
static int cpSizeofCtx_AESCMAC(void)
{
   return sizeof(IppsAES_CMACState) + AESCMAC_ALIGNMENT-1;
}

IPPFUN(IppStatus, ippsAES_CMACGetSize,(int* pSize))
{
   /* test size's pointer */
   IPP_BAD_PTR1_RET(pSize);

   *pSize = cpSizeofCtx_AESCMAC();

   return ippStsNoErr;
}


/*F*
//    Name: ippsAES_CMACInit
//
// Purpose: Init AES-CMAC context.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pCtx == NULL
//    ippStsMemAllocErr       size of buffer is not match fro operation
//    ippStsLengthErr         keyLen != 16
//                            keyLen != 24
//                            keyLen != 32
//    ippStsNoErr             no errors
//
// Parameters:
//    pKey     pointer to the secret key
//    keyLen   length of secret key
//    pCtx     pointer to the CMAC context
//    ctxSize  available size (in bytes) of buffer above
//
*F*/
static
void init(IppsAES_CMACState* pCtx)
{
   /* buffer is empty */
   CMAC_INDX(pCtx) = 0;
   /* zeros MAC */
   PaddBlock(0, CMAC_MAC(pCtx), MBS_RIJ128);
}

static
void LogicalLeftSift16(const Ipp8u* pSrc, Ipp8u* pDst)
{
   Ipp32u carry = 0;
   int n;
   for(n=0; n<16; n++) {
      Ipp32u x = pSrc[16-1-n] + pSrc[16-1-n] + carry;
      pDst[16-1-n] = (Ipp8u)x;
      carry = (x>>8) & 0xFF;
   }
}

IPPFUN(IppStatus, ippsAES_CMACInit,(const Ipp8u* pKey, int keyLen, IppsAES_CMACState* pCtx, int ctxSize))
{
   /* test pCtx pointer */
   IPP_BAD_PTR1_RET(pCtx);

   /* test available size of context buffer */
   IPP_BADARG_RET(ctxSize<cpSizeofCtx_AESCMAC(), ippStsMemAllocErr);

   /* use aligned context */
   pCtx = (IppsAES_CMACState*)( IPP_ALIGNED_PTR(pCtx, AESCMAC_ALIGNMENT) );

   {
      IppStatus sts;

      /* set context ID */
      CMAC_ID(pCtx) = idCtxCMAC;
      /* init internal buffer and DAC */
      init(pCtx);

      /* init AES cipher */
      sts = ippsAESInit(pKey, keyLen, &CMAC_CIPHER(pCtx), cpSizeofCtx_AES());

      if(ippStsNoErr==sts) {
         const IppsAESSpec* pAES = &CMAC_CIPHER(pCtx);

         /* setup encoder method */
         RijnCipher encoder = RIJ_ENCODER(pAES);

         int msb;
         encoder(CMAC_MAC(pCtx), CMAC_K1(pCtx), RIJ_NR(pAES), RIJ_EKEYS(pAES), RijEncSbox);

         /* precompute k1 subkey */
         msb = (CMAC_K1(pCtx))[0];
         LogicalLeftSift16(CMAC_K1(pCtx),CMAC_K1(pCtx));
         (CMAC_K1(pCtx))[MBS_RIJ128-1] ^= (Ipp8u)((0-(msb>>7)) & 0x87); /* ^ Rb changed for constant time execution */

         /* precompute k2 subkey */
         msb = (CMAC_K1(pCtx))[0];
         LogicalLeftSift16(CMAC_K1(pCtx),CMAC_K2(pCtx));
         (CMAC_K2(pCtx))[MBS_RIJ128-1] ^= (Ipp8u)((0-(msb>>7)) & 0x87); /* ^ Rb changed for constant time execution */
      }

      return sts;
   }
}


/*F*
//    Name: ippsAES_CMACUpdate
//
// Purpose: Updates intermadiate digest based on input stream.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSrc == NULL
//                            pCtx == NULL
//    ippStsContextMatchErr   !VALID_AESCMAC_ID()
//    ippStsLengthErr         len <0
//    ippStsNoErr             no errors
//
// Parameters:
//    pSrc     pointer to the input stream
//    len      input stream length
//    pCtx     pointer to the CMAC context
//
*F*/
static
void AES_CMAC_processing(Ipp8u* pDigest, const Ipp8u* pSrc, int processedLen, const IppsAESSpec* pAES)
{
   /* setup encoder method */
   RijnCipher encoder = RIJ_ENCODER(pAES);

   while(processedLen) {
      ((Ipp32u*)pDigest)[0] ^= ((Ipp32u*)pSrc)[0];
      ((Ipp32u*)pDigest)[1] ^= ((Ipp32u*)pSrc)[1];
      ((Ipp32u*)pDigest)[2] ^= ((Ipp32u*)pSrc)[2];
      ((Ipp32u*)pDigest)[3] ^= ((Ipp32u*)pSrc)[3];

      encoder(pDigest, pDigest, RIJ_NR(pAES), RIJ_EKEYS(pAES), RijEncSbox);

      pSrc += MBS_RIJ128;
      processedLen -= MBS_RIJ128;
   }
}

IPPFUN(IppStatus, ippsAES_CMACUpdate,(const Ipp8u* pSrc, int len, IppsAES_CMACState* pCtx))
{
   int processedLen;

   /* test context pointer */
   IPP_BAD_PTR1_RET(pCtx);
   /* use aligned context */
   pCtx = (IppsAES_CMACState*)( IPP_ALIGNED_PTR(pCtx, AESCMAC_ALIGNMENT) );

   /* test ID */
   IPP_BADARG_RET(!VALID_AESCMAC_ID(pCtx), ippStsContextMatchErr);
   /* test input message and it's length */
   IPP_BADARG_RET((len<0 && pSrc), ippStsLengthErr);
   /* test source pointer */
   IPP_BADARG_RET((len && !pSrc), ippStsNullPtrErr);

   if(!len)
      return ippStsNoErr;

   {
      /*
      // test internal buffer filling
      */
      if(CMAC_INDX(pCtx)) {
         /* copy from input stream to the internal buffer as match as possible */
         processedLen = IPP_MIN(len, (MBS_RIJ128 - CMAC_INDX(pCtx)));
         CopyBlock(pSrc, CMAC_BUFF(pCtx)+CMAC_INDX(pCtx), processedLen);

         /* internal buffer filling */
         CMAC_INDX(pCtx) += processedLen;

         /* update message pointer and length */
         pSrc += processedLen;
         len  -= processedLen;

         if(!len)
            return ippStsNoErr;

         /* update CMAC if buffer full but not the last */
         if(MBS_RIJ128==CMAC_INDX(pCtx) ) {
            const IppsAESSpec* pAES = &CMAC_CIPHER(pCtx);
            /* setup encoder method */
            RijnCipher encoder = RIJ_ENCODER(pAES);
            XorBlock16(CMAC_BUFF(pCtx), CMAC_MAC(pCtx), CMAC_MAC(pCtx));

            encoder(CMAC_MAC(pCtx), CMAC_MAC(pCtx), RIJ_NR(pAES), RIJ_EKEYS(pAES), RijEncSbox);

            CMAC_INDX(pCtx) = 0;
         }
      }

      /*
      // main part
      */
      processedLen = len & ~(MBS_RIJ128-1);
      if(!(len & (MBS_RIJ128-1)))
         processedLen -= MBS_RIJ128;
      if(processedLen) {
         const IppsAESSpec* pAES = &CMAC_CIPHER(pCtx);

         AES_CMAC_processing(CMAC_MAC(pCtx), pSrc, processedLen, pAES);

         /* update message pointer and length */
         pSrc += processedLen;
         len  -= processedLen;
      }

      /*
      // remaind
      */
      if(len) {
         CopyBlock(pSrc, (Ipp8u*)(&CMAC_BUFF(pCtx)), len);
         /* update internal buffer filling */
         CMAC_INDX(pCtx) += len;
      }

      return ippStsNoErr;
   }
}


/*F*
//    Name: ippsAES_CMACFinal
//
// Purpose: Stop message digesting and return MD.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pMD == NULL
//                            pCtx == NULL
//    ippStsContextMatchErr   !VALID_AESCMAC_ID()
//    ippStsLengthErr         MBS_RIJ128 < mdLen <1
//    ippStsNoErr             no errors
//
// Parameters:
//    pMD      pointer to the output message digest
//    mdLen    requested length of the message digest
//    pCtx     pointer to the CMAC context
//
*F*/
IPPFUN(IppStatus, ippsAES_CMACFinal,(Ipp8u* pMD, int mdLen, IppsAES_CMACState* pCtx))
{
   /* test context pointer and ID */
   IPP_BAD_PTR1_RET(pCtx);
   /* use aligned context */
   pCtx = (IppsAES_CMACState*)( IPP_ALIGNED_PTR(pCtx, AESCMAC_ALIGNMENT) );

   IPP_BADARG_RET(!VALID_AESCMAC_ID(pCtx), ippStsContextMatchErr);
   /* test DAC pointer */
   IPP_BAD_PTR1_RET(pMD);
   IPP_BADARG_RET((mdLen<1)||(MBS_RIJ128<mdLen), ippStsLengthErr);

   {
      const IppsAESSpec* pAES = &CMAC_CIPHER(pCtx);
      /* setup encoder method */
      RijnCipher encoder = RIJ_ENCODER(pAES);

      /* message length is divided by MBS_RIJ128 */
      if(MBS_RIJ128==CMAC_INDX(pCtx)) {
         XorBlock16(CMAC_BUFF(pCtx), CMAC_K1(pCtx), CMAC_BUFF(pCtx));
      }
      /* message length isn't divided by MBS_RIJ128 */
      else {
         PaddBlock(0, CMAC_BUFF(pCtx)+CMAC_INDX(pCtx), MBS_RIJ128-CMAC_INDX(pCtx));
         CMAC_BUFF(pCtx)[CMAC_INDX(pCtx)] = 0x80;
         XorBlock16(CMAC_BUFF(pCtx), CMAC_K2(pCtx), CMAC_BUFF(pCtx));
      }

      XorBlock16(CMAC_BUFF(pCtx), CMAC_MAC(pCtx), CMAC_MAC(pCtx));

      encoder(CMAC_MAC(pCtx), CMAC_MAC(pCtx), RIJ_NR(pAES), RIJ_EKEYS(pAES), RijEncSbox);

      /* return truncated DAC */
      CopyBlock(CMAC_MAC(pCtx), pMD, mdLen);

      /* re-init context */
      init(pCtx);

      return ippStsNoErr;
   }
}


/*F*
//    Name: ippsAES_CMACGetTag
//
// Purpose: computes MD value and could contunue process.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pMD == NULL
//                            pCtx == NULL
//    ippStsContextMatchErr   !VALID_AESCMAC_ID()
//    ippStsLengthErr         MBS_RIJ128 < mdLen <1
//    ippStsNoErr             no errors
//
// Parameters:
//    pMD      pointer to the output message digest
//    mdLen    requested length of the message digest
//    pCtx     pointer to the CMAC context
//
*F*/
IPPFUN(IppStatus, ippsAES_CMACGetTag,(Ipp8u* pMD, int mdLen, const IppsAES_CMACState* pCtx))
{
   /* test context pointer and ID */
   IPP_BAD_PTR1_RET(pCtx);
   /* use aligned context */
   pCtx = (IppsAES_CMACState*)( IPP_ALIGNED_PTR(pCtx, AESCMAC_ALIGNMENT) );

   IPP_BADARG_RET(!VALID_AESCMAC_ID(pCtx), ippStsContextMatchErr);
   /* test DAC pointer */
   IPP_BAD_PTR1_RET(pMD);
   IPP_BADARG_RET((mdLen<1)||(MBS_RIJ128<mdLen), ippStsLengthErr);

   {
      const IppsAESSpec* pAES = &CMAC_CIPHER(pCtx);
      /* setup encoder method */
      RijnCipher encoder = RIJ_ENCODER(pAES);

      Ipp8u locBuffer[MBS_RIJ128];
      Ipp8u locMac[MBS_RIJ128];
      CopyBlock16(CMAC_BUFF(pCtx), locBuffer);
      CopyBlock16(CMAC_MAC(pCtx), locMac);

      /* message length is divided by MBS_RIJ128 */
      if(MBS_RIJ128==CMAC_INDX(pCtx)) {
         XorBlock16(locBuffer, CMAC_K1(pCtx), locBuffer);
      }
      /* message length isn't divided by MBS_RIJ128 */
      else {
         PaddBlock(0, locBuffer+CMAC_INDX(pCtx), MBS_RIJ128-CMAC_INDX(pCtx));
         locBuffer[CMAC_INDX(pCtx)] = 0x80;
         XorBlock16(locBuffer, CMAC_K2(pCtx), locBuffer);
      }

      XorBlock16(locBuffer, locMac, locMac);

      encoder(locMac, locMac, RIJ_NR(pAES), RIJ_EKEYS(pAES), RijEncSbox);

      /* return truncated DAC */
      CopyBlock(locMac, pMD, mdLen);

      return ippStsNoErr;
   }
}
