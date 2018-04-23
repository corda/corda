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
//     PRNG Functions
// 
//  Contents:
//     ippsPRNGSetModulus()
//     ippsPRNGSetSeed()
//     ippsPRNGSetAugment()
//     ippsPRNGSetH0()
// 
// 
*/

#include "precomp.h"

#include "owncp.h"
#include "pcpbn.h"
#include "pcpprng.h"


/*F*
// Name: ippsPRNGSetModulus
//
// Purpose: Sets 160-bit modulus Q.
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pRnd
//                               NULL == pMod
//
//    ippStsContextMatchErr      illegal pRnd->idCtx
//                               illegal pMod->idCtx
//
//    ippStsBadArgErr            160 != bitsize(pMOd)
//
//    ippStsNoErr                no error
//
// Parameters:
//    pMod     pointer to the 160-bit modulus
//    pRnd     pointer to the context
*F*/
IPPFUN(IppStatus, ippsPRNGSetModulus, (const IppsBigNumState* pMod, IppsPRNGState* pRnd))
{
   /* test PRNG context */
   IPP_BAD_PTR1_RET(pRnd);
   pRnd = (IppsPRNGState*)( IPP_ALIGNED_PTR(pRnd, PRNG_ALIGNMENT) );
   IPP_BADARG_RET(!RAND_VALID_ID(pRnd), ippStsContextMatchErr);

   /* test modulus */
   IPP_BAD_PTR1_RET(pMod);
   pMod = (IppsBigNumState*)( IPP_ALIGNED_PTR(pMod, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pMod), ippStsContextMatchErr);
   IPP_BADARG_RET(160 != BITSIZE_BNU(BN_NUMBER(pMod),BN_SIZE(pMod)), ippStsBadArgErr);

   ZEXPAND_COPY_BNU(RAND_Q(pRnd), (int)(sizeof(RAND_Q(pRnd))/sizeof(BNU_CHUNK_T)), BN_NUMBER(pMod),  BN_SIZE(pMod));
   return ippStsNoErr;
}


/*F*
// Name: ippsPRNGSetH0
//
// Purpose: Sets 160-bit parameter of G() function.
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pRnd
//                               NULL == pH0
//
//    ippStsContextMatchErr      illegal pRnd->idCtx
//                               illegal pH0->idCtx
//
//    ippStsNoErr                no error
//
// Parameters:
//    pH0      pointer to the parameter used into G() function
//    pRnd     pointer to the context
*F*/
IPPFUN(IppStatus, ippsPRNGSetH0,(const IppsBigNumState* pH0, IppsPRNGState* pRnd))
{
   /* test PRNG context */
   IPP_BAD_PTR1_RET(pRnd);
   pRnd = (IppsPRNGState*)( IPP_ALIGNED_PTR(pRnd, PRNG_ALIGNMENT) );
   IPP_BADARG_RET(!RAND_VALID_ID(pRnd), ippStsContextMatchErr);

   /* test H0 */
   IPP_BAD_PTR1_RET(pH0);
   pH0 = (IppsBigNumState*)( IPP_ALIGNED_PTR(pH0, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pH0), ippStsContextMatchErr);

   {
      cpSize len = IPP_MIN(5, BN_SIZE(pH0)*(sizeof(BNU_CHUNK_T)/sizeof(Ipp32u)));
      ZEXPAND_BNU(RAND_T(pRnd), 0, (int)(sizeof(RAND_T(pRnd))/sizeof(BNU_CHUNK_T)));
      ZEXPAND_COPY_BNU((Ipp32u*)RAND_T(pRnd), (int)(sizeof(RAND_T(pRnd))/sizeof(Ipp32u)),
                       (Ipp32u*)BN_NUMBER(pH0), len);
      return ippStsNoErr;
   }
}


