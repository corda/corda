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

#include "pcpngrsa.h"
#include "pcphash.h"
#include "pcptool.h"

static int EMSA_PKCSv15(const Ipp8u* msgDg, int lenMsgDg,
                        const Ipp8u* fixPS, int lenFixPS,
                              Ipp8u*   pEM, int lenEM)
{
   /*
   // encoded message format:
   //    EM = 00 || 01 || PS=(FF..FF) || 00 || T
   //    T = fixPS || msgDg
   //    len(PS) >= 8
   */
   int  tLen = lenFixPS + lenMsgDg;

   if(lenEM >= tLen+11) {
      int psLen = lenEM - 3 - tLen;

      PaddBlock(0xFF, pEM, lenEM);
      pEM[0] = 0x00;
      pEM[1] = 0x01;
      pEM[2+psLen] = 0x00;
      CopyBlock(fixPS, pEM+3+psLen, lenFixPS);
      CopyBlock(msgDg, pEM+3+psLen+lenFixPS, lenMsgDg);
      return 1;
   }
   else
      return 0; /* encoded message length too long */
}

/*
// The DER encoding T of the DigestInfo value is equal to the following (see PKCS-1v2-2):
*/
static const Ipp8u   SHA1_fixPS[] = "\x30\x21\x30\x09\x06\x05\x2b\x0e\x03\x02\x1a\x05\x00\x04\x14";
static const Ipp8u SHA224_fixPS[] = "\x30\x2d\x30\x0d\x06\x09\x60\x86\x48\x01\x65\x03\x04\x02\x04\x05\x00\x04\x1c";
static const Ipp8u SHA256_fixPS[] = "\x30\x31\x30\x0d\x06\x09\x60\x86\x48\x01\x65\x03\x04\x02\x01\x05\x00\x04\x20";
static const Ipp8u SHA384_fixPS[] = "\x30\x41\x30\x0d\x06\x09\x60\x86\x48\x01\x65\x03\x04\x02\x02\x05\x00\x04\x30";
static const Ipp8u SHA512_fixPS[] = "\x30\x51\x30\x0d\x06\x09\x60\x86\x48\x01\x65\x03\x04\x02\x03\x05\x00\x04\x40";
static const Ipp8u    MD5_fixPS[] = "\x30\x20\x30\x0c\x06\x08\x2a\x86\x48\x86\xf7\x0d\x02\x05\x05\x00\x04\x10";
static const Ipp8u SHA512_224_fixPS[] = "\x30\x2d\x30\x0d\x06\x09\x60\x86\x48\x01\x65\x03\x04\x02\x05\x05\x00\x04\x1c";
static const Ipp8u SHA512_256_fixPS[] = "\x30\x31\x30\x0d\x06\x09\x60\x86\x48\x01\x65\x03\x04\x02\x06\x05\x00\x04\x20";

typedef struct {
   const Ipp8u* pSalt;
   int saltLen;
} SaltInfo;

static SaltInfo pksc15_salt[] = {
   {NULL,         0},
   {SHA1_fixPS,   sizeof(SHA1_fixPS)-1},
   {SHA256_fixPS, sizeof(SHA256_fixPS)-1},
   {SHA224_fixPS, sizeof(SHA224_fixPS)-1},
   {SHA512_fixPS, sizeof(SHA512_fixPS)-1},
   {SHA384_fixPS, sizeof(SHA384_fixPS)-1},
   {   MD5_fixPS, sizeof(MD5_fixPS)-1},
   {NULL,         0},
   {SHA512_224_fixPS, sizeof(SHA512_224_fixPS)-1},
   {SHA512_256_fixPS, sizeof(SHA512_256_fixPS)-1},
};
/* //////////////////////////////////////////////////////////////////////////////////////////////// */

