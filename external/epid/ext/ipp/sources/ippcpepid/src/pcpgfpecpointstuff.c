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
//     Internal EC over GF(p^m) basic Definitions & Function Prototypes
// 
//     Context:
//        cpEcGFpMakePoint()
//        cpEcGFpGetAffinePoint
// 
//        cpEcGFpIsPointEquial()
//        cpEcGFpIsPointOnCurve()
// 
//        cpEcGFpNegPoint()
//        cpEcGFpDblPoint()
//        cpEcGFpAddPoint()
//        cpEcGFpMulPoint()
// 
// 
// 
*/

#include "owncpepid.h"

#include "pcpgfpecstuff.h"
//#include "pcptool.h"


int cpEcGFpMakePoint(IppsGFpECPoint* pPoint, const BNU_CHUNK_T* pElm, IppsGFpECState* pEC)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   int elemLen = GFP_FELEN(pGF);

   BNU_CHUNK_T* pX = ECP_POINT_X(pPoint);
   BNU_CHUNK_T* pY = ECP_POINT_Y(pPoint);
   BNU_CHUNK_T* pZ = ECP_POINT_Z(pPoint);

   /* set x-coordinate */
   cpGFpElementCopy(pX, pElm, elemLen);

   /* T = X^3 + A*X + B */
   cpGFpxSqr(pY, pX, pGF);
   pGF->mul(pY, pY, pX, pGF);
   if(!EPID_PARAMS(pEC)) {
      pGF->mul(pZ, ECP_A(pEC), pX, pGF);
      pGF->add(pY, pY, pZ, pGF);
   }
   pGF->add(pY, pY, ECP_B(pEC), pGF);

   /* set z-coordinate =1 */
   cpGFpElementCopyPadd(pZ, elemLen, MNT_1(GFP_MONT(pGF)), GFP_FELEN(pGF));

   /* Y = sqrt(Y) */
   if( cpGFpSqrt(pY, pY, pGF) ) {
      ECP_POINT_FLAGS(pPoint) = ECP_AFFINE_POINT | ECP_FINITE_POINT;
      return 1;
   }
   else {
      cpEcGFpSetProjectivePointAtInfinity(pPoint, elemLen);
      //ECP_POINT_FLAGS(pPoint) = 0;
      return 0;
   }
}

#if ( ECP_PROJECTIVE_COORD == JACOBIAN )
int cpEcGFpGetAffinePoint(BNU_CHUNK_T* pX, BNU_CHUNK_T* pY, const IppsGFpECPoint* pPoint, IppsGFpECState* pEC)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   int elemLen = GFP_FELEN(pGF);

   if( !IS_ECP_FINITE_POINT(pPoint) ) {
      //GFP_ZERO(pX, elemLen);
      //if( GFP_IS_ZERO(ECP_B(pEC), elemLen) )
      //   GFP_ONE(pY, elemLen);
      //else
      //   GFP_ZERO(pY, elemLen);
      //return;
      return 0;
   }

   /* case Z == 1 */
   if( IS_ECP_AFFINE_POINT(pPoint) ) {
      if(pX)
         cpGFpElementCopy(pX, ECP_POINT_X(pPoint), elemLen);
      if(pY)
         cpGFpElementCopy(pY, ECP_POINT_Y(pPoint), elemLen);
   }

   /* case Z != 1 */
   else {
      /* T = (1/Z)*(1/Z) */
      BNU_CHUNK_T* pT    = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pZinv = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pU = cpGFpGetPool(1, pGF);
      cpGFpxInv(pZinv, ECP_POINT_Z(pPoint), pGF);
      pGF->sqr(pT, pZinv, pGF);

      if(pX) {
         pGF->mul(pU, ECP_POINT_X(pPoint), pT, pGF);
         cpGFpElementCopy(pX, pU, elemLen);
      }
      if(pY) {
         pGF->mul(pT, pZinv, pT, pGF);
         pGF->mul(pU, ECP_POINT_Y(pPoint), pT, pGF);
         cpGFpElementCopy(pY, pU, elemLen);
      }

      cpGFpReleasePool(3, pGF);
   }

   return 1;
}
#endif

#if ( ECP_PROJECTIVE_COORD == HOMOGENEOUS )
int cpEcGFpGetAffinePoint(BNU_CHUNK_T* pX, BNU_CHUNK_T* pY, const IppsGFpECPoint* pPoint, IppsGFpECState* pEC)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   int elemLen = GFP_FELEN(pGF);

   if( !IS_ECP_FINITE_POINT(pPoint) ) {
      return 0;
   }

   /* case Z == 1 */
   if( IS_ECP_AFFINE_POINT(pPoint) ) {
      if(pX)
         cpGFpElementCopy(pX, ECP_POINT_X(pPoint), elemLen);
      if(pY)
         cpGFpElementCopy(pY, ECP_POINT_Y(pPoint), elemLen);
   }

   /* case Z != 1 */
   else {
      /* T = (1/Z) */
      BNU_CHUNK_T* pZinv = cpGFpGetPool(1, pGF);
      cpGFpxInv(pZinv, ECP_POINT_Z(pPoint), pGF);

      if(pX) {
         pGF->mul(pX, ECP_POINT_X(pPoint), pZinv, pGF);
      }
      if(pY) {
         pGF->mul(pY, ECP_POINT_Y(pPoint), pZinv, pGF);
      }

      cpGFpReleasePool(1, pGF);
   }

   return 1;
}
#endif

