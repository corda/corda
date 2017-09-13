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
//     Cryptography Primitive.
//     EC over Prime Finite Field (EC Point operations)
// 
//  Contents:
//     ippsECCPSetPoint()
//     ippsECCPSetPointAtInfinity()
//     ippsECCPGetPoint()
// 
//     ippsECCPCheckPoint()
//     ippsECCPComparePoint()
// 
//     ippsECCPNegativePoint()
//     ippsECCPAddPoint()
//     ippsECCPMulPointScalar()
// 
// 
*/

#include "precomp.h"
#include "owncp.h"
#include "pcpeccppoint.h"
#include "pcpeccpmethod.h"
#include "pcpeccpmethodcom.h"


/*F*
//    Name: ippsECCPSetPoint
//
// Purpose: Converts regular affine coordinates EC point (pX,pY)
//          into internal presentation - montgomery projective.
//
// Returns:                Reason:
//    ippStsNullPtrErr        NULL == pECC
//                            NULL == pPoint
//                            NULL == pX
//                            NULL == pY
//
//    ippStsContextMatchErr   illegal pECC->idCtx
//                            illegal pX->idCtx
//                            illegal pY->idCtx
//                            illegal pPoint->idCtx
//
//    ippStsOutOfECErr        point out-of EC
//
//    ippStsNoErr             no errors
//
// Parameters:
//    pX          pointer to the regular affine coordinate X
//    pY          pointer to the regular affine coordinate Y
//    pPoint      pointer to the EC Point context
//    pECC        pointer to the ECCP context
//
// Note:
//    if B==0 and (x,y)=(0,y) then point at Infinity will be set up
//    if B!=0 and (x,y)=(0,0) then point at Infinity will be set up
//    else point with requested coordinates (x,y) wil be set up
//    There are no check validation inside!
//
*F*/
IPPFUN(IppStatus, ippsECCPSetPoint,(const IppsBigNumState* pX,
                                    const IppsBigNumState* pY,
                                    IppsECCPPointState* pPoint,
                                    IppsECCPState* pECC))
{
   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   /* test ID */
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   /* test pX and pY */
   IPP_BAD_PTR2_RET(pX,pY);
   pX = (IppsBigNumState*)( IPP_ALIGNED_PTR(pX, ALIGN_VAL) );
   pY = (IppsBigNumState*)( IPP_ALIGNED_PTR(pY, ALIGN_VAL) );
   IPP_BADARG_RET(!BN_VALID_ID(pX), ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pY), ippStsContextMatchErr);

   /* test pPoint */
   IPP_BAD_PTR1_RET(pPoint);
   pPoint = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pPoint, ALIGN_VAL) );
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pPoint), ippStsContextMatchErr);

   /* set affine coordinates at Infinity */
   if( ( IsZero_BN(ECP_BENC(pECC)) && ECCP_IsPointAtAffineInfinity1(pX,pY)) ||
       (!IsZero_BN(ECP_BENC(pECC)) && ECCP_IsPointAtAffineInfinity0(pX,pY)) )
      ECCP_SetPointToInfinity(pPoint);
   /* set point */
   else {
      ECP_METHOD(pECC)->SetPointProjective(pX, pY, BN_ONE_REF(), pPoint, pECC);
   }

   return ippStsNoErr;
}


/*F*
//    Name: ippsECCPSetPointAtInfinity
//
// Purpose: Set point at Infinity
//
// Returns:                Reason:
//    ippStsNullPtrErr        NULL == pECC
//                            NULL == pPoint
//
//    ippStsContextMatchErr   illegal pECC->idCtx
//                            illegal pPoint->idCtx
//
//    ippStsNoErr             no errors
//
// Parameters:
//    pPoint      pointer to the EC Point context
//    pECC        pointer to the ECCP context
//
*F*/
IPPFUN(IppStatus, ippsECCPSetPointAtInfinity,(IppsECCPPointState* pPoint,
                                              IppsECCPState* pECC))
{
   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   /* test ID */
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   /* test pPoint */
   IPP_BAD_PTR1_RET(pPoint);
   pPoint = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pPoint, ALIGN_VAL) );
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pPoint), ippStsContextMatchErr);

   ECCP_SetPointToInfinity(pPoint);
   return ippStsNoErr;
}


