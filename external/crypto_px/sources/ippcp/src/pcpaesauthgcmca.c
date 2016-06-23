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

/*F*
//    Name: ippsAES_GCMGetSize
//
// Purpose: Returns size of AES_GCM state (in bytes).
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSize == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pSize       pointer to size of context
//
*F*/
static int cpSizeofCtx_AESGCM(void)
{
   int precomp_size;

   precomp_size = PRECOMP_DATA_SIZE_FAST2K;

   /* decrease precomp_size as soon as BLOCK_SIZE bytes already reserved in context */
   precomp_size -= BLOCK_SIZE;

   return sizeof(IppsAES_GCMState)
         +precomp_size
         +AESGCM_ALIGNMENT-1;
}

IPPFUN(IppStatus, ippsAES_GCMGetSize,(int* pSize))
{
   /* test size's pointer */
   IPP_BAD_PTR1_RET(pSize);

   *pSize = cpSizeofCtx_AESGCM();

   return ippStsNoErr;
}


/*F*
//    Name: ippsAES_GCMReset
//
// Purpose: Resets AES_GCM context.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pState== NULL
//    ippStsContextMatchErr   pState points on invalid context
//    ippStsNoErr             no errors
//
// Parameters:
//    pState       pointer to the context
//
*F*/
IPPFUN(IppStatus, ippsAES_GCMReset,(IppsAES_GCMState* pState))
{
   /* test pState pointer */
   IPP_BAD_PTR1_RET(pState);

   /* use aligned context */
   pState = (IppsAES_GCMState*)( IPP_ALIGNED_PTR(pState, AESGCM_ALIGNMENT) );
   /* test context validity */
   IPP_BADARG_RET(!AESGCM_VALID_ID(pState), ippStsContextMatchErr);

   /* reset GCM */
   AESGCM_STATE(pState) = GcmInit;
   AESGCM_IV_LEN(pState) = CONST_64(0);
   AESGCM_AAD_LEN(pState) = CONST_64(0);
   AESGCM_TXT_LEN(pState) = CONST_64(0);

   AESGCM_BUFLEN(pState) = 0;
   PaddBlock(0, AESGCM_COUNTER(pState), BLOCK_SIZE);
   PaddBlock(0, AESGCM_ECOUNTER(pState), BLOCK_SIZE);
   PaddBlock(0, AESGCM_ECOUNTER0(pState), BLOCK_SIZE);
   PaddBlock(0, AESGCM_GHASH(pState), BLOCK_SIZE);

   return ippStsNoErr;
}


/*F*
//    Name: ippsAES_GCMInit
//
// Purpose: Init AES_GCM context for future usage.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pState == NULL
//    ippStsMemAllocErr       size of buffer is not match fro operation
//    ippStsLengthErr         keyLen != 16 &&
//                                   != 24 &&
//                                   != 32
//    ippStsNoErr             no errors
//
// Parameters:
//    pKey        pointer to the secret key
//    keyLen      length of secret key
//    pState      pointer to the AES-GCM context
//    ctxSize     available size (in bytes) of buffer above
//
*F*/
IPPFUN(IppStatus, ippsAES_GCMInit,(const Ipp8u* pKey, int keyLen, IppsAES_GCMState* pState, int ctxSize))
{
   /* test pCtx pointer */
   IPP_BAD_PTR1_RET(pState);

   /* test available size of context buffer */
   IPP_BADARG_RET(ctxSize<cpSizeofCtx_AESGCM(), ippStsMemAllocErr);

   /* use aligned context */
   pState = (IppsAES_GCMState*)( IPP_ALIGNED_PTR(pState, AESGCM_ALIGNMENT) );

   /* set and clear GCM context */
   AESGCM_ID(pState) = idCtxAESGCM;
   ippsAES_GCMReset(pState);

   /* init cipher */
   {
      IppStatus sts = ippsAESInit(pKey, keyLen, AESGCM_CIPHER(pState), cpSizeofCtx_AES());
      if(ippStsNoErr!=sts)
         return sts;
   }

   /* set up:
   // - ghash function
   // - authentication function
   */
   AESGCM_HASH(pState) = AesGcmMulGcm_table2K;
   AESGCM_AUTH(pState) = AesGcmAuth_table2K;
   AESGCM_ENC(pState)  = wrpAesGcmEnc_table2K;
   AESGCM_DEC(pState)  = wrpAesGcmDec_table2K;

   /* precomputations (for constant multiplier(s)) */
   {
      IppsAESSpec* pAES = AESGCM_CIPHER(pState);
      RijnCipher encoder = RIJ_ENCODER(pAES);

      /* multiplier c = Enc({0}) */
      PaddBlock(0, AESGCM_HKEY(pState), BLOCK_SIZE);
      encoder(AESGCM_HKEY(pState), AESGCM_HKEY(pState), RIJ_NR(pAES), RIJ_EKEYS(pAES), RijEncSbox);
   }

   AesGcmPrecompute_table2K(AES_GCM_MTBL(pState), AESGCM_HKEY(pState));

   return ippStsNoErr;
}