#if ( ECP_PROJECTIVE_COORD == JACOBIAN )
int cpEcGFpIsPointEquial(const IppsGFpECPoint* pP, const IppsGFpECPoint* pQ, IppsGFpECState* pEC)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   int elemLen = GFP_FELEN(pGF);

   /* P or/and Q at Infinity */
   if( !IS_ECP_FINITE_POINT(pP) )
      return !IS_ECP_FINITE_POINT(pQ)? 1:0;
   if( !IS_ECP_FINITE_POINT(pQ) )
      return !IS_ECP_FINITE_POINT(pP)? 1:0;

   /* Px==Qx && Py==Qy && Pz==Qz */
   if(  GFP_EQ(ECP_POINT_Z(pP), ECP_POINT_Z(pQ), elemLen)
      &&GFP_EQ(ECP_POINT_X(pP), ECP_POINT_X(pQ), elemLen)
      &&GFP_EQ(ECP_POINT_Y(pP), ECP_POINT_Y(pQ), elemLen))
      return 1;

   else {
      int isEqu = 1;

      BNU_CHUNK_T* pPtmp = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pQtmp = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pPz   = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pQz   = cpGFpGetPool(1, pGF);

      if(isEqu) {
         /* Px*Qz^2 ~ Qx*Pz^2 */
         if( IS_ECP_AFFINE_POINT(pQ) ) /* Ptmp = Px * Qz^2 */
            cpGFpElementCopy(pPtmp, ECP_POINT_X(pP), elemLen);
         else {
            pGF->sqr(pQz, ECP_POINT_Z(pQ), pGF);
            pGF->mul(pPtmp, ECP_POINT_X(pP), pQz, pGF);
         }
         if( IS_ECP_AFFINE_POINT(pP) ) /* Qtmp = Qx * Pz^2 */
            cpGFpElementCopy(pQtmp, ECP_POINT_X(pQ), elemLen);
         else {
            pGF->sqr(pPz, ECP_POINT_Z(pP), pGF);
            pGF->mul(pQtmp, ECP_POINT_X(pQ), pPz, pGF);
         }
         isEqu = GFP_EQ(pPtmp, pQtmp, elemLen);
      }

      if(isEqu) {
         /* Py*Qz^3 ~ Qy*Pz^3 */
         if( IS_ECP_AFFINE_POINT(pQ) ) /* Ptmp = Py * Qz^3 */
            cpGFpElementCopy(pPtmp, ECP_POINT_Y(pP), elemLen);
         else {
            pGF->mul(pQz, ECP_POINT_Z(pQ), pQz, pGF);
            pGF->mul(pPtmp, pQz, ECP_POINT_Y(pP), pGF);
         }
         if( IS_ECP_AFFINE_POINT(pP) ) /* Qtmp = Qy * Pz^3 */
            cpGFpElementCopy(pQtmp, ECP_POINT_Y(pQ), elemLen);
         else {
            pGF->mul(pPz, ECP_POINT_Z(pP), pPz, pGF);
            pGF->mul(pQtmp, pPz, ECP_POINT_Y(pQ), pGF);
         }
         isEqu = GFP_EQ(pPtmp, pQtmp, elemLen);
      }

      cpGFpReleasePool(4, pGF);
      return isEqu;
   }
}
#endif

#if ( ECP_PROJECTIVE_COORD == HOMOGENEOUS )
int cpEcGFpIsPointEquial(const IppsGFpECPoint* pP, const IppsGFpECPoint* pQ, IppsGFpECState* pEC)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   int elemLen = GFP_FELEN(pGF);

   /* P or/and Q at Infinity */
   if( !IS_ECP_FINITE_POINT(pP) )
      return !IS_ECP_FINITE_POINT(pQ)? 1:0;
   if( !IS_ECP_FINITE_POINT(pQ) )
      return !IS_ECP_FINITE_POINT(pP)? 1:0;

   /* Px==Qx && Py==Qy && Pz==Qz */
   if(  GFP_EQ(ECP_POINT_Z(pP), ECP_POINT_Z(pQ), elemLen)
      &&GFP_EQ(ECP_POINT_X(pP), ECP_POINT_X(pQ), elemLen)
      &&GFP_EQ(ECP_POINT_Y(pP), ECP_POINT_Y(pQ), elemLen))
      return 1;

   else {
      int isEqu = 1;

      BNU_CHUNK_T* pPtmp = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pQtmp = cpGFpGetPool(1, pGF);

      if(isEqu) {
         /* Px*Qz ~ Qx*Pz */
         if( IS_ECP_AFFINE_POINT(pQ) ) /* Ptmp = Px * Qz */
            cpGFpElementCopy(pPtmp, ECP_POINT_X(pP), elemLen);
         else {
            pGF->mul(pPtmp, ECP_POINT_X(pP), ECP_POINT_Z(pQ), pGF);
         }
         if( IS_ECP_AFFINE_POINT(pP) ) /* Qtmp = Qx * Pz */
            cpGFpElementCopy(pQtmp, ECP_POINT_X(pQ), elemLen);
         else {
            pGF->mul(pQtmp, ECP_POINT_X(pQ), ECP_POINT_Z(pP), pGF);
         }
         isEqu = GFP_EQ(pPtmp, pQtmp, elemLen);
      }

      if(isEqu) {
         /* Py*Qz ~ Qy*Pz */
         if( IS_ECP_AFFINE_POINT(pQ) ) /* Ptmp = Py * Qz */
            cpGFpElementCopy(pPtmp, ECP_POINT_Y(pP), elemLen);
         else {
            pGF->mul(pPtmp, ECP_POINT_Y(pP), ECP_POINT_Z(pQ), pGF);
         }
         if( IS_ECP_AFFINE_POINT(pP) ) /* Qtmp = Qy * Pz */
            cpGFpElementCopy(pQtmp, ECP_POINT_Y(pQ), elemLen);
         else {
            pGF->mul(pQtmp, ECP_POINT_Y(pQ), ECP_POINT_Z(pP), pGF);
         }
         isEqu = GFP_EQ(pPtmp, pQtmp, elemLen);
      }

      cpGFpReleasePool(2, pGF);
      return isEqu;
   }
}
#endif

#if ( ECP_PROJECTIVE_COORD == JACOBIAN )
int cpEcGFpIsPointOnCurve(const IppsGFpECPoint* pPoint, IppsGFpECState* pEC)
{
   /* point at infinity belongs curve */
   if( !IS_ECP_FINITE_POINT(pPoint) )
      return 1;

   /* test that 0 == R = (Y^2) - (X^3 + A*X*(Z^4) + B*(Z^6)) */
   else {
      int isOnCurve = 0;

      IppsGFpState* pGF = ECP_GFP(pEC);

      BNU_CHUNK_T* pX = ECP_POINT_X(pPoint);
      BNU_CHUNK_T* pY = ECP_POINT_Y(pPoint);
      BNU_CHUNK_T* pZ = ECP_POINT_Z(pPoint);

      BNU_CHUNK_T* pR = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pT = cpGFpGetPool(1, pGF);

      pGF->sqr(pR, pY, pGF);       /* R = Y^2 */
      pGF->sqr(pT, pX, pGF);       /* T = X^3 */
      pGF->mul(pT, pX, pT, pGF);
      pGF->sub(pR, pR, pT, pGF);   /* R -= T */

      if( IS_ECP_AFFINE_POINT(pPoint) ) {
         pGF->mul(pT, pX, ECP_A(pEC), pGF);   /* T = A*X */
         pGF->sub(pR, pR, pT, pGF);               /* R -= T */
         pGF->sub(pR, pR, ECP_B(pEC), pGF);       /* R -= B */
      }
      else {
         BNU_CHUNK_T* pZ4 = cpGFpGetPool(1, pGF);
         BNU_CHUNK_T* pZ6 = cpGFpGetPool(1, pGF);

         pGF->sqr(pZ6, pZ, pGF);         /* Z^2 */
         pGF->sqr(pZ4, pZ6, pGF);        /* Z^4 */
         pGF->mul(pZ6, pZ6, pZ4, pGF);   /* Z^6 */

         pGF->mul(pZ4, pZ4, pX, pGF);         /* X*(Z^4) */
         pGF->mul(pZ4, pZ4, ECP_A(pEC), pGF); /* A*X*(Z^4) */
         pGF->mul(pZ6, pZ6, ECP_B(pEC), pGF); /* B*(Z^4) */

         pGF->sub(pR, pR, pZ4, pGF);           /* R -= A*X*(Z^4) */
         pGF->sub(pR, pR, pZ6, pGF);           /* R -= B*(Z^6)   */

         cpGFpReleasePool(2, pGF);
      }

      isOnCurve = GFP_IS_ZERO(pR, GFP_FELEN(pGF));
      cpGFpReleasePool(2, pGF);
      return isOnCurve;
   }
}
#endif

