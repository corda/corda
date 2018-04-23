/*############################################################################
  # Copyright 2016 Intel Corporation
  #
  # Licensed under the Apache License, Version 2.0 (the "License");
  # you may not use this file except in compliance with the License.
  # You may obtain a copy of the License at
  #
  #     http://www.apache.org/licenses/LICENSE-2.0
  #
  # Unless required by applicable law or agreed to in writing, software
  # distributed under the License is distributed on an "AS IS" BASIS,
  # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  # See the License for the specific language governing permissions and
  # limitations under the License.
  ############################################################################*/

/* 
//              Intel(R) Integrated Performance Primitives
//              Cryptographic Primitives (ippCP)
// 
// 
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

#if !defined( _IPP_NO_DEFAULT_LIB )
  #if defined( _IPP_SEQUENTIAL_DYNAMIC )
    #pragma comment( lib, __FILE__ "/../../lib/" _INTEL_PLATFORM "ippcp" )
    #pragma comment( lib, __FILE__ "/../../lib/" _INTEL_PLATFORM "ippcore" )
  #elif defined( _IPP_SEQUENTIAL_STATIC )
    #pragma comment( lib, __FILE__ "/../../lib/" _INTEL_PLATFORM "ippcpmt" )
    #pragma comment( lib, __FILE__ "/../../lib/" _INTEL_PLATFORM "ippcoremt" )
  #elif defined( _IPP_PARALLEL_DYNAMIC )
    #pragma comment( lib, __FILE__ "/../../lib/" _INTEL_PLATFORM "threaded/ippcp" )
    #pragma comment( lib, __FILE__ "/../../lib/" _INTEL_PLATFORM "threaded/ippcore" )
  #elif defined( _IPP_PARALLEL_STATIC )
    #pragma comment( lib, __FILE__ "/../../lib/" _INTEL_PLATFORM "threaded/ippcpmt" )
    #pragma comment( lib, __FILE__ "/../../lib/" _INTEL_PLATFORM "threaded/ippcoremt" )
  #endif
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
// =========================================================
// Symmetric Ciphers
// =========================================================
*/

/* TDES */
IPPAPI(IppStatus, ippsDESGetSize,(int *size))
IPPAPI(IppStatus, ippsDESInit,(const Ipp8u* pKey, IppsDESSpec* pCtx))

IPPAPI(IppStatus, ippsDESPack,(const IppsDESSpec* pCtx, Ipp8u* pBuffer))
IPPAPI(IppStatus, ippsDESUnpack,(const Ipp8u* pBuffer, IppsDESSpec* pCtx))

IPPAPI(IppStatus, ippsTDESEncryptECB,(const Ipp8u* pSrc, Ipp8u* pDst, int length,
                                      const IppsDESSpec* pCtx1, const IppsDESSpec* pCtx2, const IppsDESSpec* pCtx3,
                                      IppsCPPadding padding))
IPPAPI(IppStatus, ippsTDESDecryptECB,(const Ipp8u* pSrc, Ipp8u* pDst, int length,
                                      const IppsDESSpec* pCtx1, const IppsDESSpec* pCtx2, const IppsDESSpec* pCtx3,
                                      IppsCPPadding padding))

IPPAPI(IppStatus, ippsTDESEncryptCBC,(const Ipp8u* pSrc, Ipp8u* pDst, int length,
                                      const IppsDESSpec* pCtx1, const IppsDESSpec* pCtx2, const IppsDESSpec* pCtx3,
                                      const Ipp8u* pIV,
                                      IppsCPPadding padding))
IPPAPI(IppStatus, ippsTDESDecryptCBC,(const Ipp8u* pSrc, Ipp8u* pDst, int length,
                                      const IppsDESSpec* pCtx1, const IppsDESSpec* pCtx2, const IppsDESSpec* pCtx3,
                                      const Ipp8u* pIV,
                                      IppsCPPadding padding))

IPPAPI(IppStatus, ippsTDESEncryptCFB,(const Ipp8u* pSrc, Ipp8u* pDst, int length, int cfbBlkSize,
                                      const IppsDESSpec* pCtx1, const IppsDESSpec* pCtx2, const IppsDESSpec* pCtx3,
                                      const Ipp8u* pIV,
                                      IppsCPPadding padding))
IPPAPI(IppStatus, ippsTDESDecryptCFB,(const Ipp8u* pSrc, Ipp8u* pDst, int length, int cfbBlkSize,
                                      const IppsDESSpec* pCtx1, const IppsDESSpec* pCtx2, const IppsDESSpec* pCtx3,
                                      const Ipp8u* pIV,
                                      IppsCPPadding padding))

IPPAPI(IppStatus, ippsTDESEncryptOFB,(const Ipp8u* pSrc, Ipp8u* pDst, int len, int ofbBlkSize,
                                     const IppsDESSpec* pCtx1,
                                     const IppsDESSpec* pCtx2,
                                     const IppsDESSpec* pCtx3,
                                     Ipp8u* pIV))
IPPAPI(IppStatus, ippsTDESDecryptOFB,(const Ipp8u* pSrc, Ipp8u* pDst, int len, int ofbBlkSize,
                                     const IppsDESSpec* pCtx1,
                                     const IppsDESSpec* pCtx2,
                                     const IppsDESSpec* pCtx3,
                                     Ipp8u* pIV))

IPPAPI(IppStatus, ippsTDESEncryptCTR,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                      const IppsDESSpec* pCtx1,
                                      const IppsDESSpec* pCtx2,
                                      const IppsDESSpec* pCtx3,
                                      Ipp8u* pCtrValue, int ctrNumBitSize))
IPPAPI(IppStatus, ippsTDESDecryptCTR,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                      const IppsDESSpec* pCtx1,
                                      const IppsDESSpec* pCtx2,
                                      const IppsDESSpec* pCtx3,
                                      Ipp8u* pCtrValue, int ctrNumBitSize))

