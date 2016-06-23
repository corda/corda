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