/*F*
//    Name: ippsAES_GCMProcessIV
//
// Purpose: IV processing.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pState == NULL
//                            pIV ==NULL && ivLen>0
//    ippStsContextMatchErr   !AESGCM_VALID_ID()
//    ippStsLengthErr         ivLen <0
//    ippStsBadArgErr         illegal sequence call
//    ippStsNoErr             no errors
//
// Parameters:
//    pIV         pointer to the IV
//    ivLen       length of IV (it could be 0)
//    pState      pointer to the context
//
*F*/
IPPFUN(IppStatus, ippsAES_GCMProcessIV,(const Ipp8u* pIV, int ivLen, IppsAES_GCMState* pState))
{
   /* test pState pointer */
   IPP_BAD_PTR1_RET(pState);

   /* test IV pointer and length */
   IPP_BADARG_RET(ivLen && !pIV, ippStsNullPtrErr);
   IPP_BADARG_RET(ivLen<0, ippStsLengthErr);

   /* use aligned context */
   pState = (IppsAES_GCMState*)( IPP_ALIGNED_PTR(pState, AESGCM_ALIGNMENT) );
   /* test context validity */
   IPP_BADARG_RET(!AESGCM_VALID_ID(pState), ippStsContextMatchErr);

   IPP_BADARG_RET(!(GcmInit==AESGCM_STATE(pState) || GcmIVprocessing==AESGCM_STATE(pState)), ippStsBadArgErr);

   /* switch IVprocessing on */
   AESGCM_STATE(pState) = GcmIVprocessing;

   /* test if buffer is not empty */
   if(AESGCM_BUFLEN(pState)) {
      int locLen = IPP_MIN(ivLen, BLOCK_SIZE-AESGCM_BUFLEN(pState));
      XorBlock(pIV, AESGCM_COUNTER(pState)+AESGCM_BUFLEN(pState), AESGCM_COUNTER(pState)+AESGCM_BUFLEN(pState), locLen);
      AESGCM_BUFLEN(pState) += locLen;

      /* if buffer full */
      if(BLOCK_SIZE==AESGCM_BUFLEN(pState)) {
         MulGcm_ ghashFunc = AESGCM_HASH(pState);
         ghashFunc(AESGCM_COUNTER(pState), AESGCM_HKEY(pState), AesGcmConst_table);
         AESGCM_BUFLEN(pState) = 0;
      }

      AESGCM_IV_LEN(pState) += locLen;
      pIV += locLen;
      ivLen -= locLen;
   }

   /* process main part of IV */
   {
      int lenBlks = ivLen & (-BLOCK_SIZE);
      if(lenBlks) {
         Auth_ authFunc = AESGCM_AUTH(pState);

         authFunc(AESGCM_COUNTER(pState), pIV, lenBlks, AESGCM_HKEY(pState), AesGcmConst_table);

         AESGCM_IV_LEN(pState) += lenBlks;
         pIV += lenBlks;
         ivLen -= lenBlks;
      }
   }

   /* copy the rest of IV into the buffer */
   if(ivLen) {
      XorBlock(pIV, AESGCM_COUNTER(pState), AESGCM_COUNTER(pState), ivLen);
      AESGCM_IV_LEN(pState) += ivLen;
      AESGCM_BUFLEN(pState) += ivLen;
   }

   return ippStsNoErr;
}