/* AES */
IPPAPI(IppStatus, ippsAESGetSize,(int *pSize))
IPPAPI(IppStatus, ippsAESInit,(const Ipp8u* pKey, int keyLen, IppsAESSpec* pCtx, int ctxSize))
IPPAPI(IppStatus, ippsAESSetKey,(const Ipp8u* pKey, int keyLen, IppsAESSpec* pCtx))

IPPAPI(IppStatus, ippsAESPack,(const IppsAESSpec* pCtx, Ipp8u* pBuffer, int buffSize))
IPPAPI(IppStatus, ippsAESUnpack,(const Ipp8u* pBuffer, IppsAESSpec* pCtx, int ctxSize))

IPPAPI(IppStatus, ippsAESEncryptECB,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                     const IppsAESSpec* pCtx))
IPPAPI(IppStatus, ippsAESDecryptECB,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                     const IppsAESSpec* pCtx))

IPPAPI(IppStatus, ippsAESEncryptCBC,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                     const IppsAESSpec* pCtx,
                                     const Ipp8u* pIV))
IPPAPI(IppStatus, ippsAESDecryptCBC,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                     const IppsAESSpec* pCtx,
                                     const Ipp8u* pIV))

IPPAPI(IppStatus, ippsAESEncryptCFB,(const Ipp8u* pSrc, Ipp8u* pDst, int len, int cfbBlkSize,
                                     const IppsAESSpec* pCtx,
                                     const Ipp8u* pIV))
IPPAPI(IppStatus, ippsAESDecryptCFB,(const Ipp8u* pSrc, Ipp8u* pDst, int len, int cfbBlkSize,
                                     const IppsAESSpec* pCtx,
                                     const Ipp8u* pIV))

IPPAPI(IppStatus, ippsAESEncryptOFB,(const Ipp8u* pSrc, Ipp8u* pDst, int len, int ofbBlkSize,
                                     const IppsAESSpec* pCtx,
                                     Ipp8u* pIV))
IPPAPI(IppStatus, ippsAESDecryptOFB,(const Ipp8u* pSrc, Ipp8u* pDst, int len, int ofbBlkSize,
                                     const IppsAESSpec* pCtx,
                                     Ipp8u* pIV))

IPPAPI(IppStatus, ippsAESEncryptCTR,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                     const IppsAESSpec* pCtx,
                                     Ipp8u* pCtrValue, int ctrNumBitSize))
IPPAPI(IppStatus, ippsAESDecryptCTR,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                     const IppsAESSpec* pCtx,
                                     Ipp8u* pCtrValue, int ctrNumBitSize))

/* SMS4 */
IPPAPI(IppStatus, ippsSMS4GetSize,(int *pSize))
IPPAPI(IppStatus, ippsSMS4Init,(const Ipp8u* pKey, int keyLen, IppsSMS4Spec* pCtx, int ctxSize))
IPPAPI(IppStatus, ippsSMS4SetKey,(const Ipp8u* pKey, int keyLen, IppsSMS4Spec* pCtx))

IPPAPI(IppStatus, ippsSMS4EncryptECB,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                      const IppsSMS4Spec* pCtx))
IPPAPI(IppStatus, ippsSMS4DecryptECB,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                      const IppsSMS4Spec* pCtx))

IPPAPI(IppStatus, ippsSMS4EncryptCBC,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                      const IppsSMS4Spec* pCtx,
                                      const Ipp8u* pIV))
IPPAPI(IppStatus, ippsSMS4DecryptCBC,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                      const IppsSMS4Spec* pCtx,
                                      const Ipp8u* pIV))

IPPAPI(IppStatus, ippsSMS4EncryptCFB,(const Ipp8u* pSrc, Ipp8u* pDst, int len, int cfbBlkSize,
                                      const IppsSMS4Spec* pCtx,
                                      const Ipp8u* pIV))
IPPAPI(IppStatus, ippsSMS4DecryptCFB,(const Ipp8u* pSrc, Ipp8u* pDst, int len, int cfbBlkSize,
                                      const IppsSMS4Spec* pCtx,
                                      const Ipp8u* pIV))

IPPAPI(IppStatus, ippsSMS4EncryptOFB,(const Ipp8u* pSrc, Ipp8u* pDst, int len, int ofbBlkSize,
                                      const IppsSMS4Spec* pCtx,
                                      Ipp8u* pIV))
IPPAPI(IppStatus, ippsSMS4DecryptOFB,(const Ipp8u* pSrc, Ipp8u* pDst, int len, int ofbBlkSize,
                                      const IppsSMS4Spec* pCtx,
                                      Ipp8u* pIV))

IPPAPI(IppStatus, ippsSMS4EncryptCTR,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                      const IppsSMS4Spec* pCtx,
                                      Ipp8u* pCtrValue, int ctrNumBitSize))
IPPAPI(IppStatus, ippsSMS4DecryptCTR,(const Ipp8u* pSrc, Ipp8u* pDst, int len,
                                      const IppsSMS4Spec* pCtx,
                                      Ipp8u* pCtrValue, int ctrNumBitSize))


/*
// =========================================================
// AES based  authentication & confidence Primitives
// =========================================================
*/

/*
// AES-CCM
*/
IPPAPI(IppStatus, ippsAES_CCMGetSize,(int* pSize))
IPPAPI(IppStatus, ippsAES_CCMInit,(const Ipp8u* pKey, int keyLen, IppsAES_CCMState* pCtx, int ctxSize))

IPPAPI(IppStatus, ippsAES_CCMMessageLen,(Ipp64u msgLen, IppsAES_CCMState* pCtx))
IPPAPI(IppStatus, ippsAES_CCMTagLen,(int tagLen, IppsAES_CCMState* pCtx))

