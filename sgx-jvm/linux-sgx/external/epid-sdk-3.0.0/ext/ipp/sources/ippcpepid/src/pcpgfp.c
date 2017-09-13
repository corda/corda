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
//     Intel(R) Performance Primitives. Cryptography Primitives.
//     Operations over GF(p).
// 
//     Context:
//        ippsGFpGetSize()
//        ippsGFpInit()
// 
//        ippsGFpElementGetSize()
//        ippsGFpElementInit()
// 
//        ippsGFpSetElement()
//        ippsGFpSetElementOctString()
//        ippsGFpSetElementRandom()
//        ippsGFpSetElementHash()
//        ippsGFpCpyElement()
//        ippsGFpGetElement()
//        ippsGFpGetElementOctString()
// 
//        ippsGFpCmpElement()
//        ippsGFpIsZeroElement()
//        ippsGFpIsUnityElement()
// 
//        ippsGFpSetPolyTerm()
//        ippsGFpGetPolyTerm()
// 
//        ippsGFpConj()
//        ippsGFpNeg()
//        ippsGFpInv()
//        ippsGFpSqrt()
//        ippsGFpAdd()
//        ippsGFpSub()
//        ippsGFpMul()
//        ippsGFpSqr()
//        ippsGFpExp()
//        ippsGFpMultiExp()
// 
//        ippsGFpAdd_GFpE()
//        ippsGFpSub_GFpE()
//        ippsGFpMul_GFpE()
// 
// 
*/

#include "owncpepid.h"

#include "pcpgfpstuff.h"
#include "pcpgfpxstuff.h"
#include "pcpgfphashstuff.h"


IPPFUN(IppStatus, ippsGFpGetSize,(int bitSize, int* pSizeInBytes))
{
   IPP_BAD_PTR1_RET(pSizeInBytes);
   IPP_BADARG_RET((bitSize < 2) || (bitSize > GF_MAX_BITSIZE), ippStsSizeErr);

   {
      int elemLen32 = BITS2WORD32_SIZE(bitSize);
      int elemLen = BITS_BNU_CHUNK(bitSize);
      int poolelemLen = elemLen + 1;

      int montgomeryCtxSize;
      ippsMontGetSize(ippBinaryMethod, elemLen32, &montgomeryCtxSize);

      *pSizeInBytes = sizeof(IppsGFpState)                           /* sizeof(IppsGFPState)*/
                     +elemLen*sizeof(BNU_CHUNK_T)                    /* modulus */
                     +elemLen*sizeof(BNU_CHUNK_T)                    /* half of modulus */
                     +elemLen*sizeof(BNU_CHUNK_T)                    /* quadratic non-residue */
                     +montgomeryCtxSize                              /* montgomery engine */
                     +poolelemLen*sizeof(BNU_CHUNK_T)*GF_POOL_SIZE   /* pool */
                     +CACHE_LINE_SIZE
                     +GFP_ALIGNMENT-1;
      return ippStsNoErr;
   }
}


static void gfpInitSqrt(IppsGFpState* pGF)
{
   int elemLen = GFP_FELEN(pGF);
   BNU_CHUNK_T* e = cpGFpGetPool(1, pGF);
   BNU_CHUNK_T* t = cpGFpGetPool(1, pGF);
   BNU_CHUNK_T* pMont1 = cpGFpGetPool(1, pGF);

   cpGFpElementCopyPadd(pMont1, elemLen, MNT_1(GFP_MONT(pGF)), elemLen);

   /* (modulus-1)/2 */
   cpLSR_BNU(e, GFP_MODULUS(pGF), elemLen, 1);

   /* find a non-square g, where g^{(modulus-1)/2} = -1 */
   cpGFpElementCopy(GFP_QNR(pGF), pMont1, elemLen);
   do {
      cpGFpAdd(GFP_QNR(pGF), pMont1, GFP_QNR(pGF), pGF);
      cpGFpExp(t, GFP_QNR(pGF), e, elemLen, pGF);
      cpGFpNeg(t, t, pGF);
   } while( !GFP_EQ(pMont1, t, elemLen) );

   cpGFpReleasePool(3, pGF);
}