/*F*
//    Name: ippsECCPGetPoint
//
// Purpose: Converts  internal presentation EC point - montgomery projective
//          into regular affine coordinates EC point (pX,pY)
//
// Returns:                Reason:
//    ippStsNullPtrErr        NULL == pECC
//                            NULL == pPoint
//
//    ippStsContextMatchErr   illegal pECC->idCtx
//                            illegal pPoint->idCtx
//                            NULL != pX, illegal pX->idCtx
//                            NULL != pY, illegal pY->idCtx
//
//    ippStsNoErr             no errors
//
// Parameters:
//    pX          pointer to the regular affine coordinate X
//    pY          pointer to the regular affine coordinate Y
//    pLength     pointer to the length of coordinates
//    pPoint      pointer to the EC Point context
//    pECC        pointer to the ECCP context
//
*F*/
IPPFUN(IppStatus, ippsECCPGetPoint,(IppsBigNumState* pX,
                                    IppsBigNumState* pY,
                                    const IppsECCPPointState* pPoint,
                                    IppsECCPState* pECC))
{
   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   /* test ID */
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   /* test source point */
   IPP_BAD_PTR1_RET(pPoint);
   pPoint = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pPoint, ALIGN_VAL) );
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pPoint), ippStsContextMatchErr);

   /* test pX and pY */
   if(pX) {
      pX = (IppsBigNumState*)( IPP_ALIGNED_PTR(pX, ALIGN_VAL) );
      IPP_BADARG_RET(!BN_VALID_ID(pX), ippStsContextMatchErr);
   }
   if(pY) {
      pY = (IppsBigNumState*)( IPP_ALIGNED_PTR(pY, ALIGN_VAL) );
      IPP_BADARG_RET(!BN_VALID_ID(pY), ippStsContextMatchErr);
   }

   if( ECCP_IsPointAtInfinity(pPoint) ) {
      if( IsZero_BN(ECP_BENC(pECC)) )
         ECCP_SetPointToAffineInfinity1(pX, pY);
      else
         ECCP_SetPointToAffineInfinity0(pX, pY);
   }
   else
      ECP_METHOD(pECC)->GetPointAffine(pX, pY, pPoint, pECC, ECP_BNCTX(pECC));
   return ippStsNoErr;
}


/*F*
//    Name: ippsECCPCheckPoint
//
// Purpose: Check EC point:
//             - is point lie on EC
//             - is point at infinity
//
// Returns:                Reason:
//    ippStsNullPtrErr        NULL == pECC
//                            NULL == pP
//                            NULL == pResult
//
//    ippStsContextMatchErr   illegal pECC->idCtx
//                            illegal pP->idCtx
//
//    ippStsNoErr             no errors
//
// Parameters:
//    pPoint      pointer to the EC Point context
//    pECC        pointer to the ECCP context
//    pResult     pointer to the result:
//                         ippECValid
//                         ippECPointIsNotValid
//                         ippECPointIsAtInfinite
//
*F*/
IPPFUN(IppStatus, ippsECCPCheckPoint,(const IppsECCPPointState* pP,
                                      IppECResult* pResult,
                                      IppsECCPState* pECC))
{
   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   /* test ID */
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   /* test point */
   IPP_BAD_PTR1_RET(pP);
   pP = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pP, ALIGN_VAL) );
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pP), ippStsContextMatchErr);

   /* test pResult */
   IPP_BAD_PTR1_RET(pResult);

   if( ECCP_IsPointAtInfinity(pP) )
      *pResult = ippECPointIsAtInfinite;
   else if( ECP_METHOD(pECC)->IsPointOnCurve(pP, pECC, ECP_BNCTX(pECC)) )
      *pResult = ippECValid;
   else
      *pResult = ippECPointIsNotValid;

   return ippStsNoErr;
}


/*F*
//    Name: ippsECCPComparePoint
//
// Purpose: Compare two EC points
//
// Returns:                Reason:
//    ippStsNullPtrErr        NULL == pECC
//                            NULL == pP
//                            NULL == pQ
//                            NULL == pResult
//
//    ippStsContextMatchErr   illegal pECC->idCtx
//                            illegal pP->idCtx
//                            illegal pQ->idCtx
//
//    ippStsNoErr             no errors
//
// Parameters:
//    pP          pointer to the EC Point context
//    pQ          pointer to the EC Point context
//    pECC        pointer to the ECCP context
//    pResult     pointer to the result:
//                         ippECPointIsEqual
//                         ippECPointIsNotEqual
//
*F*/
IPPFUN(IppStatus, ippsECCPComparePoint,(const IppsECCPPointState* pP,
                                        const IppsECCPPointState* pQ,
                                        IppECResult* pResult,
                                        IppsECCPState* pECC))
{
   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   /* test ID */
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   /* test points */
   IPP_BAD_PTR2_RET(pP,pQ);
   pP = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pP, ALIGN_VAL) );
   pQ = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pQ, ALIGN_VAL) );
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pP), ippStsContextMatchErr);
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pQ), ippStsContextMatchErr);

   /* test pResult */
   IPP_BAD_PTR1_RET(pResult);

   *pResult = ECP_METHOD(pECC)->ComparePoint(pP, pQ, pECC, ECP_BNCTX(pECC))? ippECPointIsNotEqual : ippECPointIsEqual;

   return ippStsNoErr;
}


