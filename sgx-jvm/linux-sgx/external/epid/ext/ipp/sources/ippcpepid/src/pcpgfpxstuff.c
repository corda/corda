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
//     Internal operations over GF(p) extension.
// 
//     Context:
//        cpGFpxCmpare
//        cpGFpxSet
//        cpGFpxRand
//        cpGFpxGet
// 
//        cpGFpxHalve
//        cpGFpxAdd, cpGFpxAdd_GFE
//        cpGFpxSub, cpGFpxSub_GFE
//        cpGFpxMul, cpGFpxMul_GFE
//        cpGFp2biMul, cpGFp3biMul, cpGFpxMul_G0
//        cpGFpxSqr
//        cpGFp2biSqr, cpGFp3biSqr
//        cpGFpxNeg
//        cpGFpxInv
//        cpGFpxExp
//        cpGFpxMultiExp
//        cpGFpxConj
// 
// 
*/

#include "owncpepid.h"

#include "pcpgfpxstuff.h"
//#include "pcptool.h"


/*
// compare GF.
// returns:
//    0 - are equial
//    1 - are different
//    2 - different structure
*/
int cpGFpxCompare(const IppsGFpState* pGFpx1, const IppsGFpState* pGFpx2)
{
   while( !GFP_IS_BASIC(pGFpx1) && !GFP_IS_BASIC(pGFpx2) ) {
      if( GFP_DEGREE(pGFpx1) != GFP_DEGREE(pGFpx2) )
         return 2;
      if( GFP_FELEN(pGFpx1) != GFP_FELEN(pGFpx2) )
         return 1;
      if(0 != cpGFpElementCmp(GFP_MODULUS(pGFpx1), GFP_MODULUS(pGFpx1), GFP_FELEN(pGFpx1)) )
         return 1;
      pGFpx1 = GFP_GROUNDGF(pGFpx1);
      pGFpx2 = GFP_GROUNDGF(pGFpx2);
   }

   return (GFP_IS_BASIC(pGFpx1) && GFP_IS_BASIC(pGFpx2))? cpGFpCompare(pGFpx1, pGFpx2) : 2;
}

BNU_CHUNK_T* cpGFpxRand(BNU_CHUNK_T* pR, IppsGFpState* pGFpx, IppBitSupplier rndFunc, void* pRndParam, int montSpace)
{
   if( GFP_IS_BASIC(pGFpx) )
      return cpGFpRand(pR, pGFpx, rndFunc, pRndParam, montSpace);

   else {
      IppsGFpState* pBasicGF = cpGFpBasic(pGFpx);
      int basicElemLen = GFP_FELEN(pBasicGF);
      int basicDeg = cpGFpBasicDegreeExtension(pGFpx);

      BNU_CHUNK_T* pTmp = pR;
      int deg;
      for(deg=0; deg<basicDeg; deg++) {
         cpGFpRand(pTmp, pBasicGF, rndFunc, pRndParam, montSpace);
         pTmp += basicElemLen;
      }
      return pR;
   }
}

BNU_CHUNK_T* cpGFpxSet(BNU_CHUNK_T* pE, const BNU_CHUNK_T* pDataA, int nsA, IppsGFpState* pGFpx, int montSpace)
{
   if( GFP_IS_BASIC(pGFpx) )
      return cpGFpSet(pE, pDataA, nsA, pGFpx, montSpace);

   else {
      IppsGFpState* pBasicGF = cpGFpBasic(pGFpx);
      int basicElemLen = GFP_FELEN(pBasicGF);

      BNU_CHUNK_T* pTmpE = pE;
      int basicDeg = cpGFpBasicDegreeExtension(pGFpx);

      int deg, error;
      for(deg=0, error=0; deg<basicDeg && !error; deg++) {
         int pieceA = IPP_MIN(nsA, basicElemLen);

         error = NULL == cpGFpSet(pTmpE, pDataA, pieceA, pBasicGF, montSpace);
         pTmpE   += basicElemLen;
         pDataA += pieceA;
         nsA -= pieceA;
      }

      return (deg<basicDeg)? NULL : pE;
   }
}

BNU_CHUNK_T* cpGFpxSetPolyTerm(BNU_CHUNK_T* pE, int deg, const BNU_CHUNK_T* pDataA, int nsA, IppsGFpState* pGFpx, int montSpace)
{
   pE += deg * GFP_FELEN(pGFpx);
   return cpGFpxSet(pE, pDataA, nsA, pGFpx, montSpace);
}

BNU_CHUNK_T* cpGFpxGet(BNU_CHUNK_T* pDataA, int nsA, const BNU_CHUNK_T* pE, IppsGFpState* pGFpx, int montSpace)
{
   cpGFpElementPadd(pDataA, nsA, 0);

   if( GFP_IS_BASIC(pGFpx) )
      return cpGFpGet(pDataA, nsA, pE, pGFpx, montSpace);

   else {
      IppsGFpState* pBasicGF = cpGFpBasic(pGFpx);
      int basicElemLen = GFP_FELEN(pBasicGF);

      BNU_CHUNK_T* pTmp = pDataA;
      int basicDeg = cpGFpBasicDegreeExtension(pGFpx);

      int deg;
      for(deg=0; deg<basicDeg && nsA>0; deg++) {
         int pieceA = IPP_MIN(nsA, basicElemLen);

         cpGFpGet(pTmp, pieceA, pE, pBasicGF, montSpace);
         pE   += basicElemLen;
         pTmp += pieceA;
         nsA -= pieceA;
      }

      return pDataA;
   }
}

BNU_CHUNK_T* cpGFpxGetPolyTerm(BNU_CHUNK_T* pDataA, int nsA, const BNU_CHUNK_T* pE, int deg, IppsGFpState* pGFpx, int montSpace)
{
   pE += deg * GFP_FELEN(pGFpx);
   return cpGFpxGet(pDataA, nsA, pE, pGFpx, montSpace);
}

BNU_CHUNK_T* cpGFpxHalve(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx)
{
   IppsGFpState* pBasicGF = cpGFpBasic(pGFpx);
   int basicElemLen = GFP_FELEN(pBasicGF);
   int basicDeg = cpGFpBasicDegreeExtension(pGFpx);

   BNU_CHUNK_T* pTmp = pR;
   int deg;
   for(deg=0; deg<basicDeg; deg++) {
      pBasicGF->div2(pTmp, pA, pBasicGF);
      pTmp += basicElemLen;
      pA += basicElemLen;
   }
   return pR;
}

BNU_CHUNK_T* cpGFpxAdd(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGFpx)
{
   IppsGFpState* pBasicGF = cpGFpBasic(pGFpx);
   int basicElemLen = GFP_FELEN(pBasicGF);
   int basicDeg = cpGFpBasicDegreeExtension(pGFpx);

   BNU_CHUNK_T* pTmp = pR;
   int deg;
   for(deg=0; deg<basicDeg; deg++) {
      pBasicGF->add(pTmp, pA, pB, pBasicGF);
      pTmp += basicElemLen;
      pA += basicElemLen;
      pB += basicElemLen;
   }
   return pR;
}

