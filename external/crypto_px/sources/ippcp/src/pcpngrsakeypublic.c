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
// Name: ippsRSA_GetSizePublicKey
//
// Purpose: Returns context size (bytes) of RSA public key context
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pSize
//
//    ippStsNotSupportedModeErr  MIN_RSA_SIZE > rsaModulusBitSize
//                               MAX_RSA_SIZE < rsaModulusBitSize
//
//    ippStsBadArgErr            0 >= publicExpBitSize
//                               publicExpBitSize > rsaModulusBitSize
//
//    ippStsNoErr                no error
//
// Parameters:
//    rsaModulusBitSize    bitsize of RSA modulus (bitsize of N)
//    publicExpBitSize     bitsize of public exponent (bitsize of E)
//    pSize                pointer to the size of RSA key context (bytes)
*F*/
static int cpSizeof_RSA_publicKey(int rsaModulusBitSize, int publicExpBitSize)
{
   int pubExpLen = BITS_BNU_CHUNK(publicExpBitSize);
   int modulusLen32 = BITS2WORD32_SIZE(rsaModulusBitSize);
   int montNsize;
   gsMontGetSize(ippBinaryMethod, modulusLen32, &montNsize);

   return sizeof(IppsRSAPublicKeyState)
        + pubExpLen*sizeof(BNU_CHUNK_T)
        + sizeof(BNU_CHUNK_T)-1
        + montNsize
        + (RSA_PUBLIC_KEY_ALIGNMENT-1);
}

IPPFUN(IppStatus, ippsRSA_GetSizePublicKey,(int rsaModulusBitSize, int publicExpBitSize, int* pKeySize))
{
   IPP_BAD_PTR1_RET(pKeySize);
   IPP_BADARG_RET((MIN_RSA_SIZE>rsaModulusBitSize) || (rsaModulusBitSize>MAX_RSA_SIZE), ippStsNotSupportedModeErr);
   IPP_BADARG_RET(!((0<publicExpBitSize) && (publicExpBitSize<=rsaModulusBitSize)), ippStsBadArgErr);

   *pKeySize = cpSizeof_RSA_publicKey(rsaModulusBitSize, publicExpBitSize);
   return ippStsNoErr;
}


/*F*
// Name: ippsRSA_InitPublicKey
//
// Purpose: Init RSA public key context
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pKey
//
//    ippStsNotSupportedModeErr  MIN_RSA_SIZE > rsaModulusBitSize
//                               MAX_RSA_SIZE < rsaModulusBitSize
//
//    ippStsBadArgErr            0 >= publicExpBitSize
//                               publicExpBitSize > rsaModulusBitSize
//
//    ippStsMemAllocErr          keyCtxSize is not enough for operation
//
//    ippStsNoErr                no error
//
// Parameters:
//    rsaModulusBitSize    bitsize of RSA modulus (bitsize of N)
//    publicExpBitSize     bitsize of public exponent (bitsize of E)
//    pKey                 pointer to the key context
//    keyCtxSize           size of memmory accosizted with key comtext
*F*/
IPPFUN(IppStatus, ippsRSA_InitPublicKey,(int rsaModulusBitSize, int publicExpBitSize,
                                         IppsRSAPublicKeyState* pKey, int keyCtxSize))
{
   IPP_BAD_PTR1_RET(pKey);
   pKey = (IppsRSAPublicKeyState*)( IPP_ALIGNED_PTR(pKey, RSA_PUBLIC_KEY_ALIGNMENT) );

   IPP_BADARG_RET((MIN_RSA_SIZE>rsaModulusBitSize) || (rsaModulusBitSize>MAX_RSA_SIZE), ippStsNotSupportedModeErr);
   IPP_BADARG_RET(!((0<publicExpBitSize) && (publicExpBitSize<=rsaModulusBitSize)), ippStsBadArgErr);

   /* test available size of context buffer */
   IPP_BADARG_RET(keyCtxSize<cpSizeof_RSA_publicKey(rsaModulusBitSize, publicExpBitSize), ippStsMemAllocErr);

   RSA_PUB_KEY_ID(pKey) = idCtxRSA_PubKey;
   RSA_PUB_KEY_MAXSIZE_N(pKey) = rsaModulusBitSize;
   RSA_PUB_KEY_MAXSIZE_E(pKey) = publicExpBitSize;
   RSA_PUB_KEY_BITSIZE_N(pKey) = 0;
   RSA_PUB_KEY_BITSIZE_E(pKey) = 0;

   {
      Ipp8u* ptr = (Ipp8u*)pKey;

      int pubExpLen = BITS_BNU_CHUNK(publicExpBitSize);
      int modulusLen32 = BITS2WORD32_SIZE(rsaModulusBitSize);
      int montNsize;
      gsMontGetSize(ippBinaryMethod, modulusLen32, &montNsize);

      /* allocate internal contexts */
      ptr += sizeof(IppsRSAPublicKeyState);

      RSA_PUB_KEY_E(pKey) = (BNU_CHUNK_T*)( IPP_ALIGNED_PTR((ptr), (int)sizeof(BNU_CHUNK_T)) );
      ptr += pubExpLen*sizeof(BNU_CHUNK_T);

      RSA_PUB_KEY_NMONT(pKey) = (IppsMontState*)( IPP_ALIGNED_PTR((ptr), (MONT_ALIGNMENT)) );
      ptr += montNsize;

      ZEXPAND_BNU(RSA_PUB_KEY_E(pKey), 0, pubExpLen);
      gsMontInit(ippBinaryMethod, modulusLen32, RSA_PUB_KEY_NMONT(pKey));

      return ippStsNoErr;
   }
}


