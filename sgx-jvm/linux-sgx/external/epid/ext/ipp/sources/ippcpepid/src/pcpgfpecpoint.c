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
// 
//  Purpose:
//     Intel(R) Performance Primitives. Cryptography Primitives.
//     EC over GF(p) Operations
// 
//     Context:
//        ippsGFpECPointGetSize()
//        ippsGFpECPointInit()
// 
//        ippsGFpECSetPointAtInfinity()
//        ippsGFpECSetPoint()
//        ippsGFpECMakePoint()
//        ippsGFpECSetPointRandom()
//        ippsGFpECSetPointHash()
//        ippsGFpECGetPoint()
//        ippsGFpECCpyPoint()
// 
//        ippsGFpECCmpPoint()
//        ippsGFpECTstPoint()
//        ippsGFpECNegPoint()
//        ippsGFpECAddPoint()
//        ippsGFpECMulPoint()
// 
// 
*/

#include "owncpepid.h"

#include "pcpgfpecstuff.h"
#include "pcpgfphashstuff.h"


IPPFUN(IppStatus, ippsGFpECPointGetSize,(const IppsGFpECState* pEC, int* pSizeInBytes))
{
   IPP_BAD_PTR2_RET(pEC, pSizeInBytes);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );

   {
      int elemLen = GFP_FELEN(ECP_GFP(pEC));
      *pSizeInBytes = sizeof(IppsGFpECPoint)
                     +elemLen*sizeof(BNU_CHUNK_T) /* X */
                     +elemLen*sizeof(BNU_CHUNK_T) /* Y */
                     +elemLen*sizeof(BNU_CHUNK_T);/* Z */
      return ippStsNoErr;
   }
}


IPPFUN(IppStatus, ippsGFpECPointInit,(const IppsGFpElement* pX, const IppsGFpElement* pY,
                                      IppsGFpECPoint* pPoint, IppsGFpECState* pEC))
{
   IPP_BAD_PTR2_RET(pPoint, pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );

   {
      Ipp8u* ptr = (Ipp8u*)pPoint;
      int elemLen = GFP_FELEN(ECP_GFP(pEC));

      ECP_POINT_ID(pPoint) = idCtxGFPPoint;
      ECP_POINT_FLAGS(pPoint) = 0;
      ECP_POINT_FELEN(pPoint) = elemLen;
      ptr += sizeof(IppsGFpECPoint);
      ECP_POINT_DATA(pPoint) = (BNU_CHUNK_T*)(ptr);

      if(pX && pY)
         return ippsGFpECSetPoint(pX, pY, pPoint, pEC);
      else {
         cpEcGFpSetProjectivePointAtInfinity(pPoint, elemLen);
         return ippStsNoErr;
      }
   }
}


IPPFUN(IppStatus, ippsGFpECSetPointAtInfinity,(IppsGFpECPoint* pPoint, IppsGFpECState* pEC))
{
   IPP_BAD_PTR2_RET(pPoint, pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pPoint), ippStsContextMatchErr );

   cpEcGFpSetProjectivePointAtInfinity(pPoint, GFP_FELEN(ECP_GFP(pEC)));
   return ippStsNoErr;
}


IPPFUN(IppStatus, ippsGFpECSetPoint,(const IppsGFpElement* pX, const IppsGFpElement* pY,
                                           IppsGFpECPoint* pPoint,
                                           IppsGFpECState* pEC))
{
   IPP_BAD_PTR2_RET(pPoint, pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pPoint), ippStsContextMatchErr );

   IPP_BAD_PTR2_RET(pX, pY);
   IPP_BADARG_RET( !GFPE_TEST_ID(pX), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pY), ippStsContextMatchErr );

   cpEcGFpSetAffinePoint(pPoint, GFPE_DATA(pX), GFPE_DATA(pY), pEC);
   return ippStsNoErr;
}


IPPFUN(IppStatus, ippsGFpECMakePoint,(const IppsGFpElement* pX, IppsGFpECPoint* pPoint, IppsGFpECState* pEC))
{
   IPP_BAD_PTR3_RET(pX, pPoint, pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFP_IS_BASIC(ECP_GFP(pEC)), ippStsBadArgErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pX), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pPoint), ippStsContextMatchErr );

   return cpEcGFpMakePoint(pPoint, GFPE_DATA(pX), pEC)? ippStsNoErr : ippStsQuadraticNonResidueErr;
}