BNU_CHUNK_T* cpGFpxSub(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGFpx)
{
   IppsGFpState* pBasicGF = cpGFpBasic(pGFpx);
   int basicElemLen = GFP_FELEN(pBasicGF);
   int basicDeg = cpGFpBasicDegreeExtension(pGFpx);

   BNU_CHUNK_T* pTmp = pR;
   int deg;
   for(deg=0; deg<basicDeg; deg++) {
      pBasicGF->sub(pTmp, pA, pB, pBasicGF);
      pTmp += basicElemLen;
      pA += basicElemLen;
      pB += basicElemLen;
   }
   return pR;
}

BNU_CHUNK_T* cpGFpxConj(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx)
{
   IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
   int groundElemLen = GFP_FELEN(pGroundGF);

   if(pR != pA)
      cpGFpElementCopy(pR, pA, groundElemLen);
   //cpGFpxNeg(pR+groundElemLen, pA+groundElemLen, pGroundGF);
   pGroundGF->neg(pR+groundElemLen, pA+groundElemLen, pGroundGF);

   return pR;
}


/*
// multiplication like GF(()^d).mul(a, g0),
// where:
//    a, g0 belongs to ground GF()
//    and g0 is low-order term of GF(()^d) generationg binominal g(t) = t^d + g0
// is very important for Intel(R) EPID 2.0.
//
// Thus, this kind of multiplication is using
// 1) in iplementation of GF(p^2) multiplication
// 2) in iplementation of GF((p^6)^2) multiplication too
*/
#if defined(_EPID20_GF_PARAM_SPECIFIC_)
#pragma message ("_EPID20_GF_PARAM_SPECIFIC_")

__INLINE BNU_CHUNK_T* cpFqMul_beta(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx)
{
   if(pR != pA)
      cpGFpElementCopy(pR, pA, GFP_FELEN(pGFpx));
   return pR;
}

__INLINE BNU_CHUNK_T* cpFq2Mul_xi(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx)
{
   IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
   BNU_CHUNK_T* t0 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* t1 = cpGFpGetPool(1, pGroundGF);

   int termLen = GFP_FELEN(pGroundGF);

   const BNU_CHUNK_T* pA0 = pA;
   const BNU_CHUNK_T* pA1 = pA+termLen;
   BNU_CHUNK_T* pR0 = pR;
   BNU_CHUNK_T* pR1 = pR+termLen;
   pGroundGF->add(t0, pA0, pA0, pGroundGF);
   pGroundGF->add(t1, pA0, pA1, pGroundGF);
   pGroundGF->sub(pR0, t0, pA1, pGroundGF);
   pGroundGF->add(pR1, t1, pA1, pGroundGF);

   cpGFpReleasePool(2, pGroundGF);
   return pR;
}

__INLINE BNU_CHUNK_T* cpFq6Mul_vi(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx)
{
   IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
   int termLen = GFP_FELEN(pGroundGF);

   const BNU_CHUNK_T* pA0 = pA;
   const BNU_CHUNK_T* pA1 = pA+termLen;
   const BNU_CHUNK_T* pA2 = pA+termLen*2;
   BNU_CHUNK_T* pR0 = pR;
   BNU_CHUNK_T* pR1 = pR+termLen;
   BNU_CHUNK_T* pR2 = pR+termLen*2;

   BNU_CHUNK_T* t = cpGFpGetPool(1, pGroundGF);

   cpFq2Mul_xi(t, pA2, pGroundGF);
   cpGFpElementCopy(pR2, pA1, termLen);
   cpGFpElementCopy(pR1, pA0, termLen);
   cpGFpElementCopy(pR0, t, termLen);

   cpGFpReleasePool(1, pGroundGF);

   return pR;
}
#endif

#if defined(_EXTENSION_2_BINOMIAL_SUPPORT_) || defined(_EXTENSION_3_BINOMIAL_SUPPORT_)
static BNU_CHUNK_T* cpGFpxMul_G0(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx)
{
   IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
   BNU_CHUNK_T* pGFpolynomial = GFP_MODULUS(pGFpx); /* g(x) = t^d + g0 */
   return pGroundGF->mul(pR, pA, pGFpolynomial, GFP_GROUNDGF(pGFpx));
}
#endif

/*
// field polynomial: g(x) = t^2 + beta - binominal
// extension degree: 2
*/
#if defined(_EXTENSION_2_BINOMIAL_SUPPORT_)
static BNU_CHUNK_T* cpGFp2biMul(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGFpx)
{
   IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
   int groundElemLen = GFP_FELEN(pGroundGF);

   const BNU_CHUNK_T* pA0 = pA;
   const BNU_CHUNK_T* pA1 = pA+groundElemLen;

   const BNU_CHUNK_T* pB0 = pB;
   const BNU_CHUNK_T* pB1 = pB+groundElemLen;

   BNU_CHUNK_T* pR0 = pR;
   BNU_CHUNK_T* pR1 = pR+groundElemLen;

   BNU_CHUNK_T* t0 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* t1 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* t2 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* t3 = cpGFpGetPool(1, pGroundGF);

   pGroundGF->mul(t0, pA0, pB0, pGroundGF);    /* t0 = a[0]*b[0] */
   pGroundGF->mul(t1, pA1, pB1, pGroundGF);    /* t1 = a[1]*b[1] */
   pGroundGF->add(t2, pA0, pA1,pGroundGF);     /* t2 = a[0]+a[1] */
   pGroundGF->add(t3, pB0, pB1,pGroundGF);     /* t3 = b[0]+b[1] */

   pGroundGF->mul(pR1, t2,  t3, pGroundGF);    /* r[1] = (a[0]+a[1]) * (b[0]+b[1]) */
   pGroundGF->sub(pR1, pR1, t0, pGroundGF);    /* r[1] -= a[0]*b[0]) + a[1]*b[1] */
   pGroundGF->sub(pR1, pR1, t1, pGroundGF);

   #if defined(_EPID20_GF_PARAM_SPECIFIC_)      /* r[0] = t0 - t1*beta */
   {
      int basicExtDegree = cpGFpBasicDegreeExtension(pGFpx);
      if(basicExtDegree==2 && EPID_PARAMS(pGFpx)) {
         //cpFqMul_beta(t1, t1, pGroundGF);
         pGroundGF->sub(pR0, t0, t1, pGroundGF);
      }
      else if(basicExtDegree==12 && EPID_PARAMS(pGFpx)) {
         cpFq6Mul_vi(t1, t1, pGroundGF);
         pGroundGF->add(pR0, t0, t1, pGroundGF);
      }
      else {
         cpGFpxMul_G0(t1, t1, pGFpx);
         pGroundGF->sub(pR0, t0, t1, pGroundGF);
      }
   }
   #else
   cpGFpxMul_G0(t1, t1, pGFpx);
   pGroundGF->sub(pR0, t0, t1, pGroundGF);
   #endif

   cpGFpReleasePool(4, pGroundGF);
   return pR;
}
#endif

