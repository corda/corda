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
// Name: ippsRSA_GetSizePrivateKeyType1
//
// Purpose: Returns context size (bytes) of RSA private key (type1) context
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pSize
//
//    ippStsNotSupportedModeErr  MIN_RSA_SIZE > rsaModulusBitSize
//                               MAX_RSA_SIZE < rsaModulusBitSize
//
//    ippStsBadArgErr            0 >= privateExpBitSize
//                               privateExpBitSize > rsaModulusBitSize
//
//    ippStsNoErr                no error
//
// Parameters:
//    rsaModulusBitSize    bitsize of RSA modulus (bitsize of N)
//    privateExpBitSize    bitsize of private exponent (bitsize of D)
//    pSize                pointer to the size of RSA key context (bytes)
*F*/
static int cpSizeof_RSA_privateKey1(int rsaModulusBitSize, int privateExpBitSize)
{
   int prvExpLen = BITS_BNU_CHUNK(privateExpBitSize);
   int modulusLen32 = BITS2WORD32_SIZE(rsaModulusBitSize);
   int montNsize;
   gsMontGetSize(ippBinaryMethod, modulusLen32, &montNsize);

   return sizeof(IppsRSAPrivateKeyState)
        + prvExpLen*sizeof(BNU_CHUNK_T)
        + sizeof(BNU_CHUNK_T)-1
        + montNsize
        + (RSA_PRIVATE_KEY_ALIGNMENT-1);
}

IPPFUN(IppStatus, ippsRSA_GetSizePrivateKeyType1,(int rsaModulusBitSize, int privateExpBitSize, int* pKeySize))
{
   IPP_BAD_PTR1_RET(pKeySize);
   IPP_BADARG_RET((MIN_RSA_SIZE>rsaModulusBitSize) || (rsaModulusBitSize>MAX_RSA_SIZE), ippStsNotSupportedModeErr);
   IPP_BADARG_RET(!((0<privateExpBitSize) && (privateExpBitSize<=rsaModulusBitSize)), ippStsBadArgErr);

   *pKeySize = cpSizeof_RSA_privateKey1(rsaModulusBitSize, privateExpBitSize);
   return ippStsNoErr;
}


/*F*
// Name: ippsRSA_InitPrivateKeyType1
//
// Purpose: Init RSA private key context
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pKey
//
//    ippStsNotSupportedModeErr  MIN_RSA_SIZE > rsaModulusBitSize
//                               MAX_RSA_SIZE < rsaModulusBitSize
//
//    ippStsBadArgErr            0 >= privateExpBitSize
//                               privateExpBitSize > rsaModulusBitSize
//
//    ippStsMemAllocErr          keyCtxSize is not enough for operation
//
//    ippStsNoErr                no error
//
// Parameters:
//    rsaModulusBitSize    bitsize of RSA modulus (bitsize of N)
//    privateExpBitSize    bitsize of private exponent (bitsize of D)
//    pKey                 pointer to the key context
//    keyCtxSize           size of memmory accosizted with key comtext
*F*/
IPPFUN(IppStatus, ippsRSA_InitPrivateKeyType1,(int rsaModulusBitSize, int privateExpBitSize,
                                               IppsRSAPrivateKeyState* pKey, int keyCtxSize))
{
   IPP_BAD_PTR1_RET(pKey);
   pKey = (IppsRSAPrivateKeyState*)( IPP_ALIGNED_PTR(pKey, RSA_PRIVATE_KEY_ALIGNMENT) );

   IPP_BADARG_RET((MIN_RSA_SIZE>rsaModulusBitSize) || (rsaModulusBitSize>MAX_RSA_SIZE), ippStsNotSupportedModeErr);
   IPP_BADARG_RET(!((0<privateExpBitSize) && (privateExpBitSize<=rsaModulusBitSize)), ippStsBadArgErr);

   /* test available size of context buffer */
   IPP_BADARG_RET(keyCtxSize<cpSizeof_RSA_privateKey1(rsaModulusBitSize,privateExpBitSize), ippStsMemAllocErr);

   RSA_PRV_KEY_ID(pKey) = idCtxRSA_PrvKey1;
   RSA_PRV_KEY_MAXSIZE_N(pKey) = rsaModulusBitSize;
   RSA_PRV_KEY_MAXSIZE_D(pKey) = privateExpBitSize;
   RSA_PRV_KEY_BITSIZE_N(pKey) = 0;
   RSA_PRV_KEY_BITSIZE_D(pKey) = 0;
   RSA_PRV_KEY_BITSIZE_P(pKey) = 0;
   RSA_PRV_KEY_BITSIZE_Q(pKey) = 0;

   RSA_PRV_KEY_DP(pKey) = NULL;
   RSA_PRV_KEY_DQ(pKey) = NULL;
   RSA_PRV_KEY_INVQ(pKey) = NULL;
   RSA_PRV_KEY_PMONT(pKey) = NULL;
   RSA_PRV_KEY_QMONT(pKey) = NULL;

   {
      Ipp8u* ptr = (Ipp8u*)pKey;

      int prvExpLen = BITS_BNU_CHUNK(privateExpBitSize);
      int modulusLen32 = BITS2WORD32_SIZE(rsaModulusBitSize);
      int montNsize;
      gsMontGetSize(ippBinaryMethod, modulusLen32, &montNsize);

      /* allocate internal contexts */
      ptr += sizeof(IppsRSAPrivateKeyState);

      RSA_PRV_KEY_D(pKey) = (BNU_CHUNK_T*)( IPP_ALIGNED_PTR((ptr), (int)sizeof(BNU_CHUNK_T)) );
      ptr += prvExpLen*sizeof(BNU_CHUNK_T);

      RSA_PRV_KEY_NMONT(pKey) = (IppsMontState*)( IPP_ALIGNED_PTR((ptr), (MONT_ALIGNMENT)) );
      ptr += montNsize;

      ZEXPAND_BNU(RSA_PRV_KEY_D(pKey), 0, prvExpLen);
      gsMontInit(ippBinaryMethod, modulusLen32, RSA_PRV_KEY_NMONT(pKey));

      return ippStsNoErr;
   }
}


