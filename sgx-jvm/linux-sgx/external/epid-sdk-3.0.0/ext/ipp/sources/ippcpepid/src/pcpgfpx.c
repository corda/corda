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
//     Operations over GF(p) ectension.
// 
//     Context:
//        ippsGFpxGetSize
//        ippsGFpxInit
//        ippsGFpGetInfo
// 
*/

#include "owncpepid.h"

#include "pcpgfpstuff.h"
#include "pcpgfpxstuff.h"

/* Get context size */
IPPFUN(IppStatus, ippsGFpxGetSize, (const IppsGFpState* pGroundGF, int deg, int* pSizeInBytes))
{
   IPP_BAD_PTR2_RET(pGroundGF, pSizeInBytes);
   IPP_BADARG_RET( deg<2, ippStsBadArgErr);
   pGroundGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGroundGF, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGroundGF), ippStsContextMatchErr );

   {
      int elemGroundLen = GFP_FELEN(pGroundGF);
      int elemLen = elemGroundLen * deg;
      *pSizeInBytes = sizeof(IppsGFpState)
                     +elemLen * sizeof(BNU_CHUNK_T) /* field polynomial coeff. excluding leading 1 */
                     +elemLen * sizeof(BNU_CHUNK_T) * GFPX_POOL_SIZE /* pool of temporary variables */
                     +GFP_ALIGNMENT-1;
      return ippStsNoErr;
   }
}

static void InitGFpxCtx(const IppsGFpState* pGroundGF, int deg, IppsGFpState* pGFpx)
{
   int elemLen = deg * GFP_FELEN(pGroundGF);
   int elemLen32 = deg* GFP_FELEN32(pGroundGF);

   Ipp8u* ptr = (Ipp8u*)pGFpx + sizeof(IppsGFpState);

   /* context identifier */
   GFP_ID(pGFpx) = idCtxGFP;
   /* extension degree */
   GFP_DEGREE(pGFpx) = deg;
   /* length of element */
   GFP_FELEN(pGFpx)= elemLen;
   GFP_FELEN32(pGFpx) = elemLen32;
   GFP_PELEN(pGFpx)   = elemLen;
   FIELD_POLY_TYPE(pGFpx) = ARBITRARY;
   EPID_PARAMS(pGFpx) = 0;

   pGFpx->add = cpGFpxAdd;
   pGFpx->sub = cpGFpxSub;
   pGFpx->neg = cpGFpxNeg;
   pGFpx->mul = cpGFpxMul;
   pGFpx->sqr = cpGFpxSqr;
   pGFpx->div2= cpGFpxHalve;

   /* save ground GF() context address */
   GFP_GROUNDGF(pGFpx) = (IppsGFpState*)pGroundGF;
   /* coefficients of field polynomial */
   GFP_MODULUS(pGFpx) = (BNU_CHUNK_T*)(ptr);    ptr += elemLen * sizeof(BNU_CHUNK_T);
   /* 1/2 modulus: no matter */
   GFP_HMODULUS(pGFpx) = NULL;
   /* quadratic non-residue: no matter */
   GFP_QNR(pGFpx) = NULL;
   /* montgomery engine: no matter */
   GFP_MONT(pGFpx) = NULL;
   /* pool addresses */
   GFP_POOL(pGFpx) = (BNU_CHUNK_T*)(IPP_ALIGNED_PTR(ptr, (int)sizeof(BNU_CHUNK_T)));

   cpGFpElementPadd(GFP_MODULUS(pGFpx), elemLen, 0);
}


/* Init context by arbitrary irreducible polynomial */
IPPFUN(IppStatus, ippsGFpxInit, (const IppsGFpState* pGroundGF,
                                 const Ipp32u* pIrrPolynomial, int deg,
                                 IppsGFpState* pGFpx))
{
   IPP_BAD_PTR3_RET(pGFpx, pGroundGF, pIrrPolynomial);
   pGroundGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGroundGF, GFP_ALIGNMENT) );
   pGFpx = (IppsGFpState*)( IPP_ALIGNED_PTR(pGFpx, GFP_ALIGNMENT) );
   pGroundGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGroundGF, GFP_ALIGNMENT) );

   /* init context */
   InitGFpxCtx(pGroundGF, deg, pGFpx);

   {
      BNU_CHUNK_T* pTmp = cpGFpGetPool(1, pGFpx);

      /* copy coefficients of irresucible (except high-order 1) */
      COPY_BNU((Ipp32u*)pTmp, pIrrPolynomial, GFP_FELEN32(pGFpx));
      /* convert coefficients of irresucible into internal representation and store */
      cpGFpxSet(GFP_MODULUS(pGFpx), pTmp, GFP_FELEN(pGFpx), pGFpx, USE_MONT_SPACE_REPRESENTATION);

      cpGFpReleasePool(1, pGFpx);
      return ippStsNoErr;
   }
}