IPPFUN(IppStatus, ippsGFpECSetPointRandom,(IppBitSupplier rndFunc, void* pRndParam,
                                           IppsGFpECPoint* pPoint, IppsGFpECState* pEC,
                                           Ipp8u* pScratchBuffer))
{
   IPP_BAD_PTR2_RET(pPoint, pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pPoint), ippStsContextMatchErr );

   IPP_BAD_PTR1_RET(rndFunc);

   {
      IppsGFpState* pGF = ECP_GFP(pEC);

      if( GFP_IS_BASIC(pGF) ) {
         BNU_CHUNK_T* pElm = cpGFpGetPool(1, pGF);

         do {
            /* get random X */
            cpGFpRand(pElm, pGF, rndFunc, pRndParam, USE_MONT_SPACE_REPRESENTATION);
         } while( !cpEcGFpMakePoint(pPoint, pElm, pEC) );

         cpGFpReleasePool(1, pGF);

         /* R = cofactor*R */
         cpEcGFpMulPoint(pPoint, pPoint, ECP_COFACTOR(pEC), GFP_FELEN(pGF), pEC, pScratchBuffer);

         return ippStsNoErr;
      }

      else {
         /* number of bits and dwords being begerated */
         int generatedBits = ECP_ORDBITSIZE(pEC) + GF_RAND_ADD_BITS;
         int generatedLen = BITS_BNU_CHUNK(generatedBits);

         /* allocate random exponent */
         int poolElements = (generatedLen + GFP_PELEN(pGF) -1) / GFP_PELEN(pGF);
         BNU_CHUNK_T* pExp = cpGFpGetPool(poolElements, pGF);

         int nsE;

         /* setup copy of the base point */
         IppsGFpECPoint G;
         cpEcGFpInitPoint(&G, ECP_G(pEC),ECP_AFFINE_POINT|ECP_FINITE_POINT, pEC);

         /* get random bits */
         rndFunc((Ipp32u*)pExp, generatedBits, pRndParam);
         /* reduce with respect to order value */
         nsE = cpMod_BNU(pExp, generatedLen, ECP_R(pEC), BITS_BNU_CHUNK(ECP_ORDBITSIZE(pEC)));

         /* compute random point */
         cpEcGFpMulPoint(pPoint, &G, pExp, nsE, pEC, pScratchBuffer);

         cpGFpReleasePool(poolElements, pGF);

         return ippStsNoErr;
      }
   }
}


IPPFUN(IppStatus, ippsGFpECGetPoint,(const IppsGFpECPoint* pPoint,
                                           IppsGFpElement* pX, IppsGFpElement* pY,
                                           IppsGFpECState* pEC))
{
   IPP_BAD_PTR2_RET(pPoint, pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pPoint), ippStsContextMatchErr );

   IPP_BADARG_RET( !IS_ECP_FINITE_POINT(pPoint), ippStsPointAtInfinity);

   IPP_BADARG_RET( pX && !GFPE_TEST_ID(pX), ippStsContextMatchErr );
   IPP_BADARG_RET( pY && !GFPE_TEST_ID(pY), ippStsContextMatchErr );

   cpEcGFpGetAffinePoint((pX)? GFPE_DATA(pX):0, (pY)?GFPE_DATA(pY):0, pPoint, pEC);
   return ippStsNoErr;
}


IPPFUN(IppStatus, ippsGFpECCpyPoint,(const IppsGFpECPoint* pA,
                                           IppsGFpECPoint* pR,
                                           IppsGFpECState* pEC))
{
   IPP_BAD_PTR3_RET(pA, pR, pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pA), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pR), ippStsContextMatchErr );

   cpEcGFpCopyPoint(pR, pA, GFP_FELEN(ECP_GFP(pEC)));
   return ippStsNoErr;
}


IPPFUN(IppStatus, ippsGFpECCmpPoint,(const IppsGFpECPoint* pP, const IppsGFpECPoint* pQ,
                                           IppECResult* pResult,
                                           IppsGFpECState* pEC))
{
   IPP_BAD_PTR4_RET(pP, pQ, pResult, pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pP), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pQ), ippStsContextMatchErr );

   *pResult = cpEcGFpIsPointEquial(pP, pQ, pEC)? ippECPointIsEqual : ippECPointIsNotEqual;
   return ippStsNoErr;
}