static int GenerateSing(const Ipp8u* pMsg,  int msgLen,  /* message representation */
                        const Ipp8u* pSalt, int saltLen, /* fied string */
                              Ipp8u* pSign,
                        const IppsRSAPrivateKeyState* pPrvKey,
                        const IppsRSAPublicKeyState*  pPubKey,
                              Ipp8u* pBuffer)
{
   /* size of RSA modulus in bytes and chunks */
   cpSize rsaBits = RSA_PRV_KEY_BITSIZE_N(pPrvKey);
   cpSize k = BITS2WORD8_SIZE(rsaBits);
   cpSize nsN = BITS_BNU_CHUNK(rsaBits);

   /* EMSA-PKCS-v1_5 encoding */
   int result = EMSA_PKCSv15(pMsg,msgLen, pSalt,saltLen, pSign, k);

   if(result) {
      /* align buffer */
      BNU_CHUNK_T* pScratchBuffer = (BNU_CHUNK_T*)(IPP_ALIGNED_PTR(pBuffer, (int)sizeof(BNU_CHUNK_T)) );

      /* temporary BNs */
      __ALIGN8 IppsBigNumState bnC;
      __ALIGN8 IppsBigNumState bnP;

      /* make BNs */
      BN_Make(pScratchBuffer, pScratchBuffer+nsN+1, nsN, &bnC);
      pScratchBuffer += (nsN+1)*2;
      BN_Make(pScratchBuffer, pScratchBuffer+nsN+1, nsN, &bnP);
      pScratchBuffer += (nsN+1)*2;

      /*
      // private-key operation
      */
      ippsSetOctString_BN(pSign, k, &bnC);

      if(RSA_PRV_KEY1_VALID_ID(pPrvKey))
         gsRSAprv_cipher(&bnP, &bnC, pPrvKey, pScratchBuffer);
      else
         gsRSAprv_cipher_crt(&bnP, &bnC, pPrvKey, pScratchBuffer);

      ippsGetOctString_BN(pSign, k, &bnP);

      /* check the result before send it out (fault attack mitigatioin) */
      if(pPubKey) {
         gsRSApub_cipher(&bnP, &bnP, pPubKey, pScratchBuffer);

         /* check signature before send it out (fault attack mitigatioin) */
         if(0!=cpBN_cmp(&bnP, &bnC)) {
            PaddBlock(0, pSign, k);
            result = 0;
         }
      }
   }

   return result;
}