/*F*
// Name: ippsPRNGSetSeed
//
// Purpose: Sets the initial state with the SEED value
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pRnd
//                               NULL == pSeed
//
//    ippStsContextMatchErr      illegal pRnd->idCtx
//                               illegal pSeed->idCtx
//
//    ippStsNoErr                no error
//
// Parameters:
//    pSeed    pointer to the SEED
//    pRnd     pointer to the context
*F*/
IPPFUN(IppStatus, ippsPRNGSetSeed, (const IppsBigNumState* pSeed, IppsPRNGState* pRnd))
{
   /* test PRNG context */
   IPP_BAD_PTR1_RET(pRnd);
   pRnd = (IppsPRNGState*)( IPP_ALIGNED_PTR(pRnd, PRNG_ALIGNMENT) );
   IPP_BADARG_RET(!RAND_VALID_ID(pRnd), ippStsContextMatchErr);

   /* test seed */
   IPP_BAD_PTR1_RET(pSeed);
   pSeed = (IppsBigNumState*)( IPP_ALIGNED_PTR(pSeed, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pSeed), ippStsContextMatchErr);

   {
      cpSize argSize = BITS_BNU_CHUNK( RAND_SEEDBITS(pRnd) );
      BNU_CHUNK_T mask = MASK_BNU_CHUNK(RAND_SEEDBITS(pRnd));
      cpSize size = IPP_MIN(BN_SIZE(pSeed), argSize);

      ZEXPAND_COPY_BNU(RAND_XKEY(pRnd), (cpSize)(sizeof(RAND_XKEY(pRnd))/sizeof(BNU_CHUNK_T)), BN_NUMBER(pSeed), size);
      RAND_XKEY(pRnd)[argSize-1] &= mask;

      return ippStsNoErr;
   }
}


/*F*
// Name: ippsPRNGSetAugment
//
// Purpose: Sets the Entropy Augmentation
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pRnd
//                               NULL == pAug
//
//    ippStsContextMatchErr      illegal pRnd->idCtx
//                               illegal pAug->idCtx
//
//    ippStsLengthErr            nBits < 1
//                               nBits > MAX_XKEY_SIZE
//    ippStsNoErr                no error
//
// Parameters:
//    pAug  pointer to the entropy eugmentation
//    pRnd  pointer to the context
*F*/
IPPFUN(IppStatus, ippsPRNGSetAugment, (const IppsBigNumState* pAug, IppsPRNGState* pRnd))
{
   /* test PRNG context */
   IPP_BAD_PTR1_RET(pRnd);
   pRnd = (IppsPRNGState*)( IPP_ALIGNED_PTR(pRnd, PRNG_ALIGNMENT) );
   IPP_BADARG_RET(!RAND_VALID_ID(pRnd), ippStsContextMatchErr);

   /* test augmentation */
   IPP_BAD_PTR1_RET(pAug);
   pAug = (IppsBigNumState*)( IPP_ALIGNED_PTR(pAug, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pAug), ippStsContextMatchErr);

   {
      cpSize argSize = BITS_BNU_CHUNK( RAND_SEEDBITS(pRnd) );
      BNU_CHUNK_T mask = MASK_BNU_CHUNK(RAND_SEEDBITS(pRnd));
      cpSize size = IPP_MIN(BN_SIZE(pAug), argSize);

      ZEXPAND_COPY_BNU(RAND_XAUGMENT(pRnd), (cpSize)(sizeof(RAND_XAUGMENT(pRnd))/sizeof(BNU_CHUNK_T)), BN_NUMBER(pAug), size);
      RAND_XAUGMENT(pRnd)[argSize-1] &= mask;

      return ippStsNoErr;
   }
}

/*F*
// Name: ippsPRNGGetSeed
//
// Purpose: Get current SEED value from the state
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pRnd
//                               NULL == pSeed
//
//    ippStsContextMatchErr      illegal pRnd->idCtx
//                               illegal pSeed->idCtx
//    ippStsOutOfRangeErr        lengtrh of the actual SEED > length SEED destination
//
//    ippStsNoErr                no error
//
// Parameters:
//    pRnd     pointer to the context
//    pSeed    pointer to the SEED
*F*/
IPPFUN(IppStatus, ippsPRNGGetSeed, (const IppsPRNGState* pRnd, IppsBigNumState* pSeed))
{
   /* test PRNG context */
   IPP_BAD_PTR1_RET(pRnd);
   pRnd = (IppsPRNGState*)( IPP_ALIGNED_PTR(pRnd, PRNG_ALIGNMENT) );
   IPP_BADARG_RET(!RAND_VALID_ID(pRnd), ippStsContextMatchErr);

   /* test seed */
   IPP_BAD_PTR1_RET(pSeed);
   pSeed = (IppsBigNumState*)( IPP_ALIGNED_PTR(pSeed, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pSeed), ippStsContextMatchErr);

   return ippsSet_BN(ippBigNumPOS,
                     BITS2WORD32_SIZE(RAND_SEEDBITS(pRnd)),
                     (Ipp32u*)RAND_XKEY(pRnd),
                     pSeed);
}