/*F*
// Name: ippsRSA_SetPrivateKeyType1
//
// Purpose: Set up the RSA private key
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pModulus
//                               NULL == pPrivateExp
//                               NULL == pKey
//
//    ippStsContextMatchErr     !BN_VALID_ID(pModulus)
//                              !BN_VALID_ID(pPrivateExp)
//                              !RSA_PRV_KEY_VALID_ID()
//
//    ippStsOutOfRangeErr        0 >= pModulus
//                               0 >= pPrivateExp
//
//    ippStsSizeErr              bitsize(pModulus) exceeds requested value
//                               bitsize(pPrivateExp) exceeds requested value
//
//    ippStsNoErr                no error
//
// Parameters:
//    pModulus       pointer to modulus (N)
//    pPrivateExp    pointer to public exponent (D)
//    pKey           pointer to the key context
*F*/
IPPFUN(IppStatus, ippsRSA_SetPrivateKeyType1,(const IppsBigNumState* pModulus,
                                              const IppsBigNumState* pPrivateExp,
                                              IppsRSAPrivateKeyState* pKey))
{
   IPP_BAD_PTR1_RET(pKey);
   pKey = (IppsRSAPrivateKeyState*)( IPP_ALIGNED_PTR(pKey, RSA_PRIVATE_KEY_ALIGNMENT) );
   IPP_BADARG_RET(!RSA_PRV_KEY1_VALID_ID(pKey), ippStsContextMatchErr);

   IPP_BAD_PTR1_RET(pModulus);
   pModulus = (IppsBigNumState*)( IPP_ALIGNED_PTR(pModulus, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pModulus), ippStsContextMatchErr);
   IPP_BADARG_RET(!(0 < cpBN_tst(pModulus)), ippStsOutOfRangeErr);
   IPP_BADARG_RET(BITSIZE_BNU(BN_NUMBER(pModulus), BN_SIZE(pModulus)) > RSA_PRV_KEY_MAXSIZE_N(pKey), ippStsSizeErr);

   IPP_BAD_PTR1_RET(pPrivateExp);
   pPrivateExp = (IppsBigNumState*)( IPP_ALIGNED_PTR(pPrivateExp, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pPrivateExp), ippStsContextMatchErr);
   IPP_BADARG_RET(!(0 < cpBN_tst(pPrivateExp)), ippStsOutOfRangeErr);
   IPP_BADARG_RET(BITSIZE_BNU(BN_NUMBER(pPrivateExp), BN_SIZE(pPrivateExp)) > RSA_PRV_KEY_MAXSIZE_D(pKey), ippStsSizeErr);

   {
      /* store D */
      ZEXPAND_COPY_BNU(RSA_PRV_KEY_D(pKey), BITS_BNU_CHUNK(RSA_PRV_KEY_MAXSIZE_D(pKey)), BN_NUMBER(pPrivateExp), BN_SIZE(pPrivateExp));

      /* setup montgomery engine */
      gsMontSet((Ipp32u*)BN_NUMBER(pModulus), BN_SIZE32(pModulus), RSA_PRV_KEY_NMONT(pKey));

      RSA_PRV_KEY_BITSIZE_N(pKey) = cpBN_bitsize(pModulus);
      RSA_PRV_KEY_BITSIZE_D(pKey) = cpBN_bitsize(pPrivateExp);

      return ippStsNoErr;
   }
}