/*
// field polynomial: g(x) = t^3 + beta - binominal
// extension degree: 3
*/
#if defined(_EXTENSION_3_BINOMIAL_SUPPORT_)
static BNU_CHUNK_T* cpGFp3biMul(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGFpx)
{
   IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
   int groundElemLen = GFP_FELEN(pGroundGF);

   const BNU_CHUNK_T* pA0 = pA;
   const BNU_CHUNK_T* pA1 = pA+groundElemLen;
   const BNU_CHUNK_T* pA2 = pA+groundElemLen*2;

   const BNU_CHUNK_T* pB0 = pB;
   const BNU_CHUNK_T* pB1 = pB+groundElemLen;
   const BNU_CHUNK_T* pB2 = pB+groundElemLen*2;

   BNU_CHUNK_T* pR0 = pR;
   BNU_CHUNK_T* pR1 = pR+groundElemLen;
   BNU_CHUNK_T* pR2 = pR+groundElemLen*2;

   BNU_CHUNK_T* t0 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* t1 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* t2 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* u0 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* u1 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* u2 = cpGFpGetPool(1, pGroundGF);

   pGroundGF->add(u0 ,pA0, pA1, pGroundGF);    /* u0 = a[0]+a[1] */
   pGroundGF->add(t0 ,pB0, pB1, pGroundGF);    /* t0 = b[0]+b[1] */
   pGroundGF->mul(u0, u0,  t0,  pGroundGF);    /* u0 = (a[0]+a[1])*(b[0]+b[1]) */
   pGroundGF->mul(t0, pA0, pB0, pGroundGF);    /* t0 = a[0]*b[0] */

   pGroundGF->add(u1 ,pA1, pA2, pGroundGF);    /* u1 = a[1]+a[2] */
   pGroundGF->add(t1 ,pB1, pB2, pGroundGF);    /* t1 = b[1]+b[2] */
   pGroundGF->mul(u1, u1,  t1,  pGroundGF);    /* u1 = (a[1]+a[2])*(b[1]+b[2]) */
   pGroundGF->mul(t1, pA1, pB1, pGroundGF);    /* t1 = a[1]*b[1] */

   pGroundGF->add(u2 ,pA2, pA0, pGroundGF);    /* u2 = a[2]+a[0] */
   pGroundGF->add(t2 ,pB2, pB0, pGroundGF);    /* t2 = b[2]+b[0] */
   pGroundGF->mul(u2, u2,  t2,  pGroundGF);    /* u2 = (a[2]+a[0])*(b[2]+b[0]) */
   pGroundGF->mul(t2, pA2, pB2, pGroundGF);    /* t2 = a[2]*b[2] */

   pGroundGF->sub(u0, u0,  t0,  pGroundGF);    /* u0 = a[0]*b[1]+a[1]*b[0] */
   pGroundGF->sub(u0, u0,  t1,  pGroundGF);
   pGroundGF->sub(u1, u1,  t1,  pGroundGF);    /* u1 = a[1]*b[2]+a[2]*b[1] */
   pGroundGF->sub(u1, u1,  t2,  pGroundGF);
   pGroundGF->sub(u2, u2,  t2,  pGroundGF);    /* u2 = a[2]*b[0]+a[0]*b[2] */
   pGroundGF->sub(u2, u2,  t0,  pGroundGF);

   #if defined(_EPID20_GF_PARAM_SPECIFIC_)
   {
      int basicExtDegree = cpGFpBasicDegreeExtension(pGFpx);
      if(basicExtDegree==6 && EPID_PARAMS(pGFpx)) {
         cpFq2Mul_xi(u1, u1, pGroundGF);
         cpFq2Mul_xi(t2, t2, pGroundGF);
         pGroundGF->add(pR0, t0, u1,  pGroundGF);  /* r[0] = a[0]*b[0] - (a[2]*b[1]+a[1]*b[2])*beta */
         pGroundGF->add(pR1, u0, t2,  pGroundGF);  /* r[1] = a[1]*b[0] + a[0]*b[1] - a[2]*b[2]*beta */
      }
      else {
         cpGFpxMul_G0(u1, u1, pGFpx);              /* u1 = (a[1]*b[2]+a[2]*b[1]) * beta */
         cpGFpxMul_G0(t2, t2, pGFpx);              /* t2 = a[2]*b[2] * beta */
         pGroundGF->sub(pR0, t0, u1,  pGroundGF);  /* r[0] = a[0]*b[0] - (a[2]*b[1]+a[1]*b[2])*beta */
         pGroundGF->sub(pR1, u0, t2,  pGroundGF);  /* r[1] = a[1]*b[0] + a[0]*b[1] - a[2]*b[2]*beta */
      }
   }
   #else
   cpGFpxMul_G0(u1, u1, pGFpx);                 /* u1 = (a[1]*b[2]+a[2]*b[1]) * beta */
   cpGFpxMul_G0(t2, t2, pGFpx);                 /* t2 = a[2]*b[2] * beta */

   pGroundGF->sub(pR0, t0, u1,  pGroundGF);     /* r[0] = a[0]*b[0] - (a[2]*b[1]+a[1]*b[2])*beta */
   pGroundGF->sub(pR1, u0, t2,  pGroundGF);     /* r[1] = a[1]*b[0] + a[0]*b[1] - a[2]*b[2]*beta */
   #endif

   pGroundGF->add(pR2, u2, t1,  pGroundGF);     /* r[2] = a[2]*b[0] + a[1]*b[1] + a[0]*b[2] */

   cpGFpReleasePool(6, pGroundGF);
   return pR;
}
#endif

BNU_CHUNK_T* cpGFpxMul(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGFpx)
{
   int extDegree = GFP_DEGREE(pGFpx);

   #if defined(_EXTENSION_2_BINOMIAL_SUPPORT_)
   #pragma message ("_EXTENSION_2_BINOMIAL_SUPPORT_")
   if(BINOMIAL==FIELD_POLY_TYPE(pGFpx) && extDegree==2)
      return cpGFp2biMul(pR, pA, pB, pGFpx);
   #endif

   #if defined(_EXTENSION_3_BINOMIAL_SUPPORT_)
   #pragma message ("_EXTENSION_3_BINOMIAL_SUPPORT_")
   if(BINOMIAL==FIELD_POLY_TYPE(pGFpx) && extDegree==3)
      return cpGFp3biMul(pR, pA, pB, pGFpx);
   #endif

   {
      BNU_CHUNK_T* pGFpolynomial = GFP_MODULUS(pGFpx);
      int degR = extDegree-1;
      int elemLen= GFP_FELEN(pGFpx);

      int degB = degR;
      BNU_CHUNK_T* pTmpProduct = cpGFpGetPool(2, pGFpx);
      BNU_CHUNK_T* pTmpResult = pTmpProduct + GFP_PELEN(pGFpx);

      IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
      BNU_CHUNK_T* r = cpGFpGetPool(1, pGroundGF);
      int groundElemLen = GFP_FELEN(pGroundGF);

      const BNU_CHUNK_T* pTmpB = GFPX_IDX_ELEMENT(pB, degB, groundElemLen);

      /* clear temporary */
      cpGFpElementPadd(pTmpProduct, elemLen, 0);

      /* R = A * B[degB-1] */
      cpGFpxMul_GFE(pTmpResult, pA, pTmpB, pGFpx);

      for(degB-=1; degB>=0; degB--) {
         /* save R[degR-1] */
         cpGFpElementCopy(r, GFPX_IDX_ELEMENT(pTmpResult, degR, groundElemLen), groundElemLen);

         { /* R = R * x */
            int j;
            for (j=degR; j>=1; j--)
               cpGFpElementCopy(GFPX_IDX_ELEMENT(pTmpResult, j, groundElemLen), GFPX_IDX_ELEMENT(pTmpResult, j-1, groundElemLen), groundElemLen);
            cpGFpElementPadd(pTmpResult, groundElemLen, 0);
         }

         cpGFpxMul_GFE(pTmpProduct, pGFpolynomial, r, pGFpx);
         pGFpx->sub(pTmpResult, pTmpResult, pTmpProduct, pGFpx);

         /* B[degB-i] */
         pTmpB -= groundElemLen;
         cpGFpxMul_GFE(pTmpProduct, pA, pTmpB, pGFpx);
         pGFpx->add(pTmpResult, pTmpResult, pTmpProduct, pGFpx);
      }

      /* copy result */
      cpGFpElementCopy(pR, pTmpResult, elemLen);

      /* release pools */
      cpGFpReleasePool(1, pGroundGF);
      cpGFpReleasePool(2, pGFpx);

      return pR;
   }
}

