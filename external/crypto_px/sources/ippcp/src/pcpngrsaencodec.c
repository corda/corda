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
#include "pcpbn.h"
#include "pcpngrsa.h"
#include "pcpngrsamontstuff.h"


/*F*
// Name: ippsRSA_GetBufferSizePublicKey
//
// Purpose: Returns size of temporary buffer (in bytes) for public key operation
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pKey
//                               NULL == pBufferSize
//
//    ippStsContextMatchErr     !RSA_PUB_KEY_VALID_ID()
//
//    ippStsIncompleteContextErr no ippsRSA_SetPublicKey() call
//
//    ippStsNoErr                no error
//
// Parameters:
//    pBufferSize pointer to size of temporary buffer
//    pKey        pointer to the key context
*F*/
IPPFUN(IppStatus, ippsRSA_GetBufferSizePublicKey,(int* pBufferSize, const IppsRSAPublicKeyState* pKey))
{
   IPP_BAD_PTR1_RET(pKey);
   pKey = (IppsRSAPublicKeyState*)( IPP_ALIGNED_PTR(pKey, RSA_PUBLIC_KEY_ALIGNMENT) );
   IPP_BADARG_RET(!RSA_PUB_KEY_VALID_ID(pKey), ippStsContextMatchErr);
   IPP_BADARG_RET(!RSA_PUB_KEY_IS_SET(pKey), ippStsIncompleteContextErr);

   IPP_BAD_PTR1_RET(pBufferSize);

   {
      cpSize expBitSize = RSA_PUB_KEY_BITSIZE_E(pKey);
      cpSize w = gsMontExp_WinSize(expBitSize);
      cpSize precompLen = (1==w)? 0 : (1<<w);
      cpSize nsM = BITS_BNU_CHUNK(RSA_PUB_KEY_BITSIZE_N(pKey));

      /*
      // scratch structure:                                 length (elements)
      //    BN data and BN buffer  (RSA-OAEP|PKCS v1.5)     (2)
      //    pre-computed resource                           (1<<w or 0, if w=1)
      //    copy of base (need if y=x^e is inplace)         (1) (w=1)
      // or resource to keep zero-extended power e          (1) (w>1)
      //    temporary product                               (2)
      */
      cpSize bufferLen = ((nsM+1)*2)*2
                        +precompLen*nsM
                        +nsM
                        +nsM*2;

      *pBufferSize = bufferLen*sizeof(BNU_CHUNK_T)
                   + sizeof(BNU_CHUNK_T)-1
                   + (CACHE_LINE_SIZE-1);
      return ippStsNoErr;
   }
}