#if ( ECP_PROJECTIVE_COORD == HOMOGENEOUS )
int cpEcGFpIsPointOnCurve(const IppsGFpECPoint* pPoint, IppsGFpECState* pEC)
{
   /* point at infinity belongs curve */
   if( !IS_ECP_FINITE_POINT(pPoint) )
      return 1;

   /* test that 0 == R = ((Y^2)*Z) - (X^3 + A*X*(Z^2) + B*(Z^3)) */
   else {
      int isOnCurve = 0;

      IppsGFpState* pGF = ECP_GFP(pEC);

      BNU_CHUNK_T* pX = ECP_POINT_X(pPoint);
      BNU_CHUNK_T* pY = ECP_POINT_Y(pPoint);
      BNU_CHUNK_T* pZ = ECP_POINT_Z(pPoint);

      BNU_CHUNK_T* pR = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pT = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pU = cpGFpGetPool(1, pGF);

      /* Right = X^3 + A*X*(Z^2) + B*(Z^3) = x^3 +(A*X + B*Z)*Z^2 */
      pGF->sqr(pR, pZ, pGF);             /* R = Z^2 */
      pGF->mul(pT, pZ, ECP_B(pEC), pGF); /* T = Z*B */
      if(!EPID_PARAMS(pEC)) {
         pGF->mul(pU, pX, ECP_A(pEC), pGF); /* U = X*A */
         pGF->add(pT, pT, pU, pGF);         /* T = (A*X + B*Z) * Z^2 */
      }
      pGF->mul(pT, pT, pR, pGF);

      pGF->sqr(pR, pX, pGF);             /* R = X^3 */
      pGF->mul(pR, pR, pX, pGF);

      pGF->add(pR, pR, pT, pGF);         /* R = X^3 + (A*X + B*Z) * Z^2 */

      /* Left = (Y^2)*Z */
      pGF->sqr(pT, pY, pGF);
      pGF->mul(pT, pT, pZ, pGF);

      pGF->sub(pR, pR, pT, pGF);         /* Left - Right */

      isOnCurve = GFP_IS_ZERO(pR, GFP_FELEN(pGF));

      cpGFpReleasePool(3, pGF);
      return isOnCurve;
   }
}
#endif

IppsGFpECPoint* cpEcGFpNegPoint (IppsGFpECPoint* pR, const IppsGFpECPoint* pP, IppsGFpECState* pEC)
{
   int elemLen = GFP_FELEN(ECP_GFP(pEC));
   IppsGFpState* pGF = ECP_GFP(pEC);

   if(pP!=pR)
      cpEcGFpCopyPoint(pR, pP, elemLen);

   if( IS_ECP_FINITE_POINT(pR) )
      pGF->neg(ECP_POINT_Y(pR), ECP_POINT_Y(pR), pGF);
   return pR;
}

#if ( ECP_PROJECTIVE_COORD == JACOBIAN )
/* general complexity = 6s+4m
      epid complexity = 4s+3m
*/
IppsGFpECPoint* cpEcGFpDblPoint (IppsGFpECPoint* pR, const IppsGFpECPoint* pP, IppsGFpECState* pEC)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   int elemLen = GFP_FELEN(pGF);

   BNU_CHUNK_T* pX = ECP_POINT_X(pP);
   BNU_CHUNK_T* pY = ECP_POINT_Y(pP);
   BNU_CHUNK_T* pZ = ECP_POINT_Z(pP);

   BNU_CHUNK_T* pU = cpGFpGetPool(1, pGF);
   BNU_CHUNK_T* pM = cpGFpGetPool(1, pGF);
   BNU_CHUNK_T* pS = cpGFpGetPool(1, pGF);

   /* M = 3*X^2 + A*Z^4 */
   pGF->sqr(pU, pX, pGF);                /* s */
   pGF->add(pM, pU, pU, pGF);
   pGF->add(pM, pU, pM, pGF);
   if(!EPID_PARAMS(pEC)) {
      if( IS_ECP_AFFINE_POINT(pP) )
         pGF->add(pM, ECP_A(pEC), pM, pGF);
      else {
         pGF->sqr(pU, pZ, pGF);             /* s */
         pGF->sqr(pU, pU, pGF);             /* s */
         pGF->mul(pU, ECP_A(pEC), pU, pGF); /* m */
         pGF->add(pM, pM, pU, pGF);
      }
   }

   /* U = 2*Y */
   pGF->add(pU, pY, pY, pGF);

   /* Rz = 2*Y*Z */
   if( IS_ECP_AFFINE_POINT(pP) )
      cpGFpElementCopy(ECP_POINT_Z(pR), pU, elemLen);
   else
      pGF->mul(ECP_POINT_Z(pR), pU, pZ, pGF);  /* m */

   /* S = X*(U^2) = 4*X*Y^2 */
   pGF->sqr(pU, pU, pGF);       /* s */
   pGF->mul(pS, pX, pU, pGF);   /* m */

   /* Rx = M^2 - 2*S */
   pGF->sqr(ECP_POINT_X(pR),pM,  pGF);      /* s */
   pGF->sub(ECP_POINT_X(pR), ECP_POINT_X(pR), pS, pGF);
   pGF->sub(ECP_POINT_X(pR), ECP_POINT_X(pR), pS, pGF);

   /* U = (U^2)/2 = (16*Y^4)/2 = 8*Y^4 */
   pGF->sqr(pU, pU, pGF);                   /* s */
   //cpGFpxHalve(pU, pU, pGF);
   pGF->div2(pU, pU, pGF);

   /* Ry = M*(S - Rx) - U */
   pGF->sub(pS, pS, ECP_POINT_X(pR), pGF);
   pGF->mul(pS, pM, pS, pGF);               /* m */
   pGF->sub(ECP_POINT_Y(pR), pS, pU, pGF);

   //ECP_POINT_FLAGS(pR) = ECP_FINITE_POINT;
   ECP_POINT_FLAGS(pR) = cpEcGFpIsProjectivePointAtInfinity(pR, elemLen)? 0 : ECP_FINITE_POINT;


   cpGFpReleasePool(3, pGF);

   return pR;
}
#endif

