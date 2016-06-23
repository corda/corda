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
#include "pcpbnresource.h"
#include "pcpeccpmethod.h"
#include "pcpeccpmethodcom.h"
#include "pcppma.h"


/*F*
//    Name: ippsECCPSet
//
// Purpose: Set EC Domain Parameters.
//
// Returns:                Reason:
//    ippStsNullPtrErr        NULL == pPrime
//                            NULL == pA
//                            NULL == pB
//                            NULL == pGX
//                            NULL == pGY
//                            NULL == pOrder
//                            NULL == pECC
//
//    ippStsContextMatchErr   illegal pPrime->idCtx
//                            illegal pA->idCtx
//                            illegal pB->idCtx
//                            illegal pGX->idCtx
//                            illegal pGY->idCtx
//                            illegal pOrder->idCtx
//                            illegal pECC->idCtx
//
//    ippStsRangeErr          not enough room for:
//                            pPrime
//                            pA, pB,
//                            pGX,pGY
//                            pOrder
//
//    ippStsRangeErr          0>= cofactor
//
//    ippStsNoErr             no errors
//
// Parameters:
//    pPrime   pointer to the prime (specify FG(p))
//    pA       pointer to the A coefficient of EC equation
//    pB       pointer to the B coefficient of EC equation
//    pGX,pGY  pointer to the Base Point (x and y coordinates) of EC
//    pOrder   pointer to the Base Point order
//    cofactor cofactor value
//    pECC     pointer to the ECC context
//
*F*/
static
void ECCPSetDP(IppECCType flag,
               int primeSize, const Ipp32u* pPrime,
               int aSize,     const Ipp32u* pA,
               int bSize,     const Ipp32u* pB,
               int gxSize,    const Ipp32u* pGx,
               int gySize,    const Ipp32u* pGy,
               int orderSize, const Ipp32u* pOrder,
               Ipp32u cofactor,
               IppsECCPState* pECC)
{
   ECP_TYPE(pECC) = flag;

   /* reset size (bits) of field element */
   ECP_GFEBITS(pECC) = cpMSBit_BNU32(pPrime, primeSize) +1;
   /* reset size (bits) of Base Point order */
   ECP_ORDBITS(pECC) = cpMSBit_BNU32(pOrder, orderSize) +1;

   /* set up prime */
   ippsSet_BN(ippBigNumPOS, primeSize, pPrime, ECP_PRIME(pECC));
   /* set up A */
   ippsSet_BN(ippBigNumPOS, aSize, pA, ECP_A(pECC));
   /* test A */
   BN_Word(ECP_B(pECC), 3);
   PMA_add(ECP_B(pECC), ECP_A(pECC), ECP_B(pECC), ECP_PRIME(pECC));
   ECP_AMI3(pECC) = IsZero_BN(ECP_B(pECC));
   /* set up B */
   ippsSet_BN(ippBigNumPOS, bSize, pB, ECP_B(pECC));

   /* set up affine coordinates of Base Point and order */
   ippsSet_BN(ippBigNumPOS, gxSize, pGx, ECP_GX(pECC));
   ippsSet_BN(ippBigNumPOS, gySize, pGy, ECP_GY(pECC));
   ippsSet_BN(ippBigNumPOS, orderSize, pOrder, ECP_ORDER(pECC));

   /* set up cofactor */
   //ippsSet_BN(ippBigNumPOS, 1, &((Ipp32u)cofactor), ECP_COFACTOR(pECC));
   ippsSet_BN(ippBigNumPOS, 1, &cofactor, ECP_COFACTOR(pECC));

   /* montgomery engine (prime) */
   if( ippStsNoErr == ippsMontSet((Ipp32u*)BN_NUMBER(ECP_PRIME(pECC)), BN_SIZE32(ECP_PRIME(pECC)), ECP_PMONT(pECC)) ) {
      /* modulo reduction and montgomery form of A and B */
      PMA_mod(ECP_AENC(pECC), ECP_A(pECC),    ECP_PRIME(pECC));
      PMA_enc(ECP_AENC(pECC), ECP_AENC(pECC), ECP_PMONT(pECC));
      PMA_mod(ECP_BENC(pECC), ECP_B(pECC),    ECP_PRIME(pECC));
      PMA_enc(ECP_BENC(pECC), ECP_BENC(pECC), ECP_PMONT(pECC));
      /* projective coordinates and montgomery form of of Base Point */
      if( ( IsZero_BN(ECP_BENC(pECC)) && ECCP_IsPointAtAffineInfinity1(ECP_GX(pECC), ECP_GY(pECC))) ||
          (!IsZero_BN(ECP_BENC(pECC)) && ECCP_IsPointAtAffineInfinity0(ECP_GX(pECC), ECP_GY(pECC))) )
         ECCP_SetPointToInfinity(ECP_GENC(pECC));
      else {
         ECP_METHOD(pECC)->SetPointProjective(ECP_GX(pECC), ECP_GY(pECC), BN_ONE_REF(), ECP_GENC(pECC), pECC);
      }
   }

   /* montgomery engine (order) */
   if( ippStsNoErr == ippsMontSet((Ipp32u*)BN_NUMBER(ECP_ORDER(pECC)), BN_SIZE32(ECP_ORDER(pECC)), ECP_RMONT(pECC)) )
      PMA_enc(ECP_COFACTOR(pECC), ECP_COFACTOR(pECC), ECP_RMONT(pECC));

   /* set zero private keys */
   BN_Word(ECP_PRIVATE(pECC), 0);
   BN_Word(ECP_PRIVATE_E(pECC), 0);

   /* set infinity public keys */
   ECCP_SetPointToInfinity(ECP_PUBLIC(pECC));
   ECCP_SetPointToInfinity(ECP_PUBLIC_E(pECC));
}