/*F*
// Name: ippsRSA_GetBufferSizePublicKey
//
// Purpose: Returns size of temporary buffer (in bytes) for public key operation
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pKey
//                               NULL == pBufferSize
//
//    ippStsContextMatchErr     !RSA_PRV_KEY_VALID_ID()
//
//    ippStsIncompleteContextErr (type1) private key is not set up
//
//    ippStsNoErr                no error
//
// Parameters:
//    pBufferSize pointer to size of temporary buffer
//    pKey        pointer to the key context
*F*/
IPPFUN(IppStatus, ippsRSA_GetBufferSizePrivateKey,(int* pBufferSize, const IppsRSAPrivateKeyState* pKey))
{
   IPP_BAD_PTR1_RET(pKey);
   pKey = (IppsRSAPrivateKeyState*)( IPP_ALIGNED_PTR(pKey, RSA_PUBLIC_KEY_ALIGNMENT) );
   IPP_BADARG_RET(!RSA_PRV_KEY_VALID_ID(pKey), ippStsContextMatchErr);
   IPP_BADARG_RET(RSA_PRV_KEY1_VALID_ID(pKey) && !RSA_PRV_KEY_IS_SET(pKey), ippStsIncompleteContextErr);

   IPP_BAD_PTR1_RET(pBufferSize);

   {
      cpSize bufferLen;
      if(RSA_PRV_KEY1_VALID_ID(pKey)) {
         cpSize expBitSize = RSA_PRV_KEY_BITSIZE_D(pKey);
         cpSize w = gsMontExp_WinSize(expBitSize);
         cpSize precompLen = (1==w)? 0 : (1<<w);
         cpSize nsN = BITS_BNU_CHUNK(RSA_PRV_KEY_BITSIZE_N(pKey));

         /*
         // scratch structure:                                 length (elements)
         //    BN data and BN buffer  (RSA-OAEP|PKCS v1.5)     (2)
         //    pre-computed resource                           (1<<w or 0, if w=1)
         //    recoure to keep "masked" multipler (x|1)        (1), (w=1)
         //    copy of base (need if y=x^e is inplace)         (1), (w>1)
         //    temporary product                               (2)
         */
         bufferLen = ((nsN+1)*2)*2
                    +gsPrecompResourcelen(precompLen,nsN) //+precompLen*nsN
                    +nsN
                    +nsN
                    +nsN*2;
      }
      else {
         cpSize expBitSize = IPP_MAX(RSA_PRV_KEY_BITSIZE_P(pKey), RSA_PRV_KEY_BITSIZE_Q(pKey));
         cpSize w = gsMontExp_WinSize(expBitSize);
         cpSize precompLen = (1==w)? 0 : (1<<w);
         cpSize nsP = BITS_BNU_CHUNK(expBitSize);

         /* for validation/generation purpose */
         cpSize validationBufferLen = 5*(nsP+1);
         cpSize generationBufferLen = 5*(nsP*2+1);

         /*
         // scratch structure:                                 length (elements)
         //    BN data and BN buffer  (RSA-OAEP|PKCS v1.5)     (2)*(~2)
         //    pre-computed resource                           (1<<w or 0, if w=1)
         //    copy of base (need if y=x^e is inplace)         (1), (w=1)
         // or resource for ScramblePut/Get                    (1), (w>1)
         //    recoure to keep "masked" multipler (x|1)        (1), (w=1)
         // or resource to keep zero-extended power e          (1), (w>1)
         //    temporary product                               (2)
         */
         bufferLen = ((nsP*2+1)*2)*2
                    +gsPrecompResourcelen(precompLen, nsP) //+precompLen*nsP
                    +nsP
                    +nsP
                    +nsP*2;
         bufferLen = IPP_MAX( IPP_MAX(validationBufferLen,generationBufferLen), bufferLen );
      }
      *pBufferSize = bufferLen*sizeof(BNU_CHUNK_T)
                   + sizeof(BNU_CHUNK_T)-1
                   + (CACHE_LINE_SIZE-1);
      return ippStsNoErr;
   }
}



void gsRSApub_cipher(IppsBigNumState* pY,
               const IppsBigNumState* pX,
               const IppsRSAPublicKeyState* pKey,
                     BNU_CHUNK_T* pScratchBuffer)
{
   IppsMontState* pMontN = RSA_PUB_KEY_NMONT(pKey);
   gsMontEnc_BN(pY, pX, pMontN, pScratchBuffer);

   {
      /* optimal size of window */
      BNU_CHUNK_T* pExp = RSA_PUB_KEY_E(pKey);
      cpSize nsExp = BITS_BNU_CHUNK(RSA_PUB_KEY_BITSIZE_E(pKey));
      cpSize w = gsMontExp_WinSize(RSA_PUB_KEY_BITSIZE_E(pKey));

      if(1==w)
         gsMontExpBin_BN(pY, pY, pExp, nsExp, pMontN, pScratchBuffer);
      else
         gsMontExpWin_BN(pY, pY, pExp, nsExp, w, pMontN, pScratchBuffer);
   }

   gsMontDec_BN(pY, pY, pMontN, pScratchBuffer);
}


