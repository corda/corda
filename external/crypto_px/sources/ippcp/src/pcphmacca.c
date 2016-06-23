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
#include "pcphmac.h"
#include "pcptool.h"

/*F*
//    Name: ippsHMAC_GetSize
//
// Purpose: Returns size of HMAC state (bytes).
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSzie == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pSize       pointer to the HMAC state size
//
*F*/
IPPFUN(IppStatus, ippsHMAC_GetSize,(int* pSize))
{
   /* test size's pointer */
   IPP_BAD_PTR1_RET(pSize);

   *pSize = sizeof(IppsHMACState);
   return ippStsNoErr;
}

/*F*
//    Name: ippsHMAC_Init
//
// Purpose: Init HMAC state.
//
// Returns:                Reason:
//    ippStsNullPtrErr           pKey == NULL
//                               pState == NULL
//    ippStsLengthErr            keyLen <0
//    ippStsNotSupportedModeErr  if algID is not match to supported hash alg
//    ippStsNoErr                no errors
//
// Parameters:
//    pKey        pointer to the secret key
//    keyLen      length (bytes) of the secret key
//    pState      pointer to the HMAC state
//    hashAlg     hash alg ID
//
*F*/
IPPFUN(IppStatus, ippsHMAC_Init,(const Ipp8u* pKey, int keyLen, IppsHMACState* pCtx, IppHashAlgId hashAlg))
{
   //int mbs;

   /* get algorithm id */
   hashAlg = cpValidHashAlg(hashAlg);
   /* test hash alg */
   IPP_BADARG_RET(ippHashAlg_Unknown==hashAlg, ippStsNotSupportedModeErr);
   //mbs = cpHashMBS(hashAlg);

   /* test pState pointer */
   IPP_BAD_PTR1_RET(pCtx);

   /* test key pointer and key length */
   IPP_BAD_PTR1_RET(pKey);
   IPP_BADARG_RET(0>keyLen, ippStsLengthErr);

   /* set state ID */
   HMAC_CTX_ID(pCtx) = idCtxHMAC;

   /* init hash context */
   ippsHashInit(&HASH_CTX(pCtx), hashAlg);

   {
      int n;

      /* hash specific */
      IppsHashState* pHashCtx = &HASH_CTX(pCtx);
      int mbs = cpHashMBS(hashAlg);
      int hashSize = cpHashSize(hashAlg);

      /* copyMask = keyLen>mbs? 0xFF : 0x00 */
      int copyMask = (mbs-keyLen) >>(BITSIZE(int)-1);

      /* actualKeyLen = keyLen>mbs? hashSize:keyLen */
      int actualKeyLen = (hashSize & copyMask) | (keyLen & ~copyMask);

      /* compute hash(key, keyLen) just in case */
      ippsHashUpdate(pKey, keyLen, pHashCtx);
      ippsHashFinal(HASH_BUFF(pHashCtx), pHashCtx);

      /* copy either key or hash(key) into ipad- and opad- buffers */
      MASKED_COPY_BNU(pCtx->ipadKey, (Ipp8u)copyMask, HASH_BUFF(pHashCtx), pKey, actualKeyLen);
      MASKED_COPY_BNU(pCtx->opadKey, (Ipp8u)copyMask, HASH_BUFF(pHashCtx), pKey, actualKeyLen);

      /* XOR-ing key */
      for(n=0; n<actualKeyLen; n++) {
         pCtx->ipadKey[n] ^= (Ipp8u)IPAD;
         pCtx->opadKey[n] ^= (Ipp8u)OPAD;
      }
      for(; n<mbs; n++) {
         pCtx->ipadKey[n] = (Ipp8u)IPAD;
         pCtx->opadKey[n] = (Ipp8u)OPAD;
      }

      /* ipad key processing */
      ippsHashUpdate(pCtx->ipadKey, mbs, pHashCtx);

      return ippStsNoErr;
   }
}

/*F*
//    Name: ippsHMAC_Update
//
// Purpose: Updates intermadiate MAC based on input stream.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSrc == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxHMAC
//    ippStsLengthErr         len <0
//    ippStsNoErr             no errors
//
// Parameters:
//    pSrc        pointer to the input stream
//    len         input stream length
//    pState      pointer to the HMAC state
//
*F*/
IPPFUN(IppStatus, ippsHMAC_Update,(const Ipp8u* pSrc, int len, IppsHMACState* pCtx))
{
   /* test state pointers */
   IPP_BAD_PTR1_RET(pCtx);

   /* test state ID */
   IPP_BADARG_RET(!HMAC_VALID_ID(pCtx), ippStsContextMatchErr);
   /* test input length */
   IPP_BADARG_RET((len<0), ippStsLengthErr);
   /* test source pointer */
   IPP_BADARG_RET((len && !pSrc), ippStsNullPtrErr);

   if(len)
      return ippsHashUpdate(pSrc, len, &HASH_CTX(pCtx));
   else
      return ippStsNoErr;
}