IPPFUN(IppStatus, ippsGFpInit,(const Ipp32u* pPrime, int primeBitSize, IppsGFpState* pGF))
{
   IPP_BAD_PTR2_RET(pPrime, pGF);
   IPP_BADARG_RET((primeBitSize< 2 ) || (primeBitSize> GF_MAX_BITSIZE), ippStsSizeErr);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );

   {
      Ipp8u* ptr = (Ipp8u*)pGF;

      int elemLen32 = BITS2WORD32_SIZE(primeBitSize);
      int elemLen = BITS_BNU_CHUNK(primeBitSize);
      int poolelemLen = elemLen + 1;
      int montgomeryCtxSize;
      ippsMontGetSize(ippBinaryMethod, elemLen32, &montgomeryCtxSize);

      GFP_ID(pGF)      = idCtxGFP;
      GFP_DEGREE(pGF)  = 1;
      GFP_FELEN(pGF)   = elemLen;
      GFP_FELEN32(pGF) = elemLen32;
      GFP_PELEN(pGF)   = poolelemLen;
      FIELD_POLY_TYPE(pGF) = ARBITRARY;

      #if(_IPP_ARCH==_IPP_ARCH_EM64T)
      /* 192 < primeBitSize <= 256 is considered as Intel(R) EPID param */
      EPID_PARAMS(pGF) = elemLen==4;
      #else
      EPID_PARAMS(pGF) = 0;
      #endif

      GFP_GROUNDGF(pGF)= pGF;

      /* methods */
      pGF->add = cpGFpAdd;
      pGF->sub = cpGFpSub;
      pGF->neg = cpGFpNeg;
      pGF->mul = cpGFpMul;
      pGF->sqr = cpGFpSqr;
      pGF->div2= cpGFpHalve;

      #if(_IPP32E >= _IPP32E_M7)
      if(EPID_PARAMS(pGF)) {
         pGF->add = cp256pAdd;
         pGF->sub = cp256pSub;
         pGF->neg = cp256pNeg;
         pGF->mul = cp256pMul;
         pGF->sqr = cp256pSqr;
         pGF->div2= cp256pHalve;
      }
      #endif

      ptr += sizeof(IppsGFpState);
      GFP_MODULUS(pGF)  = (BNU_CHUNK_T*)(ptr);    ptr += elemLen*sizeof(BNU_CHUNK_T);
      GFP_HMODULUS(pGF) = (BNU_CHUNK_T*)(ptr);    ptr += elemLen*sizeof(BNU_CHUNK_T);
      GFP_QNR(pGF)      = (BNU_CHUNK_T*)(ptr);    ptr += elemLen*sizeof(BNU_CHUNK_T);
      GFP_MONT(pGF)     = (IppsMontState*)( IPP_ALIGNED_PTR((ptr), (MONT_ALIGNMENT)) ); ptr += montgomeryCtxSize;
      GFP_POOL(pGF)     = (BNU_CHUNK_T*)(IPP_ALIGNED_PTR(ptr, (int)sizeof(BNU_CHUNK_T)));

      ippsMontInit(ippBinaryMethod, elemLen32, GFP_MONT(pGF));
      ippsMontSet(pPrime, elemLen32, GFP_MONT(pGF));

      /* modulus */
      cpGFpElementPadd(GFP_MODULUS(pGF), elemLen, 0);
      COPY_BNU((Ipp32u*)GFP_MODULUS(pGF), pPrime, elemLen32);
      /* half of modulus */
      cpGFpElementPadd(GFP_HMODULUS(pGF), elemLen, 0);
      cpLSR_BNU(GFP_HMODULUS(pGF), GFP_MODULUS(pGF), elemLen, 1);

      /* do some additional initialization to make sqrt operation faster */
      cpGFpElementPadd(GFP_QNR(pGF), elemLen, 0);
      gfpInitSqrt(pGF);

      return ippStsNoErr;
   }
}


IPPFUN(IppStatus, ippsGFpScratchBufferSize,(int nExponents, int ExpBitSize, const IppsGFpState* pGF, int* pBufferSize))
{
   IPP_BAD_PTR2_RET(pGF, pBufferSize);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );

   IPP_BADARG_RET( (0>=nExponents)||(nExponents>LOG2_CACHE_LINE_SIZE), ippStsBadArgErr);

   {
      int elmDataSize = GFP_FELEN(pGF)*sizeof(BNU_CHUNK_T);

      /* get window_size */
      int w = (nExponents==1)? cpGFpGetOptimalWinSize(ExpBitSize) : /* use optimal window size, if single-scalar operation */
                               nExponents;                          /* or pseudo-oprimal if multi-scalar operation */

      /* number of table entries */
      int nPrecomputed = 1<<w;

      *pBufferSize = elmDataSize*nPrecomputed + (CACHE_LINE_SIZE-1);

      return ippStsNoErr;
   }
}