#if ( ECP_PROJECTIVE_COORD == HOMOGENEOUS )
/*
// A = 3*X^2 + A*Z^2
// B = Y*Z
// C = X*Y*B
// D = A^2 - 8*C
// new X = 2*B*D
// new Y = A*(4*C - D) - 8*(Y*B)^2
// new Z = 8*B^3
//
// general complexity = 5s+8m
//    epid complexity = 4s+7m
*/
IppsGFpECPoint* cpEcGFpDblPoint (IppsGFpECPoint* pR, const IppsGFpECPoint* pP, IppsGFpECState* pEC)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   int elemLen = GFP_FELEN(pGF);

   /* P at infinity => R at infinity */
   if( !IS_ECP_FINITE_POINT(pP) )
      cpEcGFpSetProjectivePointAtInfinity(pR, elemLen);

   else {
      BNU_CHUNK_T* pA = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pB = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pC = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pD = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pT = cpGFpGetPool(1, pGF);

      BNU_CHUNK_T* pX = ECP_POINT_X(pR);
      BNU_CHUNK_T* pY = ECP_POINT_Y(pR);
      BNU_CHUNK_T* pZ = ECP_POINT_Z(pR);
      if(pR!=pP) {
         cpGFpElementCopy(pX, ECP_POINT_X(pP), elemLen);
         cpGFpElementCopy(pY, ECP_POINT_Y(pP), elemLen);
         cpGFpElementCopy(pZ, ECP_POINT_Z(pP), elemLen);
      }

      /* A = 3*X^2 + A*Z^2 */
      pGF->sqr(pC, pX, pGF);                /* s */
      pGF->add(pA, pC, pC, pGF);
      pGF->add(pA, pA, pC, pGF);
      if(!EPID_PARAMS(pEC)) {
         pGF->sqr(pB, pZ, pGF);             /* s */
         pGF->mul(pB, pB, ECP_A(pEC), pGF); /* m */
         pGF->add(pA, pA, pB, pGF);
      }

      /* B = Y*Z */
      pGF->mul(pB, pY, pZ, pGF);         /* m */

      /* C = X*Y*B */
      pGF->mul(pC, pX, pY, pGF);         /* m */
      pGF->mul(pC, pC, pB, pGF);         /* m */

      /* D = A^2 - 8*C */
      pGF->sqr(pT, pA, pGF);             /* s */
      pGF->add(pD, pC, pC, pGF);
      pGF->add(pD, pD, pD, pGF);
      pGF->add(pD, pD, pD, pGF);
      pGF->sub(pD, pT, pD, pGF);

      /* X = 2*B*D */
      pGF->mul(pX, pB, pD, pGF);         /* m */
      pGF->add(pX, pX, pX, pGF);

      pGF->add(pB, pB, pB, pGF);         /* B = 2*B */

      /* Y = A*(4*C-D)-8(Y*B)^2 */
      pGF->mul(pT, pY, pB, pGF);         /* m */
      pGF->sqr(pT, pT, pGF);             /* s */ /* T = 4*(Y*B)^2 */
      pGF->add(pY, pC, pC, pGF);
      pGF->add(pY, pY, pY, pGF);
      pGF->sub(pY, pY, pD, pGF);
      pGF->mul(pY, pY, pA, pGF);         /* m */
      pGF->sub(pY, pY, pT, pGF);
      pGF->sub(pY, pY, pT, pGF);

      /* Z = 8*B^3 = (2*B)^3 */
      pGF->sqr(pZ, pB, pGF);             /* s */
      pGF->mul(pZ, pZ, pB, pGF);         /* m */

      ECP_POINT_FLAGS(pR) = ECP_FINITE_POINT;

      cpGFpReleasePool(5, pGF);
   }

   return pR;
}
#endif

