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

#if !defined( __IPPCP_H__ ) || defined( _OWN_BLDPCS )
#define __IPPCP_H__


#if defined (_WIN32_WCE) && defined (_M_IX86) && defined (__stdcall)
  #define _IPP_STDCALL_CDECL
  #undef __stdcall
#endif


#ifndef __IPPDEFS_H__
  #include "ippdefs.h"
#endif

#ifndef __IPPCPDEFS_H__
  #include "ippcpdefs.h"
#endif


#ifdef  __cplusplus
extern "C" {
#endif


/* /////////////////////////////////////////////////////////////////////////////
//  Name:       ippcpGetLibVersion
//  Purpose:    getting of the library version
//  Returns:    the structure of information about version of ippCP library
//  Parameters:
//
//  Notes:      not necessary to release the returned structure
*/
IPPAPI( const IppLibraryVersion*, ippcpGetLibVersion, (void) )


/*
// AES
*/
IPPAPI(IppStatus, ippsAESGetSize,(int *pSize))
IPPAPI(IppStatus, ippsAESInit,(const Ipp8u* pKey, int keyLen, IppsAESSpec* pCtx, int ctxSize))

/* AES-CTR */
IPPAPI(IppStatus, ippsAESEncryptCTR,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                     const IppsAESSpec* pCtx,
                                     Ipp8u* pCtrValue, int ctrNumBitSize))
IPPAPI(IppStatus, ippsAESDecryptCTR,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                     const IppsAESSpec* pCtx,
                                     Ipp8u* pCtrValue, int ctrNumBitSize))

/* AES-GCM */
IPPAPI(IppStatus, ippsAES_GCMGetSize,(int * pSize))
IPPAPI(IppStatus, ippsAES_GCMInit,(const Ipp8u* pKey, int keyLen, IppsAES_GCMState* pState, int ctxSize))

IPPAPI(IppStatus, ippsAES_GCMReset,(IppsAES_GCMState* pState))
IPPAPI(IppStatus, ippsAES_GCMProcessIV,(const Ipp8u* pIV, int ivLen,
                                        IppsAES_GCMState* pState))
IPPAPI(IppStatus, ippsAES_GCMProcessAAD,(const Ipp8u* pAAD, int ivAAD,
                                        IppsAES_GCMState* pState))
IPPAPI(IppStatus, ippsAES_GCMStart,(const Ipp8u* pIV, int ivLen,
                                    const Ipp8u* pAAD, int aadLen,
                                    IppsAES_GCMState* pState))
IPPAPI(IppStatus, ippsAES_GCMEncrypt,(const Ipp8u* pSrc, Ipp8u* pDst, int len, IppsAES_GCMState* pState))
IPPAPI(IppStatus, ippsAES_GCMDecrypt,(const Ipp8u* pSrc, Ipp8u* pDst, int len, IppsAES_GCMState* pState))
IPPAPI(IppStatus, ippsAES_GCMGetTag,(Ipp8u* pDstTag, int tagLen, const IppsAES_GCMState* pState))

/* AES-CMAC */
IPPAPI(IppStatus, ippsAES_CMACGetSize,(int* pSize))
IPPAPI(IppStatus, ippsAES_CMACInit,(const Ipp8u* pKey, int keyLen, IppsAES_CMACState* pState, int ctxSize))

IPPAPI(IppStatus, ippsAES_CMACUpdate,(const Ipp8u* pSrc, int len, IppsAES_CMACState* pState))
IPPAPI(IppStatus, ippsAES_CMACFinal,(Ipp8u* pMD, int mdLen, IppsAES_CMACState* pState))
IPPAPI(IppStatus, ippsAES_CMACGetTag,(Ipp8u* pMD, int mdLen, const IppsAES_CMACState* pState))

/*
// hash
*/
IPPAPI(IppStatus, ippsHashGetSize,(int* pSize))
IPPAPI(IppStatus, ippsHashInit,(IppsHashState* pCtx, IppHashAlgId hashAlg))

IPPAPI(IppStatus, ippsHashUpdate,(const Ipp8u* pSrc, int len, IppsHashState* pCtx))
IPPAPI(IppStatus, ippsHashGetTag,(Ipp8u* pMD, int tagLen, const IppsHashState* pCtx))
IPPAPI(IppStatus, ippsHashFinal,(Ipp8u* pMD, IppsHashState* pCtx))
IPPAPI(IppStatus, ippsHashMessage,(const Ipp8u* pMsg, int len, Ipp8u* pMD, IppHashAlgId hashAlg))