IPPAPI(IppStatus, ippsAES_CCMStart,(const Ipp8u* pIV, int ivLen, const Ipp8u* pAD, int adLen, IppsAES_CCMState* pCtx))
IPPAPI(IppStatus, ippsAES_CCMEncrypt,(const Ipp8u* pSrc, Ipp8u* pDst, int len, IppsAES_CCMState* pCtx))
IPPAPI(IppStatus, ippsAES_CCMDecrypt,(const Ipp8u* pSrc, Ipp8u* pDst, int len, IppsAES_CCMState* pCtx))
IPPAPI(IppStatus, ippsAES_CCMGetTag,(Ipp8u* pTag, int tagLen, const IppsAES_CCMState* pCtx))

/*
// AES-GCM
*/
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

/*
// AES-CMAC
*/
IPPAPI(IppStatus, ippsAES_CMACGetSize,(int* pSize))
IPPAPI(IppStatus, ippsAES_CMACInit,(const Ipp8u* pKey, int keyLen, IppsAES_CMACState* pState, int ctxSize))

IPPAPI(IppStatus, ippsAES_CMACUpdate,(const Ipp8u* pSrc, int len, IppsAES_CMACState* pState))
IPPAPI(IppStatus, ippsAES_CMACFinal,(Ipp8u* pMD, int mdLen, IppsAES_CMACState* pState))
IPPAPI(IppStatus, ippsAES_CMACGetTag,(Ipp8u* pMD, int mdLen, const IppsAES_CMACState* pState))

/*
// =========================================================
// RC4 Stream Ciphers
// =========================================================
*/
IPPAPI(IppStatus, ippsARCFourCheckKey, (const Ipp8u *pKey, int keyLen, IppBool* pIsWeak))

IPPAPI(IppStatus, ippsARCFourGetSize, (int* pSize))
IPPAPI(IppStatus, ippsARCFourInit, (const Ipp8u *pKey, int keyLen, IppsARCFourState *pCtx))
IPPAPI(IppStatus, ippsARCFourReset, (IppsARCFourState* pCtx))

IPPAPI(IppStatus, ippsARCFourPack,(const IppsARCFourState* pCtx, Ipp8u* pBuffer))
IPPAPI(IppStatus, ippsARCFourUnpack,(const Ipp8u* pBuffer, IppsARCFourState* pCtx))

IPPAPI(IppStatus, ippsARCFourEncrypt, (const Ipp8u *pSrc, Ipp8u *pDst, int length, IppsARCFourState *pCtx))
IPPAPI(IppStatus, ippsARCFourDecrypt, (const Ipp8u *pSrc, Ipp8u *pDst, int length, IppsARCFourState *pCtx))


/*
// =========================================================
// One-Way Hash Functions
// =========================================================
*/
/* SHA1 Hash Primitives */
IPPAPI(IppStatus, ippsSHA1GetSize,(int* pSize))
IPPAPI(IppStatus, ippsSHA1Init,(IppsSHA1State* pCtx))
IPPAPI(IppStatus, ippsSHA1Duplicate,(const IppsSHA1State* pSrcCtx, IppsSHA1State* pDstCtx))

IPPAPI(IppStatus, ippsSHA1Pack,(const IppsSHA1State* pCtx, Ipp8u* pBuffer))
IPPAPI(IppStatus, ippsSHA1Unpack,(const Ipp8u* pBuffer, IppsSHA1State* pCtx))

IPPAPI(IppStatus, ippsSHA1Update,(const Ipp8u* pSrc, int len, IppsSHA1State* pCtx))
IPPAPI(IppStatus, ippsSHA1GetTag,(Ipp8u* pTag, Ipp32u tagLen, const IppsSHA1State* pCtx))
IPPAPI(IppStatus, ippsSHA1Final,(Ipp8u* pMD, IppsSHA1State* pCtx))
IPPAPI(IppStatus, ippsSHA1MessageDigest,(const Ipp8u* pMsg, int len, Ipp8u* pMD))

/* SHA224 Hash Primitives */
IPPAPI(IppStatus, ippsSHA224GetSize,(int* pSize))
IPPAPI(IppStatus, ippsSHA224Init,(IppsSHA224State* pCtx))
IPPAPI(IppStatus, ippsSHA224Duplicate,(const IppsSHA224State* pSrcCtx, IppsSHA224State* pDstCtx))

IPPAPI(IppStatus, ippsSHA224Pack,(const IppsSHA224State* pCtx, Ipp8u* pBuffer))
IPPAPI(IppStatus, ippsSHA224Unpack,(const Ipp8u* pBuffer, IppsSHA224State* pCtx))

IPPAPI(IppStatus, ippsSHA224Update,(const Ipp8u* pSrc, int len, IppsSHA224State* pCtx))
IPPAPI(IppStatus, ippsSHA224GetTag,(Ipp8u* pTag, Ipp32u tagLen, const IppsSHA224State* pCtx))
IPPAPI(IppStatus, ippsSHA224Final,(Ipp8u* pMD, IppsSHA224State* pCtx))
IPPAPI(IppStatus, ippsSHA224MessageDigest,(const Ipp8u* pMsg, int len, Ipp8u* pMD))

/* SHA256 Hash Primitives */
IPPAPI(IppStatus, ippsSHA256GetSize,(int* pSize))
IPPAPI(IppStatus, ippsSHA256Init,(IppsSHA256State* pCtx))
IPPAPI(IppStatus, ippsSHA256Duplicate,(const IppsSHA256State* pSrcCtx, IppsSHA256State* pDstCtx))

IPPAPI(IppStatus, ippsSHA256Pack,(const IppsSHA256State* pCtx, Ipp8u* pBuffer))
IPPAPI(IppStatus, ippsSHA256Unpack,(const Ipp8u* pBuffer, IppsSHA256State* pCtx))