#if 0
IPPFUN(IppStatus, ippsGFpECTstPoint,(const IppsGFpECPoint* pP,
                                     IppECResult* pResult,
                                     IppsGFpECState* pEC,
                                     Ipp8u* pScratchBuffer))
{
   IPP_BAD_PTR3_RET(pP, pResult, pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pP), ippStsContextMatchErr );

   {
      Ipp32u elemLen = GFP_FELEN(ECP_GFP(pEC));

      if( cpEcGFpIsProjectivePointAtInfinity(pP, elemLen) )
         *pResult = ippECPointIsAtInfinite;
      else if( !cpEcGFpIsPointOnCurve(pP, pEC) )
         *pResult = ippECPointIsNotValid;
      else {
         IppsGFpECPoint T;
         cpEcGFpInitPoint(&T, cpEcGFpGetPool(1, pEC),0, pEC);
         cpEcGFpMulPoint(&T, pP, ECP_R(pEC), BITS_BNU_CHUNK(ECP_ORDBITSIZE(pEC)), pEC, pScratchBuffer);
         *pResult = cpEcGFpIsProjectivePointAtInfinity(&T, elemLen)? ippECValid : ippECPointOutOfGroup;
         cpEcGFpReleasePool(1, pEC);
      }

      return ippStsNoErr;
   }
}
#endif

/*
// Version below is based on observation has been done by Zhao Hui Du.
// See "Opportunity to improve Intel(R) EPID 2.0 performance" Gentry Mark e-mail 1/23/20015.
//
// Shortly: In case of Intel(R) EPID 2.0 EC parameters all EC points belongs to G1.
*/
IPPFUN(IppStatus, ippsGFpECTstPoint,(const IppsGFpECPoint* pP,
                                     IppECResult* pResult,
                                     IppsGFpECState* pEC,
                                     Ipp8u* pScratchBuffer))
{
   IPP_BAD_PTR3_RET(pP, pResult, pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pP), ippStsContextMatchErr );

   {
      Ipp32u elemLen = GFP_FELEN(ECP_GFP(pEC));

      if( cpEcGFpIsProjectivePointAtInfinity(pP, elemLen) )
         *pResult = ippECPointIsAtInfinite;
      else if( !cpEcGFpIsPointOnCurve(pP, pEC) )
         *pResult = ippECPointIsNotValid;
      else {
         if(EPID_PARAMS(pEC)&&GFP_IS_BASIC(ECP_GFP(pEC)))
             *pResult = ippECValid;
         else {
            IppsGFpECPoint T;
            cpEcGFpInitPoint(&T, cpEcGFpGetPool(1, pEC),0, pEC);
            cpEcGFpMulPoint(&T, pP, ECP_R(pEC), BITS_BNU_CHUNK(ECP_ORDBITSIZE(pEC)), pEC, pScratchBuffer);
            *pResult = cpEcGFpIsProjectivePointAtInfinity(&T, elemLen)? ippECValid : ippECPointOutOfGroup;
            cpEcGFpReleasePool(1, pEC);
         }
      }

      return ippStsNoErr;
   }
}


IPPFUN(IppStatus, ippsGFpECNegPoint,(const IppsGFpECPoint* pP,
                                           IppsGFpECPoint* pR,
                                           IppsGFpECState* pEC))
{
   IPP_BAD_PTR3_RET(pP, pR, pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pP), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pR), ippStsContextMatchErr );

   cpEcGFpNegPoint(pR, pP, pEC);
   return ippStsNoErr;
}


IPPFUN(IppStatus, ippsGFpECAddPoint,(const IppsGFpECPoint* pP, const IppsGFpECPoint* pQ, IppsGFpECPoint* pR,
                  IppsGFpECState* pEC))
{
   IPP_BAD_PTR4_RET(pP, pQ, pR, pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pP), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pQ), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pR), ippStsContextMatchErr );

   cpEcGFpAddPoint(pR, pP, pQ, pEC);
   return ippStsNoErr;
}

