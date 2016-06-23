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
#include "pcptool.h"
#include "pcpngrsa.h"
#include "pcphash.h"


/*F*
// Name: ippsRSAEncrypt_OAEP
//
// Purpose: Performs RSAES-OAEP encryprion scheme
//
// Returns:                   Reason:
//    ippStsNotSupportedModeErr  unknown hashAlg
//
//    ippStsNullPtrErr           NULL == pKey
//                               NULL == pSrc
//                               NULL == pDst
//                               NULL == pLabel
//                               NULL == pSeed
//                               NULL == pBuffer
//
//    ippStsLengthErr            srcLen <0
//                               labLen <0
//                               srcLen > RSAsize -2*hashLen -2
//                               RSAsize < 2*hashLen +2
//
//    ippStsContextMatchErr      !RSA_PUB_KEY_VALID_ID()
//
//    ippStsIncompleteContextErr public key is not set up
//
//    ippStsNoErr                no error
//
// Parameters:
//    pSrc        pointer to the plaintext
//    srcLen      plaintext length (bytes)
//    pLabel      (optional) pointer to the label associated with plaintext
//    labLen      label length (bytes)
//    pSeed       seed string of hashLen size
//    pDst        pointer to the ciphertext (length of pdst is not less then size of RSA modulus)
//    pKey        pointer to the RSA public key context
//    hashAlg     hash alg ID
//    pBuffer     pointer to scratch buffer
*F*/
IPPFUN(IppStatus, ippsRSAEncrypt_OAEP,(const Ipp8u* pSrc, int srcLen,
                                       const Ipp8u* pLabel, int labLen, 
                                       const Ipp8u* pSeed,
                                             Ipp8u* pDst,
                                       const IppsRSAPublicKeyState* pKey,
                                             IppHashAlgId hashAlg,
                                             Ipp8u* pBuffer))
{
   int hashLen;

   /* test hash algorith ID */
   hashAlg = cpValidHashAlg(hashAlg);
   IPP_BADARG_RET(ippHashAlg_Unknown==hashAlg, ippStsNotSupportedModeErr);

   /* test data pointer */
   IPP_BAD_PTR3_RET(pSrc,pDst, pSeed);

   IPP_BADARG_RET(!pLabel && labLen, ippStsNullPtrErr);

   /* test public key context */
   IPP_BAD_PTR2_RET(pKey, pBuffer);
   pKey = (IppsRSAPublicKeyState*)( IPP_ALIGNED_PTR(pKey, RSA_PUBLIC_KEY_ALIGNMENT) );
   IPP_BADARG_RET(!RSA_PUB_KEY_VALID_ID(pKey), ippStsContextMatchErr);
   IPP_BADARG_RET(!RSA_PUB_KEY_IS_SET(pKey), ippStsIncompleteContextErr);

   /* test length */
   IPP_BADARG_RET(srcLen<0||labLen<0, ippStsLengthErr);

    hashLen = cpHashSize(hashAlg);
   /* test compatibility of RSA and hash length */
   IPP_BADARG_RET(BITS2WORD8_SIZE(RSA_PRV_KEY_BITSIZE_N(pKey)) < (2*hashLen +2), ippStsLengthErr);
   /* test compatibility of msg length and other (RSA and hash) lengths */
   IPP_BADARG_RET(BITS2WORD8_SIZE(RSA_PRV_KEY_BITSIZE_N(pKey))-(2*hashLen +2) < srcLen, ippStsLengthErr);

   {
      /* size of RSA modulus in bytes and chunks */
      int k = BITS2WORD8_SIZE(RSA_PUB_KEY_BITSIZE_N(pKey));
      cpSize nsN = BITS_BNU_CHUNK(RSA_PUB_KEY_BITSIZE_N(pKey));

      /*
      // EME-OAEP encoding
      */
      {
         Ipp8u  seedMask[BITS2WORD8_SIZE(IPP_SHA512_DIGEST_BITSIZE)];

         Ipp8u* pMaskedSeed = pDst+1;
         Ipp8u* pMaskedDB = pDst +hashLen +1;

         pDst[0] = 0;

         /* maskedDB = MGF(seed, k-1-hashLen)*/
         ippsMGF(pSeed, hashLen, pMaskedDB, k-1-hashLen, hashAlg);

         /* seedMask = HASH(pLab) */
         ippsHashMessage(pLabel, labLen, seedMask, hashAlg);

         /* maskedDB ^= concat(HASH(pLab),PS,0x01,pSc) */
         XorBlock(pMaskedDB, seedMask, pMaskedDB, hashLen);
         pMaskedDB[k-srcLen-hashLen-2] ^= 0x01;
         XorBlock(pMaskedDB+k-srcLen-hashLen-2+1, pSrc, pMaskedDB+k-srcLen-hashLen-2+1, srcLen);

         /* seedMask = MGF(maskedDB, hashLen) */
         ippsMGF(pMaskedDB, k-1-hashLen, seedMask, hashLen, hashAlg);
         /* maskedSeed = seed ^ seedMask */
         XorBlock(pSeed, seedMask, pMaskedSeed, hashLen);
      }

      /* RSA encryption */
      {
         /* align buffer */
         BNU_CHUNK_T* pScratchBuffer = (BNU_CHUNK_T*)(IPP_ALIGNED_PTR(pBuffer, (int)sizeof(BNU_CHUNK_T)) );

         /* temporary BN */
         __ALIGN8 IppsBigNumState tmpBN;
         BN_Make(pScratchBuffer, pScratchBuffer+nsN+1, nsN, &tmpBN);

         /* updtae buffer pointer */
         pScratchBuffer += (nsN+1)*2;

         ippsSetOctString_BN(pDst, k, &tmpBN);

         gsRSApub_cipher(&tmpBN, &tmpBN, pKey, pScratchBuffer);

         ippsGetOctString_BN(pDst, k, &tmpBN);
      }

      return ippStsNoErr;
   }
}


IPPFUN(IppStatus, ippsRSA_OAEPEncrypt_SHA256,(const Ipp8u* pSrc, int srcLen,
                                              const Ipp8u* pLabel, int labLen,
                                              const Ipp8u* pSeed,
                                              Ipp8u* pDst,
                                              const IppsRSAPublicKeyState* pKey,
                                              Ipp8u* pBuffer))
{ return ippsRSAEncrypt_OAEP(pSrc,srcLen, pLabel,labLen, pSeed,
                             pDst, pKey,
                             IPP_ALG_HASH_SHA256,
                             pBuffer); }