/*F*
// Name: ippsRSA_GetSizePrivateKeyType2
//
// Purpose: Returns context size (bytes) of RSA private key (type2) context
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pSize
//
//    ippStsNotSupportedModeErr  MIN_RSA_SIZE > (factorPbitSize+factorQbitSize)
//                               MAX_RSA_SIZE < (factorPbitSize+factorQbitSize)
//
//    ippStsBadArgErr            0 >= factorPbitSize
//                               0 >= factorQbitSize
//                               factorQbitSize > factorPbitSize
//
//    ippStsNoErr                no error
//
// Parameters:
//    factorPbitSize    bitsize of RSA modulus (bitsize of P)
//    factorPbitSize    bitsize of private exponent (bitsize of Q)
//    pSize             pointer to the size of RSA key context (bytes)
*F*/
static int cpSizeof_RSA_privateKey2(int factorPbitSize, int factorQbitSize)
{
   int factorPlen = BITS_BNU_CHUNK(factorPbitSize);
   int factorQlen = BITS_BNU_CHUNK(factorQbitSize);
   int factorPlen32 = BITS2WORD32_SIZE(factorPbitSize);
   int factorQlen32 = BITS2WORD32_SIZE(factorQbitSize);
   int rsaModulusLen32 = BITS2WORD32_SIZE(factorPbitSize+factorQbitSize);
   int montPsize;
   int montQsize;
   int montNsize;
   gsMontGetSize(ippBinaryMethod, factorPlen32, &montPsize);
   gsMontGetSize(ippBinaryMethod, factorQlen32, &montQsize);
   gsMontGetSize(ippBinaryMethod, rsaModulusLen32, &montNsize);

   return sizeof(IppsRSAPrivateKeyState)
        + factorPlen*sizeof(BNU_CHUNK_T)  /* dp slot */
        + factorQlen*sizeof(BNU_CHUNK_T)  /* dq slot */
        + factorPlen*sizeof(BNU_CHUNK_T)  /* qinv slot */
        + sizeof(BNU_CHUNK_T)-1
        + montPsize
        + montQsize
        + montNsize
        + (RSA_PRIVATE_KEY_ALIGNMENT-1);
}

IPPFUN(IppStatus, ippsRSA_GetSizePrivateKeyType2,(int factorPbitSize, int factorQbitSize, int* pKeySize))
{
   IPP_BAD_PTR1_RET(pKeySize);
   IPP_BADARG_RET((factorPbitSize<=0) || (factorQbitSize<=0), ippStsBadArgErr);
   IPP_BADARG_RET((factorPbitSize < factorQbitSize), ippStsBadArgErr);
   IPP_BADARG_RET((MIN_RSA_SIZE>(factorPbitSize+factorQbitSize) || (factorPbitSize+factorQbitSize)>MAX_RSA_SIZE), ippStsNotSupportedModeErr);

   *pKeySize = cpSizeof_RSA_privateKey2(factorPbitSize, factorQbitSize);
   return ippStsNoErr;
}


