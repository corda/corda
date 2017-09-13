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
//              ippCP Intel(R) EPID functionality
// 
// 
*/


#if !defined( __IPPCPEPID_H__ ) || defined( _OWN_BLDPCS )
#define __IPPCPEPID_H__


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
  #if defined( _IPP_PARALLEL_DYNAMIC ) || defined( _IPP_SEQUENTIAL_DYNAMIC )
    #pragma comment( lib, "ippcpepid" )
    #pragma comment( lib, "ippcp" )
    #pragma comment( lib, "ippcore" )
  #elif defined( _IPP_PARALLEL_STATIC ) || defined( _IPP_SEQUENTIAL_STATIC )
    #pragma comment( lib, "ippcpepidmt" )
    #pragma comment( lib, "ippcpmt" )
    #pragma comment( lib, "ippcoremt" )
  #endif
#endif


/* /////////////////////////////////////////////////////////////////////////////
//  Name:       ippcpepidGetLibVersion
//  Purpose:    getting of the library version
//  Returns:    the structure of information about version of ippCP EPID library
//  Parameters:
//
//  Notes:      not necessary to release the returned structure
*/
IPPAPI( const IppLibraryVersion*, ippcpepidGetLibVersion, (void) )


/*
// Finite Field Low Level Math
*/
#define IPP_MIN_GF_BITSIZE      (2)  /* min bitsize for GF element */
#define IPP_MAX_GF_BITSIZE   (4096)  /* max bitsize for GF element */

//#define IPP_IS_EQ    IS_ZERO //(0)
//#define IPP_IS_NE (1)
//#define IPP_IS_GT    GREATER_THAN_ZERO //(2)
//#define IPP_IS_LT    LESS_THAN_ZERO // (3)
//#define IPP_IS_NA (4)

#if !defined( _OWN_BLDPCS )
typedef struct _cpGFp IppsGFpState;
typedef struct _cpElementGFp IppsGFpElement;

typedef struct {
   const IppsGFpState* pBasicGF;
   const IppsGFpState* pGroundGF;
   int   basicGFdegree;
   int   groundGFdegree;
   int   elementLen;
} IppsGFpInfo;
#endif

#if !defined( _OWN_BLDPCS )
typedef enum {
   ippMD5  = 0x00,
   ippSHA1 = 0x01,
   ippSHA256 = 0x02, ippSHA224 = 0x12,
   ippSHA512 = 0x03, ippSHA384 = 0x13
} IppHashID;
#endif /* _OWN_BLDPCS */


IPPAPI(IppStatus, ippsGFpGetSize, (int bitSize, int* pStateSizeInBytes))
IPPAPI(IppStatus, ippsGFpInit,    (const Ipp32u* pPime, int bitSize, IppsGFpState* pGFp))

IPPAPI(IppStatus, ippsGFpxGetSize,(const IppsGFpState* pGroundGF, int degree, int* pStateSizeInBytes))
IPPAPI(IppStatus, ippsGFpxInit,   (const IppsGFpState* pGroundGF, const Ipp32u* pIrrPolynomial, int degree, IppsGFpState* pGFpx))
IPPAPI(IppStatus, ippsGFpxInitBinomial,(const IppsGFpState* pGroundGF, const IppsGFpElement* pGroundElm, int degree, IppsGFpState* pGFpx))

IPPAPI(IppStatus, ippsGFpGetInfo,(const IppsGFpState* pGFp, IppsGFpInfo* pInfo))
IPPAPI(IppStatus, ippsGFpGetModulus,(const IppsGFpState* pGFp, Ipp32u* pModulus))

IPPAPI(IppStatus, ippsGFpScratchBufferSize,(int nExponents, int ExpBitSize, const IppsGFpState* pGF, int* pBufferSize))

//IPPAPI(IppStatus, ippsBasicGFpRef,(const IppsGFpState* pGFp, IppsGFpState** ppBasicGF))
//IPPAPI(IppStatus, ippsGroundGFpRef,(const IppsGFpState* pGFp, IppsGFpState** ppGroundGF))
//IPPAPI(IppStatus, ippsGFpGetDegree,(const IppsGFpState* pGFp, int* pDegree))
//IPPAPI(IppStatus, ippsGFpGetElementLen,(const IppsGFpState* pGFp, int* pElmLen))
//IPPAPI(IppStatus, ippsGFpCmp,     (const IppsGFpState* pGFp1, const IppsGFpState* pGFp2, IppGFpResult* pCmpResult))