IPPFUN(IppStatus, ippsECCPSet, (const IppsBigNumState* pPrime,
                                const IppsBigNumState* pA, const IppsBigNumState* pB,
                                const IppsBigNumState* pGX,const IppsBigNumState* pGY,const IppsBigNumState* pOrder,
                                int cofactor,
                                IppsECCPState* pECC))
{
   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   /* test ID */
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   /* test pPrime */
   IPP_BAD_PTR1_RET(pPrime);
   pPrime = (IppsBigNumState*)( IPP_ALIGNED_PTR(pPrime, ALIGN_VAL) );
   IPP_BADARG_RET(!BN_VALID_ID(pPrime), ippStsContextMatchErr);
   IPP_BADARG_RET((cpBN_bitsize(pPrime)>ECP_GFEBITS(pECC)), ippStsRangeErr);

   /* test pA and pB */
   IPP_BAD_PTR2_RET(pA,pB);
   pA = (IppsBigNumState*)( IPP_ALIGNED_PTR(pA, ALIGN_VAL) );
   pB = (IppsBigNumState*)( IPP_ALIGNED_PTR(pB, ALIGN_VAL) );
   IPP_BADARG_RET(!BN_VALID_ID(pA), ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pB), ippStsContextMatchErr);
   IPP_BADARG_RET((cpBN_bitsize(pA)>ECP_GFEBITS(pECC)), ippStsRangeErr);
   IPP_BADARG_RET((cpBN_bitsize(pB)>ECP_GFEBITS(pECC)), ippStsRangeErr);

   /* test pG and pGorder pointers */
   IPP_BAD_PTR3_RET(pGX,pGY, pOrder);
   pGX    = (IppsBigNumState*)( IPP_ALIGNED_PTR(pGX,    ALIGN_VAL) );
   pGY    = (IppsBigNumState*)( IPP_ALIGNED_PTR(pGY,    ALIGN_VAL) );
   pOrder = (IppsBigNumState*)( IPP_ALIGNED_PTR(pOrder, ALIGN_VAL) );
   IPP_BADARG_RET(!BN_VALID_ID(pGX),    ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pGY),    ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pOrder), ippStsContextMatchErr);
   IPP_BADARG_RET((cpBN_bitsize(pGX)>ECP_GFEBITS(pECC)),    ippStsRangeErr);
   IPP_BADARG_RET((cpBN_bitsize(pGY)>ECP_GFEBITS(pECC)),    ippStsRangeErr);
   IPP_BADARG_RET((cpBN_bitsize(pOrder)>ECP_ORDBITS(pECC)), ippStsRangeErr);

   /* test cofactor */
   IPP_BADARG_RET(!(0<cofactor), ippStsRangeErr);

   /* set general methods */
   *(ECP_METHOD(pECC)) = *(ECCPcom_Methods());

   /* set domain parameters */
   ECCPSetDP(IppECCArbitrary,
             BN_SIZE32(pPrime), (Ipp32u*)BN_NUMBER(pPrime),
             BN_SIZE32(pA),     (Ipp32u*)BN_NUMBER(pA),
             BN_SIZE32(pB),     (Ipp32u*)BN_NUMBER(pB),
             BN_SIZE32(pGX),    (Ipp32u*)BN_NUMBER(pGX),
             BN_SIZE32(pGY),    (Ipp32u*)BN_NUMBER(pGY),
             BN_SIZE32(pOrder), (Ipp32u*)BN_NUMBER(pOrder),
             cofactor,
             pECC);

   return ippStsNoErr;
}