/*F*
// Name: ippsRSA_InitPrivateKeyType2
//
// Purpose: Init RSA private key context
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pKey
//
//    ippStsNotSupportedModeErr  MIN_RSA_SIZE > (factorPbitSize+factorQbitSize)
//                               MAX_RSA_SIZE < (factorPbitSize+factorQbitSize)
//
//    ippStsBadArgErr            0 >= factorPbitSize
//                               0 >= factorQbitSize
//                               factorQbitSize > factorPbitSize
//
//    ippStsMemAllocErr          keyCtxSize is not enough for operation
//
//    ippStsNoErr                no error
//
// Parameters:
//    factorPbitSize       bitsize of RSA modulus (bitsize of P)
//    factorQbitSize       bitsize of private exponent (bitsize of Q)
//    pKey                 pointer to the key context
//    keyCtxSize           size of memmory accosizted with key comtext
*F*/
IPPFUN(IppStatus, ippsRSA_InitPrivateKeyType2,(int factorPbitSize, int factorQbitSize,
                                               IppsRSAPrivateKeyState* pKey, int keyCtxSize))
{
   IPP_BAD_PTR1_RET(pKey);
   IPP_BADARG_RET((factorPbitSize<=0) || (factorQbitSize<=0), ippStsBadArgErr);
   IPP_BADARG_RET((factorPbitSize < factorQbitSize), ippStsBadArgErr);
   IPP_BADARG_RET((MIN_RSA_SIZE>(factorPbitSize+factorQbitSize) || (factorPbitSize+factorQbitSize)>MAX_RSA_SIZE), ippStsNotSupportedModeErr);

   /* test available size of context buffer */
   IPP_BADARG_RET(keyCtxSize<cpSizeof_RSA_privateKey2(factorPbitSize,factorQbitSize), ippStsMemAllocErr);

   RSA_PRV_KEY_ID(pKey) = idCtxRSA_PrvKey2;
   RSA_PRV_KEY_MAXSIZE_N(pKey) = 0;
   RSA_PRV_KEY_MAXSIZE_D(pKey) = 0;
   RSA_PRV_KEY_BITSIZE_N(pKey) = 0;
   RSA_PRV_KEY_BITSIZE_D(pKey) = 0;
   RSA_PRV_KEY_BITSIZE_P(pKey) = factorPbitSize;
   RSA_PRV_KEY_BITSIZE_Q(pKey) = factorQbitSize;

   RSA_PRV_KEY_D(pKey) = NULL;

   {
      Ipp8u* ptr = (Ipp8u*)pKey;

      int factorPlen = BITS_BNU_CHUNK(factorPbitSize);
      int factorQlen = BITS_BNU_CHUNK(factorQbitSize);
      int factorPlen32 = BITS2WORD32_SIZE(factorPbitSize);
      int factorQlen32 = BITS2WORD32_SIZE(factorQbitSize);
      int rsaModulusLen32 = BITS2WORD32_SIZE(factorPbitSize+factorQbitSize);
      int montPsize;
      int montQsize;
      int montNsize;
      gsMontGetSize(ippBinaryMethod, factorPlen32, &montPsize);
      gsMontGetSize(ippBinaryMethod, factorQlen32, &montQsize);
      gsMontGetSize(ippBinaryMethod, rsaModulusLen32, &montNsize);

      /* allocate internal contexts */
      ptr += sizeof(IppsRSAPrivateKeyState);

      RSA_PRV_KEY_DP(pKey) = (BNU_CHUNK_T*)( IPP_ALIGNED_PTR((ptr), (int)sizeof(BNU_CHUNK_T)) );
      ptr += factorPlen*sizeof(BNU_CHUNK_T);

      RSA_PRV_KEY_DQ(pKey) = (BNU_CHUNK_T*)(ptr);
      ptr += factorQlen*sizeof(BNU_CHUNK_T);

      RSA_PRV_KEY_INVQ(pKey) = (BNU_CHUNK_T*)(ptr);
      ptr += factorPlen*sizeof(BNU_CHUNK_T);

      RSA_PRV_KEY_PMONT(pKey) = (IppsMontState*)( IPP_ALIGNED_PTR((ptr), (MONT_ALIGNMENT)) );
      ptr += montPsize;

      RSA_PRV_KEY_QMONT(pKey) = (IppsMontState*)( IPP_ALIGNED_PTR((ptr), (MONT_ALIGNMENT)) );
      ptr += montQsize;

      RSA_PRV_KEY_NMONT(pKey) = (IppsMontState*)( IPP_ALIGNED_PTR((ptr), (MONT_ALIGNMENT)) );
      ptr += montNsize;

      ZEXPAND_BNU(RSA_PRV_KEY_DP(pKey), 0, factorPlen);
      ZEXPAND_BNU(RSA_PRV_KEY_DQ(pKey), 0, factorQlen);
      ZEXPAND_BNU(RSA_PRV_KEY_INVQ(pKey), 0, factorPlen);
      gsMontInit(ippBinaryMethod, factorPlen32, RSA_PRV_KEY_PMONT(pKey));
      gsMontInit(ippBinaryMethod, factorQlen32, RSA_PRV_KEY_QMONT(pKey));
      gsMontInit(ippBinaryMethod, rsaModulusLen32, RSA_PRV_KEY_NMONT(pKey));

      return ippStsNoErr;
   }
}


