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
//     ippsPRNGGetSize()
//     ippsPRNGInit()
// 
// 
*/

#include "precomp.h"

#include "owncp.h"
#include "pcpbn.h"
#include "pcpprng.h"
#include "pcphash.h"
#include "pcptool.h"


/*F*
//    Name: ippsPRNGGetSize
//
// Purpose: Returns size of PRNG context (bytes).
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pSize
//
//    ippStsNoErr                no error
//
// Parameters:
//    pSize       pointer to the size of internal context
*F*/
IPPFUN(IppStatus, ippsPRNGGetSize, (int* pSize))
{
   IPP_BAD_PTR1_RET(pSize);

   *pSize = sizeof(IppsPRNGState)
           +PRNG_ALIGNMENT-1;
   return ippStsNoErr;
}


/*F*
// Name: ippsPRNGInit
//
// Purpose: Initializes PRNG context
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pRnd
//
//    ippStsLengthErr            seedBits < 1
//                               seedBits < MAX_XKEY_SIZE
//                               seedBits%8 !=0
//
//    ippStsNoErr                no error
//
// Parameters:
//    seedBits    seed bitsize
//    pRnd        pointer to the context to be initialized
*F*/
IPPFUN(IppStatus, ippsPRNGInit, (int seedBits, IppsPRNGState* pRnd))
{
   /* test PRNG context */
   IPP_BAD_PTR1_RET(pRnd);
   pRnd = (IppsPRNGState*)( IPP_ALIGNED_PTR(pRnd, PRNG_ALIGNMENT) );

   /* test sizes */
   IPP_BADARG_RET((1>seedBits) || (seedBits>MAX_XKEY_SIZE) ||(seedBits&7), ippStsLengthErr);

   {
      int hashIvSize = cpHashIvSize(ippHashAlg_SHA1);
      const Ipp8u* iv = cpHashIV[ippHashAlg_SHA1];

      /* cleanup context */
      ZEXPAND_BNU((Ipp8u*)pRnd, 0, (cpSize)(sizeof(IppsPRNGState)));

      RAND_ID(pRnd) = idCtxPRNG;
      RAND_SEEDBITS(pRnd) = seedBits;

      /* default Q parameter */
      ((Ipp32u*)RAND_Q(pRnd))[0] = 0xFFFFFFFF;
      ((Ipp32u*)RAND_Q(pRnd))[1] = 0xFFFFFFFF;
      ((Ipp32u*)RAND_Q(pRnd))[2] = 0xFFFFFFFF;
      ((Ipp32u*)RAND_Q(pRnd))[3] = 0xFFFFFFFF;
      ((Ipp32u*)RAND_Q(pRnd))[4] = 0xFFFFFFFF;

      /* default T parameter */
      CopyBlock(iv, RAND_T(pRnd), hashIvSize);

      return ippStsNoErr;
   }
}