/*F*
// Name: ippsRSA_Encrypt
//
// Purpose: Performs RSA Encryprion
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pKey
//                               NULL == pPtxt
//                               NULL == pCtxt
//                               NULL == pBuffer
//
//    ippStsContextMatchErr     !RSA_PUB_KEY_VALID_ID()
//                              !BN_VALID_ID(pPtxt)
//                              !BN_VALID_ID(pCtxt)
//
//    ippStsIncompleteContextErr public key is not setup
//
//    ippStsOutOfRangeErr        pPtxt >= modulus
//                               pPtxt <0
//
//    ippStsSizeErr              BN_ROOM(pCtxt) is not enough
//
//    ippStsNoErr                no error
//
// Parameters:
//    pPtxt          pointer to the plaintext
//    pCtxt          pointer to the ciphertext
//    pKey           pointer to the key context
//    pScratchBuffer pointer to the temporary buffer
*F*/
IPPFUN(IppStatus, ippsRSA_Encrypt,(const IppsBigNumState* pPtxt,
                                         IppsBigNumState* pCtxt,
                                   const IppsRSAPublicKeyState* pKey,
                                         Ipp8u* pScratchBuffer))
{
   IPP_BAD_PTR2_RET(pKey, pScratchBuffer);
   pKey = (IppsRSAPublicKeyState*)( IPP_ALIGNED_PTR(pKey, RSA_PUBLIC_KEY_ALIGNMENT) );
   IPP_BADARG_RET(!RSA_PUB_KEY_VALID_ID(pKey), ippStsContextMatchErr);
   IPP_BADARG_RET(!RSA_PUB_KEY_IS_SET(pKey), ippStsIncompleteContextErr);

   IPP_BAD_PTR1_RET(pPtxt);
   pPtxt = (IppsBigNumState*)( IPP_ALIGNED_PTR(pPtxt, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pPtxt), ippStsContextMatchErr);
   IPP_BADARG_RET(BN_NEGATIVE(pPtxt), ippStsOutOfRangeErr);
   IPP_BADARG_RET(0 <= cpCmp_BNU(BN_NUMBER(pPtxt), BN_SIZE(pPtxt),
                                 MNT_MODULUS(RSA_PUB_KEY_NMONT(pKey)), MNT_SIZE(RSA_PUB_KEY_NMONT(pKey))), ippStsOutOfRangeErr);

   IPP_BAD_PTR1_RET(pCtxt);
   pCtxt = (IppsBigNumState*)( IPP_ALIGNED_PTR(pCtxt, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pCtxt), ippStsContextMatchErr);
   IPP_BADARG_RET(BN_ROOM(pCtxt) < BITS_BNU_CHUNK(RSA_PUB_KEY_BITSIZE_N(pKey)), ippStsSizeErr);

   gsRSApub_cipher(pCtxt,
                   pPtxt,
                   pKey,
                   (BNU_CHUNK_T*)(IPP_ALIGNED_PTR((pScratchBuffer), (int)sizeof(BNU_CHUNK_T))) );
   return ippStsNoErr;
}



void gsRSAprv_cipher(IppsBigNumState* pY,
               const IppsBigNumState* pX,
               const IppsRSAPrivateKeyState* pKey,
                     BNU_CHUNK_T* pScratchBuffer)
{
   IppsMontState* pMontN = RSA_PRV_KEY_NMONT(pKey);
   gsMontEnc_BN(pY, pX, pMontN, pScratchBuffer);

   {
      /* optimal size of window */
      BNU_CHUNK_T* pExp = RSA_PRV_KEY_D(pKey);
      cpSize nsExp = BITS_BNU_CHUNK(RSA_PRV_KEY_BITSIZE_D(pKey));
      cpSize w = gsMontExp_WinSize(RSA_PRV_KEY_BITSIZE_D(pKey));

      if(1==w)
         gsMontExpBin_BN_sscm(pY, pY, pExp, nsExp, pMontN, pScratchBuffer);
      else
         gsMontExpWin_BN_sscm(pY, pY, pExp, nsExp, w, pMontN, pScratchBuffer);
   }

   gsMontDec_BN(pY, pY, pMontN, pScratchBuffer);
}