IPPAPI(IppStatus, ippsMGF,(const Ipp8u* pSeed, int seedLen, Ipp8u* pMask, int maskLen, IppHashAlgId hashAlg))

IPPAPI(IppStatus, ippsHMAC_GetSize,(int* pSize))
IPPAPI(IppStatus, ippsHMAC_Init,(const Ipp8u* pKey, int keyLen, IppsHMACState* pCtx, IppHashAlgId hashAlg))

IPPAPI(IppStatus, ippsHMAC_Update,(const Ipp8u* pSrc, int len, IppsHMACState* pCtx))
IPPAPI(IppStatus, ippsHMAC_Final,(Ipp8u* pMD, int mdLen, IppsHMACState* pCtx))
IPPAPI(IppStatus, ippsHMAC_GetTag,(Ipp8u* pMD, int mdLen, const IppsHMACState* pCtx))
IPPAPI(IppStatus, ippsHMAC_Message,(const Ipp8u* pMsg, int msgLen,
                                    const Ipp8u* pKey, int keyLen,
                                    Ipp8u* pMD, int mdLen,
                                    IppHashAlgId hashAlg))

/*
// Big Number Integer Arithmetic
*/
IPPAPI(IppStatus, ippsBigNumGetSize,(int length, int* pSize))
IPPAPI(IppStatus, ippsBigNumInit,(int length, IppsBigNumState* pBN))

IPPAPI(IppStatus, ippsSet_BN,(IppsBigNumSGN sgn,
                              int length, const Ipp32u* pData,
                              IppsBigNumState* pBN))
IPPAPI(IppStatus, ippsRef_BN,(IppsBigNumSGN* pSgn, int* bitSize, Ipp32u** const ppData,
                              const IppsBigNumState* pBN))

IPPAPI(IppStatus, ippsAdd_BN,   (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR))
IPPAPI(IppStatus, ippsSub_BN,   (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR))
IPPAPI(IppStatus, ippsMod_BN,   (IppsBigNumState* pA, IppsBigNumState* pM, IppsBigNumState* pR))
IPPAPI(IppStatus, ippsModInv_BN,(IppsBigNumState* pA, IppsBigNumState* pM, IppsBigNumState* pInv))
IPPAPI(IppStatus, ippsDiv_BN,   (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pQ, IppsBigNumState* pR))
IPPAPI(IppStatus, ippsCmpZero_BN,(const IppsBigNumState* pBN, Ipp32u* pResult))
IPPAPI(IppStatus, ippsCmp_BN,(const IppsBigNumState* pA, const IppsBigNumState* pB, Ipp32u* pResult))
IPPAPI(IppStatus, ippsMul_BN,   (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR))
IPPAPI(IppStatus, ippsGcd_BN,   (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pGCD))

IPPAPI(IppStatus, ippsSetOctString_BN,(const Ipp8u* pStr, int strLen, IppsBigNumState* pBN))
IPPAPI(IppStatus, ippsGetOctString_BN,(Ipp8u* pStr, int strLen, const IppsBigNumState* pBN))

/*
// Montgomery Operations
*/
IPPAPI(IppStatus, ippsMontGetSize,(IppsExpMethod method, int length, int* pSize))
IPPAPI(IppStatus, ippsMontInit,(IppsExpMethod method, int length, IppsMontState* pCtx))
IPPAPI(IppStatus, ippsMontSet,(const Ipp32u* pModulo, int size, IppsMontState* pCtx))
IPPAPI(IppStatus, ippsMontMul, (const IppsBigNumState* pA, const IppsBigNumState* pB, IppsMontState* m, IppsBigNumState* pR))

/*
// PRNG
*/
IPPAPI(IppStatus, ippsPRNGGetSize,(int* pSize))
IPPAPI(IppStatus, ippsPRNGInit,   (int seedBits, IppsPRNGState* pCtx))
IPPAPI(IppStatus, ippsPRNGen,     (Ipp32u* pRand, int nBits, void* pCtx))