/*F*
//    Name: ippsECCPNegativePoint
//
// Purpose: Perforn EC point operation: R = -P
//
// Returns:                Reason:
//    ippStsNullPtrErr        NULL == pECC
//                            NULL == pP
//                            NULL == pR
//
//    ippStsContextMatchErr   illegal pECC->idCtx
//                            illegal pP->idCtx
//                            illegal pR->idCtx
//
//    ippStsNoErr             no errors
//
// Parameters:
//    pP          pointer to the source EC Point context
//    pR          pointer to the resultant EC Point context
//    pECC        pointer to the ECCP context
//
*F*/
IPPFUN(IppStatus, ippsECCPNegativePoint, (const IppsECCPPointState* pP,
                                          IppsECCPPointState* pR,
                                          IppsECCPState* pECC))
{
   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   /* test ID */
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   /* test points */
   IPP_BAD_PTR2_RET(pP,pR);
   pP = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pP, ALIGN_VAL) );
   pR = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pR, ALIGN_VAL) );
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pP), ippStsContextMatchErr);
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pR), ippStsContextMatchErr);

   ECP_METHOD(pECC)->NegPoint(pP, pR, pECC);

   return ippStsNoErr;
}


/*F*
//    Name: ippsECCPAddPoint
//
// Purpose: Perforn EC point operation: R = P+Q
//
// Returns:                Reason:
//    ippStsNullPtrErr        NULL == pECC
//                            NULL == pP
//                            NULL == pQ
//                            NULL == pR
//
//    ippStsContextMatchErr   illegal pECC->idCtx
//                            illegal pP->idCtx
//                            illegal pQ->idCtx
//                            illegal pR->idCtx
//
//    ippStsNoErr             no errors
//
// Parameters:
//    pP          pointer to the source EC Point context
//    pQ          pointer to the source EC Point context
//    pR          pointer to the resultant EC Point context
//    pECC        pointer to the ECCP context
//
*F*/
IPPFUN(IppStatus, ippsECCPAddPoint,(const IppsECCPPointState* pP,
                                    const IppsECCPPointState* pQ,
                                    IppsECCPPointState* pR,
                                    IppsECCPState* pECC))
{
   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   /* test ID */
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   /* test points */
   IPP_BAD_PTR3_RET(pP,pQ,pR);
   pP = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pP, ALIGN_VAL) );
   pQ = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pQ, ALIGN_VAL) );
   pR = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pR, ALIGN_VAL) );
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pP), ippStsContextMatchErr);
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pQ), ippStsContextMatchErr);
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pR), ippStsContextMatchErr);

   if(pP==pQ)
      ECP_METHOD(pECC)->DblPoint(pP, pR, pECC, ECP_BNCTX(pECC));
   else
      ECP_METHOD(pECC)->AddPoint(pP, pQ, pR, pECC, ECP_BNCTX(pECC));

   return ippStsNoErr;
}


/*F*
//    Name: ippsECCPMulPointScalar
//
// Purpose: Perforn EC point operation: R = k*P
//
// Returns:                Reason:
//    ippStsNullPtrErr        NULL == pECC
//                            NULL == pP
//                            NULL == pK
//                            NULL == pR
//
//    ippStsContextMatchErr   illegal pECC->idCtx
//                            illegal pP->idCtx
//                            illegal pK->idCtx
//                            illegal pR->idCtx
//
//    ippStsNoErr             no errors
//
// Parameters:
//    pP          pointer to the source EC Point context
//    pK          pointer to the source BigNum multiplier context
//    pR          pointer to the resultant EC Point context
//    pECC        pointer to the ECCP context
//
*F*/
IPPFUN(IppStatus, ippsECCPMulPointScalar,(const IppsECCPPointState* pP,
                                          const IppsBigNumState* pK,
                                          IppsECCPPointState* pR,
                                          IppsECCPState* pECC))
{
   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   /* test ID */
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   /* test points */
   IPP_BAD_PTR2_RET(pP,pR);
   pP = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pP, ALIGN_VAL) );
   pR = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pR, ALIGN_VAL) );
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pP), ippStsContextMatchErr);
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pR), ippStsContextMatchErr);

   /* test scalar */
   IPP_BAD_PTR1_RET(pK);
   pK = (IppsBigNumState*)( IPP_ALIGNED_PTR(pK, ALIGN_VAL) );
   IPP_BADARG_RET(!BN_VALID_ID(pK), ippStsContextMatchErr);

   ECP_METHOD(pECC)->MulPoint(pP, pK, pR, pECC, ECP_BNCTX(pECC));

   return ippStsNoErr;
}