#if 0
#if ( ECP_PROJECTIVE_COORD == JACOBIAN )
/*
// initial k-Doubling routine:
//
// for(i=0; i<k; i++) { // complexity = 6s+4m
//    W[i] = a*Z[i]^4
//    M[i] = 3*X[i]^2 + W[i]
//    S[i] = 4*X[i]*Y[i]^2
//    T[i] = 8*Y[i]^4
//    X[i+1] = M[i]^2 -2*S[i]
//    Y[i+1] = M[i]*(S[i]-X[i+1]) -T[i]
//    Z[i+1] = 2Y[i]*Z[i]
// }
//
// could be improved by considering:
//    W[i] = a*Z[i]^4
//    Z[i+1] = 2*Y[i]*Z[i]
//    T[i] = 8*Y[i]^4
// therefore
//    W[i+1] = a*Z[i+1]^4
//           = a*(2*Y[i]*Z[i])^4
//           = a*16*Y[i]^4*Z[i]^4 = (a*Z[i]^4) *2 * (8*Y[i]^4)
//           = 2*W[i]*T[i] - which eliminates 2 squarings
//
// improved k-Doubling routine:
//    W[0] = a*Z[0]^4
//    M[0] = 3*X[0]^2 + W[0]
//    S[0] = 4*X[0]*Y[0]^2
//    T[0] = 8*Y[0]^4
//    X[1] = M[0]^2 -2*S[0]
//    Y[1] = M[0]*(S[0]-X[1]) -T[0]
//    Z[1] = 2Y[0]*Z[0]
// for(i=1; i<k; i++) { // complexity = 4s+4m per pass
//    W[i] = 2*T[i-1]*W[i-1]
//    M[i] = 3*X[i]^2 + W[i]
//    S[i] = 4*X[i]*Y[i]^2
//    T[i] = 8*Y[i]^4
//    X[i+1] = M[i]^2 -2*S[i]
//    Y[i+1] = M[i]*(S[i]-X[i+1]) -T[i]
//    Z[i+1] = 2Y[i]*Z[i]
// }
//
// general complexity = (6s+4m) + (k-1)*(4s+4m)
//   epidl complexity = (4s+3m) + (k-1)*(4s+3m)
*/
IppsGFpECPoint* cpEcGFpDblPoint_k(IppsGFpECPoint* pR, const IppsGFpECPoint* pP,int k, IppsGFpECState* pEC)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   int elemLen = GFP_FELEN(pGF);

   /* P at infinity => R at infinity */
   if( !IS_ECP_FINITE_POINT(pP) )
      return cpEcGFpSetProjectivePointAtInfinity(pR, elemLen);

   else {
      BNU_CHUNK_T* pW = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pM = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pS = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pT = cpGFpGetPool(1, pGF);

      BNU_CHUNK_T* pX = ECP_POINT_X(pR);
      BNU_CHUNK_T* pY = ECP_POINT_Y(pR);
      BNU_CHUNK_T* pZ = ECP_POINT_Z(pR);
      if(pR!=pP) {
         cpGFpElementCopy(pX, ECP_POINT_X(pP), elemLen);
         cpGFpElementCopy(pY, ECP_POINT_Y(pP), elemLen);
         cpGFpElementCopy(pZ, ECP_POINT_Z(pP), elemLen);
      }

      /* M = 3*X^2 + A*Z^4 */
      pGF->sqr(pS, pX, pGF);             /* s */
      pGF->add(pM, pS, pS, pGF);
      pGF->add(pM, pM, pS, pGF);
      if(!EPID_PARAMS(pEC)) {         /* W = A*Z^4 */
         pGF->sqr(pW, pZ, pGF);             /* s */
         pGF->sqr(pW, pW, pGF);             /* s */
         pGF->mul(pW, pW, ECP_A(pEC), pGF); /* m */

         pGF->add(pM, pM, pW, pGF);
      }
      
      /* T = 2*Y */
      pGF->add(pT, pY, pY, pGF);

      /* new Z = 2*Y*Z */
      pGF->mul(pZ, pT, pZ, pGF);         /* m */

      /* S = X*(T^2) = 4*X*Y^2 */
      pGF->sqr(pT, pT, pGF);             /* s */
      pGF->mul(pS, pX, pT, pGF);         /* m */

      /* T = (T^2)/2 = (16*Y^4)/2 = 8*Y^4 */
      pGF->sqr(pT, pT, pGF);             /* s */
      //cpGFpxHalve(pT, pT, pGF);
      cpGF->div2(pT, pT, pGF);

      /* new X = M^2 - 2*S */
      pGF->sqr(pX, pM, pGF);             /* s */
      pGF->sub(pX, pX, pS, pGF);
      pGF->sub(pX, pX, pS, pGF);

      /* new Y = M*(S - new X) - T */
      pGF->sub(pY, pS, pX, pGF);
      pGF->mul(pY, pY, pM, pGF);         /* m */
      pGF->sub(pY, pY, pT, pGF);

      for(k--; k>0; k--) {
         /* new W = 2*T*W */
         if(!EPID_PARAMS(pEC)) {
            pGF->mul(pW, pW, pT, pGF);      /* m */
            pGF->add(pW, pW, pW, pGF);
         }
         
         /* M = 3*X^2 + new W */
         pGF->sqr(pS, pX, pGF);          /* s */
         pGF->add(pM, pS, pS, pGF);
         pGF->add(pM, pM, pS, pGF);
         if(!EPID_PARAMS(pEC)) {
            pGF->add(pM, pM, pW, pGF);
         }
         
         /* T = 2*Y */
         pGF->add(pT, pY, pY, pGF);

         /* new Z = 2*Y*Z */
         pGF->mul(pZ, pT, pZ, pGF);      /* m */

         /* S = X*(T^2) = 4*X*Y^2 */
         pGF->sqr(pT, pT, pGF);          /* s */
         pGF->mul(pS, pX, pT, pGF);      /* m */

         /* T = (T^2)/2 = (16*Y^4)/2 = 8*Y^4 */
         pGF->sqr(pT, pT, pGF);          /* s */
         //cpGFpxHalve(pT, pT, pGF);
         cpGF->div2(pT, pT, pGF);

         /* new X = M^2 - 2*S */
         pGF->sqr(pX, pM, pGF);          /* s */
         pGF->sub(pX, pX, pS, pGF);
         pGF->sub(pX, pX, pS, pGF);

         /* new Y = M*(S - new X) - T */
         pGF->sub(pY, pS, pX, pGF);
         pGF->mul(pY, pY, pM, pGF);      /* m */
         pGF->sub(pY, pY, pT, pGF);
      }

      ECP_POINT_FLAGS(pR) = ECP_FINITE_POINT;

      cpGFpReleasePool(4, pGF);
      return pR;
   }
}
#endif
#endif


IppsGFpECPoint* cpEcGFpDblPoint_k(IppsGFpECPoint* pR, const IppsGFpECPoint* pP,int k, IppsGFpECState* pEC)
{
   cpEcGFpDblPoint(pR, pP, pEC);
   k--;
   for(; k>0; k--)
      cpEcGFpDblPoint(pR, pR, pEC);

   return pR;
}