IPPAPI(IppStatus, ippsSHA256Update,(const Ipp8u* pSrc, int len, IppsSHA256State* pCtx))
IPPAPI(IppStatus, ippsSHA256GetTag,(Ipp8u* pTag, Ipp32u tagLen, const IppsSHA256State* pCtx))
IPPAPI(IppStatus, ippsSHA256Final,(Ipp8u* pMD, IppsSHA256State* pCtx))
IPPAPI(IppStatus, ippsSHA256MessageDigest,(const Ipp8u* pMsg, int len, Ipp8u* pMD))

/* SHA384 Hash Primitives */
IPPAPI(IppStatus, ippsSHA384GetSize,(int* pSize))
IPPAPI(IppStatus, ippsSHA384Init,(IppsSHA384State* pCtx))
IPPAPI(IppStatus, ippsSHA384Duplicate,(const IppsSHA384State* pSrcCtx, IppsSHA384State* pDstCtx))

IPPAPI(IppStatus, ippsSHA384Pack,(const IppsSHA384State* pCtx, Ipp8u* pBuffer))
IPPAPI(IppStatus, ippsSHA384Unpack,(const Ipp8u* pBuffer, IppsSHA384State* pCtx))

IPPAPI(IppStatus, ippsSHA384Update,(const Ipp8u* pSrc, int len, IppsSHA384State* pCtx))
IPPAPI(IppStatus, ippsSHA384GetTag,(Ipp8u* pTag, Ipp32u tagLen, const IppsSHA384State* pCtx))
IPPAPI(IppStatus, ippsSHA384Final,(Ipp8u* pMD, IppsSHA384State* pCtx))
IPPAPI(IppStatus, ippsSHA384MessageDigest,(const Ipp8u* pMsg, int len, Ipp8u* pMD))

/* SHA512 Hash Primitives */
IPPAPI(IppStatus, ippsSHA512GetSize,(int* pSize))
IPPAPI(IppStatus, ippsSHA512Init,(IppsSHA512State* pCtx))
IPPAPI(IppStatus, ippsSHA512Duplicate,(const IppsSHA512State* pSrcCtx, IppsSHA512State* pDstCtx))

IPPAPI(IppStatus, ippsSHA512Pack,(const IppsSHA512State* pCtx, Ipp8u* pBuffer))
IPPAPI(IppStatus, ippsSHA512Unpack,(const Ipp8u* pBuffer, IppsSHA512State* pCtx))

IPPAPI(IppStatus, ippsSHA512Update,(const Ipp8u* pSrc, int len, IppsSHA512State* pCtx))
IPPAPI(IppStatus, ippsSHA512GetTag,(Ipp8u* pTag, Ipp32u tagLen, const IppsSHA512State* pCtx))
IPPAPI(IppStatus, ippsSHA512Final,(Ipp8u* pMD, IppsSHA512State* pCtx))
IPPAPI(IppStatus, ippsSHA512MessageDigest,(const Ipp8u* pMsg, int len, Ipp8u* pMD))

/* MD5 Hash Primitives */
IPPAPI(IppStatus, ippsMD5GetSize,(int* pSize))
IPPAPI(IppStatus, ippsMD5Init,(IppsMD5State* pCtx))
IPPAPI(IppStatus, ippsMD5Duplicate,(const IppsMD5State* pSrcCtx, IppsMD5State* pDstCtx))

IPPAPI(IppStatus, ippsMD5Pack,(const IppsMD5State* pCtx, Ipp8u* pBuffer))
IPPAPI(IppStatus, ippsMD5Unpack,(const Ipp8u* pBuffer, IppsMD5State* pCtx))

IPPAPI(IppStatus, ippsMD5Update,(const Ipp8u* pSrc, int len, IppsMD5State* pCtx))
IPPAPI(IppStatus, ippsMD5GetTag,(Ipp8u* pTag, Ipp32u tagLen, const IppsMD5State* pCtx))
IPPAPI(IppStatus, ippsMD5Final,(Ipp8u* pMD, IppsMD5State* pCtx))
IPPAPI(IppStatus, ippsMD5MessageDigest,(const Ipp8u* pMsg, int len, Ipp8u* pMD))

/* SM3 Hash Primitives */
IPPAPI(IppStatus, ippsSM3GetSize,(int* pSize))
IPPAPI(IppStatus, ippsSM3Init,(IppsSM3State* pCtx))
IPPAPI(IppStatus, ippsSM3Duplicate,(const IppsSM3State* pSrcCtx, IppsSM3State* pDstCtx))

IPPAPI(IppStatus, ippsSM3Pack,(const IppsSM3State* pCtx, Ipp8u* pBuffer))
IPPAPI(IppStatus, ippsSM3Unpack,(const Ipp8u* pBuffer, IppsSM3State* pCtx))

IPPAPI(IppStatus, ippsSM3Update,(const Ipp8u* pSrc, int len, IppsSM3State* pCtx))
IPPAPI(IppStatus, ippsSM3GetTag,(Ipp8u* pTag, Ipp32u tagLen, const IppsSM3State* pCtx))
IPPAPI(IppStatus, ippsSM3Final,(Ipp8u* pMD, IppsSM3State* pCtx))
IPPAPI(IppStatus, ippsSM3MessageDigest,(const Ipp8u* pMsg, int len, Ipp8u* pMD))

/* generalized Hash Primitives */
IPPAPI(IppStatus, ippsHashGetSize,(int* pSize))
IPPAPI(IppStatus, ippsHashInit,(IppsHashState* pCtx, IppHashAlgId hashAlg))

IPPAPI(IppStatus, ippsHashPack,(const IppsHashState* pCtx, Ipp8u* pBuffer, int bufSize))
IPPAPI(IppStatus, ippsHashUnpack,(const Ipp8u* pBuffer, IppsHashState* pCtx))
IPPAPI(IppStatus, ippsHashDuplicate,(const IppsHashState* pSrcCtx, IppsHashState* pDstCtx))

