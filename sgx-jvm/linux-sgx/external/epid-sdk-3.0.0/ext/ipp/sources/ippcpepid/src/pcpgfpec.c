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
//     EC over GF(p^m) definitinons
// 
//     Context:
//        ippsGFpECGetSize()
//        ippsGFpECInit()
// 
//        ippsGFpECSet()
//        ippsGFpECGet()
//        ippsGFpECVerify()
// 
// 
*/

#include "owncpepid.h"

#include "pcpgfpecstuff.h"


IPPFUN(IppStatus, ippsGFpECGetSize,(const IppsGFpState* pGF, int* pCtxSizeInBytes))
{
   IPP_BAD_PTR2_RET(pGF, pCtxSizeInBytes);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );

   {
      int elemLen = GFP_FELEN(pGF);
      int maxOrderBits = 1+ cpGFpBasicDegreeExtension(pGF) * GFP_FEBITSIZE(cpGFpBasic(pGF));
      int maxOrdLen = BITS_BNU_CHUNK(maxOrderBits);

      *pCtxSizeInBytes = sizeof(IppsGFpECState)
                       +elemLen*sizeof(BNU_CHUNK_T)    /* EC coeff    A */
                       +elemLen*sizeof(BNU_CHUNK_T)    /* EC coeff    B */
                       +elemLen*sizeof(BNU_CHUNK_T)    /* generator G.x */
                       +elemLen*sizeof(BNU_CHUNK_T)    /* generator G.y */
                       +elemLen*sizeof(BNU_CHUNK_T)    /* generator G.z */
                       +maxOrdLen*sizeof(BNU_CHUNK_T)  /* base_point order */
                       +elemLen*sizeof(BNU_CHUNK_T)    /* cofactor */
                       +elemLen*sizeof(BNU_CHUNK_T)*3*EC_POOL_SIZE
                       +ECGFP_ALIGNMENT
                       +CACHE_LINE_SIZE;

      return ippStsNoErr;
   }
}


IPPFUN(IppStatus, ippsGFpECInit,(const IppsGFpElement* pA, const IppsGFpElement* pB,
                                 const IppsGFpElement* pX, const IppsGFpElement* pY,
                                 const Ipp32u* pOrder, int ordLen,
                                 const Ipp32u* pCofactor, int cofactorLen,
                                 IppsGFpState* pGF, IppsGFpECState* pEC))
{
   IPP_BAD_PTR2_RET(pGF, pEC);
   pGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGF), ippStsContextMatchErr );

   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );

   {
      Ipp8u* ptr = (Ipp8u*)pEC;

      int elemLen = GFP_FELEN(pGF);
      int maxOrderBits = 1+ cpGFpBasicDegreeExtension(pGF) * GFP_FEBITSIZE(cpGFpBasic(pGF));
      int maxOrdLen = BITS_BNU_CHUNK(maxOrderBits);

      ECP_ID(pEC) = idCtxGFPEC;
      ECP_FELEN(pEC) = elemLen*3;
      ECP_GFP(pEC) = pGF;
      ECP_ORDBITSIZE(pEC) = maxOrderBits;

      ptr += sizeof(IppsGFpECState);
      ECP_A(pEC) = (BNU_CHUNK_T*)(ptr);  ptr += elemLen*sizeof(BNU_CHUNK_T);
      ECP_B(pEC) = (BNU_CHUNK_T*)(ptr);  ptr += elemLen*sizeof(BNU_CHUNK_T);
      ECP_G(pEC) = (BNU_CHUNK_T*)(ptr);  ptr += elemLen*sizeof(BNU_CHUNK_T)*3;
      ECP_R(pEC) = (BNU_CHUNK_T*)(ptr);  ptr += maxOrdLen*sizeof(BNU_CHUNK_T);
      ECP_COFACTOR(pEC) = (BNU_CHUNK_T*)(ptr); ptr += elemLen*sizeof(BNU_CHUNK_T);
      ECP_POOL(pEC) = (BNU_CHUNK_T*)(ptr);  //ptr += elemLen*sizeof(Ipp32u)*EC_POOL_SIZE;

      cpGFpElementPadd(ECP_A(pEC), elemLen, 0);
      cpGFpElementPadd(ECP_B(pEC), elemLen, 0);
      cpGFpElementPadd(ECP_G(pEC), elemLen*3, 0);
      cpGFpElementPadd(ECP_R(pEC), maxOrdLen, 0);
      cpGFpElementPadd(ECP_COFACTOR(pEC), elemLen, 0);
      EPID_PARAMS(pEC) = 0;
      #if defined (_EPID20_EC_PARAM_SPECIFIC_)
      EPID_PARAMS(pEC) = 1;
      #endif
      ECP_COFACTOR(pEC)[0] = 1;

      return ippsGFpECSet(pA,pB, pX,pY, pOrder,ordLen, pCofactor, cofactorLen, pEC);
   }
}