#if ( ECP_PROJECTIVE_COORD == JACOBIAN )
/* complexity = 4s+12m */
IppsGFpECPoint* cpEcGFpAddPoint (IppsGFpECPoint* pPointR, const IppsGFpECPoint* pPointP, const IppsGFpECPoint* pPointQ, IppsGFpECState* pEC)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   int elemLen = GFP_FELEN(pGF);

   int inftyP = cpEcGFpIsProjectivePointAtInfinity(pPointP, elemLen);
   int inftyQ = cpEcGFpIsProjectivePointAtInfinity(pPointQ, elemLen);

   /*
   // addition
   */
   BNU_CHUNK_T* pA = cpEcGFpGetPool(3, pEC);
   BNU_CHUNK_T* pB = pA + elemLen;
   BNU_CHUNK_T* pC = pB + elemLen;
   BNU_CHUNK_T* pD = pC + elemLen;
   BNU_CHUNK_T* pW = pD + elemLen;
   BNU_CHUNK_T* pV = pW + elemLen;

   BNU_CHUNK_T* pRx = pV + elemLen; /* temporary result */
   BNU_CHUNK_T* pRy = pRx+ elemLen;
   BNU_CHUNK_T* pRz = pRy+ elemLen;

   /* coordinates of P */
   BNU_CHUNK_T* px1 = ECP_POINT_X(pPointP);
   BNU_CHUNK_T* py1 = ECP_POINT_Y(pPointP);
   BNU_CHUNK_T* pz1 = ECP_POINT_Z(pPointP);

   /* coordinates of Q */
   BNU_CHUNK_T* px2 = ECP_POINT_X(pPointQ);
   BNU_CHUNK_T* py2 = ECP_POINT_Y(pPointQ);
   BNU_CHUNK_T* pz2 = ECP_POINT_Z(pPointQ);

   /* coordinates of R */
   //BNU_CHUNK_T* px3 = ECP_POINT_X(pPointR);
   //BNU_CHUNK_T* py3 = ECP_POINT_Y(pPointR);
   //BNU_CHUNK_T* pz3 = ECP_POINT_Z(pPointR);

   /* A = X1 * Z2^2 */
   /* C = Y1 * Z2^3 */
   if( IS_ECP_AFFINE_POINT(pPointQ) ) {
      cpGFpElementCopy(pA, px1, elemLen);
      cpGFpElementCopy(pC, py1, elemLen);
   }
   else {
      pGF->sqr(pA, pz2, pGF);      /* s */
      pGF->mul(pC, pz2, pA, pGF);  /* m */
      pGF->mul(pA, pA, px1, pGF);  /* m */
      pGF->mul(pC, pC, py1, pGF);  /* m */
   }

   /* B = X2 * Z1^2 */
   /* D = Y2 * Z1^3 */
   if( IS_ECP_AFFINE_POINT(pPointP) ) {
      cpGFpElementCopy(pB, px2, elemLen);
      cpGFpElementCopy(pD, py2, elemLen);
   }
   else {
      pGF->sqr(pB, pz1, pGF);      /* s */
      pGF->mul(pD, pz1, pB, pGF);  /* m */
      pGF->mul(pB, pB, px2, pGF);  /* m */
      pGF->mul(pD, pD, py2, pGF);  /* m */
   }

   /* W = B-A */
   /* V = D-C */
   pGF->sub(pW, pB, pA, pGF);
   pGF->sub(pV, pD, pC, pGF);

   if( GFP_IS_ZERO(pW, elemLen) && !inftyP && !inftyQ ) {
      cpEcGFpReleasePool(3, pEC);
      if( GFP_IS_ZERO(pV, elemLen) )
         return cpEcGFpDblPoint(pPointR, pPointP, pEC);
      else
         return cpEcGFpSetProjectivePointAtInfinity(pPointR, elemLen);
   }

   /* Z3 = Z1*Z2*W */
   if( IS_ECP_AFFINE_POINT(pPointP) && IS_ECP_AFFINE_POINT(pPointQ) )
      cpGFpElementCopy(pRz, pW, elemLen);
   else {
      if( IS_ECP_AFFINE_POINT(pPointQ) )
         cpGFpElementCopy(pB, pz1, elemLen);
      else if ( IS_ECP_AFFINE_POINT(pPointP) )
         cpGFpElementCopy(pB, pz2, elemLen);
      else
         pGF->mul(pB, pz1, pz2, pGF);    /* m */
      pGF->mul(pRz, pB, pW, pGF);        /* m */
   }

   /* B = W^2 */
   pGF->sqr(pB, pW, pGF);          /* s */
   /* A = A*W^2 */
   pGF->mul(pA, pB, pA, pGF);      /* m */
   /* W = W^3 */
   pGF->mul(pW, pB, pW, pGF);      /* m */

   /* X3 = V^2 - W^3 -2*A*W^2 */
   pGF->sqr(pRx, pV, pGF);         /* s */
   pGF->sub(pRx, pRx, pW, pGF);
   pGF->sub(pRx, pRx, pA, pGF);
   pGF->sub(pRx, pRx, pA, pGF);

   /* Y3 = V*(A*W^2 - X3) -C*W^3 */
   pGF->sub(pRy, pA, pRx, pGF);
   pGF->mul(pC, pC, pW, pGF);      /* m */
   pGF->mul(pRy, pRy, pV, pGF);    /* m */
   pGF->sub(pRy, pRy, pC, pGF);

   cpMaskMove(pRx, px2, elemLen, inftyP);
   cpMaskMove(pRy, py2, elemLen, inftyP);
   cpMaskMove(pRz, pz2, elemLen, inftyP);

   cpMaskMove(pRx, px1, elemLen, inftyQ);
   cpMaskMove(pRy, py1, elemLen, inftyQ);
   cpMaskMove(pRz, pz1, elemLen, inftyQ);

   cpGFpElementCopy(ECP_POINT_DATA(pPointR), pRx, 3*elemLen);
   ECP_POINT_FLAGS(pPointR) = cpEcGFpIsProjectivePointAtInfinity(pPointR, elemLen)? 0 : ECP_FINITE_POINT;

   cpEcGFpReleasePool(3, pEC);
   return pPointR;
}
#endif

