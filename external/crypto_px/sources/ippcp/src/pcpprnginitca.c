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
