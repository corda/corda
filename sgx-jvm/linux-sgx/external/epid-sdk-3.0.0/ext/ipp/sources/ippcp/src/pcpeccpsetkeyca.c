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
//     EC over Prime Finite Field (EC Key Generation)
// 
//  Contents:
//     ippsECCPSetKeyPair()
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
//    Name: ippsECCPSetKeyPair
//
// Purpose: Generate (private,public) Key Pair
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pECC
//                               NULL == pPrivate
//                               NULL == pPublic
//
//    ippStsContextMatchErr      illegal pECC->idCtx
//                               illegal pPrivate->idCtx
//                               illegal pPublic->idCtx
//
//    ippStsNoErr                no errors
//
// Parameters:
//    pPrivate    pointer to the private key
//    pPublic     pointer to the public  key
//    regular     flag regular/ephemeral keys
//    pECC        pointer to the ECCP context
//
*F*/
IPPFUN(IppStatus, ippsECCPSetKeyPair, (const IppsBigNumState* pPrivate, const IppsECCPPointState* pPublic,
                                       IppBool regular,
                                       IppsECCPState* pECC))
{
   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   /* test ID */
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   {
      IppsBigNumState*  targetPrivate;
      IppsECCPPointState* targetPublic;

      if( regular ) {
         targetPrivate = ECP_PRIVATE(pECC);
         targetPublic  = ECP_PUBLIC(pECC);
      }
      else {
         targetPrivate = ECP_PRIVATE_E(pECC);
         targetPublic  = ECP_PUBLIC_E(pECC);
      }

      /* set up private key request */
      if( pPrivate ) {
         pPrivate = (IppsBigNumState*)( IPP_ALIGNED_PTR(pPrivate, ALIGN_VAL) );
         IPP_BADARG_RET(!BN_VALID_ID(pPrivate), ippStsContextMatchErr);
         ippsSet_BN(ippBigNumPOS, BN_SIZE32(pPrivate), (Ipp32u*)BN_NUMBER(pPrivate), targetPrivate);
      }

      /* set up public  key request */
      if( pPublic ) {
         pPublic = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pPublic, ALIGN_VAL) );
         IPP_BADARG_RET(!ECP_POINT_VALID_ID(pPublic), ippStsContextMatchErr);

         ECP_METHOD(pECC)->GetPointAffine(ECP_POINT_X(targetPublic), ECP_POINT_Y(targetPublic), pPublic, pECC, ECP_BNCTX(pECC));
         ECP_METHOD(pECC)->SetPointAffine(ECP_POINT_X(targetPublic), ECP_POINT_Y(targetPublic), targetPublic, pECC);
      }

      return ippStsNoErr;
   }
}