#if 0
IPPFUN(IppStatus, ippsBasicGFpRef,(const IppsGFpState* pGF, IppsGFpState** ppBasicGF))
{
   IPP_BAD_PTR2_RET(pGF, ppBasicGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );

   *ppBasicGF = cpGFpBasic(pGF);
   return ippStsNoErr;
}
#endif

#if 0
IPPFUN(IppStatus, ippsGroundGFpRef,(const IppsGFpState* pGF, IppsGFpState** ppGroundGF))
{
   IPP_BAD_PTR2_RET(pGF, ppGroundGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );

   *ppGroundGF = GFP_GROUNDGF(pGF);
   return ippStsNoErr;
}
#endif

#if 0
IPPFUN(IppStatus, ippsGFpGetDegree,(const IppsGFpState* pGF, int* pDegree))
{
   IPP_BAD_PTR2_RET(pGF, pDegree);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );

   *pDegree = GFP_DEGREE(pGF);
   return ippStsNoErr;
}
#endif

#if 0
IPPFUN(IppStatus, ippsGFpGetElementLen,(const IppsGFpState* pGF, int* pElmLen))
{
   IPP_BAD_PTR2_RET(pGF, pElmLen);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );

   *pElmLen = GFP_FELEN32(pGF);
   return ippStsNoErr;
}
#endif


IPPFUN(IppStatus, ippsGFpGetModulus,(const IppsGFpState* pGF, Ipp32u* pModulus))
{
   IPP_BAD_PTR2_RET(pGF, pModulus);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );

   if( GFP_IS_BASIC(pGF) ) {
      cpGFpxCopyFromChunk(pModulus, GFP_MODULUS(pGF), pGF);
   }
   else {
      int elemLen32 = GFP_FELEN32(pGF);
      int elemLen = GFP_FELEN(pGF);
      BNU_CHUNK_T* pTmp = cpGFpGetPool(1, (IppsGFpState*)pGF);

      cpGFpxGet(pTmp, elemLen, GFP_MODULUS(pGF), (IppsGFpState*)pGF, USE_MONT_SPACE_REPRESENTATION);
      cpGFpxCopyFromChunk(pModulus, pTmp, pGF);
      pModulus[elemLen32] = 1;

      cpGFpReleasePool(1, (IppsGFpState*)pGF);
   }
   return ippStsNoErr;
}

#if 0
IPPFUN(IppStatus, ippsGFpCmp, (const IppsGFpState* pGFp1, const IppsGFpState* pGFp2, IppGFpResult* pCmpResult))
{
   IPP_BAD_PTR3_RET(pGFp1, pGFp2, pCmpResult);
   pGFp1 = (IppsGFpState*)( IPP_ALIGNED_PTR(pGFp1, GFP_ALIGNMENT) );
   pGFp2 = (IppsGFpState*)( IPP_ALIGNED_PTR(pGFp2, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGFp1), ippStsContextMatchErr);
   IPP_BADARG_RET( !GFP_TEST_ID(pGFp2), ippStsContextMatchErr);

   if(pGFp1 != pGFp2) {
      int flag = cpGFpxCompare(pGFp1, pGFp2);
      *pCmpResult = 0==flag? ippGFpEQ : 1==flag? ippGFpNE : ippGFpNA;
   }
   else
      *pCmpResult = ippGFpEQ;
   return ippStsNoErr;
}
#endif

IPPFUN(IppStatus, ippsGFpElementGetSize,(const IppsGFpState* pGF, int* pElementSize))
{
   IPP_BAD_PTR2_RET(pElementSize, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );

   *pElementSize = sizeof(IppsGFpElement)
                  +GFP_FELEN(pGF)*sizeof(BNU_CHUNK_T);
   return ippStsNoErr;
}

IPPFUN(IppStatus, ippsGFpElementInit,(const Ipp32u* pA, int nsA, IppsGFpElement* pR, IppsGFpState* pGF))
{
   IPP_BAD_PTR2_RET(pR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );

   IPP_BADARG_RET(0>nsA, ippStsSizeErr);

   {
      int elemLen = GFP_FELEN(pGF);

      Ipp8u* ptr = (Ipp8u*)pR;

      GFPE_ID(pR) = idCtxGFPE;
      GFPE_ROOM(pR) = elemLen;
      ptr += sizeof(IppsGFpElement);
      GFPE_DATA(pR) = (BNU_CHUNK_T*)ptr;

      return ippsGFpSetElement(pA, nsA, pR, pGF);
   }
}

