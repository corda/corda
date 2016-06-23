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
#include "pcpaesm.h"
#include "pcptool.h"

#include "pcprijtables.h"

/*
// AES-CRT processing.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pCtx == NULL
//                            pSrc == NULL
//                            pDst == NULL
//                            pCtrValue ==NULL
//    ippStsContextMatchErr   !VALID_AES_ID()
//    ippStsLengthErr         len <1
//    ippStsCTRSizeErr        128 < ctrNumBitSize < 1
//    ippStsNoErr             no errors
//
// Parameters:
//    pSrc           pointer to the source data buffer
//    pDst           pointer to the target data buffer
//    dataLen        input/output buffer length (in bytes)
//    pCtx           pointer to rge AES context
//    pCtrValue      pointer to the counter block
//    ctrNumBitSize  counter block size (bits)
//
// Note:
//    counter will updated on return
//
*/
static
IppStatus cpProcessAES_ctr(const Ipp8u* pSrc, Ipp8u* pDst, int dataLen,
                           const IppsAESSpec* pCtx,
                           Ipp8u* pCtrValue, int ctrNumBitSize)
{
   /* test context */
   IPP_BAD_PTR1_RET(pCtx);
   /* use aligned AES context */
   pCtx = (IppsAESSpec*)( IPP_ALIGNED_PTR(pCtx, AES_ALIGNMENT) );
   /* test the context ID */
   IPP_BADARG_RET(!VALID_AES_ID(pCtx), ippStsContextMatchErr);

   /* test source, target and counter block pointers */
   IPP_BAD_PTR3_RET(pSrc, pDst, pCtrValue);
   /* test stream length */
   IPP_BADARG_RET((dataLen<1), ippStsLengthErr);

   /* test counter block size */
   IPP_BADARG_RET(((MBS_RIJ128*8)<ctrNumBitSize)||(ctrNumBitSize<1), ippStsCTRSizeErr);

   {
      Ipp32u counter[NB(128)];
      Ipp32u  output[NB(128)];

      /* setup encoder method */
      RijnCipher encoder = RIJ_ENCODER(pCtx);

      /* copy counter */
      CopyBlock16(pCtrValue, counter);

      /*
      // encrypt block-by-block aligned streams
      */
      while(dataLen>= MBS_RIJ128) {
         /* encrypt counter block */
         encoder((Ipp8u*)counter, (Ipp8u*)output, RIJ_NR(pCtx), RIJ_EKEYS(pCtx), RijEncSbox);

         /* compute ciphertext block */
         if( !(IPP_UINT_PTR(pSrc) & 0x3) && !(IPP_UINT_PTR(pDst) & 0x3)) {
            ((Ipp32u*)pDst)[0] = output[0]^((Ipp32u*)pSrc)[0];
            ((Ipp32u*)pDst)[1] = output[1]^((Ipp32u*)pSrc)[1];
            ((Ipp32u*)pDst)[2] = output[2]^((Ipp32u*)pSrc)[2];
            ((Ipp32u*)pDst)[3] = output[3]^((Ipp32u*)pSrc)[3];
         }
         else
            XorBlock16(pSrc, output, pDst);
         /* encrement counter block */
         StdIncrement((Ipp8u*)counter,MBS_RIJ128*8, ctrNumBitSize);

         pSrc += MBS_RIJ128;
         pDst += MBS_RIJ128;
         dataLen -= MBS_RIJ128;
      }
      /*
      // encrypt last data block
      */
      if(dataLen) {
         /* encrypt counter block */
         encoder((Ipp8u*)counter, (Ipp8u*)output, RIJ_NR(pCtx), RIJ_EKEYS(pCtx), RijEncSbox);

         /* compute ciphertext block */
         XorBlock(pSrc, output, pDst,dataLen);
         /* encrement counter block */
         StdIncrement((Ipp8u*)counter,MBS_RIJ128*8, ctrNumBitSize);
      }

      /* update counter */
      CopyBlock16(counter, pCtrValue);

      return ippStsNoErr;
   }
}

IPPFUN(IppStatus, ippsAESEncryptCTR,(const Ipp8u* pSrc, Ipp8u* pDst, int dataLen,
                                     const IppsAESSpec* pCtx,
                                     Ipp8u* pCtrValue, int ctrNumBitSize))
{
   return cpProcessAES_ctr(pSrc, pDst, dataLen, pCtx, pCtrValue, ctrNumBitSize);
}

IPPFUN(IppStatus, ippsAESDecryptCTR,(const Ipp8u* pSrc, Ipp8u* pDst, int dataLen,
                                     const IppsAESSpec* pCtx,
                                     Ipp8u* pCtrValue, int ctrNumBitSize))
{
   return cpProcessAES_ctr(pSrc, pDst, dataLen, pCtx, pCtrValue, ctrNumBitSize);
}