/*F*
//    Name: ippsAES_GCMProcessAAD
//
// Purpose: AAD processing.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pState == NULL
//                            pAAD == NULL, aadLen>0
//    ippStsContextMatchErr   !AESGCM_VALID_ID()
//    ippStsLengthErr         aadLen <0
//    ippStsBadArgErr         illegal sequence call
//    ippStsNoErr             no errors
//
// Parameters:
//    pAAD        pointer to the AAD
//    aadlen      length of AAD (it could be 0)
//    pState      pointer to the context
//
*F*/
IPPFUN(IppStatus, ippsAES_GCMProcessAAD,(const Ipp8u* pAAD, int aadLen, IppsAES_GCMState* pState))
{
   /* test pState pointer */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsAES_GCMState*)( IPP_ALIGNED_PTR(pState, AESGCM_ALIGNMENT) );
   /* test if context is valid */
   IPP_BADARG_RET(!AESGCM_VALID_ID(pState), ippStsContextMatchErr);

   /* test AAD pointer and length */
   IPP_BADARG_RET(aadLen && !pAAD, ippStsNullPtrErr);
   IPP_BADARG_RET(aadLen<0, ippStsLengthErr);

   IPP_BADARG_RET(!(GcmIVprocessing==AESGCM_STATE(pState) || GcmAADprocessing==AESGCM_STATE(pState)), ippStsBadArgErr);

   {
      /* get method */
      MulGcm_ hashFunc = AESGCM_HASH(pState);

      if( GcmIVprocessing==AESGCM_STATE(pState) ) {
         IPP_BADARG_RET(0==AESGCM_IV_LEN(pState), ippStsBadArgErr);

         /* complete IV processing */
         if(CTR_POS==AESGCM_IV_LEN(pState)) {
            /* apply special format if IV length is 12 bytes */
            AESGCM_COUNTER(pState)[12] = 0;
            AESGCM_COUNTER(pState)[13] = 0;
            AESGCM_COUNTER(pState)[14] = 0;
            AESGCM_COUNTER(pState)[15] = 1;
         }
         else {
            /* process the rest of IV */
            if(AESGCM_BUFLEN(pState))
               hashFunc(AESGCM_COUNTER(pState), AESGCM_HKEY(pState), AesGcmConst_table);

            /* add IV bit length */
            {
               Ipp64u ivBitLen = AESGCM_IV_LEN(pState)*BYTESIZE;
               Ipp8u tmp[BLOCK_SIZE];
               PaddBlock(0, tmp, BLOCK_SIZE-8);
               U32_TO_HSTRING(tmp+8,  HIDWORD(ivBitLen));
               U32_TO_HSTRING(tmp+12, LODWORD(ivBitLen));
               XorBlock16(tmp, AESGCM_COUNTER(pState), AESGCM_COUNTER(pState));
               hashFunc(AESGCM_COUNTER(pState), AESGCM_HKEY(pState), AesGcmConst_table);
            }
         }

         /* prepare initial counter */
         {
            IppsAESSpec* pAES = AESGCM_CIPHER(pState);
            RijnCipher encoder = RIJ_ENCODER(pAES);
            encoder(AESGCM_COUNTER(pState), AESGCM_ECOUNTER0(pState), RIJ_NR(pAES), RIJ_EKEYS(pAES), RijEncSbox);
         }

         /* switch mode and init counters */
         AESGCM_STATE(pState) = GcmAADprocessing;
         AESGCM_AAD_LEN(pState) = CONST_64(0);
         AESGCM_BUFLEN(pState) = 0;
      }

      /*
      // AAD processing
      */

      /* test if buffer is not empty */
      if(AESGCM_BUFLEN(pState)) {
         int locLen = IPP_MIN(aadLen, BLOCK_SIZE-AESGCM_BUFLEN(pState));
         XorBlock(pAAD, AESGCM_GHASH(pState)+AESGCM_BUFLEN(pState), AESGCM_GHASH(pState)+AESGCM_BUFLEN(pState), locLen);
         AESGCM_BUFLEN(pState) += locLen;

         /* if buffer full */
         if(BLOCK_SIZE==AESGCM_BUFLEN(pState)) {
            hashFunc(AESGCM_GHASH(pState), AESGCM_HKEY(pState), AesGcmConst_table);
            AESGCM_BUFLEN(pState) = 0;
         }

         AESGCM_AAD_LEN(pState) += locLen;
         pAAD += locLen;
         aadLen -= locLen;
      }

      /* process main part of AAD */
      {
         int lenBlks = aadLen & (-BLOCK_SIZE);
         if(lenBlks) {
            Auth_ authFunc = AESGCM_AUTH(pState);

            authFunc(AESGCM_GHASH(pState), pAAD, lenBlks, AESGCM_HKEY(pState), AesGcmConst_table);

            AESGCM_AAD_LEN(pState) += lenBlks;
            pAAD += lenBlks;
            aadLen -= lenBlks;
         }
      }

      /* copy the rest of AAD into the buffer */
      if(aadLen) {
         XorBlock(pAAD, AESGCM_GHASH(pState), AESGCM_GHASH(pState), aadLen);
         AESGCM_AAD_LEN(pState) += aadLen;
         AESGCM_BUFLEN(pState) = aadLen;
      }

      return ippStsNoErr;
   }
}