IPPFUN(IppStatus, ippsGFpSetElement,(const Ipp32u* pDataA, int nsA, IppsGFpElement* pElm, IppsGFpState* pGF))
{
   IPP_BAD_PTR2_RET(pElm, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElm), ippStsContextMatchErr );

   IPP_BADARG_RET( (pDataA && (nsA<0)), ippStsSizeErr );

   {
      IppStatus sts = ippStsNoErr;

      int elemLen32 = GFP_FELEN32(pGF);
      if(pDataA) FIX_BNU(pDataA, nsA);
      if(pDataA && (nsA>elemLen32)) IPP_ERROR_RET(ippStsOutOfRangeErr);

      {
         BNU_CHUNK_T* pTmp = cpGFpGetPool(1, pGF);
         int elemLen = GFP_FELEN(pGF);
         ZEXPAND_BNU(pTmp, 0, elemLen);
         if(pDataA)
            cpGFpxCopyToChunk(pTmp, pDataA, nsA, pGF);

         if(!cpGFpxSet(GFPE_DATA(pElm), pTmp, elemLen, pGF, USE_MONT_SPACE_REPRESENTATION))
            sts = ippStsOutOfRangeErr;

         cpGFpReleasePool(1, pGF);
      }

      return sts;
   }
}

IPPFUN(IppStatus, ippsGFpSetElementOctString,(const Ipp8u* pStr, int strSize, IppsGFpElement* pElm, IppsGFpState* pGF))
{
   IPP_BAD_PTR3_RET(pStr, pElm, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElm), ippStsContextMatchErr );

   {
      IppsGFpState* pBasicGF = cpGFpBasic(pGF);
      int basicDeg = cpGFpBasicDegreeExtension(pGF);
      int basicElemLen = GFP_FELEN(pBasicGF);
      int basicSize = BITS2WORD8_SIZE(BITSIZE_BNU(GFP_MODULUS(pBasicGF),GFP_FELEN(pBasicGF)));

      BNU_CHUNK_T* pDataElm = GFPE_DATA(pElm);

      int deg, error;
      /* set element to zero */
      cpGFpElementPadd(pDataElm, GFP_FELEN(pGF), 0);

      /* convert oct string to element (from low to high) */
      for(deg=0, error=0; deg<basicDeg && !error; deg++) {
         int size = IPP_MIN(strSize, basicSize);
         error = NULL == cpGFpSetOctString(pDataElm, pStr, size, pBasicGF, USE_MONT_SPACE_REPRESENTATION);

         pDataElm += basicElemLen;
         strSize -= size;
         pStr += size;
      }

      return error? ippStsOutOfRangeErr : ippStsNoErr;
   }
}


#if 0
IPPFUN(IppStatus, ippsGFPSetElementPower2,(Ipp32u power, IppsGFPElement* pR, IppsGFPState* pGFp))
{
   IPP_BAD_PTR2_RET(pR, pGFp);
   IPP_BADARG_RET( !GFP_TEST_ID(pGFp), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pR), ippStsContextMatchErr );
   IPP_BADARG_RET( (power+1) > GF_POOL_SIZE*GFP_PESIZE(pGFp)*BITSIZE(ipp32u), ippStsBadArgErr);

   {
      Ipp32u moduloBitSize = GFP_FESIZE32(pGFp)*BITSIZE(ipp32u) - NLZ32u(GFP_MODULUS(pGFp)[GFP_FESIZE32(pGFp)-1]);
      if(moduloBitSize>power) {
         gfpFFelementPadd(0, GFPE_DATA(pR), GFP_FELEN(pGFp));
         SET_BIT(GFPE_DATA(pR), power);
      }
      else {
         Ipp32u dataLen = BITS2WORD32_SIZE(power+1);
         Ipp32u* pData = GFP_POOL(pGFp);
         gfpFFelementPadd(0, pData, dataLen);
         SET_BIT(pData, power);
         gfpReduce(pData, dataLen, GFPE_DATA(pR), pGFp);
      }
      return ippStsNoErr;
   }
}
#endif