IPPAPI(IppStatus, ippsHashUpdate,(const Ipp8u* pSrc, int len, IppsHashState* pCtx))
IPPAPI(IppStatus, ippsHashGetTag,(Ipp8u* pMD, int tagLen, const IppsHashState* pCtx))
IPPAPI(IppStatus, ippsHashFinal,(Ipp8u* pMD, IppsHashState* pCtx))
IPPAPI(IppStatus, ippsHashMessage,(const Ipp8u* pMsg, int len, Ipp8u* pMD, IppHashAlgId hashAlg))

/* general MGF Primitives*/
IPPAPI(IppStatus, ippsMGF,(const Ipp8u* pSeed, int seedLen, Ipp8u* pMask, int maskLen, IppHashAlgId hashAlg))


/*
// =========================================================
// Keyed-Hash Message Authentication Codes
// =========================================================
*/
IPPAPI(IppStatus, ippsHMAC_GetSize,(int* pSize))
IPPAPI(IppStatus, ippsHMAC_Init,(const Ipp8u* pKey, int keyLen, IppsHMACState* pCtx, IppHashAlgId hashAlg))

IPPAPI(IppStatus, ippsHMAC_Pack,(const IppsHMACState* pCtx, Ipp8u* pBuffer, int bufSize))
IPPAPI(IppStatus, ippsHMAC_Unpack,(const Ipp8u* pBuffer, IppsHMACState* pCtx))
IPPAPI(IppStatus, ippsHMAC_Duplicate,(const IppsHMACState* pSrcCtx, IppsHMACState* pDstCtx))

IPPAPI(IppStatus, ippsHMAC_Update,(const Ipp8u* pSrc, int len, IppsHMACState* pCtx))
IPPAPI(IppStatus, ippsHMAC_Final,(Ipp8u* pMD, int mdLen, IppsHMACState* pCtx))
IPPAPI(IppStatus, ippsHMAC_GetTag,(Ipp8u* pMD, int mdLen, const IppsHMACState* pCtx))
IPPAPI(IppStatus, ippsHMAC_Message,(const Ipp8u* pMsg, int msgLen,
                                    const Ipp8u* pKey, int keyLen,
                                    Ipp8u* pMD, int mdLen,
                                    IppHashAlgId hashAlg))


/*
// =========================================================
// Big Number Integer Arithmetic
// =========================================================
*/

/* Signed BigNum Operations */
IPPAPI(IppStatus, ippsBigNumGetSize,(int length, int* pSize))
IPPAPI(IppStatus, ippsBigNumInit,(int length, IppsBigNumState* pBN))

IPPAPI(IppStatus, ippsCmpZero_BN,(const IppsBigNumState* pBN, Ipp32u* pResult))
IPPAPI(IppStatus, ippsCmp_BN,(const IppsBigNumState* pA, const IppsBigNumState* pB, Ipp32u* pResult))

IPPAPI(IppStatus, ippsGetSize_BN,(const IppsBigNumState* pBN, int* pSize))
IPPAPI(IppStatus, ippsSet_BN,(IppsBigNumSGN sgn,
                              int length, const Ipp32u* pData,
                              IppsBigNumState* pBN))
IPPAPI(IppStatus, ippsGet_BN,(IppsBigNumSGN* pSgn,
                              int* pLength, Ipp32u* pData,
                              const IppsBigNumState* pBN))
IPPAPI(IppStatus, ippsRef_BN,(IppsBigNumSGN* pSgn, int* bitSize, Ipp32u** const ppData,
                              const IppsBigNumState* pBN))
IPPAPI(IppStatus, ippsExtGet_BN,(IppsBigNumSGN* pSgn,
                              int* pBitSize, Ipp32u* pData,
                              const IppsBigNumState* pBN))

IPPAPI(IppStatus, ippsAdd_BN,   (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR))
IPPAPI(IppStatus, ippsSub_BN,   (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR))
IPPAPI(IppStatus, ippsMul_BN,   (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR))
IPPAPI(IppStatus, ippsMAC_BN_I, (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR))
IPPAPI(IppStatus, ippsDiv_BN,   (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pQ, IppsBigNumState* pR))
IPPAPI(IppStatus, ippsMod_BN,   (IppsBigNumState* pA, IppsBigNumState* pM, IppsBigNumState* pR))
IPPAPI(IppStatus, ippsGcd_BN,   (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pGCD))
IPPAPI(IppStatus, ippsModInv_BN,(IppsBigNumState* pA, IppsBigNumState* pM, IppsBigNumState* pInv))

IPPAPI(IppStatus, ippsSetOctString_BN,(const Ipp8u* pStr, int strLen, IppsBigNumState* pBN))
IPPAPI(IppStatus, ippsGetOctString_BN,(Ipp8u* pStr, int strLen, const IppsBigNumState* pBN))

/* Montgomery Operations */
IPPAPI(IppStatus, ippsMontGetSize,(IppsExpMethod method, int length, int* pSize))
IPPAPI(IppStatus, ippsMontInit,(IppsExpMethod method, int length, IppsMontState* pCtx))

IPPAPI(IppStatus, ippsMontSet,(const Ipp32u* pModulo, int size, IppsMontState* pCtx))
IPPAPI(IppStatus, ippsMontGet,(Ipp32u* pModulo, int* pSize, const IppsMontState* pCtx))

IPPAPI(IppStatus, ippsMontForm,(const IppsBigNumState* pA, IppsMontState* pCtx, IppsBigNumState* pR))
IPPAPI(IppStatus, ippsMontMul, (const IppsBigNumState* pA, const IppsBigNumState* pB, IppsMontState* m, IppsBigNumState* pR))
IPPAPI(IppStatus, ippsMontExp, (const IppsBigNumState* pA, const IppsBigNumState* pE, IppsMontState* m, IppsBigNumState* pR))

/* Pseudo-Random Number Generation */
IPPAPI(IppStatus, ippsPRNGGetSize,(int* pSize))
IPPAPI(IppStatus, ippsPRNGInit,   (int seedBits, IppsPRNGState* pCtx))