IPPFUN(IppStatus, ippsGFpECScratchBufferSize,(int nScalars, const IppsGFpECState* pEC, int* pBufferSize))
{
   IPP_BAD_PTR2_RET(pEC, pBufferSize);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );

   IPP_BADARG_RET( (0>=nScalars)||(nScalars>LOG2_CACHE_LINE_SIZE), ippStsBadArgErr);

   {
      int pointDataSize = ECP_FELEN(pEC)*sizeof(BNU_CHUNK_T);

      /* get window_size */
      #if 0
      int w = (nScalars==1)? cpEcGFpGetOptimalWinSize(orderBitSize) : /* use optimal window size, if single-scalar operation */
                             nScalars;                                /* or pseudo-oprimal if multi-scalar operation */
      #endif
      int w = (nScalars==1)? 5 : nScalars;

      /* number of table entries */
      int nPrecomputed = 1<<w;

      *pBufferSize = pointDataSize*nPrecomputed + (CACHE_LINE_SIZE-1);

      return ippStsNoErr;
   }
}

IPPFUN(IppStatus, ippsGFpECSet,(const IppsGFpElement* pA, const IppsGFpElement* pB,
                     const IppsGFpElement* pX, const IppsGFpElement* pY,
                     const Ipp32u* pOrder, int ordLen,
                     const Ipp32u* pCofactor, int cofactorLen,
                     IppsGFpECState* pEC))
{
   IPP_BAD_PTR1_RET(pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );

   {
      IppsGFpState* pGF = ECP_GFP(pEC);
      int elemLen = GFP_FELEN(pGF);

      if(pA) {
         IPP_BADARG_RET( !GFPE_TEST_ID(pA), ippStsContextMatchErr );
         cpGFpElementCopy(ECP_A(pEC), GFPE_DATA(pA), elemLen);
         #if defined(_EPID20_EC_PARAM_SPECIFIC_)
         EPID_PARAMS(pEC) = GFP_IS_ZERO(GFPE_DATA(pA), elemLen);
         #endif
      }

      if(pB) {
         IPP_BADARG_RET( !GFPE_TEST_ID(pB), ippStsContextMatchErr );
         cpGFpElementCopy(ECP_B(pEC), GFPE_DATA(pB), elemLen);
      }

      if(pX && pY) {
         IPP_BADARG_RET( !GFPE_TEST_ID(pX), ippStsContextMatchErr );
         IPP_BADARG_RET( !GFPE_TEST_ID(pY), ippStsContextMatchErr );
         cpGFpElementCopy(ECP_G(pEC), GFPE_DATA(pX), elemLen);
         cpGFpElementCopy(ECP_G(pEC)+elemLen, GFPE_DATA(pY), elemLen);
         cpGFpElementCopyPadd(ECP_G(pEC)+elemLen*2, elemLen, MNT_1(GFP_MONT(cpGFpBasic(pGF))), GFP_FELEN(cpGFpBasic(pGF)));
      }

      if(pOrder && ordLen) {
         int inOrderBitSize;
         FIX_BNU(pOrder, ordLen);
         inOrderBitSize = BITSIZE_BNU32(pOrder, ordLen);
         IPP_BADARG_RET(inOrderBitSize>ECP_ORDBITSIZE(pEC), ippStsRangeErr)

         ECP_ORDBITSIZE(pEC) = inOrderBitSize;
         ZEXPAND_COPY_BNU((Ipp32u*)ECP_R(pEC), BITS_BNU_CHUNK(inOrderBitSize)*(int)(sizeof(BNU_CHUNK_T)/sizeof(Ipp32u)), pOrder,ordLen);
      }

      if(pCofactor) {
         int cofactorOrderBitSize;
         FIX_BNU(pCofactor, cofactorLen);
         cofactorOrderBitSize = BITSIZE_BNU32(pCofactor, cofactorLen);
         IPP_BADARG_RET(cofactorOrderBitSize>elemLen*BITSIZE(BNU_CHUNK_T), ippStsRangeErr)
         cofactorLen = BITS2WORD32_SIZE(cofactorOrderBitSize);
         ZEXPAND_COPY_BNU((Ipp32u*)ECP_COFACTOR(pEC), BITS_BNU_CHUNK(cofactorOrderBitSize)*(int)(sizeof(BNU_CHUNK_T)/sizeof(Ipp32u)), pCofactor,cofactorLen);
      }

      return ippStsNoErr;
   }
}