/* 
// Prime Number Generation
*/
IPPAPI(IppStatus, ippsPrimeGetSize,(int nMaxBits, int* pSize))
IPPAPI(IppStatus, ippsPrimeInit,   (int nMaxBits, IppsPrimeState* pCtx))


/*
// RSA
*/
IPPAPI(IppStatus, ippsRSA_GetSizePublicKey,(int rsaModulusBitSize, int pubicExpBitSize, int* pKeySize))
IPPAPI(IppStatus, ippsRSA_InitPublicKey,(int rsaModulusBitSize, int publicExpBitSize,
                                         IppsRSAPublicKeyState* pKey, int keyCtxSize))
IPPAPI(IppStatus, ippsRSA_SetPublicKey,(const IppsBigNumState* pModulus,
                                        const IppsBigNumState* pPublicExp,
                                        IppsRSAPublicKeyState* pKey))
IPPAPI(IppStatus, ippsRSA_GetPublicKey,(IppsBigNumState* pModulus,
                                        IppsBigNumState* pPublicExp,
                                  const IppsRSAPublicKeyState* pKey))

IPPAPI(IppStatus, ippsRSA_GetSizePrivateKeyType1,(int rsaModulusBitSize, int privateExpBitSize, int* pKeySize))
IPPAPI(IppStatus, ippsRSA_InitPrivateKeyType1,(int rsaModulusBitSize, int privateExpBitSize,
                                               IppsRSAPrivateKeyState* pKey, int keyCtxSize))
IPPAPI(IppStatus, ippsRSA_SetPrivateKeyType1,(const IppsBigNumState* pModulus,
                                              const IppsBigNumState* pPrivateExp,
                                              IppsRSAPrivateKeyState* pKey))

IPPAPI(IppStatus, ippsRSA_GetSizePrivateKeyType2,(int factorPbitSize, int factorQbitSize, int* pKeySize))
IPPAPI(IppStatus, ippsRSA_InitPrivateKeyType2,(int factorPbitSize, int factorQbitSize,
                                               IppsRSAPrivateKeyState* pKey, int keyCtxSize))
IPPAPI(IppStatus, ippsRSA_SetPrivateKeyType2,(const IppsBigNumState* pFactorP,
                                              const IppsBigNumState* pFactorQ,
                                              const IppsBigNumState* pCrtExpP,
                                              const IppsBigNumState* pCrtExpQ,
                                              const IppsBigNumState* pInverseQ,
                                              IppsRSAPrivateKeyState* pKey))

IPPAPI(IppStatus, ippsRSA_GetBufferSizePublicKey,(int* pBufferSize, const IppsRSAPublicKeyState* pKey))
IPPAPI(IppStatus, ippsRSA_GetBufferSizePrivateKey,(int* pBufferSize, const IppsRSAPrivateKeyState* pKey))

IPPAPI(IppStatus, ippsRSA_Encrypt,(const IppsBigNumState* pPtxt,
                                         IppsBigNumState* pCtxt,
                                   const IppsRSAPublicKeyState* pKey,
                                         Ipp8u* pScratchBuffer))
IPPAPI(IppStatus, ippsRSA_Decrypt,(const IppsBigNumState* pCtxt,
                                         IppsBigNumState* pPtxt,
                                   const IppsRSAPrivateKeyState* pKey,
                                         Ipp8u* pScratchBuffer))

IPPAPI(IppStatus, ippsRSA_ValidateKeys,(int* pResult,
                                 const IppsRSAPublicKeyState* pPublicKey,
                                 const IppsRSAPrivateKeyState* pPrivateKeyType2,
                                 const IppsRSAPrivateKeyState* pPrivateKeyType1,
                                 Ipp8u* pScratchBuffer,
                                 int nTrials,
                                 IppsPrimeState* pPrimeGen,
                                 IppBitSupplier rndFunc, void* pRndParam))

/* encryption scheme: RSAES-OAEP */
IPPAPI(IppStatus, ippsRSAEncrypt_OAEP,(const Ipp8u* pSrc, int srcLen,
                                       const Ipp8u* pLabel, int labLen, 
                                       const Ipp8u* pSeed,
                                             Ipp8u* pDst,
                                       const IppsRSAPublicKeyState* pKey,
                                             IppHashAlgId hashAlg,
                                             Ipp8u* pBuffer))
