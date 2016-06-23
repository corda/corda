/*
* Copyright (C) 2016 Intel Corporation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions
* are met:
*
*   * Redistributions of source code must retain the above copyright
*     notice, this list of conditions and the following disclaimer.
*   * Redistributions in binary form must reproduce the above copyright
*     notice, this list of conditions and the following disclaimer in
*     the documentation and/or other materials provided with the
*     distribution.
*   * Neither the name of Intel Corporation nor the names of its
*     contributors may be used to endorse or promote products derived
*     from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
* "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
* LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
* A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
* OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
* SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
* DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
* THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
*/

#include "owndefs.h"
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

      /* compute h1*BasePoint + h2*publicKey */
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