/*
// field polynomial: binominal
// extension degree: 2
*/
#if defined(_EXTENSION_2_BINOMIAL_SUPPORT_)
static BNU_CHUNK_T* cpGFp2biSqr(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx)
{
   IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
   int groundElemLen = GFP_FELEN(pGroundGF);

   const BNU_CHUNK_T* pA0 = pA;
   const BNU_CHUNK_T* pA1 = pA+groundElemLen;

   BNU_CHUNK_T* pR0 = pR;
   BNU_CHUNK_T* pR1 = pR+groundElemLen;

   BNU_CHUNK_T* t0 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* t1 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* u0 = cpGFpGetPool(1, pGroundGF);

   pGroundGF->mul(u0, pA0, pA1, pGroundGF); /* u0 = a[0]*a[1] */

   #if defined(_EPID20_GF_PARAM_SPECIFIC_)  /* r[0] = t0 - t1*beta */
   {
      int basicExtDegree = cpGFpBasicDegreeExtension(pGFpx);
      if(basicExtDegree==2 && EPID_PARAMS(pGFpx)) {
         pGroundGF->add(t0, pA0, pA1, pGroundGF);
         pGroundGF->sub(t1, pA0, pA1, pGroundGF);
         pGroundGF->mul(pR0, t0, t1, pGroundGF);
         pGroundGF->add(pR1, u0, u0, pGroundGF);  /* r[1] = 2*a[0]*a[1] */
      }
      else if(basicExtDegree==12 && EPID_PARAMS(pGFpx)) {
         pGroundGF->sub(t0, pA0, pA1, pGroundGF);
         cpFq6Mul_vi(t1, pA1, pGroundGF);
         pGroundGF->sub(t1, pA0, t1, pGroundGF);
         pGroundGF->mul(t0, t0, t1, pGroundGF);
         pGroundGF->add(t0, t0, u0, pGroundGF);
         cpFq6Mul_vi(t1, u0, pGroundGF);
         pGroundGF->add(pR0, t0, t1, pGroundGF);
         pGroundGF->add(pR1, u0, u0, pGroundGF);
      }
      else {
         pGroundGF->sqr(t0, pA0, pGroundGF);      /* t0 = a[0]*a[0] */
         pGroundGF->sqr(t1, pA1, pGroundGF);      /* t1 = a[1]*a[1] */
         cpGFpxMul_G0(t1, t1, pGFpx);
         pGroundGF->sub(pR0, t0, t1, pGroundGF);
         pGroundGF->add(pR1, u0, u0, pGroundGF);  /* r[1] = 2*a[0]*a[1] */
      }
   }
   #else
   pGroundGF->sqr(t0, pA0, pGroundGF);      /* t0 = a[0]*a[0] */
   pGroundGF->sqr(t1, pA1, pGroundGF);      /* t1 = a[1]*a[1] */
   cpGFpxMul_G0(t1, t1, pGFpx);
   pGroundGF->sub(pR0, t0, t1, pGroundGF);
   pGroundGF->add(pR1, u0, u0, pGroundGF);  /* r[1] = 2*a[0]*a[1] */
   #endif

   cpGFpReleasePool(3, pGroundGF);
   return pR;
}
#endif

/*
// field polynomial: binominal
// extension degree: 3
*/
#if defined(_EXTENSION_3_BINOMIAL_SUPPORT_)
static BNU_CHUNK_T* cpGFp3biSqr(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx)
{
   IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
   int groundElemLen = GFP_FELEN(pGroundGF);

   const BNU_CHUNK_T* pA0 = pA;
   const BNU_CHUNK_T* pA1 = pA+groundElemLen;
   const BNU_CHUNK_T* pA2 = pA+groundElemLen*2;

   BNU_CHUNK_T* pR0 = pR;
   BNU_CHUNK_T* pR1 = pR+groundElemLen;
   BNU_CHUNK_T* pR2 = pR+groundElemLen*2;

   BNU_CHUNK_T* s0 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* s1 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* s2 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* s3 = cpGFpGetPool(1, pGroundGF);
   BNU_CHUNK_T* s4 = cpGFpGetPool(1, pGroundGF);

   pGroundGF->add(s2, pA0, pA2, pGroundGF);
   pGroundGF->sub(s2,  s2, pA1, pGroundGF);
   pGroundGF->sqr(s2,  s2, pGroundGF);
   pGroundGF->sqr(s0, pA0, pGroundGF);
   pGroundGF->sqr(s4, pA2, pGroundGF);
   pGroundGF->mul(s1, pA0, pA1, pGroundGF);
   pGroundGF->mul(s3, pA1, pA2, pGroundGF);
   pGroundGF->add(s1,  s1,  s1, pGroundGF);
   pGroundGF->add(s3,  s3,  s3, pGroundGF);

   pGroundGF->add(pR2,  s1, s2, pGroundGF);
   pGroundGF->add(pR2, pR2, s3, pGroundGF);
   pGroundGF->sub(pR2, pR2, s0, pGroundGF);
   pGroundGF->sub(pR2, pR2, s4, pGroundGF);

   #if defined(_EPID20_GF_PARAM_SPECIFIC_)
   {
      int basicExtDegree = cpGFpBasicDegreeExtension(pGFpx);
      if(basicExtDegree==6 && EPID_PARAMS(pGFpx)) {
         cpFq2Mul_xi(s4, s4, pGroundGF);
         cpFq2Mul_xi(s3, s3, pGroundGF);
         pGroundGF->add(pR1, s1,  s4, pGroundGF);
         pGroundGF->add(pR0, s0,  s3, pGroundGF);
      }
      else {
         cpGFpxMul_G0(s4, s4, pGFpx);
         cpGFpxMul_G0(s3, s3, pGFpx);
         pGroundGF->sub(pR1, s1,  s4, pGroundGF);
         pGroundGF->sub(pR0, s0,  s3, pGroundGF);
      }
   }

   #else
   cpGFpxMul_G0(s4, s4, pGFpx);
   pGroundGF->sub(pR1, s1,  s4, pGroundGF);

   cpGFpxMul_G0(s3, s3, pGFpx);
   pGroundGF->sub(pR0, s0,  s3, pGroundGF);
   #endif

   cpGFpReleasePool(5, pGroundGF);
   return pR;
}
#endif