IPPFUN(IppStatus, ippsGFpSetElementRandom,(IppBitSupplier rndFunc, void* pRndParam,
                                           IppsGFpElement* pElm, IppsGFpState* pGF))
{
   IPP_BAD_PTR2_RET(pElm, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElm), ippStsContextMatchErr );

   cpGFpxRand(GFPE_DATA(pElm), pGF, rndFunc, pRndParam, USE_MONT_SPACE_REPRESENTATION);
   return ippStsNoErr;
}

IPPFUN(IppStatus, ippsGFpCpyElement, (const IppsGFpElement* pElmA, IppsGFpElement* pElmR, IppsGFpState* pGF))
{
   IPP_BAD_PTR3_RET(pElmA, pElmR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmR), ippStsContextMatchErr );

   cpGFpElementCopy(GFPE_DATA(pElmR), GFPE_DATA(pElmA), GFP_FELEN(pGF));
   return ippStsNoErr;
}

IPPFUN(IppStatus, ippsGFpGetElement, (const IppsGFpElement* pElm, Ipp32u* pDataA, int nsA, IppsGFpState* pGF))
{
   IPP_BAD_PTR3_RET(pElm, pDataA, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElm), ippStsContextMatchErr );
   IPP_BADARG_RET( 0>nsA, ippStsSizeErr );

   {
      int elemLen = GFP_FELEN(pGF);
      BNU_CHUNK_T* pTmp = cpGFpGetPool(1, pGF);

      cpGFpxGet(pTmp, elemLen, GFPE_DATA(pElm), pGF, USE_MONT_SPACE_REPRESENTATION);
      cpGFpxCopyFromChunk(pDataA, pTmp, pGF);

      cpGFpReleasePool(1, pGF);
      return ippStsNoErr;
   }
}

IPPFUN(IppStatus, ippsGFpGetElementOctString,(const IppsGFpElement* pElm, Ipp8u* pStr, int strSize, IppsGFpState* pGF))
{
   IPP_BAD_PTR3_RET(pStr, pElm, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElm), ippStsContextMatchErr );

   {
      IppsGFpState* pBasicGF = cpGFpBasic(pGF);
      int basicDeg = cpGFpBasicDegreeExtension(pGF);
      int basicElemLen = GFP_FELEN(pBasicGF);
      int basicSize = BITS2WORD8_SIZE(BITSIZE_BNU(GFP_MODULUS(pBasicGF),GFP_FELEN(pBasicGF)));

      BNU_CHUNK_T* pDataElm = GFPE_DATA(pElm);

      int deg;
      for(deg=0; deg<basicDeg; deg++) {
         int size = IPP_MIN(strSize, basicSize);
         cpGFpGetOctString(pStr, size, pDataElm, pBasicGF, USE_MONT_SPACE_REPRESENTATION);

         pDataElm += basicElemLen;
         pStr += size;
         strSize -= size;
      }

      return ippStsNoErr;
   }
}

IPPFUN(IppStatus, ippsGFpCmpElement,(const IppsGFpElement* pElmA, const IppsGFpElement* pElmB,
                                     int* pResult,
                                     const IppsGFpState* pGF))
{
   IPP_BAD_PTR4_RET(pElmA, pElmB, pResult, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmB), ippStsContextMatchErr );

   {
      int flag = cpGFpElementCmp(GFPE_DATA(pElmA), GFPE_DATA(pElmB), GFP_FELEN(pGF));
      if( GFP_IS_BASIC(pGF) )
         *pResult = (0==flag)? IPP_IS_EQ : (0<flag)? IPP_IS_GT : IPP_IS_LT;
      else
         *pResult = (0==flag)? IPP_IS_EQ : IPP_IS_NE;
      return ippStsNoErr;
   }
}

IPPFUN(IppStatus, ippsGFpIsZeroElement,(const IppsGFpElement* pElmA,
                                     int* pResult,
                                     const IppsGFpState* pGF))
{
   IPP_BAD_PTR3_RET(pElmA, pResult, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );

   {
      int flag = GFP_IS_ZERO(GFPE_DATA(pElmA), GFP_FELEN(pGF));
      *pResult = (1==flag)? IPP_IS_EQ : IPP_IS_NE;
      return ippStsNoErr;
   }
}

