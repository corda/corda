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
//     ippsECCPPublicKey()
// 
// 
*/

#include "precomp.h"
#include "owncp.h"
#include "pcpeccppoint.h"
#include "pcpeccpmethod.h"
#include "pcpeccpmethodcom.h"


/*F*
//    Name: ippsECCPPublicKey
//
// Purpose: Calculate Public Key
//
// Returns:                Reason:
//    ippStsNullPtrErr        NULL == pECC
//                            NULL == pPrivate
//                            NULL == pPublic
//
//    ippStsContextMatchErr   illegal pECC->idCtx
//                            illegal pPrivate->idCtx
//                            illegal pPublic->idCtx
//
//    ippStsIvalidPrivateKey  !(0 < pPrivate < order)
//
//    ippStsNoErr             no errors
//
// Parameters:
//    pPrivate    pointer to the private key
//    pPublic     pointer to the resultant public key
//    pECC        pointer to the ECCP context
//
*F*/
IPPFUN(IppStatus, ippsECCPPublicKey, (const IppsBigNumState* pPrivate,
                                      IppsECCPPointState* pPublic,
                                      IppsECCPState* pECC))
{
   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   /* test ID */
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   /* test public key */
   IPP_BAD_PTR1_RET(pPublic);
   pPublic = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pPublic, ALIGN_VAL) );
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pPublic), ippStsContextMatchErr);

   /* test private keys */
   IPP_BAD_PTR1_RET(pPrivate);
   pPrivate = (IppsBigNumState*)( IPP_ALIGNED_PTR(pPrivate, ALIGN_VAL) );
   IPP_BADARG_RET(!BN_VALID_ID(pPrivate), ippStsContextMatchErr);
   IPP_BADARG_RET(!((0<cpBN_tst(pPrivate)) && (0>cpBN_cmp(pPrivate, ECP_ORDER(pECC))) ), ippStsIvalidPrivateKey);

   /* calculates public key */
   //ECP_METHOD(pECC)->MulPoint(ECP_GENC(pECC), pPrivate, pPublic, pECC, ECP_BNCTX(pECC));
   ECP_METHOD(pECC)->MulBasePoint(pPrivate, pPublic, pECC, ECP_BNCTX(pECC));

   return ippStsNoErr;
}