void gsRSAprv_cipher_crt(IppsBigNumState* pY,
               const IppsBigNumState* pX,
               const IppsRSAPrivateKeyState* pKey,
                     BNU_CHUNK_T* pScratchBuffer)
{
   /* P- and Q- montgometry engines */
   IppsMontState* pMontP = RSA_PRV_KEY_PMONT(pKey);
   IppsMontState* pMontQ = RSA_PRV_KEY_QMONT(pKey);
   cpSize nsP = MNT_SIZE(pMontP);
   cpSize nsQ = MNT_SIZE(pMontQ);

   const BNU_CHUNK_T* dataX = BN_NUMBER(pX);
   cpSize nsX = BN_SIZE(pX);
   BNU_CHUNK_T* dataXp = BN_NUMBER(pY);
   BNU_CHUNK_T* dataXq = BN_BUFFER(pY);

   cpSize bitSizeDP = BITSIZE_BNU(RSA_PRV_KEY_DP(pKey), nsP);
   cpSize bitSizeDQ = BITSIZE_BNU(RSA_PRV_KEY_DQ(pKey), nsQ);
   cpSize w;
   BNU_CHUNK_T cf;

   /* compute xq = x^dQ mod Q */
   COPY_BNU(dataXq, dataX, nsX);
   cpMod_BNU(dataXq, nsX, MNT_MODULUS(pMontQ), nsQ);
   gsMontEnc_BNU(dataXq, dataXq, nsQ, pMontQ, pScratchBuffer);
   w = gsMontExp_WinSize(bitSizeDQ);

   if(1==w)
      gsMontExpBin_BNU_sscm(dataXq,
                            dataXq, nsQ,
                            RSA_PRV_KEY_DQ(pKey), BITS_BNU_CHUNK(bitSizeDQ),
                            pMontQ, pScratchBuffer);
   else
      gsMontExpWin_BNU_sscm(dataXq,
                            dataXq, nsQ,
                            RSA_PRV_KEY_DQ(pKey), BITS_BNU_CHUNK(bitSizeDQ), w,
                            pMontQ, pScratchBuffer);

   gsMontDec_BNU(dataXq, dataXq, nsQ, pMontQ, pScratchBuffer);

   /* compute xp = x^dP mod P */
   COPY_BNU(dataXp, dataX, nsX);
   cpMod_BNU(dataXp, nsX, MNT_MODULUS(pMontP), nsP);
   gsMontEnc_BNU(dataXp, dataXp, nsP, pMontP, pScratchBuffer);
   w = gsMontExp_WinSize(bitSizeDP);

   if(1==w)
      gsMontExpBin_BNU_sscm(dataXp,
                            dataXp, nsP,
                            RSA_PRV_KEY_DP(pKey), BITS_BNU_CHUNK(bitSizeDP),
                            pMontP, pScratchBuffer);
   else
      gsMontExpWin_BNU_sscm(dataXp,
                            dataXp, nsP,
                            RSA_PRV_KEY_DP(pKey), BITS_BNU_CHUNK(bitSizeDP), w,
                            pMontP, pScratchBuffer);

   gsMontDec_BNU(dataXp, dataXp, nsP, pMontP, pScratchBuffer);

   /* xp -= xq */
   cf = cpSub_BNU(dataXp, dataXp, dataXq, nsQ);
   if(nsP-nsQ)
      cf = cpDec_BNU(dataXp+nsQ, dataXp+nsQ, (nsP-nsQ), cf);
   if(cf)
      cpAdd_BNU(dataXp, dataXp, MNT_MODULUS(pMontP), nsP);

   /* xp = xp*qInv mod P */
   cpMontMul_BNU(dataXp,
                 dataXp, nsP,
                 RSA_PRV_KEY_INVQ(pKey), nsP,
                 MNT_MODULUS(pMontP), nsP, MNT_HELPER(pMontP),
                 pScratchBuffer, NULL);

   /* Y = xq + xp*Q */
   cpMul_BNU_school(pScratchBuffer,
                    dataXp, nsP,
                    MNT_MODULUS(pMontQ), nsQ);
   cf = cpAdd_BNU(BN_NUMBER(pY), pScratchBuffer, dataXq, nsQ);
   cpInc_BNU(BN_NUMBER(pY)+nsQ, pScratchBuffer+nsQ, nsP, cf);

   nsX = nsP+nsQ;
   FIX_BNU(BN_NUMBER(pY), nsX);
   BN_SIZE(pY) = nsX;
   BN_SIGN(pY) = ippBigNumPOS;
}

