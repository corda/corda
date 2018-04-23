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
//     EC over Prime Finite Field (Verify Signature, DSA version)
// 
//  Contents:
//     ippsECCPVerifyDSA()
// 
// 
*/

#include "precomp.h"
#include "owncp.h"
#include "pcpeccp.h"
#include "pcpeccppoint.h"
#include "pcpeccpmethod.h"
#include "pcpeccpmethodcom.h"


/*F*
//    Name: ippsECCPVerifyDSA
//
// Purpose: Verify Signature (DSA version).
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pECC
//                               NULL == pMsgDigest
//                               NULL == pSignX
//                               NULL == pSignY
//                               NULL == pResult
//
//    ippStsContextMatchErr      illegal pECC->idCtx
//                               illegal pMsgDigest->idCtx
//                               illegal pSignX->idCtx
//                               illegal pSignY->idCtx
//
//    ippStsMessageErr           MsgDigest >= order
//
//    ippStsNoErr                no errors
//
// Parameters:
//    pMsgDigest     pointer to the message representative to be signed
//    pSignX,pSignY  pointer to the signature
//    pResult        pointer to the result: ippECValid/ippECInvalidSignature
//    pECC           pointer to the ECCP context
//
// Note:
//    - signer's key must be set up in ECCP context
//      before ippsECCPVerifyDSA() usage
//
*F*/
IPPFUN(IppStatus, ippsECCPVerifyDSA,(const IppsBigNumState* pMsgDigest,
                                     const IppsBigNumState* pSignX, const IppsBigNumState* pSignY,
                                     IppECResult* pResult,
                                     IppsECCPState* pECC))
{
   IppsMontState* rMont;

   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   /* test message representative */
   IPP_BAD_PTR1_RET(pMsgDigest);
   pMsgDigest = (IppsBigNumState*)( IPP_ALIGNED_PTR(pMsgDigest, ALIGN_VAL) );
   IPP_BADARG_RET(!BN_VALID_ID(pMsgDigest), ippStsContextMatchErr);
   rMont = ECP_RMONT(pECC);
   IPP_BADARG_RET((0<=cpBN_cmp(pMsgDigest, ECP_ORDER(pECC))), ippStsMessageErr);

   /* test result */
   IPP_BAD_PTR1_RET(pResult);

   /* test signature */
   IPP_BAD_PTR2_RET(pSignX,pSignY);
   pSignX = (IppsBigNumState*)( IPP_ALIGNED_PTR(pSignX, ALIGN_VAL) );
   pSignY = (IppsBigNumState*)( IPP_ALIGNED_PTR(pSignY, ALIGN_VAL) );
   IPP_BADARG_RET(!BN_VALID_ID(pSignX), ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pSignY), ippStsContextMatchErr);

   /* test signature value */
   if( (0>cpBN_tst(pSignX)) || (0>cpBN_tst(pSignY)) ||
       (0<=cpBN_cmp(pSignX, ECP_ORDER(pECC))) ||
       (0<=cpBN_cmp(pSignY, ECP_ORDER(pECC))) ) {
      *pResult = ippECInvalidSignature;
      return ippStsNoErr;
   }

   /* validate signature */
   else {
      IppsECCPPointState P1;

      BigNumNode* pList = ECP_BNCTX(pECC);
      IppsBigNumState* pH1 = cpBigNumListGet(&pList);
      IppsBigNumState* pH2 = cpBigNumListGet(&pList);
      IppsBigNumState* pOrder = cpBigNumListGet(&pList);
      BN_Set(MNT_MODULUS(rMont), MNT_SIZE(rMont), pOrder);

      ECP_POINT_X(&P1) = cpBigNumListGet(&pList);
      ECP_POINT_Y(&P1) = cpBigNumListGet(&pList);
      ECP_POINT_Z(&P1) = cpBigNumListGet(&pList);

      PMA_inv(pH1, (IppsBigNumState*)pSignY, pOrder);/* h = 1/signY (mod order) */
      PMA_enc(pH1, pH1, rMont);
      PMA_mule(pH2, (IppsBigNumState*)pSignX,     pH1, rMont);   /* h2 = pSignX     * h (mod order) */
      PMA_mule(pH1, (IppsBigNumState*)pMsgDigest, pH1, rMont);   /* h1 = pMsgDigest * h (mod order) */
#if 0
      /* compute h1*BasePoint + h2*publicKey */
      if(ippEC_TPM_SM2_P256 == ECP_TYPE(pECC)) {
         IppsECCPPointState P0;
         ECP_POINT_X(&P0) = cpBigNumListGet(&pList);
         ECP_POINT_Y(&P0) = cpBigNumListGet(&pList);
         ECP_POINT_Z(&P0) = cpBigNumListGet(&pList);
         ECP_METHOD(pECC)->MulBasePoint(pH1, &P0, pECC, pList);
         ECP_METHOD(pECC)->MulPoint(ECP_PUBLIC(pECC), pH2, &P1, pECC, pList);
         ECP_METHOD(pECC)->AddPoint(&P1, &P0, &P1, pECC, pList);
      }
      else
         ECP_METHOD(pECC)->ProdPoint(ECP_GENC(pECC),   pH1,
                                  ECP_PUBLIC(pECC), pH2,
                                  &P1, pECC, pList);
#endif
      /* compute h1*BasePoint + h2*publicKey */
      if((IppECCPStd128r1 == ECP_TYPE(pECC)) || (IppECCPStd128r2 == ECP_TYPE(pECC))
       ||(IppECCPStd192r1 == ECP_TYPE(pECC))
       ||(IppECCPStd224r1 == ECP_TYPE(pECC))
       ||(IppECCPStd256r1 == ECP_TYPE(pECC))
       ||(IppECCPStd384r1 == ECP_TYPE(pECC))
       ||(IppECCPStd521r1 == ECP_TYPE(pECC))
       ||(IppECCPStdSM2   == ECP_TYPE(pECC))) {
         IppsECCPPointState P0;
         ECP_POINT_X(&P0) = cpBigNumListGet(&pList);
         ECP_POINT_Y(&P0) = cpBigNumListGet(&pList);
         ECP_POINT_Z(&P0) = cpBigNumListGet(&pList);
         ECP_METHOD(pECC)->MulBasePoint(pH1, &P0, pECC, pList);
         ECP_METHOD(pECC)->MulPoint(ECP_PUBLIC(pECC), pH2, &P1, pECC, pList);
         ECP_METHOD(pECC)->AddPoint(&P1, &P0, &P1, pECC, pList);
      }
      else
         ECP_METHOD(pECC)->ProdPoint(ECP_GENC(pECC),   pH1,
                                  ECP_PUBLIC(pECC), pH2,
                                  &P1, pECC, pList);

      if( ECCP_IsPointAtInfinity(&P1) ) {
         *pResult = ippECInvalidSignature;
         return ippStsNoErr;
      }
      /* extract X component */
      ECP_METHOD(pECC)->GetPointAffine(pH1, NULL, &P1, pECC, pList);
      /* compare with signX */
      PMA_mod(pH1, pH1, pOrder);
      *pResult = (0==cpBN_cmp(pH1, pSignX))? ippECValid : ippECInvalidSignature;
      return ippStsNoErr;
   }
}