BNU_CHUNK_T* cpGFpxSqr(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx)
{
   int extDegree = GFP_DEGREE(pGFpx);

   #if defined(_EXTENSION_2_BINOMIAL_SUPPORT_)
   #pragma message ("_EXTENSION_2_BINOMIAL_SUPPORT_")
   if(BINOMIAL==FIELD_POLY_TYPE(pGFpx) && extDegree==2)
      return cpGFp2biSqr(pR, pA, pGFpx);
   #endif

   #if defined(_EXTENSION_3_BINOMIAL_SUPPORT_)
   #pragma message ("_EXTENSION_3_BINOMIAL_SUPPORT_")
   if(BINOMIAL==FIELD_POLY_TYPE(pGFpx) && extDegree==3)
      return cpGFp3biSqr(pR, pA, pGFpx);
   #endif

   {
      BNU_CHUNK_T* pGFpolynomial = GFP_MODULUS(pGFpx);
      int degR = extDegree-1;
      int elemLen= GFP_FELEN(pGFpx);

      int degA = degR;
      BNU_CHUNK_T* pTmpProduct = cpGFpGetPool(2, pGFpx);
      BNU_CHUNK_T* pTmpResult = pTmpProduct + GFP_PELEN(pGFpx);

      IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
      BNU_CHUNK_T* r = cpGFpGetPool(1, pGroundGF);
      int groundElemLen = GFP_FELEN(pGroundGF);

      const BNU_CHUNK_T* pTmpA = GFPX_IDX_ELEMENT(pA, degA, groundElemLen);

      /* clear temporary */
      cpGFpElementPadd(pTmpProduct, elemLen, 0);

      /* R = A * A[degA-1] */
      cpGFpxMul_GFE(pTmpResult, pA, pTmpA, pGFpx);

      for(degA-=1; degA>=0; degA--) {
         /* save R[degR-1] */
         cpGFpElementCopy(r, GFPX_IDX_ELEMENT(pTmpResult, degR, groundElemLen), groundElemLen);

         { /* R = R * x */
            int j;
            for (j=degR; j>=1; j--)
               cpGFpElementCopy(GFPX_IDX_ELEMENT(pTmpResult, j, groundElemLen), GFPX_IDX_ELEMENT(pTmpResult, j-1, groundElemLen), groundElemLen);
            cpGFpElementPadd(pTmpResult, groundElemLen, 0);
         }

         cpGFpxMul_GFE(pTmpProduct, pGFpolynomial, r, pGFpx);
         pGFpx->sub(pTmpResult, pTmpResult, pTmpProduct, pGFpx);

         /* A[degA-i] */
         pTmpA -= groundElemLen;
         cpGFpxMul_GFE(pTmpProduct, pA, pTmpA, pGFpx);
         pGFpx->add(pTmpResult, pTmpResult, pTmpProduct, pGFpx);
      }

      /* copy result */
      cpGFpElementCopy(pR, pTmpResult, elemLen);

      /* release pools */
      cpGFpReleasePool(1, pGroundGF);
      cpGFpReleasePool(2, pGFpx);

      return pR;
   }
}

BNU_CHUNK_T* cpGFpxAdd_GFE(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pGroundB, IppsGFpState* pGFpx)
{
   IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);

   if(pR != pA) {
      int groundElemLen = GFP_FELEN(pGroundGF);
      int deg = GFP_DEGREE(pGFpx);
      cpGFpElementCopy(pR+groundElemLen, pA+groundElemLen, groundElemLen*(deg-1));
   }
   return pGroundGF->add(pR, pA, pGroundB, pGroundGF);
}

BNU_CHUNK_T* cpGFpxSub_GFE(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pGroundB, IppsGFpState* pGFpx)
{
   IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);

   if(pR != pA) {
      int groundElemLen = GFP_FELEN(pGroundGF);
      int deg = GFP_DEGREE(pGFpx);
      cpGFpElementCopy(pR+groundElemLen, pA+groundElemLen, groundElemLen*(deg-1));
   }
   return pGroundGF->sub(pR, pA, pGroundB, pGroundGF);
}

BNU_CHUNK_T* cpGFpxMul_GFE(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pGroundB, IppsGFpState* pGFpx)
{
   IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
   int grounfElemLen = GFP_FELEN(pGroundGF);

   BNU_CHUNK_T* pTmp = pR;

   int deg;
   for(deg=0; deg<GFP_DEGREE(pGFpx); deg++) {
      pGroundGF->mul(pTmp, pA, pGroundB, pGroundGF);
      pTmp += grounfElemLen;
      pA += grounfElemLen;
   }
   return pR;
}

BNU_CHUNK_T* cpGFpxNeg(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx)
{
   IppsGFpState* pBasicGF = cpGFpBasic(pGFpx);
   int basicElemLen = GFP_FELEN(pBasicGF);
   int basicDeg = cpGFpBasicDegreeExtension(pGFpx);

   BNU_CHUNK_T* pTmp = pR;
   int deg;
   for(deg=0; deg<basicDeg; deg++) {
      pBasicGF->neg(pTmp, pA, pBasicGF);
      pTmp += basicElemLen;
      pA += basicElemLen;
   }
   return pR;
}