#if ( ECP_PROJECTIVE_COORD == HOMOGENEOUS )
/*
// A = Y2 * Z1 - Y1 * Z2
// B = X2 * Z1 - X1 * Z2
// C = A^2*Z1*Z2 -B^3 -2*B^2*X1*Z2 = A^2*Z1*Z2 -B^2*(B+2*X1*Z2) = A^2*Z1*Z2 -B^2*(X2*Z1+X1*Z2)
// new X = B*C
// new Y = A*(B^2*X1*Z2 -C) -B^3*Y1*Z2
// new Z = B^3*Z1*Z2
//
// note: Y1*Z2, X2*Z1, X1*Z2, Z1*Z2 are using several times
//       (T1),  (T2),  (T3)   (T4)
//
// complexity = 2s+13m
*/
IppsGFpECPoint* cpEcGFpAddPoint (IppsGFpECPoint* pPointR, const IppsGFpECPoint* pP1, const IppsGFpECPoint* pP2, IppsGFpECState* pEC)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   int elemLen = GFP_FELEN(pGF);

   /* test stupid call */
   if( pP1 == pP2)
      return cpEcGFpDblPoint(pPointR, pP1, pEC);

   /* prevent operation with point at Infinity */
   if( !IS_ECP_FINITE_POINT(pP1) )
      return cpEcGFpCopyPoint(pPointR, pP2, elemLen);
   if( !IS_ECP_FINITE_POINT(pP2) )
      return cpEcGFpCopyPoint(pPointR, pP1, elemLen);

   /*
   // addition
   */
   {
      BNU_CHUNK_T* pT1 = cpEcGFpGetPool(3, pEC);
      BNU_CHUNK_T* pT2 = pT1 + elemLen;
      BNU_CHUNK_T* pT3 = pT2 + elemLen;
      BNU_CHUNK_T* pT4 = pT3 + elemLen;
      BNU_CHUNK_T* pA  = pT4 + elemLen;
      BNU_CHUNK_T* pB  = pA  + elemLen;
      BNU_CHUNK_T* pC  = pB  + elemLen;
      BNU_CHUNK_T* pB2 = pC  + elemLen;
      BNU_CHUNK_T* pB3 = pB2 + elemLen;

      /* coordinates of P1 */
      BNU_CHUNK_T* pX1 = ECP_POINT_X(pP1);
      BNU_CHUNK_T* pY1 = ECP_POINT_Y(pP1);
      BNU_CHUNK_T* pZ1 = ECP_POINT_Z(pP1);

      /* coordinates of P2 */
      BNU_CHUNK_T* pX2 = ECP_POINT_X(pP2);
      BNU_CHUNK_T* pY2 = ECP_POINT_Y(pP2);
      BNU_CHUNK_T* pZ2 = ECP_POINT_Z(pP2);

      /* A = Y2 * Z1 - Y1 * Z2 */
      pGF->mul(pA, pY2, pZ1, pGF);    /* m */
      pGF->mul(pT1,pY1, pZ2, pGF);    /* m */
      pGF->sub(pA, pA, pT1, pGF);

      /* B = X2 * Z1 - X1 * Z2 */
      pGF->mul(pT2,pX2, pZ1, pGF);    /* m */
      pGF->mul(pT3,pX1, pZ2, pGF);    /* m */
      pGF->sub(pB, pT2, pT3, pGF);

      if( GFP_IS_ZERO(pB, elemLen) ) {
         cpEcGFpReleasePool(3, pEC);
         if( GFP_IS_ZERO(pA, elemLen) )
            return cpEcGFpDblPoint(pPointR, pP1, pEC);
         else
            return cpEcGFpSetProjectivePointAtInfinity(pPointR, elemLen);
      }

      /* C = A^2*Z1*Z2 -B^2*(X2*Z1+X1*Z2) */
      pGF->sqr(pB2, pB, pGF);         /* s */
      pGF->add(pT2,pT2, pT3, pGF);
      pGF->mul(pT2,pT2, pB2, pGF);    /* m */
      pGF->mul(pT4,pZ1, pZ2, pGF);    /* m */
      pGF->sqr(pC, pA,  pGF);         /* s */
      pGF->mul(pC, pC,  pT4, pGF);    /* m */
      pGF->sub(pC, pC,  pT2, pGF);

      /* new X = B*C */
      pGF->mul(ECP_POINT_X(pPointR), pB, pC, pGF);     /* m */

      /* new Y = A*(B^2*X1*Z2 -C) -B^3*Y1*Z2 */
      pGF->mul(pT3, pT3,  pB2, pGF);    /* m */ /* T3 = (X1*Z2)*B^2 */
      pGF->sub(pT3, pT3,  pC,  pGF);
      pGF->mul(pT3, pT3,  pA,  pGF);    /* m */ /* T3 = A*(B^2*X1*Z2 -C) */
      pGF->mul(pB3, pB2,  pB,  pGF);    /* m */ /* B3 = B^3 */
      pGF->mul(pT1, pT1,  pB3, pGF);    /* m */ /* T1 = B^3*Y1*Z2 */
      pGF->sub(ECP_POINT_Y(pPointR), pT3, pT1, pGF);

      /* new Z = B^3*Z1*Z2 */
      pGF->mul(ECP_POINT_Z(pPointR), pB3, pT4, pGF); /* m */

      ECP_POINT_FLAGS(pPointR) = ECP_FINITE_POINT;

      cpEcGFpReleasePool(3, pEC);
      return pPointR;
   }
}
#endif

#if 0
/* non-sscm version */
IppsGFpECPoint* cpEcGFpMulPoint(IppsGFpECPoint* pPointR, const IppsGFpECPoint* pPointP, const BNU_CHUNK_T* pN, int nsN, IppsGFpECState* pEC, Ipp8u* pScratchBuffer)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   int elemLen = GFP_FELEN(pGF);

   UNREFERENCED_PARAMETER(pScratchBuffer);

   /* test scalar and input point */
   if( GFP_IS_ZERO(pN, nsN) || !IS_ECP_FINITE_POINT(pPointP) )
      return cpEcGFpSetProjectivePointAtInfinity(pPointR, elemLen);

   /* remove leding zeros */
   FIX_BNU(pN, nsN);

   /* case N==1 => R = P */
   if( GFP_IS_ONE(pN, nsN) ) {
      cpEcGFpCopyPoint(pPointR, pPointP, elemLen);
      return pPointR;
   }

   /*
   // scalar multiplication
   */
   else {
      int i;

      BNU_CHUNK_T* pH = cpGFpGetPool(1, pGF);
      BNU_CHUNK_T* pK = cpGFpGetPool(1, pGF);

      IppsGFpECPoint T, U;
      cpEcGFpInitPoint(&T, cpEcGFpGetPool(1, pEC),0, pEC);
      cpEcGFpInitPoint(&U, cpEcGFpGetPool(1, pEC),0, pEC);

      /* H = 3*N */
      cpGFpElementCopy(pK, pN, nsN);
      pK[nsN] = 0;
      i = cpAdd_BNU(pH, pK, pK, nsN+1);
      i = cpAdd_BNU(pH, pK, pH, nsN+1);

      /* T = affine(P) */
      if( IS_ECP_AFFINE_POINT(pPointP) )
         cpEcGFpCopyPoint(&T, pPointP, elemLen);
      else {
         cpEcGFpGetAffinePoint(ECP_POINT_X(&T), ECP_POINT_Y(&T), pPointP, pEC);
         cpEcGFpSetAffinePoint(&T, ECP_POINT_X(&T), ECP_POINT_Y(&T), pEC);
      }
      /* U = affine(-P) */
      cpEcGFpNegPoint(&U, &T, pEC);

      /* R = T = affine(P) */
      cpEcGFpCopyPoint(pPointR, &T, elemLen);

      /*
      // point multiplication
      */
      for(i=MSB_BNU(pH, nsN+1)-1; i>0; i--) {
         Ipp32u hBit = TST_BIT(pH, i);
         Ipp32u kBit = TST_BIT(pK, i);
         cpEcGFpDblPoint(pPointR, pPointR, pEC);
         if( hBit && !kBit )
            cpEcGFpAddPoint(pPointR, &T, pPointR, pEC);
         if(!hBit &&  kBit )
            cpEcGFpAddPoint(pPointR, &U, pPointR, pEC);
      }

      cpEcGFpReleasePool(2, pEC);
      cpGFpReleasePool(2, pGF);

      return pPointR;
   }
}
#endif

static int div_upper(int a, int d)
{ return (a+d-1)/d; }

#if 0
static int getNumOperations(int bitsize, int w)
{
   int n_overhead = (1<<w) -1;
   int n_ops = div_upper(bitsize, w) + n_overhead;
   return n_ops;
}
int cpEcGFpGetOptimalWinSize(int bitsize)
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