/* Init context by arbitrary irreducible binimial */
IPPFUN(IppStatus, ippsGFpxInitBinomial,(const IppsGFpState* pGroundGF,
                                        const IppsGFpElement* pGroundElm, int deg,
                                        IppsGFpState* pGFpx))
{
   IPP_BAD_PTR3_RET(pGFpx, pGroundGF, pGroundElm);
   pGroundGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGroundGF, GFP_ALIGNMENT) );
   pGFpx = (IppsGFpState*)( IPP_ALIGNED_PTR(pGFpx, GFP_ALIGNMENT) );
   pGroundGF = (IppsGFpState*)( IPP_ALIGNED_PTR(pGroundGF, GFP_ALIGNMENT) );

   /* init context */
   InitGFpxCtx(pGroundGF, deg, pGFpx);

   /* store low-order coefficient of irresucible into the context */
   cpGFpElementCopy(GFP_MODULUS(pGFpx), GFPE_DATA(pGroundElm), GFP_FELEN(pGroundGF));
   FIELD_POLY_TYPE(pGFpx) = BINOMIAL;

   /* test if field polynomial is match to Intel(R) EPID specific */
   {
      int isEpidParam = 0;

      BNU_CHUNK_T* g0 = cpGFpGetPool(1, (IppsGFpState*)pGroundGF);
      int elmLen = GFP_FELEN(pGroundGF);

      int basicExt = cpGFpBasicDegreeExtension(pGFpx);
      int basicTermLen = GFP_FELEN(cpGFpBasic(pGroundGF));

      /* convert g0 into regular representation */
      cpGFpxGet(g0, elmLen, GFPE_DATA(pGroundElm), (IppsGFpState*)pGroundGF, USE_MONT_SPACE_REPRESENTATION);

      switch(basicExt) {
      case 2:
         /* expected polynomial is g() = t^2 + (-beta),
            beta =q-1 */
         isEpidParam = cpGFpElementIsEquChunk(g0,basicTermLen, 1);
         break;
      case 6:
         /* expected polynomial is g() = t^3 + (-xi),
            xi = 2+1*t, coeffs belongs to Fq */
         cpGFpxNeg(g0, g0, (IppsGFpState*)pGroundGF);
         isEpidParam = EPID_PARAMS(pGroundGF)
                    && cpGFpElementIsEquChunk(g0,basicTermLen, 2)
                    && cpGFpElementIsEquChunk(g0+basicTermLen,basicTermLen, 1);
         break;
      case 12:
         /* expected polynomial is g() = t^2 + (-vi),
            vi = (0+0*t) + (1*t^2+0*t^3) + (0*t^4+0*t^5), coeffs belongs to Fq */
         cpGFpxNeg(g0, g0, (IppsGFpState*)pGroundGF);
         isEpidParam = EPID_PARAMS(pGroundGF)
                    && cpGFpElementIsEquChunk(g0,basicTermLen, 0)
                    && cpGFpElementIsEquChunk(g0+basicTermLen,  basicTermLen, 0)
                    && cpGFpElementIsEquChunk(g0+basicTermLen*2,basicTermLen, 1)
                    && cpGFpElementIsEquChunk(g0+basicTermLen*3,basicTermLen, 0)
                    && cpGFpElementIsEquChunk(g0+basicTermLen*4,basicTermLen, 0)
                    && cpGFpElementIsEquChunk(g0+basicTermLen*5,basicTermLen, 0);
         break;
      default:
         isEpidParam = 0;
         break;
      }
      EPID_PARAMS(pGFpx) = isEpidParam;

      cpGFpReleasePool(1, (IppsGFpState*)pGroundGF);
   }

   return ippStsNoErr;
}

/* get general info */
IPPFUN(IppStatus, ippsGFpGetInfo,(const IppsGFpState* pGFpx, IppsGFpInfo* pInfo))
{
   IPP_BAD_PTR2_RET(pGFpx, pInfo);
   pGFpx = (IppsGFpState*)( IPP_ALIGNED_PTR(pGFpx, GFP_ALIGNMENT) );
   IPP_BADARG_RET( !GFP_TEST_ID(pGFpx), ippStsContextMatchErr );

   pInfo->pBasicGF = cpGFpBasic(pGFpx);
   pInfo->pGroundGF = GFP_GROUNDGF(pGFpx);
   pInfo->basicGFdegree = cpGFpBasicDegreeExtension(pGFpx);
   pInfo->groundGFdegree = GFP_DEGREE(pGFpx);
   pInfo->elementLen = GFP_FELEN32(pGFpx);

   return ippStsNoErr;
}