IPPFUN(IppStatus, ippsRSASign_PKCS1v15,(const Ipp8u* pMsg, int msgLen,
                                              Ipp8u* pSign,
                                        const IppsRSAPrivateKeyState* pPrvKey,
                                        const IppsRSAPublicKeyState*  pPubKey,
                                              IppHashAlgId hashAlg,
                                              Ipp8u* pBuffer))
{
   /* test private key context */
   IPP_BAD_PTR2_RET(pPrvKey, pBuffer);
   pPrvKey = (IppsRSAPrivateKeyState*)( IPP_ALIGNED_PTR(pPrvKey, RSA_PRIVATE_KEY_ALIGNMENT) );
   IPP_BADARG_RET(!RSA_PRV_KEY_VALID_ID(pPrvKey), ippStsContextMatchErr);
   IPP_BADARG_RET(!RSA_PRV_KEY_IS_SET(pPrvKey), ippStsIncompleteContextErr);

   /* test hash algorith ID */
   hashAlg = cpValidHashAlg(hashAlg);
   IPP_BADARG_RET(ippHashAlg_Unknown==hashAlg, ippStsNotSupportedModeErr);

   /* use aligned public key context if defined */
   if(pPubKey) {
      pPubKey = (IppsRSAPublicKeyState*)( IPP_ALIGNED_PTR(pPubKey, RSA_PUBLIC_KEY_ALIGNMENT) );
      IPP_BADARG_RET(!RSA_PUB_KEY_VALID_ID(pPubKey), ippStsContextMatchErr);
      IPP_BADARG_RET(!RSA_PUB_KEY_IS_SET(pPubKey), ippStsIncompleteContextErr);
   }

   /* test data pointer */
   IPP_BAD_PTR2_RET(pMsg, pSign);
   /* test length */
   IPP_BADARG_RET(msgLen<0, ippStsLengthErr);

   {
      Ipp8u md[IPP_SHA512_DIGEST_BITSIZE/BYTESIZE];
      int mdLen = cpHashSize(hashAlg);
      ippsHashMessage(pMsg, msgLen, md, hashAlg);

      {
         const Ipp8u* pSalt = pksc15_salt[hashAlg].pSalt;
         int saltLen = pksc15_salt[hashAlg].saltLen;

         int sts = GenerateSing(md, mdLen,
                         pSalt, saltLen,
                         pSign,
                         pPrvKey, pPubKey, pBuffer);

         return (1==sts)? ippStsNoErr : ippStsSizeErr;
      }
   }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

static int VerifySing(const Ipp8u* pMsg,  int msgLen,  /* message representation */
                      const Ipp8u* pSalt, int saltLen, /* fied string */
                      const Ipp8u* pSign,
                            int* pIsValid,
                      const IppsRSAPublicKeyState* pKey,
                            Ipp8u* pBuffer)
{
   /* size of RSA modulus in bytes and chunks */
   cpSize rsaBits = RSA_PUB_KEY_BITSIZE_N(pKey);
   cpSize k = BITS2WORD8_SIZE(rsaBits);
   cpSize nsN = BITS_BNU_CHUNK(rsaBits);

      /* align buffer */
   BNU_CHUNK_T* pScratchBuffer = (BNU_CHUNK_T*)(IPP_ALIGNED_PTR(pBuffer, (int)sizeof(BNU_CHUNK_T)) );

   /* temporary BNs */
   __ALIGN8 IppsBigNumState bnC;
   __ALIGN8 IppsBigNumState bnP;

   /* make BNs */
   BN_Make(pScratchBuffer, pScratchBuffer+nsN+1, nsN, &bnC);
   pScratchBuffer += (nsN+1)*2;
   BN_Make(pScratchBuffer, pScratchBuffer+nsN+1, nsN, &bnP);
   pScratchBuffer += (nsN+1)*2;

   /*
   // public-key operation
   */
   ippsSetOctString_BN(pSign, k, &bnP);
   gsRSApub_cipher(&bnC, &bnP, pKey, pScratchBuffer);

   /* convert EM into the string */
   ippsGetOctString_BN((Ipp8u*)(BN_BUFFER(&bnC)), k, &bnC);

   /* EMSA-PKCS-v1_5 encoding */
   if( EMSA_PKCSv15(pMsg,msgLen, pSalt,saltLen, (Ipp8u*)(BN_NUMBER(&bnC)), k) ) {
      *pIsValid = 1==EquBlock((Ipp8u*)(BN_BUFFER(&bnC)), (Ipp8u*)(BN_NUMBER(&bnC)), k);
      return 1;
   }
   else
      return 0;
}

IPPFUN(IppStatus, ippsRSAVerify_PKCS1v15,(const Ipp8u* pMsg, int msgLen,
                                          const Ipp8u* pSign, int* pIsValid,
                                          const IppsRSAPublicKeyState* pKey,
                                                IppHashAlgId hashAlg,
                                                Ipp8u* pBuffer))
{
   /* test public key context */
   IPP_BAD_PTR2_RET(pKey, pBuffer);
   pKey = (IppsRSAPublicKeyState*)( IPP_ALIGNED_PTR(pKey, RSA_PUBLIC_KEY_ALIGNMENT) );
   IPP_BADARG_RET(!RSA_PUB_KEY_VALID_ID(pKey), ippStsContextMatchErr);
   IPP_BADARG_RET(!RSA_PUB_KEY_IS_SET(pKey), ippStsIncompleteContextErr);

   /* test hash algorith ID */
   hashAlg = cpValidHashAlg(hashAlg);
   IPP_BADARG_RET(ippHashAlg_Unknown==hashAlg, ippStsNotSupportedModeErr);

   /* test data pointer */
   IPP_BAD_PTR3_RET(pMsg, pSign, pIsValid);
   /* test length */
   IPP_BADARG_RET(msgLen<0, ippStsLengthErr);

   *pIsValid = 0;
   {
      Ipp8u md[IPP_SHA512_DIGEST_BITSIZE/BYTESIZE];
      int mdLen = cpHashSize(hashAlg);
      ippsHashMessage(pMsg, msgLen, md, hashAlg);

      return VerifySing(md, mdLen,
                        pksc15_salt[hashAlg].pSalt, pksc15_salt[hashAlg].saltLen,
                        pSign, pIsValid,
                        pKey, pBuffer)? ippStsNoErr : ippStsSizeErr;
   }
}