IPPFUN(IppStatus, ippsGFpECGet,(const IppsGFpECState* pEC,
                     const IppsGFpState** ppGF,
                     IppsGFpElement* pA, IppsGFpElement* pB,
                     IppsGFpElement* pX, IppsGFpElement* pY,
                     const Ipp32u** ppOrder, int* pOrderLen,
                     const Ipp32u** ppCofactor, int* pCofactorLen))
{
   IPP_BAD_PTR1_RET(pEC);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );

   {
      const IppsGFpState* pGF = ECP_GFP(pEC);
      Ipp32u elementSize = GFP_FELEN(pGF);

      if(ppGF) {
         *ppGF = pGF;
      }

      if(pA) {
         IPP_BADARG_RET( !GFPE_TEST_ID(pA), ippStsContextMatchErr );
         cpGFpElementCopy(GFPE_DATA(pA), ECP_A(pEC), elementSize);
      }
      if(pB) {
         IPP_BADARG_RET( !GFPE_TEST_ID(pB), ippStsContextMatchErr );
         cpGFpElementCopy(GFPE_DATA(pB), ECP_B(pEC), elementSize);
      }

      if(pX) {
         IPP_BADARG_RET( !GFPE_TEST_ID(pX), ippStsContextMatchErr );
         cpGFpElementCopy(GFPE_DATA(pX), ECP_G(pEC), elementSize);
      }
      if(pY) {
         IPP_BADARG_RET( !GFPE_TEST_ID(pY), ippStsContextMatchErr );
         cpGFpElementCopy(GFPE_DATA(pY), ECP_G(pEC)+elementSize, elementSize);
      }

      if(ppOrder) {
         *ppOrder = (Ipp32u*)ECP_R(pEC);
      }
      if(pOrderLen) {
         *pOrderLen = BITS2WORD32_SIZE(ECP_ORDBITSIZE(pEC));
      }

      if(ppCofactor) {
         *ppCofactor = (Ipp32u*)ECP_COFACTOR(pEC);
      }
      if(pCofactorLen) {
         int cofactorLen = GFP_FELEN32(pGF);
         FIX_BNU((Ipp32u*)ECP_COFACTOR(pEC), cofactorLen);
         *pCofactorLen = cofactorLen;
      }

      return ippStsNoErr;
   }
}

IPPFUN(IppStatus, ippsGFpECVerify,(IppECResult* pResult, IppsGFpECState* pEC, Ipp8u* pScratchBuffer))
{
   IPP_BAD_PTR2_RET(pEC, pResult);
   pEC = (IppsGFpECState*)( IPP_ALIGNED_PTR(pEC, ECGFP_ALIGNMENT) );
   IPP_BADARG_RET( !ECP_TEST_ID(pEC), ippStsContextMatchErr );

   *pResult = ippECValid;

   {
      IppsGFpState* pGF = ECP_GFP(pEC);
      int elemLen = GFP_FELEN(pGF);

      /*
      // check discriminant ( 4*A^3 + 27*B^2 != 0 mod P)
      */
      if(ippECValid == *pResult) {
         BNU_CHUNK_T* pT = cpGFpGetPool(1, pGF);
         BNU_CHUNK_T* pU = cpGFpGetPool(1, pGF);

         if(EPID_PARAMS(pEC))
            cpGFpElementPadd(pT, elemLen, 0);            /* T = 4*A^3 = 0 */
         else {
            pGF->add(pT, ECP_A(pEC), ECP_A(pEC), pGF);   /* T = 4*A^3 */
            pGF->sqr(pT, pT, pGF);
            pGF->mul(pT, ECP_A(pEC), pT, pGF);
         }

         pGF->add(pU, ECP_B(pEC), ECP_B(pEC), pGF);      /* U = 9*B^2 */
         pGF->add(pU, pU, ECP_B(pEC), pGF);
         pGF->sqr(pU, pU, pGF);

         pGF->add(pT, pU, pT, pGF);                      /* T += 3*U */
         pGF->add(pT, pU, pT, pGF);
         pGF->add(pT, pU, pT, pGF);

         *pResult = GFP_IS_ZERO(pT, elemLen)? ippECIsZeroDiscriminant: ippECValid;

         cpGFpReleasePool(2, pGF);
      }

      /*
      // check base point and it order
      */
      if(ippECValid == *pResult) {
         IppsGFpECPoint G;
         cpEcGFpInitPoint(&G, ECP_G(pEC), ECP_AFFINE_POINT|ECP_FINITE_POINT, pEC);

         /* check G != infinity */
         *pResult = cpEcGFpIsProjectivePointAtInfinity(&G, elemLen)? ippECPointIsAtInfinite : ippECValid;

         /* check G lies on EC */
         if(ippECValid == *pResult)
            *pResult = cpEcGFpIsPointOnCurve(&G, pEC)? ippECValid : ippECPointIsNotValid;

         /* check Gorder*G = infinity */
         if(ippECValid == *pResult) {
            IppsGFpECPoint T;
            cpEcGFpInitPoint(&T, cpEcGFpGetPool(1, pEC),0, pEC);

            cpEcGFpMulPoint(&T, &G, ECP_R(pEC), BITS_BNU_CHUNK(ECP_ORDBITSIZE(pEC)), pEC, pScratchBuffer);
            *pResult = cpEcGFpIsProjectivePointAtInfinity(&T, elemLen)? ippECValid : ippECInvalidOrder;

            cpEcGFpReleasePool(1, pEC);
         }
      }

      return ippStsNoErr;
   }
}
