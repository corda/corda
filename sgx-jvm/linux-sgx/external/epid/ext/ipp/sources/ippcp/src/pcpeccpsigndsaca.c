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
//     EC over Prime Finite Field (Sign, DSA version)
// 
//  Contents:
//     ippsECCPSignDSA()
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
//    Name: ippsECCPSignDSA
//
// Purpose: Signing of message representative.
//          (DSA version).
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pECC
//                               NULL == pMsgDigest
//                               NULL == pPrivate
//                               NULL == pSignX
//                               NULL == pSignY
//
//    ippStsContextMatchErr      illegal pECC->idCtx
//                               illegal pMsgDigest->idCtx
//                               illegal pPrivate->idCtx
//                               illegal pSignX->idCtx
//                               illegal pSignY->idCtx
//
//    ippStsMessageErr           MsgDigest >= order
//
//    ippStsRangeErr             not enough room for:
//                               signX
//                               signY
//
//    ippStsEphemeralKeyErr      (0==signX) || (0==signY)
//
//    ippStsNoErr                no errors
//
// Parameters:
//    pMsgDigest     pointer to the message representative to be signed
//    pPrivate       pointer to the regular private key
//    pSignX,pSignY  pointer to the signature
//    pECC           pointer to the ECCP context
//
// Note:
//    - ephemeral key pair extracted from pECC and
//      must be generated and before ippsECCPDSASign() usage
//    - ephemeral key pair destroy before exit
//
*F*/
IPPFUN(IppStatus, ippsECCPSignDSA,(const IppsBigNumState* pMsgDigest,
                                   const IppsBigNumState* pPrivate,
                                   IppsBigNumState* pSignX, IppsBigNumState* pSignY,
                                   IppsECCPState* pECC))
{
   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   /* test private key*/
   IPP_BAD_PTR1_RET(pPrivate);
   pPrivate = (IppsBigNumState*)( IPP_ALIGNED_PTR(pPrivate, ALIGN_VAL) );
   IPP_BADARG_RET(!BN_VALID_ID(pPrivate), ippStsContextMatchErr);

   /* test message representative */
   IPP_BAD_PTR1_RET(pMsgDigest);
   pMsgDigest = (IppsBigNumState*)( IPP_ALIGNED_PTR(pMsgDigest, ALIGN_VAL) );
   IPP_BADARG_RET(!BN_VALID_ID(pMsgDigest), ippStsContextMatchErr);
   IPP_BADARG_RET((0<=cpBN_cmp(pMsgDigest, ECP_ORDER(pECC))), ippStsMessageErr);

   /* test signature */
   IPP_BAD_PTR2_RET(pSignX,pSignY);
   pSignX = (IppsBigNumState*)( IPP_ALIGNED_PTR(pSignX, ALIGN_VAL) );
   pSignY = (IppsBigNumState*)( IPP_ALIGNED_PTR(pSignY, ALIGN_VAL) );
   IPP_BADARG_RET(!BN_VALID_ID(pSignX), ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pSignY), ippStsContextMatchErr);
   IPP_BADARG_RET((BN_ROOM(pSignX)*BITSIZE(BNU_CHUNK_T)<ECP_ORDBITS(pECC)), ippStsRangeErr);
   IPP_BADARG_RET((BN_ROOM(pSignY)*BITSIZE(BNU_CHUNK_T)<ECP_ORDBITS(pECC)), ippStsRangeErr);

   {
      IppsMontState* rMont = ECP_RMONT(pECC);
      IppsBigNumState* pOrder = ECP_ORDER(pECC);

      BigNumNode* pList = ECP_BNCTX(pECC);
      IppsBigNumState* pTmp = cpBigNumListGet(&pList);

      /* extract ephemeral public key (X component only) */
      ECP_METHOD(pECC)->GetPointAffine(pTmp, NULL, ECP_PUBLIC_E(pECC), pECC, pList);

      /*
      // compute
      // signX = eph_pub_x (mod order)
      */
      PMA_mod(pSignX, pTmp, pOrder);
      if( !IsZero_BN(pSignX) ) {

         IppsBigNumState* pEncMsg   = cpBigNumListGet(&pList);
         IppsBigNumState* pEncSignX = cpBigNumListGet(&pList);
         PMA_enc(pEncMsg,   (IppsBigNumState*)pMsgDigest, rMont);
         PMA_enc(pEncSignX, pSignX,     rMont);

         /*
         // compute
         // signY = (1/eph_private)*(pMsgDigest + private*signX) (mod order)
         */
         PMA_inv(pSignY, ECP_PRIVATE_E(pECC), pOrder);
         PMA_enc(ECP_PRIVATE_E(pECC), pPrivate, rMont);
         PMA_mule(pTmp, ECP_PRIVATE_E(pECC), pEncSignX, rMont);
         PMA_add(pTmp, pTmp, pEncMsg, pOrder);
         PMA_mule(pSignY, pSignY, pTmp, rMont);
         if( !IsZero_BN(pSignY) )
            return ippStsNoErr;
      }

      return ippStsEphemeralKeyErr;
   }
}