static int cpEcGFpConverRepresentation(BNU_CHUNK_T* pInput, int inpBits, int w)
{
   Ipp32u* pR   = (Ipp32u*)pInput;
   Ipp16u* pR16 = (Ipp16u*)pInput;

   int outBits = 0;
   Ipp32u base = (BNU_CHUNK_T)1<<w;
   Ipp32u digitMask = base-1;
   int i;

   int nsR =    BITS2WORD32_SIZE(inpBits);
   pR[nsR] = 0;               // expand input
   for(i=0; i<inpBits; i+=w) {
      cpSize chunkIdx = i/BITSIZE(Ipp16u);
      Ipp32u chunk = ((Ipp32u*)(pR16+chunkIdx))[0];
      int  digitShift = i % BITSIZE(Ipp16u);
      Ipp32u digit = (chunk>>digitShift) &digitMask;

      Ipp32u delta = (base-digit) & ~digitMask;
      delta <<= digitShift;
      cpDec_BNU32((Ipp32u*)(pR16+chunkIdx), (Ipp32u*)(pR16+chunkIdx), (2*nsR-chunkIdx+1)/2, delta);

      inpBits = BITSIZE_BNU32(pR, nsR);
      outBits += w;
   }

   return outBits;
}
#endif

/* sscm version */
IppsGFpECPoint* cpEcGFpMulPoint(IppsGFpECPoint* pPointR, const IppsGFpECPoint* pPointP, const BNU_CHUNK_T* pN, int nsN, IppsGFpECState* pEC, Ipp8u* pScratchBuffer)
{
   IppsGFpState* pGF = ECP_GFP(pEC);
   int elemLen = GFP_FELEN(pGF);

   /* test scalar and input point */
   if( GFP_IS_ZERO(pN, nsN) || !IS_ECP_FINITE_POINT(pPointP) )
      return cpEcGFpSetProjectivePointAtInfinity(pPointR, elemLen);

   /* remove leding zeros */
   FIX_BNU(pN, nsN);

   /* case N==1 => R = P */
   if( GFP_IS_ONE(pN, nsN) ) {
      cpEcGFpCopyPoint(pPointR, pPointP, elemLen);
      return pPointR;
   }

   {
      Ipp8u* pScratchAligned; /* aligned scratch buffer */
      int nAllocation = 0;    /* points from the pool */

      /* size of point (dwords) */
      int pointDataSize = ECP_FELEN(pEC)*sizeof(BNU_CHUNK_T);
      int pointDataSize32 = ECP_FELEN(pEC)*sizeof(BNU_CHUNK_T)/sizeof(Ipp32u);

      /* scalar bitsize */
      int scalarBitSize = BITSIZE_BNU(pN, nsN);
      /* optimal size of window */
      int window_size = (NULL==pScratchBuffer)? 1 : 5;
      /* number of table entries */
      int nPrecomputed = 1<<(window_size-1);

      IppsGFpECPoint T;
      cpEcGFpInitPoint(&T, cpEcGFpGetPool(1, pEC),0, pEC);
      cpEcGFpCopyPoint(&T, pPointP, elemLen);

      if(NULL==pScratchBuffer) {
         nAllocation = 1 + div_upper(CACHE_LINE_SIZE, pointDataSize);
         pScratchBuffer = (Ipp8u*)cpEcGFpGetPool(nAllocation, pEC);
      }
      pScratchAligned = IPP_ALIGNED_PTR(pScratchBuffer, CACHE_LINE_SIZE);

      /* pre-compute auxiliary table t[] = {1*P, 2*P, ..., nPrecomputed*P} */
      {
         int n;
         cpScatter32((Ipp32u*)pScratchAligned, nPrecomputed, 0, (Ipp32u*)ECP_POINT_DATA(&T), pointDataSize32);
         for(n=1; n<nPrecomputed; n++) {
            cpEcGFpAddPoint(&T, &T, pPointP, pEC);
            cpScatter32((Ipp32u*)pScratchAligned, nPrecomputed, n, (Ipp32u*)ECP_POINT_DATA(&T), pointDataSize32);
         }
      }

      {
         BNU_CHUNK_T* pNegY = cpGFpGetPool(1, pGF);

         BNU_CHUNK_T* pScalar = cpGFpGetPool(2, pGF);
         Ipp8u* pScalar8 = (Ipp8u*)pScalar;
         /* copy scalar value */
         cpGFpElementCopy(pScalar, pN, nsN);

         /* zero expanded scalar value */
         pScalar[BITS_BNU_CHUNK(scalarBitSize)] = 0;

         /*
         // scalar multiplication
         */
         {
            Ipp8u digit, sign;

            BNU_CHUNK_T dmask = (1<<(window_size+1)) -1;

            /* position (bit number) of the leftmost window */
            //int bit = scalarBitSize-window_size;
            int bit = scalarBitSize - (scalarBitSize % window_size);

            /* first window */
            int wvalue = *((Ipp16u*)&pScalar8[(bit-1)/8]);
            wvalue = (wvalue>> ((bit-1)%8)) & dmask;
            booth_recode(&sign, &digit, (Ipp8u)wvalue, window_size);

            cpGather32((Ipp32u*)ECP_POINT_DATA(pPointR), pointDataSize32, (Ipp32u*)pScratchAligned, nPrecomputed, digit);
            ECP_POINT_FLAGS(pPointR) = 0;

            for(bit-=window_size; bit>=window_size; bit-=window_size) {
               /* window_size times doubling */
               cpEcGFpDblPoint_k(pPointR, pPointR, window_size, pEC);

               /* extract next window value */
               wvalue = *((Ipp16u*)&pScalar8[(bit-1)/8]);
               wvalue = (wvalue>> ((bit-1)%8)) & dmask;
               booth_recode(&sign, &digit, (Ipp8u)wvalue, window_size);

               /* extract value from the pre-computed table */
               cpGather32((Ipp32u*)ECP_POINT_DATA(&T), pointDataSize32, (Ipp32u*)pScratchAligned, nPrecomputed, digit);

               pGF->neg(pNegY, ECP_POINT_Y(&T), pGF);
               cpMaskMove(ECP_POINT_Y(&T), pNegY, elemLen, sign);

               /* and add it */
               cpEcGFpAddPoint(pPointR, pPointR, &T, pEC);
            }

            /* last window */
            cpEcGFpDblPoint_k(pPointR, pPointR, window_size, pEC);

            wvalue = *((Ipp16u*)&pScalar8[0]);
            wvalue = (wvalue <<1) & dmask;
            booth_recode(&sign, &digit, (Ipp8u)wvalue, window_size);

            cpGather32((Ipp32u*)ECP_POINT_DATA(&T), pointDataSize32, (Ipp32u*)pScratchAligned, nPrecomputed, digit);

            pGF->neg(pNegY, ECP_POINT_Y(&T), pGF);
            cpMaskMove(ECP_POINT_Y(&T), pNegY, elemLen, sign);

            cpEcGFpAddPoint(pPointR, pPointR, &T, pEC);
         }

         cpGFpReleasePool(2+1, pGF);
      }

      cpEcGFpReleasePool(nAllocation+1, pEC);

      return pPointR;
   }
}
