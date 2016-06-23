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
      ECP_METHOD(pECC)->MulBasePoint(pPrivate, pPublic, pECC, ECP_BNCTX(pECC));

      return ippStsNoErr;
   }
}