/*F*
// Name: ippsRSA_Decrypt
//
// Purpose: Performs RSA Decryprion
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pKey
//                               NULL == pCtxt
//                               NULL == pPtxt
//                               NULL == pBuffer
//
//    ippStsContextMatchErr     !RSA_PUB_KEY_VALID_ID()
//                              !BN_VALID_ID(pCtxt)
//                              !BN_VALID_ID(pPtxt)
//
//    ippStsIncompleteContextErr private key is not set up
//
//    ippStsOutOfRangeErr        pCtxt >= modulus
//                               pCtxt <0
//
//    ippStsSizeErr              BN_ROOM(pPtxt) is not enough
//
//    ippStsNoErr                no error
//
// Parameters:
//    pCtxt          pointer to the ciphertext
//    pPtxt          pointer to the plaintext
//    pKey           pointer to the key context
//    pScratchBuffer pointer to the temporary buffer
*F*/
IPPFUN(IppStatus, ippsRSA_Decrypt,(const IppsBigNumState* pCtxt,
                                         IppsBigNumState* pPtxt,
                                   const IppsRSAPrivateKeyState* pKey,
                                         Ipp8u* pScratchBuffer))
{
   IPP_BAD_PTR2_RET(pKey, pScratchBuffer);
   pKey = (IppsRSAPrivateKeyState*)( IPP_ALIGNED_PTR(pKey, RSA_PRIVATE_KEY_ALIGNMENT) );
   IPP_BADARG_RET(!RSA_PRV_KEY_VALID_ID(pKey), ippStsContextMatchErr);
   IPP_BADARG_RET(!RSA_PRV_KEY_IS_SET(pKey), ippStsIncompleteContextErr);

   IPP_BAD_PTR1_RET(pCtxt);
   pCtxt = (IppsBigNumState*)( IPP_ALIGNED_PTR(pCtxt, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pCtxt), ippStsContextMatchErr);
   IPP_BADARG_RET(BN_NEGATIVE(pCtxt), ippStsOutOfRangeErr);
   IPP_BADARG_RET(0 <= cpCmp_BNU(BN_NUMBER(pCtxt), BN_SIZE(pCtxt),
                                 MNT_MODULUS(RSA_PRV_KEY_NMONT(pKey)), MNT_SIZE(RSA_PRV_KEY_NMONT(pKey))), ippStsOutOfRangeErr);

   IPP_BAD_PTR1_RET(pPtxt);
   pPtxt = (IppsBigNumState*)( IPP_ALIGNED_PTR(pPtxt, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pPtxt), ippStsContextMatchErr);
   IPP_BADARG_RET(BN_ROOM(pPtxt) < BITS_BNU_CHUNK(RSA_PRV_KEY_BITSIZE_N(pKey)), ippStsSizeErr);

   if(RSA_PRV_KEY1_VALID_ID(pKey))
      gsRSAprv_cipher(pPtxt,
                      pCtxt,
                      pKey,
                      (BNU_CHUNK_T*)(IPP_ALIGNED_PTR((pScratchBuffer), (int)sizeof(BNU_CHUNK_T))) );
   else
      gsRSAprv_cipher_crt(pPtxt,
                          pCtxt,
                          pKey,
                          (BNU_CHUNK_T*)(IPP_ALIGNED_PTR((pScratchBuffer), (int)sizeof(BNU_CHUNK_T))) );
   return ippStsNoErr;
}