IPPAPI(IppStatus, ippsRSA_OAEPEncrypt_SHA256,(const Ipp8u* pSrc, int srcLen,
                                              const Ipp8u* pLabel, int labLen,
                                              const Ipp8u* pSeed,
                                              Ipp8u* pDst,
                                              const IppsRSAPublicKeyState* pKey,
                                              Ipp8u* pBuffer))

/* signature scheme : RSA-SSA-PKCS1-v1_5 */
IPPAPI(IppStatus, ippsRSASign_PKCS1v15,(const Ipp8u* pMsg, int msgLen,
                                              Ipp8u* pSign,
                                        const IppsRSAPrivateKeyState* pPrvKey,
                                        const IppsRSAPublicKeyState*  pPubKey,
                                              IppHashAlgId hashAlg,
                                              Ipp8u* pBuffer))


IPPAPI(IppStatus, ippsRSAVerify_PKCS1v15,(const Ipp8u* pMsg, int msgLen,
                                          const Ipp8u* pSign, int* pIsValid,
                                          const IppsRSAPublicKeyState* pKey,
                                                IppHashAlgId hashAlg,
                                                Ipp8u* pBuffer))


/*
// EC Cryptography
*/
IPPAPI(IppStatus, ippsECCPGetSize,(int feBitSize, int* pSize))
IPPAPI(IppStatus, ippsECCPInit,(int feBitSize, IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPSet,(const IppsBigNumState* pPrime,
                               const IppsBigNumState* pA, const IppsBigNumState* pB,
                               const IppsBigNumState* pGX,const IppsBigNumState* pGY,const IppsBigNumState* pOrder,
                               int cofactor,
                               IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPSetStd,(IppECCType flag, IppsECCPState* pECC))


IPPAPI(IppStatus, ippsECCPPointGetSize,(int feBitSize, int* pSize))
IPPAPI(IppStatus, ippsECCPPointInit,(int feBitSize, IppsECCPPointState* pPoint))

IPPAPI(IppStatus, ippsECCPSetPoint,(const IppsBigNumState* pX, const IppsBigNumState* pY,
                                    IppsECCPPointState* pPoint, IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPSetPointAtInfinity,(IppsECCPPointState* pPoint, IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPGetPoint,(IppsBigNumState* pX, IppsBigNumState* pY,
                                    const IppsECCPPointState* pPoint, IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPCheckPoint,(const IppsECCPPointState* pP,
                                      IppECResult* pResult, IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPComparePoint,(const IppsECCPPointState* pP, const IppsECCPPointState* pQ,
                                        IppECResult* pResult, IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPNegativePoint,(const IppsECCPPointState* pP,
                                         IppsECCPPointState* pR, IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPAddPoint,(const IppsECCPPointState* pP, const IppsECCPPointState* pQ,
                                    IppsECCPPointState* pR, IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPMulPointScalar,(const IppsECCPPointState* pP, const IppsBigNumState* pK,
                                          IppsECCPPointState* pR, IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPGenKeyPair,(IppsBigNumState* pPrivate, IppsECCPPointState* pPublic,
                                      IppsECCPState* pECC,
                                      IppBitSupplier rndFunc, void* pRndParam))
IPPAPI(IppStatus, ippsECCPPublicKey,(const IppsBigNumState* pPrivate,
                                     IppsECCPPointState* pPublic,
                                     IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPSetKeyPair,(const IppsBigNumState* pPrivate, const IppsECCPPointState* pPublic,
                                      IppBool regular,
                                      IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPSharedSecretDH,(const IppsBigNumState* pPrivateA,
                                          const IppsECCPPointState* pPublicB,
                                          IppsBigNumState* pShare,
                                          IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPSignDSA,(const IppsBigNumState* pMsgDigest,
                        const IppsBigNumState* pPrivate,
                        IppsBigNumState* pSignX, IppsBigNumState* pSignY,
                        IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPVerifyDSA,(const IppsBigNumState* pMsgDigest,
                        const IppsBigNumState* pSignX, const IppsBigNumState* pSignY,
                        IppECResult* pResult,
                        IppsECCPState* pECC))

#ifdef  __cplusplus
}
#endif

#if defined (_IPP_STDCALL_CDECL)
  #undef  _IPP_STDCALL_CDECL
  #define __stdcall __cdecl
#endif

#endif /* __IPPCP_H__ */