IPPFUN(IppStatus, ippsGFpIsUnityElement,(const IppsGFpElement* pElmA,
                                     int* pResult,
                                     const IppsGFpState* pGF))
{
   IPP_BAD_PTR3_RET(pElmA, pResult, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );

   {
      IppsGFpState* pBasicGF = cpGFpBasic(pGF);
      int basicElmLen = GFP_FELEN(pBasicGF);
      BNU_CHUNK_T* pUnity = MNT_1(GFP_MONT(pBasicGF));

      int elmLen = GFP_FELEN(pGF);
      int flag;

      FIX_BNU(pUnity, basicElmLen);
      FIX_BNU(GFPE_DATA(pElmA), elmLen);

      flag = (basicElmLen==elmLen) && (0 == cpGFpElementCmp(GFPE_DATA(pElmA), pUnity, elmLen));
      *pResult = (1==flag)? IPP_IS_EQ : IPP_IS_NE;
      return ippStsNoErr;
   }
}

#if 0
IPPFUN(IppStatus, ippsGFpSetPolyTerm,(const Ipp32u* pTerm, int nsT, IppsGFpElement* pElm, int termDegree, IppsGFpState* pGF))
{
   IPP_BAD_PTR3_RET(pTerm, pElm, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElm), ippStsContextMatchErr );
   IPP_BADARG_RET( 0>nsT, ippStsSizeErr );

   if(termDegree>=0 && termDegree * GFP_FELEN(pGF) < GFPE_ROOM(pElm) )
      cpGFpxSetPolyTerm(GFPE_DATA(pElm), termDegree, pTerm, nsT, pGF, USE_MONT_SPACE_REPRESENTATION);
   return ippStsNoErr;
}
#endif

#if 0
IPPFUN(IppStatus, ippsGFpGetPolyTerm, (const IppsGFpElement* pElm, int termDegree, Ipp32u* pTerm, int nsT, IppsGFpState* pGF))
{
   IPP_BAD_PTR3_RET(pElm, pTerm, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElm), ippStsContextMatchErr );
   IPP_BADARG_RET( 0>nsT, ippStsSizeErr );

   cpGFpElementPadd(pTerm, nsT, 0);
   if(termDegree>=0 && termDegree * GFP_FELEN(pGF) < GFPE_ROOM(pElm) )
      cpGFpxGetPolyTerm(pTerm, nsT, GFPE_DATA(pElm), termDegree, pGF, USE_MONT_SPACE_REPRESENTATION);
   return ippStsNoErr;
}
#endif

IPPFUN(IppStatus, ippsGFpConj,(const IppsGFpElement* pElmA,
                                     IppsGFpElement* pElmR, IppsGFpState* pGF))
{
   IPP_BAD_PTR3_RET(pElmA, pElmR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( 2!=GFP_DEGREE(pGF), ippStsBadArgErr )
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmR), ippStsContextMatchErr );

   cpGFpxConj(GFPE_DATA(pElmR), GFPE_DATA(pElmA), pGF);
   return ippStsNoErr;
}

IPPFUN(IppStatus, ippsGFpNeg,(const IppsGFpElement* pElmA,
                                    IppsGFpElement* pElmR, IppsGFpState* pGF))
{
   IPP_BAD_PTR3_RET(pElmA, pElmR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmR), ippStsContextMatchErr );

   pGF->neg(GFPE_DATA(pElmR), GFPE_DATA(pElmA), pGF);
   return ippStsNoErr;
}


IPPFUN(IppStatus, ippsGFpInv,(const IppsGFpElement* pElmA,
                                    IppsGFpElement* pElmR, IppsGFpState* pGF))
{
   IPP_BAD_PTR3_RET(pElmA, pElmR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmR), ippStsContextMatchErr );
   IPP_BADARG_RET( GFP_IS_ZERO(GFPE_DATA(pElmA),GFP_FELEN(pGF)), ippStsDivByZeroErr );

   return NULL != cpGFpxInv(GFPE_DATA(pElmR), GFPE_DATA(pElmA), pGF)? ippStsNoErr : ippStsBadArgErr;
}


IPPFUN(IppStatus, ippsGFpSqrt,(const IppsGFpElement* pElmA,
                                    IppsGFpElement* pElmR, IppsGFpState* pGF))
{
   IPP_BAD_PTR3_RET(pElmA, pElmR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFP_IS_BASIC(pGF), ippStsBadArgErr )
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmR), ippStsContextMatchErr );

   return cpGFpSqrt(GFPE_DATA(pElmR), GFPE_DATA(pElmA), pGF)? ippStsNoErr : ippStsQuadraticNonResidueErr;
}