//////////////////////////////////////////////////////////////////
BNU_CHUNK_T* gfpolyDiv_v0(BNU_CHUNK_T* pQ, BNU_CHUNK_T* pR,
               const BNU_CHUNK_T* pA,
               const BNU_CHUNK_T* pB,
                     IppsGFpState* pGFpx)
{
   IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);

   if( GFP_IS_BASIC(pGroundGF) ) {
      int elemLen = GFP_FELEN(pGFpx);
      int termLen = GFP_FELEN(pGroundGF);

      int degA = degree(pA, pGFpx);
      int degB = degree(pB, pGFpx);

      if(degB==0) {
         if( GFP_IS_ZERO(pB, termLen) )
            return NULL;
         else {
            cpGFpInv(pR, pB, pGroundGF);
            cpGFpElementPadd(pQ, elemLen, 0);
            cpGFpxMul_GFE(pQ, pA, pR, pGFpx);
            cpGFpElementPadd(pR, elemLen, 0);
            return pR;
         }
      }

      if(degA < degB) {
         cpGFpElementPadd(pQ, elemLen, 0);
         cpGFpElementCopyPadd(pR, elemLen, pA, (degA+1)*termLen);
         return pR;
      }

      else {
         int i, j;
         BNU_CHUNK_T* pProduct = cpGFpGetPool(2, pGroundGF);
         BNU_CHUNK_T* pInvB = pProduct + GFP_PELEN(pGroundGF);

         cpGFpElementCopyPadd(pR, elemLen, pA, (degA+1)*termLen);
         cpGFpElementPadd(pQ, elemLen, 0);

         cpGFpInv(pInvB, GFPX_IDX_ELEMENT(pB, degB, termLen), pGroundGF);

         for(i=0; i<=degA-degB && !GFP_IS_ZERO(GFPX_IDX_ELEMENT(pR, degA-i, termLen), termLen); i++) {
            /* compute q term */
            cpGFpMul(GFPX_IDX_ELEMENT(pQ, degA-degB-i, termLen),
                     GFPX_IDX_ELEMENT(pR, degA-i, termLen),
                     pInvB,
                     pGroundGF);

            /* R -= B * q */
            cpGFpElementPadd(GFPX_IDX_ELEMENT(pR, degA-i, termLen), termLen, 0);
            for(j=0; j<degB; j++) {
               cpGFpMul(pProduct,
                        GFPX_IDX_ELEMENT(pB, j ,termLen),
                        GFPX_IDX_ELEMENT(pQ, degA-degB-i, termLen),
                        pGroundGF);
               cpGFpSub(GFPX_IDX_ELEMENT(pR, degA-degB-i+j, termLen),
                        GFPX_IDX_ELEMENT(pR, degA-degB-i+j, termLen),
                        pProduct,
                        pGroundGF);
            }
         }

         cpGFpReleasePool(2, pGroundGF);
         return pR;
      }
   }
   return NULL;
}

static BNU_CHUNK_T* gfpgeneratorDiv_v0(BNU_CHUNK_T* pQ, BNU_CHUNK_T* pR, const BNU_CHUNK_T* pB, IppsGFpState* pGFpx)
{
   IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);

   if( GFP_IS_BASIC(pGroundGF) ) {
      int elemLen = GFP_FELEN(pGFpx);
      int termLen = GFP_FELEN(pGroundGF);

      BNU_CHUNK_T* pInvB = cpGFpGetPool(2, pGroundGF);
      BNU_CHUNK_T* pTmp  = pInvB + GFP_PELEN(pGroundGF);

      int degB = degree(pB, pGFpx);
      int i;

      cpGFpElementCopy(pR, GFP_MODULUS(pGFpx), elemLen);
      cpGFpElementPadd(pQ, elemLen, 0);

      cpGFpInv(pInvB, GFPX_IDX_ELEMENT(pB, degB, termLen), pGroundGF);

      for(i=0; i<degB; i++) {
         BNU_CHUNK_T* ptr;
         cpGFpMul(pTmp, pInvB, GFPX_IDX_ELEMENT(pB, i, termLen), pGroundGF);
         ptr = GFPX_IDX_ELEMENT(pR, GFP_DEGREE(pGFpx)-degB+i, termLen);
         cpGFpSub(ptr, ptr, pTmp, pGroundGF);
      }

      gfpolyDiv_v0(pQ, pR, pR, pB, pGFpx);

      cpGFpElementCopy(GFPX_IDX_ELEMENT(pQ, GFP_DEGREE(pGFpx)-degB, termLen), pInvB, termLen);

      cpGFpReleasePool(2, pGroundGF);
      return pR;
   }

   return NULL;
}


///////////////////////////////////////////////////////////////////////////////
static BNU_CHUNK_T* gfpxPolyDiv(BNU_CHUNK_T* pQ, BNU_CHUNK_T* pR,
                        const BNU_CHUNK_T* pA,
                        const BNU_CHUNK_T* pB,
                        IppsGFpState* pGFpx)
{
   if( GFP_IS_BASIC(pGFpx) )
      return NULL;

   else {
      int elemLen = GFP_FELEN(pGFpx);
      IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
      int termLen = GFP_FELEN(pGroundGF);

      int degA = degree(pA, pGFpx);
      int degB = degree(pB, pGFpx);

      if(degB==0) {
         if( GFP_IS_ZERO(pB, termLen) )
            return NULL;
         else {
            IppsGFpState* pBasicGF = cpGFpBasic(pGroundGF);

            cpGFpInv(pR, pB, pBasicGF);
            cpGFpElementPadd(pR+GFP_FELEN(pGroundGF), termLen-GFP_FELEN(pGroundGF), 0);
            cpGFpxMul_GFE(pQ, pA, pR, pGFpx);
            cpGFpElementPadd(pR, elemLen, 0);
            return pR;
         }
      }

      if(degA < degB) {
         cpGFpElementPadd(pQ, elemLen, 0);
         cpGFpElementCopyPadd(pR, elemLen, pA, (degA+1)*termLen);
         return pR;
      }

      else {
         int i, j;
         BNU_CHUNK_T* pProduct = cpGFpGetPool(2, pGroundGF);
         BNU_CHUNK_T* pInvB = pProduct + GFP_PELEN(pGroundGF);

         cpGFpElementCopyPadd(pR, elemLen, pA, (degA+1)*termLen);
         cpGFpElementPadd(pQ, elemLen, 0);

         cpGFpxInv(pInvB, GFPX_IDX_ELEMENT(pB, degB, termLen), pGroundGF);

         for(i=0; i<=degA-degB && !GFP_IS_ZERO(GFPX_IDX_ELEMENT(pR, degA-i, termLen), termLen); i++) {
            /* compute q term */
            cpGFpxMul(GFPX_IDX_ELEMENT(pQ, degA-degB-i, termLen),
                      GFPX_IDX_ELEMENT(pR, degA-i, termLen),
                      pInvB,
                      pGroundGF);

            /* R -= B * q */
            cpGFpElementPadd(GFPX_IDX_ELEMENT(pR, degA-i, termLen), termLen, 0);
            for(j=0; j<degB; j++) {
               cpGFpxMul(pProduct,
                         GFPX_IDX_ELEMENT(pB, j ,termLen),
                         GFPX_IDX_ELEMENT(pQ, degA-degB-i, termLen),
                         pGroundGF);
               cpGFpxSub(GFPX_IDX_ELEMENT(pR, degA-degB-i+j, termLen),
                         GFPX_IDX_ELEMENT(pR, degA-degB-i+j, termLen),
                         pProduct,
                         pGroundGF);
            }
         }

         cpGFpReleasePool(2, pGroundGF);
         return pR;
      }
   }
}