/*F*
// Name: ippsRSA_SetPrivateKeyType2
//
// Purpose: Set up the RSA private key
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pFactorP, NULL == pFactorQ
//                               NULL == pCrtExpP, NULL == pCrtExpQ
//                               NULL == pInverseQ
//                               NULL == pKey
//
//    ippStsContextMatchErr     !BN_VALID_ID(pFactorP), !BN_VALID_ID(pFactorQ)
//                              !BN_VALID_ID(pCrtExpP), !BN_VALID_ID(pCrtExpQ)
//                              !BN_VALID_ID(pInverseQ)
//                              !RSA_PRV_KEY_VALID_ID()
//
//    ippStsOutOfRangeErr        0 >= pFactorP, 0 >= pFactorQ
//                               0 >= pCrtExpP, 0 >= pCrtExpQ
//                               0 >= pInverseQ
//
//    ippStsSizeErr              bitsize(pFactorP) exceeds requested value
//                               bitsize(pFactorQ) exceeds requested value
//                               bitsize(pCrtExpP) > bitsize(pFactorP)
//                               bitsize(pCrtExpQ) > bitsize(pFactorQ)
//                               bitsize(pInverseQ) > bitsize(pFactorP)
//
//    ippStsNoErr                no error
//
// Parameters:
//    pFactorP, pFactorQ   pointer to the RSA modulus (N) prime factors
//    pCrtExpP, pCrtExpQ   pointer to CTR's exponent
//    pInverseQ            1/Q mod P
//    pKey                 pointer to the key context
*F*/
IPPFUN(IppStatus, ippsRSA_SetPrivateKeyType2,(const IppsBigNumState* pFactorP,
                                              const IppsBigNumState* pFactorQ,
                                              const IppsBigNumState* pCrtExpP,
                                              const IppsBigNumState* pCrtExpQ,
                                              const IppsBigNumState* pInverseQ,
                                              IppsRSAPrivateKeyState* pKey))
{
   IPP_BAD_PTR1_RET(pKey);
   pKey = (IppsRSAPrivateKeyState*)( IPP_ALIGNED_PTR(pKey, RSA_PRIVATE_KEY_ALIGNMENT) );
   IPP_BADARG_RET(!RSA_PRV_KEY2_VALID_ID(pKey), ippStsContextMatchErr);

   IPP_BAD_PTR1_RET(pFactorP);
   pFactorP = (IppsBigNumState*)( IPP_ALIGNED_PTR(pFactorP, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pFactorP), ippStsContextMatchErr);
   IPP_BADARG_RET(!(0 < cpBN_tst(pFactorP)), ippStsOutOfRangeErr);
   IPP_BADARG_RET(BITSIZE_BNU(BN_NUMBER(pFactorP), BN_SIZE(pFactorP)) > RSA_PRV_KEY_BITSIZE_P(pKey), ippStsSizeErr);

   IPP_BAD_PTR1_RET(pFactorQ);
   pFactorQ = (IppsBigNumState*)( IPP_ALIGNED_PTR(pFactorQ, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pFactorQ), ippStsContextMatchErr);
   IPP_BADARG_RET(!(0 < cpBN_tst(pFactorQ)), ippStsOutOfRangeErr);
   IPP_BADARG_RET(BITSIZE_BNU(BN_NUMBER(pFactorQ), BN_SIZE(pFactorQ)) > RSA_PRV_KEY_BITSIZE_Q(pKey), ippStsSizeErr);

   /* let P>Q */
   IPP_BADARG_RET(0>=cpBN_cmp(pFactorP,pFactorQ), ippStsBadArgErr);

   IPP_BAD_PTR1_RET(pCrtExpP);
   pCrtExpP = (IppsBigNumState*)( IPP_ALIGNED_PTR(pCrtExpP, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pCrtExpP), ippStsContextMatchErr);
   IPP_BADARG_RET(!(0 < cpBN_tst(pCrtExpP)), ippStsOutOfRangeErr);
   IPP_BADARG_RET(BITSIZE_BNU(BN_NUMBER(pCrtExpP), BN_SIZE(pCrtExpP)) > RSA_PRV_KEY_BITSIZE_P(pKey), ippStsSizeErr);

   IPP_BAD_PTR1_RET(pCrtExpQ);
   pCrtExpQ = (IppsBigNumState*)( IPP_ALIGNED_PTR(pCrtExpQ, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pCrtExpQ), ippStsContextMatchErr);
   IPP_BADARG_RET(!(0 < cpBN_tst(pCrtExpQ)), ippStsOutOfRangeErr);
   IPP_BADARG_RET(BITSIZE_BNU(BN_NUMBER(pCrtExpQ), BN_SIZE(pCrtExpQ)) > RSA_PRV_KEY_BITSIZE_Q(pKey), ippStsSizeErr);

   IPP_BAD_PTR1_RET(pInverseQ);
   pInverseQ = (IppsBigNumState*)( IPP_ALIGNED_PTR(pInverseQ, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pInverseQ), ippStsContextMatchErr);
   IPP_BADARG_RET(!(0 < cpBN_tst(pInverseQ)), ippStsOutOfRangeErr);
   IPP_BADARG_RET(BITSIZE_BNU(BN_NUMBER(pInverseQ), BN_SIZE(pInverseQ)) > RSA_PRV_KEY_BITSIZE_P(pKey), ippStsSizeErr);

   /* set bitsize(N) = 0, so the key contex is not ready */
   RSA_PRV_KEY_BITSIZE_N(pKey) = 0;
   RSA_PRV_KEY_BITSIZE_D(pKey) = 0;

   /* setup montgomery engine P */
   gsMontSet((Ipp32u*)BN_NUMBER(pFactorP), BN_SIZE32(pFactorP), RSA_PRV_KEY_PMONT(pKey));
   /* setup montgomery engine Q */
   gsMontSet((Ipp32u*)BN_NUMBER(pFactorQ), BN_SIZE32(pFactorQ), RSA_PRV_KEY_QMONT(pKey));

   /* actual size of key components */
   RSA_PRV_KEY_BITSIZE_P(pKey) = cpBN_bitsize(pFactorP);
   RSA_PRV_KEY_BITSIZE_Q(pKey) = cpBN_bitsize(pFactorQ);

   /* store CTR's exp dp */
   ZEXPAND_COPY_BNU(RSA_PRV_KEY_DP(pKey), BITS_BNU_CHUNK(RSA_PRV_KEY_BITSIZE_P(pKey)), BN_NUMBER(pCrtExpP), BN_SIZE(pCrtExpP));
   /* store CTR's exp dq */
   ZEXPAND_COPY_BNU(RSA_PRV_KEY_DQ(pKey), BITS_BNU_CHUNK(RSA_PRV_KEY_BITSIZE_Q(pKey)), BN_NUMBER(pCrtExpQ), BN_SIZE(pCrtExpQ));
   /* store mont encoded CTR's coeff qinv */
   {
      IppsMontState* pMontP = RSA_PRV_KEY_PMONT(pKey);
      BNU_CHUNK_T* pTmpProduct = MNT_MODULUS(RSA_PRV_KEY_NMONT(pKey));
      cpMontMul_BNU(RSA_PRV_KEY_INVQ(pKey),
                 BN_NUMBER(pInverseQ), BN_SIZE(pInverseQ),
                 MNT_SQUARE_R(pMontP), MNT_SIZE(pMontP),
                 MNT_MODULUS(pMontP), MNT_SIZE(pMontP), MNT_HELPER(pMontP),
                 pTmpProduct, NULL);
   }

   /* setup montgomery engine N = P*Q */
   {
      BNU_CHUNK_T* pN = MNT_MODULUS(RSA_PRV_KEY_NMONT(pKey));
      cpSize nsN = BITS_BNU_CHUNK(RSA_PRV_KEY_BITSIZE_P(pKey) + RSA_PRV_KEY_BITSIZE_Q(pKey));

      cpMul_BNU_school(pN,
                       BN_NUMBER(pFactorP), BN_SIZE(pFactorP),
                       BN_NUMBER(pFactorQ), BN_SIZE(pFactorQ));

      gsMontSet((Ipp32u*)MNT_MODULUS(RSA_PRV_KEY_NMONT(pKey)), BITS2WORD32_SIZE(RSA_PRV_KEY_BITSIZE_P(pKey)+RSA_PRV_KEY_BITSIZE_Q(pKey)), RSA_PRV_KEY_NMONT(pKey));

      FIX_BNU(pN, nsN);
      RSA_PRV_KEY_BITSIZE_N(pKey) = BITSIZE_BNU(pN, nsN);
   }

   return ippStsNoErr;
}