IPPFUN(IppStatus, ippsGFpAdd,(const IppsGFpElement* pElmA, const IppsGFpElement* pElmB,
                                    IppsGFpElement* pElmR, IppsGFpState* pGF))
{
   IPP_BAD_PTR4_RET(pElmA, pElmB, pElmR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmB), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmR), ippStsContextMatchErr );

   pGF->add(GFPE_DATA(pElmR), GFPE_DATA(pElmA), GFPE_DATA(pElmB), pGF);
   return ippStsNoErr;
}


IPPFUN(IppStatus, ippsGFpSub,(const IppsGFpElement* pElmA, const IppsGFpElement* pElmB,
                                    IppsGFpElement* pElmR, IppsGFpState* pGF))
{
   IPP_BAD_PTR4_RET(pElmA, pElmB, pElmR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmB), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmR), ippStsContextMatchErr );

   pGF->sub(GFPE_DATA(pElmR), GFPE_DATA(pElmA), GFPE_DATA(pElmB), pGF);
   return ippStsNoErr;
}

IPPFUN(IppStatus, ippsGFpMul,(const IppsGFpElement* pElmA, const IppsGFpElement* pElmB,
                                    IppsGFpElement* pElmR, IppsGFpState* pGF))
{
   IPP_BAD_PTR4_RET(pElmA, pElmB, pElmR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmB), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmR), ippStsContextMatchErr );

   pGF->mul(GFPE_DATA(pElmR), GFPE_DATA(pElmA), GFPE_DATA(pElmB), pGF);
   return ippStsNoErr;
}

IPPFUN(IppStatus, ippsGFpSqr,(const IppsGFpElement* pElmA,
                                    IppsGFpElement* pElmR, IppsGFpState* pGF))
{
   IPP_BAD_PTR3_RET(pElmA, pElmR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmR), ippStsContextMatchErr );

   pGF->sqr(GFPE_DATA(pElmR), GFPE_DATA(pElmA), pGF);
   return ippStsNoErr;
}

IPPFUN(IppStatus, ippsGFpAdd_GFpE,(const IppsGFpElement* pElmA, const IppsGFpElement* pGroundElmB,
                                    IppsGFpElement* pElmR, IppsGFpState* pGF))
{
   IPP_BAD_PTR4_RET(pElmA, pGroundElmB, pElmR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( GFP_IS_BASIC(pGF), ippStsBadArgErr )
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pGroundElmB), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmR), ippStsContextMatchErr );

   cpGFpxAdd_GFE(GFPE_DATA(pElmR), GFPE_DATA(pElmA), GFPE_DATA(pGroundElmB), pGF);
   return ippStsNoErr;
}

IPPFUN(IppStatus, ippsGFpSub_GFpE,(const IppsGFpElement* pElmA, const IppsGFpElement* pGroundElmB,
                                    IppsGFpElement* pElmR, IppsGFpState* pGF))
{
   IPP_BAD_PTR4_RET(pElmA, pGroundElmB, pElmR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( GFP_IS_BASIC(pGF), ippStsBadArgErr )
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pGroundElmB), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmR), ippStsContextMatchErr );

   cpGFpxSub_GFE(GFPE_DATA(pElmR), GFPE_DATA(pElmA), GFPE_DATA(pGroundElmB), pGF);
   return ippStsNoErr;
}

IPPFUN(IppStatus, ippsGFpMul_GFpE,(const IppsGFpElement* pElmA, const IppsGFpElement* pGroundElmB,
                                    IppsGFpElement* pElmR, IppsGFpState* pGF))
{
   IPP_BAD_PTR4_RET(pElmA, pGroundElmB, pElmR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( GFP_IS_BASIC(pGF), ippStsBadArgErr )
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pGroundElmB), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmR), ippStsContextMatchErr );

   cpGFpxMul_GFE(GFPE_DATA(pElmR), GFPE_DATA(pElmA), GFPE_DATA(pGroundElmB), pGF);
   return ippStsNoErr;
}

IPPFUN(IppStatus, ippsGFpExp,(const IppsGFpElement* pElmA, const IppsBigNumState* pE,
                                    IppsGFpElement* pElmR, IppsGFpState* pGF,
                                    Ipp8u* pScratchBuffer))
{
   IPP_BAD_PTR4_RET(pElmA, pE, pElmR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmR), ippStsContextMatchErr );

   IPP_BADARG_RET( !BN_VALID_ID(pE), ippStsContextMatchErr );
   IPP_BADARG_RET( BN_SIZE(pE) > GFP_FELEN(pGF), ippStsRangeErr );

   cpGFpxExp(GFPE_DATA(pElmR), GFPE_DATA(pElmA), BN_NUMBER(pE), BN_SIZE(pE), pGF, pScratchBuffer);

   return ippStsNoErr;
}