static BNU_CHUNK_T* gfpxGeneratorDiv(BNU_CHUNK_T* pQ, BNU_CHUNK_T* pR, const BNU_CHUNK_T* pB, IppsGFpState* pGFpx)
{
   if( GFP_IS_BASIC(pGFpx) )
      return NULL;

   else {
      int elemLen = GFP_FELEN(pGFpx);
      IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
      int termLen = GFP_FELEN(pGroundGF);

      BNU_CHUNK_T* pInvB = cpGFpGetPool(2, pGroundGF);
      BNU_CHUNK_T* pTmp  = pInvB + GFP_PELEN(pGroundGF);

      int degB = degree(pB, pGFpx);
      int i;

      cpGFpElementCopy(pR, GFP_MODULUS(pGFpx), elemLen);
      cpGFpElementPadd(pQ, elemLen, 0);

      cpGFpxInv(pInvB, GFPX_IDX_ELEMENT(pB, degB, termLen), pGroundGF);

      for(i=0; i<degB; i++) {
         BNU_CHUNK_T* ptr;
         cpGFpxMul(pTmp, pInvB, GFPX_IDX_ELEMENT(pB, i, termLen), pGroundGF);
         ptr = GFPX_IDX_ELEMENT(pR, GFP_DEGREE(pGFpx)-degB+i, termLen);
         cpGFpxSub(ptr, ptr, pTmp, pGroundGF);
      }

      gfpxPolyDiv(pQ, pR, pR, pB, pGFpx);

      cpGFpElementCopy(GFPX_IDX_ELEMENT(pQ, GFP_DEGREE(pGFpx)-degB, termLen), pInvB, termLen);

      cpGFpReleasePool(2, pGroundGF);
      return pR;
   }
}

BNU_CHUNK_T* cpGFpxInv(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx)
{
   if( GFP_IS_BASIC(pGFpx) )
      return cpGFpInv(pR, pA, pGFpx);

   if(0==degree(pA, pGFpx)) {
      IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
      BNU_CHUNK_T* tmpR = cpGFpGetPool(1, pGroundGF);

      cpGFpxInv(tmpR, pA, pGroundGF);

      cpGFpElementCopyPadd(pR, GFP_FELEN(pGFpx), tmpR, GFP_FELEN(pGroundGF));
      cpGFpReleasePool(1, pGroundGF);
      return pR;
   }

   else {
      int elemLen = GFP_FELEN(pGFpx);
      IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFpx);
      IppsGFpState* pBasicGF = cpGFpBasic(pGFpx);

      int pxVars = 6;
      int pelemLen = GFP_PELEN(pGFpx);
      BNU_CHUNK_T* lastrem = cpGFpGetPool(pxVars, pGFpx);
      BNU_CHUNK_T* rem     = lastrem + pelemLen;
      BNU_CHUNK_T* quo     = rem +  pelemLen;
      BNU_CHUNK_T* lastaux = quo + pelemLen;
      BNU_CHUNK_T* aux     = lastaux + pelemLen;
      BNU_CHUNK_T* temp    = aux + pelemLen;

      cpGFpElementCopy(lastrem, pA, elemLen);
      cpGFpElementCopyPadd(lastaux, elemLen, MNT_1(GFP_MONT(pBasicGF)), GFP_FELEN(pBasicGF));

      gfpxGeneratorDiv(quo, rem, pA, pGFpx);
      cpGFpxNeg(aux, quo, pGFpx);

      while(degree(rem, pGFpx) > 0) {
         gfpxPolyDiv(quo, temp, lastrem, rem, pGFpx);
         SWAP_PTR(BNU_CHUNK_T, rem, lastrem); //
         SWAP_PTR(BNU_CHUNK_T, temp, rem);

         cpGFpxNeg(quo, quo, pGFpx);
         cpGFpxMul(temp, quo, aux, pGFpx);
         cpGFpxAdd(temp, lastaux, temp, pGFpx);
         SWAP_PTR(BNU_CHUNK_T, aux, lastaux);
         SWAP_PTR(BNU_CHUNK_T, temp, aux);
      }
      if (GFP_IS_ZERO(rem, elemLen)) { /* gcd != 1 */
         cpGFpReleasePool(pxVars, pGFpx);
         return NULL;
      }

      {
         BNU_CHUNK_T* invRem  = cpGFpGetPool(1, pGroundGF);

         cpGFpxInv(invRem, rem, pGroundGF);
         cpGFpxMul_GFE(pR, aux, invRem, pGFpx);

         cpGFpReleasePool(1, pGroundGF);
      }

      cpGFpReleasePool(pxVars, pGFpx);

      return pR;
   }
}


static int div_upper(int a, int d)
{ return (a+d-1)/d; }

static int getNumOperations(int bitsize, int w)
{
   int n_overhead = (1<<w) -1;
   int n_ops = div_upper(bitsize, w) + n_overhead;
   return n_ops;
}
int cpGFpGetOptimalWinSize(int bitsize)
{
#define LIMIT (LOG2_CACHE_LINE_SIZE)
   int w_opt = 1;
   int n_opt = getNumOperations(bitsize, w_opt);
   int w_trial;
   for(w_trial=w_opt+1; w_trial<=LIMIT; w_trial++) {
      int n_trial = getNumOperations(bitsize, w_trial);
      if(n_trial>=n_opt) break;
      w_opt = w_trial;
      n_opt = n_trial;
   }
   return w_opt;
#undef LIMIT
}


/* sscm version */
BNU_CHUNK_T* cpGFpxExp(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pE, int nsE,
                     IppsGFpState* pGFpx, Ipp8u* pScratchBuffer)
{
   IppsGFpState* pBasicGF = cpGFpBasic(pGFpx);

   /* remove leding zeros */
   FIX_BNU(pE, nsE);

   {
      Ipp8u* pScratchAligned; /* aligned scratch buffer */
      int nAllocation = 0;    /* points from the pool */

      /* size of element (bytes) */
      int elmDataSize = GFP_FELEN(pGFpx)*sizeof(BNU_CHUNK_T);

      /* exponent bitsize */
      int expBitSize = BITSIZE_BNU(pE, nsE);
      /* optimal size of window */
      int w = (NULL==pScratchBuffer)? 1 : cpGFpGetOptimalWinSize(expBitSize);
      /* number of table entries */
      int nPrecomputed = 1<<w;

      BNU_CHUNK_T* pExpandedE = cpGFpGetPool(1, pGFpx);
      BNU_CHUNK_T* pTmp = cpGFpGetPool(1, pGFpx);
      int poolElmLen = GFP_PELEN(pGFpx);

      if(NULL==pScratchBuffer) {
         nAllocation = 2 + div_upper(CACHE_LINE_SIZE, poolElmLen*sizeof(BNU_CHUNK_T));
         pScratchBuffer = (Ipp8u*)cpGFpGetPool(nAllocation, pGFpx);
      }
      pScratchAligned = IPP_ALIGNED_PTR(pScratchBuffer, CACHE_LINE_SIZE);

      /* pre-compute auxiliary table t[] = {1, A, A^2, ..., A^(2^w-1)} */
      cpGFpElementCopyPadd(pTmp, GFP_FELEN(pGFpx), MNT_1(GFP_MONT(pBasicGF)), GFP_FELEN(pBasicGF));
      cpScramblePut(pScratchAligned+0, nPrecomputed, (Ipp8u*)pTmp, elmDataSize);
      {
         int n;
         for(n=1; n<nPrecomputed; n++) {
            pGFpx->mul(pTmp, pTmp, pA, pGFpx);
            cpScramblePut(pScratchAligned+n, nPrecomputed, (Ipp8u*)pTmp, elmDataSize);
         }
      }

      {
         /* copy exponent value */
         cpGFpElementCopy(pExpandedE, pE, nsE);

         /* expand exponent value */
         ((Ipp32u*)pExpandedE)[BITS2WORD32_SIZE(expBitSize)] = 0;
         expBitSize = ((expBitSize+w-1)/w)*w;

         /*
         // exponentiation
         */
         {
            /* digit mask */
            BNU_CHUNK_T dmask = nPrecomputed-1;

            /* position (bit number) of the leftmost window */
            int wPosition = expBitSize-w;

            /* extract leftmost window value */
            Ipp32u eChunk = *((Ipp32u*)((Ipp16u*)pExpandedE+ wPosition/BITSIZE(Ipp16u)));
            int shift = wPosition & 0xF;
            Ipp32u windowVal = (eChunk>>shift) & dmask;

            /* initialize result */
            cpScrambleGet((Ipp8u*)pR, elmDataSize, pScratchAligned+windowVal, nPrecomputed);

            for(wPosition-=w; wPosition>=0; wPosition-=w) {
               int k;
               /* w times squaring */
               for(k=0; k<w; k++)
                  pGFpx->sqr(pR, pR, pGFpx);

               /* extract next window value */
               eChunk = *((Ipp32u*)((Ipp16u*)pExpandedE+ wPosition/BITSIZE(Ipp16u)));
               shift = wPosition & 0xF;
               windowVal = (eChunk>>shift) & dmask;

               /* extract value from the pre-computed table */
               cpScrambleGet((Ipp8u*)pTmp, elmDataSize, pScratchAligned+windowVal, nPrecomputed);

               /* and multiply */
               pGFpx->mul(pR, pR, pTmp, pGFpx);
            }
         }

      }

      cpGFpReleasePool(nAllocation+2, pGFpx);

      return pR;
   }
}