IPPAPI(IppStatus, ippsPRNGSetModulus,(const IppsBigNumState* pMod, IppsPRNGState* pCtx))
IPPAPI(IppStatus, ippsPRNGSetH0,     (const IppsBigNumState* pH0,  IppsPRNGState* pCtx))
IPPAPI(IppStatus, ippsPRNGSetAugment,(const IppsBigNumState* pAug, IppsPRNGState* pCtx))
IPPAPI(IppStatus, ippsPRNGSetSeed,   (const IppsBigNumState* pSeed,IppsPRNGState* pCtx))
IPPAPI(IppStatus, ippsPRNGGetSeed,   (const IppsPRNGState* pCtx,IppsBigNumState* pSeed))

IPPAPI(IppStatus, ippsPRNGen,     (Ipp32u* pRand, int nBits, void* pCtx))
IPPAPI(IppStatus, ippsPRNGen_BN,  (IppsBigNumState* pRand, int nBits, void* pCtx))
IPPAPI(IppStatus, ippsPRNGenRDRAND,   (Ipp32u* pRand, int nBits, void* pCtx))
IPPAPI(IppStatus, ippsPRNGenRDRAND_BN,(IppsBigNumState* pRand, int nBits, void* pCtx))
IPPAPI(IppStatus, ippsTRNGenRDSEED,   (Ipp32u* pRand, int nBits, void* pCtx))
IPPAPI(IppStatus, ippsTRNGenRDSEED_BN,(IppsBigNumState* pRand, int nBits, void* pCtx))

/* Probable Prime Number Generation */
IPPAPI(IppStatus, ippsPrimeGetSize,(int nMaxBits, int* pSize))
IPPAPI(IppStatus, ippsPrimeInit,   (int nMaxBits, IppsPrimeState* pCtx))

IPPAPI(IppStatus, ippsPrimeGen, (int nBits, int nTrials, IppsPrimeState* pCtx,
                                 IppBitSupplier rndFunc, void* pRndParam))
IPPAPI(IppStatus, ippsPrimeTest,(int nTrials, Ipp32u* pResult, IppsPrimeState* pCtx,
                                 IppBitSupplier rndFunc, void* pRndParam))
IPPAPI(IppStatus, ippsPrimeGen_BN,(IppsBigNumState* pPrime, int nBits, int nTrials, IppsPrimeState* pCtx,
                                 IppBitSupplier rndFunc, void* pRndParam))
IPPAPI(IppStatus, ippsPrimeTest_BN,(const IppsBigNumState* pPrime, int nTrials, Ipp32u* pResult, IppsPrimeState* pCtx,
                                 IppBitSupplier rndFunc, void* pRndParam))

IPPAPI(IppStatus, ippsPrimeGet,   (Ipp32u* pPrime, int* pLen, const IppsPrimeState* pCtx))
IPPAPI(IppStatus, ippsPrimeGet_BN,(IppsBigNumState* pPrime, const IppsPrimeState* pCtx))

IPPAPI(IppStatus, ippsPrimeSet,   (const Ipp32u* pPrime, int nBits, IppsPrimeState* pCtx))
IPPAPI(IppStatus, ippsPrimeSet_BN,(const IppsBigNumState* pPrime, IppsPrimeState* pCtx))


/*
// =========================================================
// RSA Cryptography
// =========================================================
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
IPPAPI(IppStatus, ippsRSA_GetPrivateKeyType1,(IppsBigNumState* pModulus,
                                              IppsBigNumState* pPrivateExp,
                                        const IppsRSAPrivateKeyState* pKey))

IPPAPI(IppStatus, ippsRSA_GetSizePrivateKeyType2,(int factorPbitSize, int factorQbitSize, int* pKeySize))
IPPAPI(IppStatus, ippsRSA_InitPrivateKeyType2,(int factorPbitSize, int factorQbitSize,
                                               IppsRSAPrivateKeyState* pKey, int keyCtxSize))
IPPAPI(IppStatus, ippsRSA_SetPrivateKeyType2,(const IppsBigNumState* pFactorP,
                                              const IppsBigNumState* pFactorQ,
                                              const IppsBigNumState* pCrtExpP,
                                              const IppsBigNumState* pCrtExpQ,
                                              const IppsBigNumState* pInverseQ,
                                              IppsRSAPrivateKeyState* pKey))
IPPAPI(IppStatus, ippsRSA_GetPrivateKeyType2,(IppsBigNumState* pFactorP,
                                              IppsBigNumState* pFactorQ,
                                              IppsBigNumState* pCrtExpP,
                                              IppsBigNumState* pCrtExpQ,
                                              IppsBigNumState* pInverseQ,
                                              const IppsRSAPrivateKeyState* pKey))

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

IPPAPI(IppStatus, ippsRSA_GenerateKeys,(const IppsBigNumState* pSrcPublicExp,
                                    IppsBigNumState* pModulus,
                                    IppsBigNumState* pPublicExp,
                                    IppsBigNumState* pPrivateExp,
                                    IppsRSAPrivateKeyState* pPrivateKeyType2,
                                    Ipp8u* pScratchBuffer,
                                    int nTrials,
                                    IppsPrimeState* pPrimeGen,
                                    IppBitSupplier rndFunc, void* pRndParam))

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

IPPAPI(IppStatus, ippsRSADecrypt_OAEP,(const Ipp8u* pSrc,
                                       const Ipp8u* pLab, int labLen,
                                             Ipp8u* pDst, int* pDstLen,
                                       const IppsRSAPrivateKeyState* pKey,
                                             IppHashAlgId hashAlg,
                                             Ipp8u* pBuffer))

/* encryption scheme: RSAES-PKCS_v1_5 */
IPPAPI(IppStatus, ippsRSAEncrypt_PKCSv15,(const Ipp8u* pSrc, int srcLen,
                                          const Ipp8u* pRndPS,
                                                Ipp8u* pDst,
                                          const IppsRSAPublicKeyState* pKey,
                                                Ipp8u* pBuffer))