IPPFUN(IppStatus, ippsGFpMultiExp,(const IppsGFpElement* const ppElmA[], const IppsBigNumState* const ppE[], int nItems,
                                    IppsGFpElement* pElmR, IppsGFpState* pGF,
                                    Ipp8u* pScratchBuffer))
{
   IPP_BAD_PTR2_RET(pElmR, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );
   IPP_BADARG_RET( !GFPE_TEST_ID(pElmR), ippStsContextMatchErr );

   IPP_BAD_PTR2_RET(ppElmA, ppE);

   if(nItems==1)
      return ippsGFpExp(ppElmA[0], ppE[0], pElmR, pGF, pScratchBuffer);

   else {
      int n;
      for(n=0; n<nItems; n++) {
         const IppsGFpElement* pElmA = ppElmA[n];
         const IppsBigNumState* pE = ppE[n];
         IPP_BADARG_RET( !GFPE_TEST_ID(pElmA), ippStsContextMatchErr );
         IPP_BADARG_RET( !BN_VALID_ID(pE), ippStsContextMatchErr );
         IPP_BADARG_RET( BN_SIZE(pE) > GFP_FELEN(pGF), ippStsRangeErr );
      }

      if(NULL==pScratchBuffer) {
         BNU_CHUNK_T* pTmpR = cpGFpGetPool(1, pGF);
         cpGFpxExp(GFPE_DATA(pElmR), GFPE_DATA(ppElmA[0]), BN_NUMBER(ppE[0]), BN_SIZE(ppE[0]), pGF, 0);
         for(n=1; n<nItems; n++) {
            cpGFpxExp(pTmpR, GFPE_DATA(ppElmA[n]), BN_NUMBER(ppE[n]), BN_SIZE(ppE[n]), pGF, 0);
            cpGFpxMul(GFPE_DATA(pElmR), GFPE_DATA(pElmR), pTmpR, pGF);
         }
         cpGFpReleasePool(1, pGF);
      }
      else {
         const BNU_CHUNK_T* ppAdata[LOG2_CACHE_LINE_SIZE];
         const BNU_CHUNK_T* ppEdata[LOG2_CACHE_LINE_SIZE];
         int nsEdataLen[LOG2_CACHE_LINE_SIZE];
         for(n=0; n<nItems; n++) {
            ppAdata[n] = GFPE_DATA(ppElmA[n]);
            ppEdata[n] = BN_NUMBER(ppE[n]);
            nsEdataLen[n] = BN_SIZE(ppE[n]);
         }
         cpGFpxMultiExp(GFPE_DATA(pElmR), ppAdata, ppEdata, nsEdataLen, nItems, pGF, pScratchBuffer);
      }
      return ippStsNoErr;
   }
}

IPPFUN(IppStatus, ippsGFpSetElementHash,(const Ipp8u* pMsg, int msgLen, IppHashID hashID, IppsGFpElement* pElm, IppsGFpState* pGF))
{
   IPP_BAD_PTR2_RET(pElm, pGF);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr);
   IPP_BADARG_RET( !GFP_IS_BASIC(pGF), ippStsBadArgErr);
   IPP_BADARG_RET( !GFPE_TEST_ID(pElm), ippStsContextMatchErr);
   IPP_BADARG_RET( !cpTestHashID(hashID), ippStsBadArgErr);

   {
      Ipp8u md[IPP_SHA512_DIGEST_BITSIZE/BYTESIZE];
      BNU_CHUNK_T hashVal[IPP_SHA512_DIGEST_BITSIZE/BITSIZE(BNU_CHUNK_T)+1]; /* +1 to meet cpMod_BNU() implementtaion specific */
      IppStatus sts = cpHashMessage(pMsg, msgLen, md, hashID);

      if(ippStsNoErr==sts) {
         int hashValLen = cpFromOctStr_BNU(hashVal, md, cpHashLength(hashID));
         int elemLen = GFP_FELEN(pGF);
         hashValLen = cpMod_BNU(hashVal, hashValLen, GFP_MODULUS(pGF), elemLen);
         cpGFpSet(GFPE_DATA(pElm), hashVal, hashValLen, pGF, USE_MONT_SPACE_REPRESENTATION);
      }
      return sts;
   }
}