/*F*
//    Name: ippsHMAC_Final
//
// Purpose: Stop message digesting and return digest.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pMD == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxHMAC
//    ippStsLengthErr         sizeof(DigestMD5) < mdLen <1
//    ippStsNoErr             no errors
//
// Parameters:
//    pMD         address of the output digest
//    pState      pointer to the HMAC state
//
*F*/
IPPFUN(IppStatus, ippsHMAC_Final,(Ipp8u* pMD, int mdLen, IppsHMACState* pCtx))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pCtx);
   IPP_BADARG_RET(!HMAC_VALID_ID(pCtx), ippStsContextMatchErr);

   /* test MD pointer and length */
   IPP_BAD_PTR1_RET(pMD);
   IPP_BADARG_RET(mdLen<=0, ippStsLengthErr);

   {
      /* hash specific */
      IppsHashState* pHashCtx = &HASH_CTX(pCtx);
      int mbs = cpHashMBS(HASH_ALG_ID(pHashCtx));
      int hashSize = cpHashSize(HASH_ALG_ID(pHashCtx));
      if(mdLen>hashSize)
         IPP_ERROR_RET(ippStsLengthErr);

      /*
      // finalize hmac
      */
      {
         /* finalize 1-st step */
         Ipp8u md[IPP_SHA512_DIGEST_BITSIZE/8];
         IppStatus sts = ippsHashFinal(md, pHashCtx);

         if(ippStsNoErr==sts) {
            /* perform outer hash */
            ippsHashUpdate(pCtx->opadKey, mbs, pHashCtx);
            ippsHashUpdate(md, hashSize, pHashCtx);

            /* complete HMAC */
            ippsHashFinal(md, pHashCtx);
            CopyBlock(md, pMD, IPP_MIN(hashSize, mdLen));

            /* ready to the next HMAC computation */
            ippsHashUpdate(pCtx->ipadKey, mbs, pHashCtx);
         }

         return sts;
      }
   }
}

/*F*
//    Name: ippsHMAC_GetTag
//
// Purpose: Compute digest with further digesting ability.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pMD == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxHMAC
//    ippStsLengthErr         size_of_digest < mdLen <1
//    ippStsNoErr             no errors
//
// Parameters:
//    pMD         address of the output digest
//    mdLen       length of the digest
//    pState      pointer to the HMAC state
//
*F*/
IPPFUN(IppStatus, ippsHMAC_GetTag,(Ipp8u* pMD, int mdLen, const IppsHMACState* pCtx))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pCtx);
   IPP_BADARG_RET(!HMAC_VALID_ID(pCtx), ippStsContextMatchErr);

   /* test MD pointer */
   IPP_BAD_PTR1_RET(pMD);

   {
      IppsHMACState tmpCtx;
      CopyBlock(pCtx, &tmpCtx, sizeof(IppsHMACState));
      return ippsHMAC_Final(pMD, mdLen, &tmpCtx);
   }
}

/*F*
//    Name: ippsHMAC_Message
//
// Purpose: MAC of the whole message.
//
// Returns:                Reason:
//    ippStsNullPtrErr           pMsg == NULL
//                               pKey == NULL
//                               pMD == NULL
//    ippStsLengthErr            msgLen <0
//                               keyLen <0
//                               size_of_digest < mdLen <1
//    ippStsNotSupportedModeErr  if algID is not match to supported hash alg
//    ippStsNoErr                no errors
//
// Parameters:
//    pMsg        pointer to the input message
//    msgLen      input message length
//    pKey        pointer to the secret key
//    keyLen      secret key length
//    pMD         pointer to message digest
//    mdLen       MD length
//    hashAlg     hash alg ID
//
*F*/
IPPFUN(IppStatus, ippsHMAC_Message,(const Ipp8u* pMsg, int msgLen,
                                   const Ipp8u* pKey, int keyLen,
                                   Ipp8u* pMD, int mdLen,
                                   IppHashAlgId hashAlg))
{
   /* get algorithm id */
   hashAlg = cpValidHashAlg(hashAlg);
   /* test hash alg */
   IPP_BADARG_RET(ippHashAlg_Unknown==hashAlg, ippStsNotSupportedModeErr);

   /* test secret key pointer and length */
   IPP_BAD_PTR1_RET(pKey);
   IPP_BADARG_RET((keyLen<0), ippStsLengthErr);

   /* test input message pointer and length */
   IPP_BADARG_RET((msgLen<0), ippStsLengthErr);
   IPP_BADARG_RET((msgLen && !pMsg), ippStsNullPtrErr);

   /* test MD pointer and length */
   IPP_BAD_PTR1_RET(pMD);
   IPP_BADARG_RET(0>=mdLen || mdLen>cpHashSize(hashAlg), ippStsLengthErr);

   {
      IppsHMACState ctx;
      IppStatus sts = ippsHMAC_Init(pKey, keyLen, &ctx, hashAlg);
      if(ippStsNoErr!=sts) goto exit;

      sts = ippsHashUpdate(pMsg,msgLen, &HASH_CTX(&ctx));
      if(ippStsNoErr!=sts) goto exit;

      sts = ippsHMAC_Final(pMD, mdLen, &ctx);

      exit:
      PurgeBlock(&ctx, sizeof(IppsHMACState));
      return sts;
   }
}