static void cpPrecomputeMultiExp(Ipp8u* pTable, const BNU_CHUNK_T* ppA[], int nItems, IppsGFpState* pGFpx)
{
   IppsGFpState* pBasicGF = cpGFpBasic(pGFpx);

   int nPrecomputed = 1<<nItems;

   /* length of element (BNU_CHUNK_T) */
   int elmLen = GFP_FELEN(pGFpx);
   /* size of element (bytes) */
   int elmDataSize = GFP_FELEN(pGFpx)*sizeof(BNU_CHUNK_T);

   /* get resource */
   BNU_CHUNK_T* pT = cpGFpGetPool(1, pGFpx);

   /* pTable[0] = 1 */
   cpGFpElementCopyPadd(pT, elmLen, MNT_1(GFP_MONT(pBasicGF)), GFP_FELEN(pBasicGF));
   cpScramblePut(pTable+0, nPrecomputed, (Ipp8u*)pT, elmDataSize);
   /* pTable[1] = A[0] */
   cpScramblePut(pTable+1, nPrecomputed, (Ipp8u*)(ppA[0]), elmDataSize);

   {
      int i, baseIdx;
      for(i=1, baseIdx=2; i<nItems; i++, baseIdx*=2) {
         /* pTable[baseIdx] = A[i] */
         cpScramblePut(pTable+baseIdx, nPrecomputed, (Ipp8u*)(ppA[i]), elmDataSize);

         {
            int nPasses = 1;
            int step = baseIdx/2;

            int k;
            for(k=i-1; k>=0; k--) {
               int tblIdx = baseIdx;

               int n;
               for(n=0; n<nPasses; n++, tblIdx+=2*step) {
                  /* use pre-computed value */
                  cpScrambleGet((Ipp8u*)pT, elmDataSize, pTable+tblIdx, nPrecomputed);
                  pGFpx->mul(pT, pT, ppA[k], pGFpx);
                  cpScramblePut(pTable+tblIdx+step, nPrecomputed, (Ipp8u*)pT, elmDataSize);
               }

               nPasses *= 2;
               step /= 2;
            }
         }
      }
   }

   /* release resourse */
   cpGFpReleasePool(1, pGFpx);
}

static int cpGetMaxBitsizeExponent(const BNU_CHUNK_T* ppE[], int nsE[], int nItems)
{
   int n;
   /* find out the longest exponent */
   int expBitSize = BITSIZE_BNU(ppE[0], nsE[0]);
   for(n=1; n<nItems; n++) {
      expBitSize = IPP_MAX(expBitSize, BITSIZE_BNU(ppE[n], nsE[n]));
   }
   return expBitSize;
}

static int GetIndex(const BNU_CHUNK_T* ppE[], int nItems, int nBit)
{
   int shift = nBit%BYTESIZE;
   int offset= nBit/BYTESIZE;
   int index = 0;

   int n;
   for(n=nItems; n>0; n--) {
      const Ipp8u* pE = ((Ipp8u*)ppE[n-1]) + offset;
      Ipp8u e = pE[0];
      index <<= 1;
      index += (e>>shift) &1;
   }
   return index;
}

/* sscm version */
BNU_CHUNK_T* cpGFpxMultiExp(BNU_CHUNK_T* pR, const BNU_CHUNK_T* ppA[], const BNU_CHUNK_T* ppE[], int nsE[], int nItems,
                          IppsGFpState* pGFpx, Ipp8u* pScratchBuffer)
{
   /* align scratch buffer */
   Ipp8u* pScratchAligned = IPP_ALIGNED_PTR(pScratchBuffer, CACHE_LINE_SIZE);
   /* pre-compute table */
   cpPrecomputeMultiExp(pScratchAligned, ppA, nItems, pGFpx);

   {
      /* find out the longest exponent */
      int expBitSize = cpGetMaxBitsizeExponent(ppE, nsE, nItems);

      /* allocate resource and copy expanded exponents into */
      const BNU_CHUNK_T* ppExponent[LOG2_CACHE_LINE_SIZE];
      {
         int n;
         for(n=0; n<nItems; n++) {
            BNU_CHUNK_T* pData = cpGFpGetPool(1, pGFpx);
            cpGFpElementCopyPadd(pData, GFP_FELEN(pGFpx), ppE[n], nsE[n]);
            ppExponent[n] = pData;
         }
      }

      /* multiexponentiation */
      {
         int nPrecomputed = 1<<nItems;
         int elmDataSize = GFP_FELEN(pGFpx)*sizeof(BNU_CHUNK_T);

         /* get temporary */
         BNU_CHUNK_T* pT = cpGFpGetPool(1, pGFpx);

         /* init result */
         int tblIdx = GetIndex(ppExponent, nItems, --expBitSize);
         cpScrambleGet((Ipp8u*)pR, elmDataSize, pScratchAligned+tblIdx, nPrecomputed);

         /* compute the rest: square and multiply */
         for(--expBitSize; expBitSize>=0; expBitSize--) {
            pGFpx->sqr(pR, pR, pGFpx);
            tblIdx = GetIndex(ppExponent, nItems, expBitSize);
            cpScrambleGet((Ipp8u*)pT, elmDataSize, pScratchAligned+tblIdx, nPrecomputed);
            pGFpx->mul(pR, pR, pT, pGFpx);
         }

         /* release resourse */
         cpGFpReleasePool(1, pGFpx);
      }

      /* release resourse */
      cpGFpReleasePool(nItems, pGFpx);

      return pR;
   }
}