/*F*
//    Name: ippsECCPSetStd
//
// Purpose: Set Standard ECC Domain Parameter.
//
// Returns:                Reason:
//    ippStsNullPtrErr        NULL == pECC
//
//    ippStsContextMatchErr   illegal pECC->idCtx
//
//    ippStsECCInvalidFlagErr invalid flag
//
//    ippStsNoErr             no errors
//
// Parameters:
//    flag     specify standard ECC parameter(s) to be setup
//    pECC     pointer to the ECC context
//
*F*/
IPPFUN(IppStatus, ippsECCPSetStd, (IppECCType flag, IppsECCPState* pECC))
{
   /* test pECC */
   IPP_BAD_PTR1_RET(pECC);
   /* use aligned EC context */
   pECC = (IppsECCPState*)( IPP_ALIGNED_PTR(pECC, ALIGN_VAL) );
   /* test ID */
   IPP_BADARG_RET(!ECP_VALID_ID(pECC), ippStsContextMatchErr);

   *(ECP_METHOD(pECC)) = *(ECCPcom_Methods());

   switch(flag) {
      case IppECCPStd112r1:
         ECCPSetDP(IppECCPStd112r1,
            BITS2WORD32_SIZE(112), secp112r1_p,
            BITS2WORD32_SIZE(112), secp112r1_a,
            BITS2WORD32_SIZE(112), secp112r1_b,
            BITS2WORD32_SIZE(112), secp112r1_gx,
            BITS2WORD32_SIZE(112), secp112r1_gy,
            BITS2WORD32_SIZE(112), secp112r1_r,
            secp112r1_h, pECC);
         break;

      case IppECCPStd112r2:
         ECCPSetDP(IppECCPStd112r2,
            BITS2WORD32_SIZE(112), secp112r2_p,
            BITS2WORD32_SIZE(112), secp112r2_a,
            BITS2WORD32_SIZE(112), secp112r2_b,
            BITS2WORD32_SIZE(112), secp112r2_gx,
            BITS2WORD32_SIZE(112), secp112r2_gy,
            BITS2WORD32_SIZE(112), secp112r2_r,
            secp112r2_h, pECC);
         break;

      case IppECCPStd128r1:
         ECCPSetDP(IppECCPStd128r1,
            BITS2WORD32_SIZE(128), secp128r1_p,
            BITS2WORD32_SIZE(128), secp128r1_a,
            BITS2WORD32_SIZE(128), secp128r1_b,
            BITS2WORD32_SIZE(128), secp128r1_gx,
            BITS2WORD32_SIZE(128), secp128r1_gy,
            BITS2WORD32_SIZE(128), secp128r1_r,
            secp128r1_h, pECC);
         break;

      case IppECCPStd128r2:
         ECCPSetDP(IppECCPStd128r2,
            BITS2WORD32_SIZE(128), secp128r2_p,
            BITS2WORD32_SIZE(128), secp128r2_a,
            BITS2WORD32_SIZE(128), secp128r2_b,
            BITS2WORD32_SIZE(128), secp128r2_gx,
            BITS2WORD32_SIZE(128), secp128r2_gy,
            BITS2WORD32_SIZE(128), secp128r2_r,
            secp128r2_h, pECC);
         break;

      case IppECCPStd160r1:
         ECCPSetDP(IppECCPStd160r1,
            BITS2WORD32_SIZE(160), secp160r1_p,
            BITS2WORD32_SIZE(160), secp160r1_a,
            BITS2WORD32_SIZE(160), secp160r1_b,
            BITS2WORD32_SIZE(160), secp160r1_gx,
            BITS2WORD32_SIZE(160), secp160r1_gy,
            BITS2WORD32_SIZE(161), secp160r1_r,
            secp160r1_h, pECC);
         break;

      case IppECCPStd160r2:
         ECCPSetDP(IppECCPStd160r2,
            BITS2WORD32_SIZE(160), secp160r2_p,
            BITS2WORD32_SIZE(160), secp160r2_a,
            BITS2WORD32_SIZE(160), secp160r2_b,
            BITS2WORD32_SIZE(160), secp160r2_gx,
            BITS2WORD32_SIZE(160), secp160r2_gy,
            BITS2WORD32_SIZE(161), secp160r2_r,
            secp160r2_h, pECC);
         break;

      case IppECCPStd192r1:
         ECCPSetDP(IppECCPStd192r1,
            BITS2WORD32_SIZE(192), secp192r1_p,
            BITS2WORD32_SIZE(192), secp192r1_a,
            BITS2WORD32_SIZE(192), secp192r1_b,
            BITS2WORD32_SIZE(192), secp192r1_gx,
            BITS2WORD32_SIZE(192), secp192r1_gy,
            BITS2WORD32_SIZE(192), secp192r1_r,
            secp192r1_h, pECC);
         break;

      case IppECCPStd224r1:
         ECCPSetDP(IppECCPStd224r1,
            BITS2WORD32_SIZE(224), secp224r1_p,
            BITS2WORD32_SIZE(224), secp224r1_a,
            BITS2WORD32_SIZE(224), secp224r1_b,
            BITS2WORD32_SIZE(224), secp224r1_gx,
            BITS2WORD32_SIZE(224), secp224r1_gy,
            BITS2WORD32_SIZE(224), secp224r1_r,
            secp224r1_h, pECC);
         break;

      case IppECCPStd256r1:
         ECCPSetDP(IppECCPStd256r1,
            BITS2WORD32_SIZE(256), secp256r1_p,
            BITS2WORD32_SIZE(256), secp256r1_a,
            BITS2WORD32_SIZE(256), secp256r1_b,
            BITS2WORD32_SIZE(256), secp256r1_gx,
            BITS2WORD32_SIZE(256), secp256r1_gy,
            BITS2WORD32_SIZE(256), secp256r1_r,
            secp256r1_h, pECC);
         break;

      case IppECCPStd384r1:
         ECCPSetDP(IppECCPStd384r1,
            BITS2WORD32_SIZE(384), secp384r1_p,
            BITS2WORD32_SIZE(384), secp384r1_a,
            BITS2WORD32_SIZE(384), secp384r1_b,
            BITS2WORD32_SIZE(384), secp384r1_gx,
            BITS2WORD32_SIZE(384), secp384r1_gy,
            BITS2WORD32_SIZE(384), secp384r1_r,
            secp384r1_h, pECC);
         break;

      case IppECCPStd521r1:
         ECCPSetDP(IppECCPStd521r1,
            BITS2WORD32_SIZE(521), secp521r1_p,
            BITS2WORD32_SIZE(521), secp521r1_a,
            BITS2WORD32_SIZE(521), secp521r1_b,
            BITS2WORD32_SIZE(521), secp521r1_gx,
            BITS2WORD32_SIZE(521), secp521r1_gy,
            BITS2WORD32_SIZE(521), secp521r1_r,
            secp521r1_h, pECC);
         break;

      default:
         return ippStsECCInvalidFlagErr;
   }

   return ippStsNoErr;
}