/*F*
// Name: ippsRSA_SetPublicKey
//
// Purpose: Set up the RSA public key
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pKey
//                               NULL == pPublicExp
//                               NULL == pKey
//
//    ippStsContextMatchErr     !BN_VALID_ID(pModulus)
//                              !BN_VALID_ID(pPublicExp)
//                              !RSA_PUB_KEY_VALID_ID()
//
//    ippStsOutOfRangeErr        0 >= pModulus
//                               0 >= pPublicExp
//
//    ippStsSizeErr              bitsize(pModulus) exceeds requested value
//                               bitsize(pPublicExp) exceeds requested value
//
//    ippStsNoErr                no error
//
// Parameters:
//    pModulus       pointer to modulus (N)
//    pPublicExp     pointer to public exponent (E)
//    pKey           pointer to the key context
*F*/
IPPFUN(IppStatus, ippsRSA_SetPublicKey,(const IppsBigNumState* pModulus,
                                        const IppsBigNumState* pPublicExp,
                                        IppsRSAPublicKeyState* pKey))
{
   IPP_BAD_PTR1_RET(pKey);
   pKey = (IppsRSAPublicKeyState*)( IPP_ALIGNED_PTR(pKey, RSA_PUBLIC_KEY_ALIGNMENT) );
   IPP_BADARG_RET(!RSA_PUB_KEY_VALID_ID(pKey), ippStsContextMatchErr);

   IPP_BAD_PTR1_RET(pModulus);
   pModulus = (IppsBigNumState*)( IPP_ALIGNED_PTR(pModulus, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pModulus), ippStsContextMatchErr);
   IPP_BADARG_RET(!(0 < cpBN_tst(pModulus)), ippStsOutOfRangeErr);
   IPP_BADARG_RET(BITSIZE_BNU(BN_NUMBER(pModulus), BN_SIZE(pModulus)) > RSA_PUB_KEY_MAXSIZE_N(pKey), ippStsSizeErr);

   IPP_BAD_PTR1_RET(pPublicExp);
   pPublicExp = (IppsBigNumState*)( IPP_ALIGNED_PTR(pPublicExp, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pPublicExp), ippStsContextMatchErr);
   IPP_BADARG_RET(!(0 < cpBN_tst(pPublicExp)), ippStsOutOfRangeErr);
   IPP_BADARG_RET(BITSIZE_BNU(BN_NUMBER(pPublicExp), BN_SIZE(pPublicExp)) > RSA_PUB_KEY_MAXSIZE_E(pKey), ippStsSizeErr);

   {
      RSA_PUB_KEY_BITSIZE_N(pKey) = 0;
      RSA_PUB_KEY_BITSIZE_E(pKey) = 0;

      /* store E */
      ZEXPAND_COPY_BNU(RSA_PUB_KEY_E(pKey), BITS_BNU_CHUNK(RSA_PUB_KEY_MAXSIZE_E(pKey)), BN_NUMBER(pPublicExp), BN_SIZE(pPublicExp));

      /* setup montgomery engine */
      gsMontSet((Ipp32u*)BN_NUMBER(pModulus), BN_SIZE32(pModulus), RSA_PUB_KEY_NMONT(pKey));

      RSA_PUB_KEY_BITSIZE_N(pKey) = cpBN_bitsize(pModulus);
      RSA_PUB_KEY_BITSIZE_E(pKey) = cpBN_bitsize(pPublicExp);

      return ippStsNoErr;
   }
}