IPPAPI(IppStatus, ippsGFpElementGetSize,(const IppsGFpState* pGFp, int* pElementSize))
IPPAPI(IppStatus, ippsGFpElementInit,   (const Ipp32u* pA, int lenA, IppsGFpElement* pR, IppsGFpState* pGFp))

IPPAPI(IppStatus, ippsGFpSetElement,      (const Ipp32u* pA, int nsA, IppsGFpElement* pR, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpSetElementOctString,(const Ipp8u* pStr, int strSize, IppsGFpElement* pR, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpSetElementRandom,(IppBitSupplier rndFunc, void* pRndParam, IppsGFpElement* pR, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpSetElementHash,(const Ipp8u* pMsg, int msgLen, IppHashID hashID, IppsGFpElement* pElm, IppsGFpState* pGF))
IPPAPI(IppStatus, ippsGFpCpyElement,(const IppsGFpElement* pA, IppsGFpElement* pR, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpGetElement,(const IppsGFpElement* pA, Ipp32u* pDataA, int nsA, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpGetElementOctString,(const IppsGFpElement* pA, Ipp8u* pStr, int strSize, IppsGFpState* pGFp))

IPPAPI(IppStatus, ippsGFpCmpElement,(const IppsGFpElement* pA, const IppsGFpElement* pB, int* pResult, const IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpIsZeroElement,(const IppsGFpElement* pA, int* pResult, const IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpIsUnityElement,(const IppsGFpElement* pA, int* pResult, const IppsGFpState* pGFp))

//IPPAPI(IppStatus, ippsGFpSetPolyTerm, (const Ipp32u* pTerm, int nsT, int termDegree, IppsGFpElement* pElm, IppsGFpState* pGF))
//IPPAPI(IppStatus, ippsGFpGetPolyTerm, (const IppsGFpElement* pElm, int termDegree, Ipp32u* pTerm, int nsT, IppsGFpState* pGF))

IPPAPI(IppStatus, ippsGFpConj,(const IppsGFpElement* pA, IppsGFpElement* pR, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpNeg, (const IppsGFpElement* pA, IppsGFpElement* pR, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpInv, (const IppsGFpElement* pA, IppsGFpElement* pR, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpSqrt,(const IppsGFpElement* pA, IppsGFpElement* pR, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpAdd, (const IppsGFpElement* pA, const IppsGFpElement* pB,  IppsGFpElement* pR, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpSub, (const IppsGFpElement* pA, const IppsGFpElement* pB,  IppsGFpElement* pR, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpMul, (const IppsGFpElement* pA, const IppsGFpElement* pB,  IppsGFpElement* pR, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpSqr, (const IppsGFpElement* pA, IppsGFpElement* pR, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpExp, (const IppsGFpElement* pA, const IppsBigNumState* pE, IppsGFpElement* pR, IppsGFpState* pGFp, Ipp8u* pScratchBuffer))
IPPAPI(IppStatus, ippsGFpMultiExp,(const IppsGFpElement* const ppElmA[], const IppsBigNumState* const ppE[], int nItems, IppsGFpElement* pElmR, IppsGFpState* pGF, Ipp8u* pScratchBuffer))

IPPAPI(IppStatus, ippsGFpAdd_GFpE,(const IppsGFpElement* pA, const IppsGFpElement* pGroundB, IppsGFpElement* pR, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpSub_GFpE,(const IppsGFpElement* pA, const IppsGFpElement* pGroundB, IppsGFpElement* pR, IppsGFpState* pGFp))
IPPAPI(IppStatus, ippsGFpMul_GFpE,(const IppsGFpElement* pA, const IppsGFpElement* pGroundB, IppsGFpElement* pR, IppsGFpState* pGFp))


#if !defined( _OWN_BLDPCS )
typedef struct _cpGFpEC      IppsGFpECState;
typedef struct _cpGFpECPoint IppsGFpECPoint;
#endif

IPPAPI(IppStatus, ippsGFpECGetSize,(const IppsGFpState* pGF, int* pCtxSizeInBytes))
IPPAPI(IppStatus, ippsGFpECInit,   (const IppsGFpElement* pA, const IppsGFpElement* pB,
                                    const IppsGFpElement* pX, const IppsGFpElement* pY,
                                    const Ipp32u* pOrder, int orderLen,
                                    const Ipp32u* pCofactor, int cofactorLen,
                                    IppsGFpState* pGF, IppsGFpECState* pEC))

IPPAPI(IppStatus, ippsGFpECScratchBufferSize,(int nScalars, const IppsGFpECState* pEC, int* pBufferSize))

IPPAPI(IppStatus, ippsGFpECSet,(const IppsGFpElement* pA, const IppsGFpElement* pB,
                     const IppsGFpElement* pX, const IppsGFpElement* pY,
                     const Ipp32u* pOrder, int orderLen,
                     const Ipp32u* pCofactor, int cofactorLen,
                     IppsGFpECState* pEC))

IPPAPI(IppStatus, ippsGFpECGet,(const IppsGFpECState* pEC,
                     const IppsGFpState** ppGF,
                     IppsGFpElement* pA, IppsGFpElement* pB,
                     IppsGFpElement* pX, IppsGFpElement* pY,
                     const Ipp32u** ppOrder, int* pOrderLen,
                     const Ipp32u** ppCofactor, int* pCoFactorLen))

IPPAPI(IppStatus, ippsGFpECVerify,(IppECResult* pResult, IppsGFpECState* pEC, Ipp8u* pScratchBuffer))

IPPAPI(IppStatus, ippsGFpECPointGetSize,(const IppsGFpECState* pEC, int* pSizeInBytes))
IPPAPI(IppStatus, ippsGFpECPointInit,   (const IppsGFpElement* pX, const IppsGFpElement* pY, IppsGFpECPoint* pPoint, IppsGFpECState* pEC))

IPPAPI(IppStatus, ippsGFpECSetPointAtInfinity,(IppsGFpECPoint* pPoint, IppsGFpECState* pEC))
IPPAPI(IppStatus, ippsGFpECSetPoint,(const IppsGFpElement* pX, const IppsGFpElement* pY, IppsGFpECPoint* pPoint, IppsGFpECState* pEC))
IPPAPI(IppStatus, ippsGFpECSetPointRandom,(IppBitSupplier rndFunc, void* pRndParam, IppsGFpECPoint* pPoint, IppsGFpECState* pEC, Ipp8u* pScratchBuffer))
IPPAPI(IppStatus, ippsGFpECMakePoint,(const IppsGFpElement* pX, IppsGFpECPoint* pPoint, IppsGFpECState* pEC))
IPPAPI(IppStatus, ippsGFpECSetPointHash,(Ipp32u hdr, const Ipp8u* pMsg, int msgLen, IppHashID hashID, IppsGFpECPoint* pPoint, IppsGFpECState* pEC, Ipp8u* pScratchBuffer))

IPPAPI(IppStatus, ippsGFpECCpyPoint,(const IppsGFpECPoint* pA, IppsGFpECPoint* pR, IppsGFpECState* pEC))
IPPAPI(IppStatus, ippsGFpECCmpPoint,(const IppsGFpECPoint* pP, const IppsGFpECPoint* pQ, IppECResult* pResult, IppsGFpECState* pEC))

IPPAPI(IppStatus, ippsGFpECTstPoint,(const IppsGFpECPoint* pP, IppECResult* pResult, IppsGFpECState* pEC, Ipp8u* pScratchBuffer))
IPPAPI(IppStatus, ippsGFpECGetPoint,(const IppsGFpECPoint* pPoint, IppsGFpElement* pX, IppsGFpElement* pY, IppsGFpECState* pEC))

IPPAPI(IppStatus, ippsGFpECNegPoint,(const IppsGFpECPoint* pP, IppsGFpECPoint* pR, IppsGFpECState* pEC))
IPPAPI(IppStatus, ippsGFpECAddPoint,(const IppsGFpECPoint* pP, const IppsGFpECPoint* pQ, IppsGFpECPoint* pR, IppsGFpECState* pEC))
IPPAPI(IppStatus, ippsGFpECMulPoint,(const IppsGFpECPoint* pP, const IppsBigNumState* pN, IppsGFpECPoint* pR, IppsGFpECState* pEC, Ipp8u* pScratchBuffer))

#ifdef  __cplusplus
}
#endif


#if defined (_IPP_STDCALL_CDECL)
  #undef  _IPP_STDCALL_CDECL
  #define __stdcall __cdecl
#endif


#endif /* __IPPCPEPID_H__ */