IPPAPI(IppStatus, ippsRSADecrypt_PKCSv15,(const Ipp8u* pSrc,
                                                Ipp8u* pDst, int* pDstLen,
                                          const IppsRSAPrivateKeyState* pKey,
                                                Ipp8u* pBuffer))


/* signature scheme : RSA-SSA-PSS */
IPPAPI(IppStatus, ippsRSASign_PSS,(const Ipp8u* pMsg,  int msgLen,
                                   const Ipp8u* pSalt, int saltLen,
                                         Ipp8u* pSign,
                                   const IppsRSAPrivateKeyState* pPrvKey,
                                   const IppsRSAPublicKeyState*  pPubKey,
                                         IppHashAlgId hashAlg,
                                         Ipp8u* pBuffer))

IPPAPI(IppStatus, ippsRSAVerify_PSS,(const Ipp8u* pMsg,  int msgLen,
                                     const Ipp8u* pSign,
                                           int* pIsValid,
                                     const IppsRSAPublicKeyState*  pKey,
                                           IppHashAlgId hashAlg,
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
// =========================================================
// DL Cryptography
// =========================================================
*/
IPPAPI( const char*, ippsDLGetResultString, (IppDLResult code))

/* Initialization */
IPPAPI(IppStatus, ippsDLPGetSize,(int bitSizeP, int bitSizeR, int* pSize))
IPPAPI(IppStatus, ippsDLPInit,   (int bitSizeP, int bitSizeR, IppsDLPState* pCtx))

IPPAPI(IppStatus, ippsDLPPack,(const IppsDLPState* pCtx, Ipp8u* pBuffer))
IPPAPI(IppStatus, ippsDLPUnpack,(const Ipp8u* pBuffer, IppsDLPState* pCtx))

/* Set Up and Retrieve Domain Parameters */
IPPAPI(IppStatus, ippsDLPSet,(const IppsBigNumState* pP,
                              const IppsBigNumState* pR,
                              const IppsBigNumState* pG,
                              IppsDLPState* pCtx))
IPPAPI(IppStatus, ippsDLPGet,(IppsBigNumState* pP,
                              IppsBigNumState* pR,
                              IppsBigNumState* pG,
                              IppsDLPState* pCtx))
IPPAPI(IppStatus, ippsDLPSetDP,(const IppsBigNumState* pDP, IppDLPKeyTag tag, IppsDLPState* pCtx))
IPPAPI(IppStatus, ippsDLPGetDP,(IppsBigNumState* pDP, IppDLPKeyTag tag, const IppsDLPState* pCtx))

/* Key Generation, Validation and Set Up */
IPPAPI(IppStatus, ippsDLPGenKeyPair,(IppsBigNumState* pPrvKey, IppsBigNumState* pPubKey,
                                     IppsDLPState* pCtx,
                                     IppBitSupplier rndFunc, void* pRndParam))
IPPAPI(IppStatus, ippsDLPPublicKey, (const IppsBigNumState* pPrvKey,
                                     IppsBigNumState* pPubKey,
                                     IppsDLPState* pCtx))
IPPAPI(IppStatus, ippsDLPValidateKeyPair,(const IppsBigNumState* pPrvKey,
                                     const IppsBigNumState* pPubKey,
                                     IppDLResult* pResult,
                                     IppsDLPState* pCtx))

IPPAPI(IppStatus, ippsDLPSetKeyPair,(const IppsBigNumState* pPrvKey,
                                     const IppsBigNumState* pPubKey,
                                     IppsDLPState* pCtx))

/* Singing/Verifying (DSA version) */
IPPAPI(IppStatus, ippsDLPSignDSA,  (const IppsBigNumState* pMsgDigest,
                                    const IppsBigNumState* pPrvKey,
                                    IppsBigNumState* pSignR, IppsBigNumState* pSignS,
                                    IppsDLPState* pCtx))
IPPAPI(IppStatus, ippsDLPVerifyDSA,(const IppsBigNumState* pMsgDigest,
                                    const IppsBigNumState* pSignR, const IppsBigNumState* pSignS,
                                    IppDLResult* pResult,
                                    IppsDLPState* pCtx))

/* Shared Secret Element (DH version) */
IPPAPI(IppStatus, ippsDLPSharedSecretDH,(const IppsBigNumState* pPrvKeyA,
                                         const IppsBigNumState* pPubKeyB,
                                         IppsBigNumState* pShare,
                                         IppsDLPState* pCtx))

/* DSA's parameter Generation and Validation */
IPPAPI(IppStatus, ippsDLPGenerateDSA,(const IppsBigNumState* pSeedIn,
                                      int nTrials, IppsDLPState* pCtx,
                                      IppsBigNumState* pSeedOut, int* pCounter,
                                      IppBitSupplier rndFunc, void* pRndParam))
IPPAPI(IppStatus, ippsDLPValidateDSA,(int nTrials, IppDLResult* pResult, IppsDLPState* pCtx,
                                      IppBitSupplier rndFunc, void* pRndParam))

/* DH parameter's Generation and Validation */
IPPAPI(IppStatus, ippsDLPGenerateDH,(const IppsBigNumState* pSeedIn,
                                     int nTrials, IppsDLPState* pCtx,
                                     IppsBigNumState* pSeedOut, int* pCounter,
                                     IppBitSupplier rndFunc, void* pRndParam))
IPPAPI(IppStatus, ippsDLPValidateDH,(int nTrials, IppDLResult* pResult, IppsDLPState* pCtx,
                                     IppBitSupplier rndFunc, void* pRndParam))


/*
// =========================================================
// EC Cryptography
// =========================================================
*/
IPPAPI( const char*, ippsECCGetResultString, (IppECResult code))

/*
// EC over Prime Fields
*/
/* general EC initialization */
IPPAPI(IppStatus, ippsECCPGetSize,(int feBitSize, int* pSize))
IPPAPI(IppStatus, ippsECCPInit,(int feBitSize, IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPSet,(const IppsBigNumState* pPrime,
                               const IppsBigNumState* pA, const IppsBigNumState* pB,
                               const IppsBigNumState* pGX,const IppsBigNumState* pGY,const IppsBigNumState* pOrder,
                               int cofactor,
                               IppsECCPState* pECC))

/* standard EC initialization */
IPPAPI(IppStatus, ippsECCPGetSizeStd128r1,(int* pSize))
IPPAPI(IppStatus, ippsECCPGetSizeStd128r2,(int* pSize))
IPPAPI(IppStatus, ippsECCPGetSizeStd192r1,(int* pSize))
IPPAPI(IppStatus, ippsECCPGetSizeStd224r1,(int* pSize))
IPPAPI(IppStatus, ippsECCPGetSizeStd256r1,(int* pSize))
IPPAPI(IppStatus, ippsECCPGetSizeStd384r1,(int* pSize))
IPPAPI(IppStatus, ippsECCPGetSizeStd521r1,(int* pSize))
IPPAPI(IppStatus, ippsECCPGetSizeStdSM2,(int* pSize))

IPPAPI(IppStatus, ippsECCPInitStd128r1,(IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPInitStd128r2,(IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPInitStd192r1,(IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPInitStd224r1,(IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPInitStd256r1,(IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPInitStd384r1,(IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPInitStd521r1,(IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPInitStdSM2,(IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPSetStd128r1,(IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPSetStd128r2,(IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPSetStd192r1,(IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPSetStd224r1,(IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPSetStd256r1,(IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPSetStd384r1,(IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPSetStd521r1,(IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPSetStdSM2,(IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPSetStd,(IppECCType flag, IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPGet,(IppsBigNumState* pPrime,
                               IppsBigNumState* pA, IppsBigNumState* pB,
                               IppsBigNumState* pGX,IppsBigNumState* pGY,IppsBigNumState* pOrder,
                               int* cofactor,
                               IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPGetOrderBitSize,(int* pBitSize, IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPValidate,(int nTrials, IppECResult* pResult, IppsECCPState* pECC,
                                    IppBitSupplier rndFunc, void* pRndParam))

/* EC Point */
IPPAPI(IppStatus, ippsECCPPointGetSize,(int feBitSize, int* pSize))

IPPAPI(IppStatus, ippsECCPPointInit,(int feBitSize, IppsECCPPointState* pPoint))

/* Setup/retrieve point's coordinates */
IPPAPI(IppStatus, ippsECCPSetPoint,(const IppsBigNumState* pX, const IppsBigNumState* pY,
                                    IppsECCPPointState* pPoint, IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPSetPointAtInfinity,(IppsECCPPointState* pPoint, IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPGetPoint,(IppsBigNumState* pX, IppsBigNumState* pY,
                                    const IppsECCPPointState* pPoint, IppsECCPState* pECC))

/* EC Point Operations */
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

/* Key Generation, Setup and Validation */
IPPAPI(IppStatus, ippsECCPGenKeyPair,(IppsBigNumState* pPrivate, IppsECCPPointState* pPublic,
                                      IppsECCPState* pECC,
                                      IppBitSupplier rndFunc, void* pRndParam))
IPPAPI(IppStatus, ippsECCPPublicKey,(const IppsBigNumState* pPrivate,
                                     IppsECCPPointState* pPublic,
                                     IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPValidateKeyPair,(const IppsBigNumState* pPrivate, const IppsECCPPointState* pPublic,
                                           IppECResult* pResult,
                                           IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPSetKeyPair,(const IppsBigNumState* pPrivate, const IppsECCPPointState* pPublic,
                                      IppBool regular,
                                      IppsECCPState* pECC))

/* Shared Secret (DH scheme ) */
IPPAPI(IppStatus, ippsECCPSharedSecretDH,(const IppsBigNumState* pPrivateA,
                                          const IppsECCPPointState* pPublicB,
                                          IppsBigNumState* pShare,
                                          IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPSharedSecretDHC,(const IppsBigNumState* pPrivateA,
                                           const IppsECCPPointState* pPublicB,
                                           IppsBigNumState* pShare,
                                           IppsECCPState* pECC))

/* Sing/Verify */
IPPAPI(IppStatus, ippsECCPSignDSA,(const IppsBigNumState* pMsgDigest,
                        const IppsBigNumState* pPrivate,
                        IppsBigNumState* pSignX, IppsBigNumState* pSignY,
                        IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPVerifyDSA,(const IppsBigNumState* pMsgDigest,
                        const IppsBigNumState* pSignX, const IppsBigNumState* pSignY,
                        IppECResult* pResult,
                        IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPSignNR,(const IppsBigNumState* pMsgDigest,
                        const IppsBigNumState* pPrivate,
                        IppsBigNumState* pSignX, IppsBigNumState* pSignY,
                        IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPVerifyNR,(const IppsBigNumState* pMsgDigest,
                        const IppsBigNumState* pSignX, const IppsBigNumState* pSignY,
                        IppECResult* pResult,
                        IppsECCPState* pECC))

IPPAPI(IppStatus, ippsECCPSignSM2,(const IppsBigNumState* pMsgDigest,
                        const IppsBigNumState* pRegPrivate,
                        const IppsBigNumState* pEphPrivate,
                        IppsBigNumState* pSignR, IppsBigNumState* pSignS,
                        IppsECCPState* pECC))
IPPAPI(IppStatus, ippsECCPVerifySM2,(const IppsBigNumState* pMsgDigest,
                        const IppsECCPPointState* pRegPublic,
                        const IppsBigNumState* pSignR, const IppsBigNumState* pSignS,
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