/*F*
// Name: ippsRSA_GetPublicKey
//
// Purpose: Extract key component from the key context
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pKey
//
//    ippStsContextMatchErr     !RSA_PUB_KEY_VALID_ID()
//                              !BN_VALID_ID(pModulus)
//                              !BN_VALID_ID(pExp)
//
//    ippStsIncompleteContextErr public key is not set up
//
//    ippStsSizeErr              BN_ROOM(pModulus), BN_ROOM(pExp) is not enough
//
//    ippStsNoErr                no error
//
// Parameters:
//    pModulus    (optional) pointer to the modulus (N)
//    pExp        (optional) pointer to the public exponent (E)
//    pKey        pointer to the key context
*F*/
IPPFUN(IppStatus, ippsRSA_GetPublicKey,(IppsBigNumState* pModulus,
                                        IppsBigNumState* pExp,
                                  const IppsRSAPublicKeyState* pKey))
{
   IPP_BAD_PTR1_RET(pKey);
   pKey = (IppsRSAPublicKeyState*)( IPP_ALIGNED_PTR(pKey, RSA_PUBLIC_KEY_ALIGNMENT) );
   IPP_BADARG_RET(!RSA_PUB_KEY_VALID_ID(pKey), ippStsContextMatchErr);

   if(pModulus) {
      pModulus = (IppsBigNumState*)( IPP_ALIGNED_PTR(pModulus, BN_ALIGNMENT) );
      IPP_BADARG_RET(!BN_VALID_ID(pModulus), ippStsContextMatchErr);
      IPP_BADARG_RET(!RSA_PUB_KEY_IS_SET(pKey), ippStsIncompleteContextErr);
      IPP_BADARG_RET(BN_ROOM(pModulus)<BITS_BNU_CHUNK(RSA_PUB_KEY_BITSIZE_N(pKey)), ippStsSizeErr);

      BN_Set(MNT_MODULUS(RSA_PUB_KEY_NMONT(pKey)),
             MNT_SIZE(RSA_PUB_KEY_NMONT(pKey)),
             pModulus);
   }

   if(pExp) {
      cpSize expLen = BITS_BNU_CHUNK(RSA_PUB_KEY_BITSIZE_E(pKey));
      FIX_BNU(RSA_PUB_KEY_E(pKey), expLen);

      pExp = (IppsBigNumState*)( IPP_ALIGNED_PTR(pExp, BN_ALIGNMENT) );
      IPP_BADARG_RET(!BN_VALID_ID(pExp), ippStsContextMatchErr);
      IPP_BADARG_RET(!RSA_PUB_KEY_IS_SET(pKey), ippStsIncompleteContextErr);
      IPP_BADARG_RET(BN_ROOM(pExp) < expLen, ippStsSizeErr);

      BN_Set(RSA_PUB_KEY_E(pKey), expLen, pExp);
   }

   return ippStsNoErr;
}