IPPFUN(IppStatus, ippsGFpECMulPoint,(const IppsGFpECPoint* pP,
                                     const IppsBigNumState* pN,
                                     IppsGFpECPoint* pR,
                                     IppsGFpECState* pEC,
                                     Ipp8u* pScratchBuffer))
{
   IPP_BAD_PTR3_RET(pP, pR, pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pP), ippStsContextMatchErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pR), ippStsContextMatchErr );

   IPP_BAD_PTR1_RET(pN);
   pN = (IppsBigNumState*)( IPP_ALIGNED_PTR(pN, BN_ALIGNMENT) );
   /* test if N >= order */
   IPP_BADARG_RET(0<=cpCmp_BNU(BN_NUMBER(pN), BN_SIZE(pN), ECP_R(pEC), BITS_BNU_CHUNK(ECP_ORDBITSIZE(pEC))), ippStsOutOfRangeErr);

   cpEcGFpMulPoint(pR, pP, BN_NUMBER(pN), BN_SIZE(pN), pEC, pScratchBuffer);
   return ippStsNoErr;
}

IPPFUN(IppStatus, ippsGFpECSetPointHash,(Ipp32u hdr, const Ipp8u* pMsg, int msgLen, IppHashID hashID, IppsGFpECPoint* pPoint,
                                         IppsGFpECState* pEC,
                                         Ipp8u* pScratchBuffer))
{
   IPP_BAD_PTR2_RET(pPoint, pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFP_IS_BASIC(ECP_GFP(pEC)), ippStsBadArgErr );
   IPP_BADARG_RET( !ECP_POINT_TEST_ID(pPoint), ippStsContextMatchErr );
   IPP_BADARG_RET( !cpTestHashID(hashID), ippStsBadArgErr);

   {
      IppsGFpState* pGF = ECP_GFP(pEC);
      int elemLen = GFP_FELEN(pGF);
      BNU_CHUNK_T* pModulus = GFP_MODULUS(pGF);

      Ipp8u md[IPP_SHA512_DIGEST_BITSIZE/BYTESIZE];
      int hashLen = cpHashLength(hashID);
      BNU_CHUNK_T hashVal[BITS_BNU_CHUNK(IPP_SHA512_DIGEST_BITSIZE)+1];
      int hashValLen;

      Ipp8u hashCtx[sizeof(IppsSHA512State)+SHA512_ALIGNMENT-1];
      cpHashInit(hashCtx, hashID);

      {
         BNU_CHUNK_T* pPoolElm = cpGFpGetPool(1, pGF);

         /* convert hdr => hdrStr */
         BNU_CHUNK_T locHdr = (BNU_CHUNK_T)hdr;
         Ipp8u hdrOctStr[sizeof(hdr/*locHdr*/)];
         cpToOctStr_BNU(hdrOctStr, sizeof(hdrOctStr), &locHdr, 1);

         /* compute md = hash(hrd||msg) */
         cpHashUpdate(hdrOctStr, sizeof(hdrOctStr), hashCtx, hashID);
         cpHashUpdate(pMsg, msgLen, hashCtx, hashID);
         cpHashFinal(md, hashCtx, hashID);

         /* convert hash into the integer */
         hashValLen = cpFromOctStr_BNU(hashVal, md, hashLen);
         hashValLen = cpMod_BNU(hashVal, hashValLen, pModulus, elemLen);
         cpGFpSet(pPoolElm, hashVal, hashValLen, pGF, USE_MONT_SPACE_REPRESENTATION);

         if( cpEcGFpMakePoint(pPoint, pPoolElm, pEC)) {
            /* set y-coordinate of the point (positive or negative) */
            BNU_CHUNK_T* pY = ECP_POINT_Y(pPoint);
            if(pY[0] & 1)
               cpGFpNeg(pY, pY, pGF);

            /* update point if cofactor>1 */
            cpEcGFpMulPoint(pPoint, pPoint, ECP_COFACTOR(pEC), GFP_FELEN(pGF), pEC, pScratchBuffer);

            cpGFpReleasePool(1, pGF);
            return ippStsNoErr;
         }
      }

      cpGFpReleasePool(1, pGF);
      return ippStsQuadraticNonResidueErr;
   }
}
