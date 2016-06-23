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
//    Name: ippsECCPSharedSecretDH
//
// Purpose: Shared Secret Value Derivation
//          (Diffie-Hellman version).
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pECC
//                               NULL == pPrivateA
//                               NULL == pPublicB
//                               NULL == pShare
//
//    ippStsContextMatchErr      illegal pECC->idCtx
//                               illegal pPrivateA->idCtx
//                               illegal pPublicB->idCtx
//                               illegal pShare->idCtx
//
//    ippStsRangeErr             not enough room for share key
//
//    ippStsShareKeyErr          (infinity) => z
//
//    ippStsNoErr                no errors
//
// Parameters:
//    pPrivateA   pointer to own   private key
//    pPublicB    pointer to alien public  key
//    pShare      pointer to the shareds secret value
//    pECC        pointer to the ECCP context
//
*F*/
IPPFUN(IppStatus, ippsECCPSharedSecretDH,(const IppsBigNumState* pPrivateA,
                                          const IppsECCPPointState* pPublicB,
                                          IppsBigNumState* pShare,
                                          IppsECCPState* pECC))
{
   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   /* test ID */
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   /* test private (own) key */
   IPP_BAD_PTR1_RET(pPrivateA);
   pPrivateA = (IppsBigNumState*)( IPP_ALIGNED_PTR(pPrivateA, ALIGN_VAL) );
   IPP_BADARG_RET(!BN_VALID_ID(pPrivateA), ippStsContextMatchErr);

   /* test public (other party) key */
   IPP_BAD_PTR1_RET(pPublicB);
   pPublicB = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pPublicB, ALIGN_VAL) );
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pPublicB), ippStsContextMatchErr);

   /* test share secret value */
   IPP_BAD_PTR1_RET(pShare);
   pShare = (IppsBigNumState*)( IPP_ALIGNED_PTR(pShare, ALIGN_VAL) );
   IPP_BADARG_RET(!BN_VALID_ID(pShare), ippStsContextMatchErr);
   IPP_BADARG_RET((BN_ROOM(pShare)*BITSIZE(BNU_CHUNK_T)<ECP_GFEBITS(pECC)), ippStsRangeErr);

   {
      BigNumNode* pList = ECP_BNCTX(pECC);
      IppsECCPPointState Tmp;
      ECP_POINT_X(&Tmp) = cpBigNumListGet(&pList);
      ECP_POINT_Y(&Tmp) = cpBigNumListGet(&pList);
      ECP_POINT_Z(&Tmp) = cpBigNumListGet(&pList);

      /* Tmp = (own)_private * (alien)_public */
      ECP_METHOD(pECC)->MulPoint(pPublicB, pPrivateA, &Tmp, pECC, pList);

      /* test: Tmp ~ point at Infinity */
      if( ECCP_IsPointAtInfinity(&Tmp) )
         return ippStsShareKeyErr;
      else {
         ECP_METHOD(pECC)->GetPointAffine(pShare, NULL, &Tmp, pECC, pList);
         return ippStsNoErr;
      }
   }
}