/*F*
//    Name: ippsAES_GCMStart
//
// Purpose: Start the process of encryption or decryption and authentication tag generation.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pState == NULL
//                            pIV == NULL, ivLen>0
//                            pAAD == NULL, aadLen>0
//    ippStsContextMatchErr   !AESGCM_VALID_ID()
//    ippStsLengthErr         ivLen < 0
//                            aadLen < 0
//    ippStsNoErr             no errors
//
// Parameters:
//    pIV         pointer to the IV (nonce)
//    ivLen       length of the IV in bytes
//    pAAD        pointer to the Addition Authenticated Data (header)
//    aadLen      length of the AAD in bytes
//    pState      pointer to the AES-GCM state
//
*F*/
IPPFUN(IppStatus, ippsAES_GCMStart,(const Ipp8u* pIV,  int ivLen,
                                    const Ipp8u* pAAD, int aadLen,
                                    IppsAES_GCMState* pState))
{
   IppStatus sts = ippsAES_GCMReset(pState);
   if(ippStsNoErr==sts)
      sts = ippsAES_GCMProcessIV(pIV, ivLen, pState);
   if(ippStsNoErr==sts)
      sts = ippsAES_GCMProcessAAD(pAAD, aadLen, pState);
   return sts;
}


/*F*
//    Name: ippsAES_GCMEncrypt
//
// Purpose: Encrypts a data buffer in the GCM mode.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSrc == NULL
//                            pDst == NULL
//                            pState == NULL
//    ippStsContextMatchErr  !AESGCM_VALID_ID()
//    ippStsLengthErr         txtLen<0
//    ippStsNoErr              no errors
//
// Parameters:
//    pSrc        Pointer to plaintext.
//    pDst        Pointer to ciphertext.
//    len         Length of the plaintext and ciphertext in bytes
//    pState      pointer to the context
//
*F*/
IPPFUN(IppStatus, ippsAES_GCMEncrypt,(const Ipp8u* pSrc, Ipp8u* pDst, int txtLen,
                                      IppsAES_GCMState* pState))
{
   /* test pState pointer */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsAES_GCMState*)( IPP_ALIGNED_PTR(pState, AESGCM_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(!AESGCM_VALID_ID(pState), ippStsContextMatchErr);
   /* test context validity */
   IPP_BADARG_RET(!(GcmAADprocessing==AESGCM_STATE(pState) || GcmTXTprocessing==AESGCM_STATE(pState)), ippStsBadArgErr);

   /* test text pointers and length */
   IPP_BAD_PTR2_RET(pSrc, pDst);
   IPP_BADARG_RET(txtLen<0, ippStsLengthErr);


   {
      /* get method */
      IppsAESSpec* pAES = AESGCM_CIPHER(pState);
      RijnCipher encoder = RIJ_ENCODER(pAES);
      MulGcm_ hashFunc = AESGCM_HASH(pState);

      if( GcmAADprocessing==AESGCM_STATE(pState) ) {
         /* complete AAD processing */
         if(AESGCM_BUFLEN(pState))
            hashFunc(AESGCM_GHASH(pState), AESGCM_HKEY(pState), AesGcmConst_table);

         /* increment counter block */
         IncrementCounter32(AESGCM_COUNTER(pState));
         /* and encrypt counter */
         encoder(AESGCM_COUNTER(pState), AESGCM_ECOUNTER(pState), RIJ_NR(pAES), RIJ_EKEYS(pAES), RijEncSbox);

         /* switch mode and init counters */
         AESGCM_STATE(pState) = GcmTXTprocessing;
         AESGCM_TXT_LEN(pState) = CONST_64(0);
         AESGCM_BUFLEN(pState) = 0;
      }

      /*
      // process text (encrypt and authenticate)
      */
      /* process partial block */
      if(AESGCM_BUFLEN(pState)) {
         int locLen = IPP_MIN(txtLen, BLOCK_SIZE-AESGCM_BUFLEN(pState));
         /* ctr encryption */
         XorBlock(pSrc, AESGCM_ECOUNTER(pState)+AESGCM_BUFLEN(pState), pDst, locLen);
         /* authentication */
         XorBlock(pDst, AESGCM_GHASH(pState)+AESGCM_BUFLEN(pState), AESGCM_GHASH(pState)+AESGCM_BUFLEN(pState), locLen);

         AESGCM_BUFLEN(pState) += locLen;
         AESGCM_TXT_LEN(pState) += locLen;
         pSrc += locLen;
         pDst += locLen;
         txtLen -= locLen;

         /* if buffer full */
         if(BLOCK_SIZE==AESGCM_BUFLEN(pState)) {
            /* hash buffer */
            hashFunc(AESGCM_GHASH(pState), AESGCM_HKEY(pState), AesGcmConst_table);
            AESGCM_BUFLEN(pState) = 0;

            /* increment counter block */
            IncrementCounter32(AESGCM_COUNTER(pState));
            /* and encrypt counter */
            encoder(AESGCM_COUNTER(pState), AESGCM_ECOUNTER(pState), RIJ_NR(pAES), RIJ_EKEYS(pAES), RijEncSbox);
         }
      }

      /* process the main part of text */
      {
         int lenBlks = txtLen & (-BLOCK_SIZE);
         if(lenBlks) {
            Encrypt_ encFunc = AESGCM_ENC(pState);

            encFunc(pDst, pSrc, lenBlks, pState);

            AESGCM_TXT_LEN(pState) += lenBlks;
            pSrc += lenBlks;
            pDst += lenBlks;
            txtLen -= lenBlks;
         }
      }

      /* process the rest of text */
      if(txtLen) {
         XorBlock(pSrc, AESGCM_ECOUNTER(pState)+AESGCM_BUFLEN(pState), pDst, txtLen);
         XorBlock(pDst, AESGCM_GHASH(pState)+AESGCM_BUFLEN(pState), AESGCM_GHASH(pState)+AESGCM_BUFLEN(pState), txtLen);

         AESGCM_BUFLEN(pState) += txtLen;
         AESGCM_TXT_LEN(pState) += txtLen;
      }

      return ippStsNoErr;
   }
}


/*F*
//    Name: ippsAES_GCMDecrypt
//
// Purpose: Decrypts a data buffer in the GCM mode.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSrc == NULL
//                            pDst == NULL
//                            pState == NULL
//    ippStsContextMatchErr  !AESGCM_VALID_ID()
//    ippStsLengthErr         txtLen<0
//    ippStsNoErr              no errors
//
// Parameters:
//    pSrc        Pointer to ciphertext.
//    pDst        Pointer to plaintext.
//    len         Length of the plaintext and ciphertext in bytes
//    pState      pointer to the context
//
*F*/
IPPFUN(IppStatus, ippsAES_GCMDecrypt,(const Ipp8u* pSrc, Ipp8u* pDst, int txtLen, IppsAES_GCMState* pState))
{
   /* test pState pointer */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsAES_GCMState*)( IPP_ALIGNED_PTR(pState, AESGCM_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(!AESGCM_VALID_ID(pState), ippStsContextMatchErr);
   /* test context validity */
   IPP_BADARG_RET(!(GcmAADprocessing==AESGCM_STATE(pState) || GcmTXTprocessing==AESGCM_STATE(pState)), ippStsBadArgErr);

   /* test text pointers and length */
   IPP_BAD_PTR2_RET(pSrc, pDst);
   IPP_BADARG_RET(txtLen<0, ippStsLengthErr);


   {
      /* get method */
      IppsAESSpec* pAES = AESGCM_CIPHER(pState);
      RijnCipher encoder = RIJ_ENCODER(pAES);
      MulGcm_ hashFunc = AESGCM_HASH(pState);

      if( GcmAADprocessing==AESGCM_STATE(pState) ) {
         /* complete AAD processing */
         if(AESGCM_BUFLEN(pState))
            hashFunc(AESGCM_GHASH(pState), AESGCM_HKEY(pState), AesGcmConst_table);

         /* increment counter block */
         IncrementCounter32(AESGCM_COUNTER(pState));
         /* and encrypt counter */
         encoder(AESGCM_COUNTER(pState), AESGCM_ECOUNTER(pState), RIJ_NR(pAES), RIJ_EKEYS(pAES), RijEncSbox);

         /* switch mode and init counters */
         AESGCM_BUFLEN(pState) = 0;
         AESGCM_TXT_LEN(pState) = CONST_64(0);
         AESGCM_STATE(pState) = GcmTXTprocessing;
      }

      /*
      // process text (authenticate and decrypt )
      */
      /* process partial block */
      if(AESGCM_BUFLEN(pState)) {
         int locLen = IPP_MIN(txtLen, BLOCK_SIZE-AESGCM_BUFLEN(pState));
         /* authentication */
         XorBlock(pSrc, AESGCM_GHASH(pState)+AESGCM_BUFLEN(pState), AESGCM_GHASH(pState)+AESGCM_BUFLEN(pState), locLen);
         /* ctr decryption */
         XorBlock(pSrc, AESGCM_ECOUNTER(pState)+AESGCM_BUFLEN(pState), pDst, locLen);

         AESGCM_BUFLEN(pState) += locLen;
         AESGCM_TXT_LEN(pState) += locLen;
         pSrc += locLen;
         pDst += locLen;
         txtLen -= locLen;

         /* if buffer full */
         if(BLOCK_SIZE==AESGCM_BUFLEN(pState)) {
            /* hash buffer */
            hashFunc(AESGCM_GHASH(pState), AESGCM_HKEY(pState), AesGcmConst_table);
            AESGCM_BUFLEN(pState) = 0;

            /* increment counter block */
            IncrementCounter32(AESGCM_COUNTER(pState));
            /* and encrypt counter */
            encoder(AESGCM_COUNTER(pState), AESGCM_ECOUNTER(pState), RIJ_NR(pAES), RIJ_EKEYS(pAES), RijEncSbox);
         }
      }

      /* process the main part of text */
      {
         int lenBlks = txtLen & (-BLOCK_SIZE);
         if(lenBlks) {
            Decrypt_ decFunc = AESGCM_DEC(pState);

            decFunc(pDst, pSrc, lenBlks, pState);

            AESGCM_TXT_LEN(pState) += lenBlks;
            pSrc += lenBlks;
            pDst += lenBlks;
            txtLen -= lenBlks;
         }
      }

      /* process the rest of text */
      if(txtLen) {
         /* ctr encryption */
         XorBlock(pSrc, AESGCM_GHASH(pState)+AESGCM_BUFLEN(pState), AESGCM_GHASH(pState)+AESGCM_BUFLEN(pState), txtLen);
         XorBlock(pSrc, AESGCM_ECOUNTER(pState)+AESGCM_BUFLEN(pState), pDst, txtLen);

         AESGCM_BUFLEN(pState) += txtLen;
         AESGCM_TXT_LEN(pState) += txtLen;
      }

      return ippStsNoErr;
   }
}


/*F*
//    Name: ippsAES_GCMGetTag
//
// Purpose: Generates authentication tag in the GCM mode.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pDstTag == NULL
//                            pState == NULL
//    ippStsLengthErr         tagLen<=0 || tagLen>16
//    ippStsContextMatchErr  !AESGCM_VALID_ID()
//    ippStsNoErr             no errors
//
// Parameters:
//    pDstTag     pointer to the authentication tag.
//    tagLen      length of the authentication tag *pDstTag in bytes
//    pState      pointer to the context
//
*F*/
IPPFUN(IppStatus, ippsAES_GCMGetTag,(Ipp8u* pTag, int tagLen, const IppsAES_GCMState* pState))
{
   /* test State pointer */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsAES_GCMState*)( IPP_ALIGNED_PTR(pState, AESGCM_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(!AESGCM_VALID_ID(pState), ippStsContextMatchErr);

   /* test tag pointer and length */
   IPP_BAD_PTR1_RET(pTag);
   IPP_BADARG_RET(tagLen<=0 || tagLen>BLOCK_SIZE, ippStsLengthErr);


   {
      /* get method */
      MulGcm_ hashFunc = AESGCM_HASH(pState);

      __ALIGN16 Ipp8u tmpHash[BLOCK_SIZE];
      Ipp8u tmpCntr[BLOCK_SIZE];

      /* local copy of AAD and text counters (in bits) */
      Ipp64u aadBitLen = AESGCM_AAD_LEN(pState)*BYTESIZE;
      Ipp64u txtBitLen = AESGCM_TXT_LEN(pState)*BYTESIZE;

      /* do local copy of ghash */
      CopyBlock16(AESGCM_GHASH(pState), tmpHash);

      /* complete text processing */
      if(AESGCM_BUFLEN(pState)) {
         hashFunc(tmpHash, AESGCM_HKEY(pState), AesGcmConst_table);
      }

      /* process lengths of AAD and text */
      U32_TO_HSTRING(tmpCntr,   HIDWORD(aadBitLen));
      U32_TO_HSTRING(tmpCntr+4, LODWORD(aadBitLen));
      U32_TO_HSTRING(tmpCntr+8, HIDWORD(txtBitLen));
      U32_TO_HSTRING(tmpCntr+12,LODWORD(txtBitLen));

      XorBlock16(tmpHash, tmpCntr, tmpHash);
      hashFunc(tmpHash, AESGCM_HKEY(pState), AesGcmConst_table);

      /* add encrypted initial counter */
      XorBlock16(tmpHash, AESGCM_ECOUNTER0(pState), tmpHash);

      /* return tag of required lenth */
      CopyBlock(tmpHash, pTag, tagLen);

      return ippStsNoErr;
   }
}
