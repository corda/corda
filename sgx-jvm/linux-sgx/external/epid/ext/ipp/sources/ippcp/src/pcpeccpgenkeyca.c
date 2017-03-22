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
//     ippsECCPGenKeyPair()
// 
// 
*/

#include "precomp.h"
#include "owncp.h"
#include "pcpeccppoint.h"
#include "pcpeccpmethod.h"
#include "pcpeccpmethodcom.h"


/*F*
//    Name: ippsECCPGenKeyPair
//
// Purpose: Generate (private,public) Key Pair
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
//    ippStsNoErr             no errors
//
// Parameters:
//    pPrivate    pointer to the resultant private key
//    pPublic     pointer to the resultant public  key
//    pECC        pointer to the ECCP context
//
*F*/
IPPFUN(IppStatus, ippsECCPGenKeyPair, (IppsBigNumState* pPrivate, IppsECCPPointState* pPublic,
                                       IppsECCPState* pECC,
                                       IppBitSupplier rndFunc, void* pRndParam))
{
   IPP_BAD_PTR2_RET(pECC, rndFunc);

   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   /* test ID */
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   /* test private/public keys */
   IPP_BAD_PTR2_RET(pPrivate,pPublic);
   pPrivate = (IppsBigNumState*)( IPP_ALIGNED_PTR(pPrivate, ALIGN_VAL) );
   pPublic = (IppsECCPPointState*)( IPP_ALIGNED_PTR(pPublic, ALIGN_VAL) );
   IPP_BADARG_RET(!BN_VALID_ID(pPrivate), ippStsContextMatchErr);
   IPP_BADARG_RET((BN_ROOM(pPrivate)*BITSIZE(BNU_CHUNK_T)<ECP_ORDBITS(pECC)), ippStsSizeErr);
   IPP_BADARG_RET(!ECP_POINT_VALID_ID(pPublic), ippStsContextMatchErr);

   {
      /*
      // generate random private key X:  0 < X < R
      */
      int reqBitLen = ECP_ORDBITS(pECC);

      IppsBigNumState* pOrder = ECP_ORDER(pECC);

      int xSize;
      Ipp32u* pX = (Ipp32u*)BN_NUMBER(pPrivate);
      Ipp32u xMask = MAKEMASK32(reqBitLen);

      BN_SIGN(pPrivate) = ippBigNumPOS;
      do {
         xSize = BITS2WORD32_SIZE(reqBitLen);
         rndFunc(pX, reqBitLen, pRndParam);
         pX[xSize-1] &= xMask;
         FIX_BNU(pX, xSize);
         BN_SIZE(pPrivate) = INTERNAL_BNU_LENGTH(xSize);
      } while( (0 == cpBN_tst(pPrivate)) ||
               (0 <= cpBN_cmp(pPrivate, pOrder)) );

      /* calculate public key */
      //ECP_METHOD(pECC)->MulPoint(ECP_GENC(pECC), pPrivate, pPublic, pECC, ECP_BNCTX(pECC));
      ECP_METHOD(pECC)->MulBasePoint(pPrivate, pPublic, pECC, ECP_BNCTX(pECC));

      return ippStsNoErr;
   }
}
