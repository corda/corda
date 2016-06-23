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

#include "owncp.h"
#include "pcpprimeg.h"
#include "pcptool.h"


/*F*
// Name: ippsPrimeGetSize
//
// Purpose: Returns size of Prime Number Generator context (bytes).
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pSize
//    ippStsLengthErr            1 > maxBits
//    ippStsNoErr                no error
//
// Parameters:
//    maxBits  max length of a prime number
//    pSize    pointer to the size of internal context
*F*/
IPPFUN(IppStatus, ippsPrimeGetSize, (cpSize maxBits, cpSize* pSize))
{
   IPP_BAD_PTR1_RET(pSize);
   IPP_BADARG_RET(maxBits<1, ippStsLengthErr);

   {
      cpSize len = BITS_BNU_CHUNK(maxBits);
      cpSize len32 = BITS2WORD32_SIZE(maxBits);
      cpSize montSize;
      ippsMontGetSize(ippBinaryMethod, len32, &montSize);

      *pSize = sizeof(IppsPrimeState)
              +len*sizeof(BNU_CHUNK_T)
              +len*sizeof(BNU_CHUNK_T)
              +len*sizeof(BNU_CHUNK_T)
              +len*sizeof(BNU_CHUNK_T)
              +montSize
              +PRIME_ALIGNMENT-1;

      return ippStsNoErr;
   }
}


/*F*
// Name: ippsPrimeInit
//
// Purpose: Initializes Prime Number Generator context
//
// Returns:                   Reason:
//    ippStsNullPtrErr           NULL == pCtx
//    ippStsLengthErr            1 > maxBits
//    ippStsNoErr                no error
//
// Parameters:
//    maxBits  max length of a prime number
//    pCtx     pointer to the context to be initialized
*F*/
IPPFUN(IppStatus, ippsPrimeInit, (cpSize maxBits, IppsPrimeState* pCtx))
{
   IPP_BAD_PTR1_RET(pCtx);
   IPP_BADARG_RET(maxBits<1, ippStsLengthErr);

   /* use aligned PRNG context */
   pCtx = (IppsPrimeState*)( IPP_ALIGNED_PTR(pCtx, PRIME_ALIGNMENT) );

   {
      Ipp8u* ptr = (Ipp8u*)pCtx;

      cpSize len = BITS_BNU_CHUNK(maxBits);
      cpSize len32 = BITS2WORD32_SIZE(maxBits);

      PRIME_ID(pCtx) = idCtxPrimeNumber;
      PRIME_MAXBITSIZE(pCtx) = maxBits;

      ptr += sizeof(IppsPrimeState);
      PRIME_NUMBER(pCtx) = (BNU_CHUNK_T*)ptr;

      ptr += len*sizeof(BNU_CHUNK_T);
      PRIME_TEMP1(pCtx) = (BNU_CHUNK_T*)ptr;

      ptr += len*sizeof(BNU_CHUNK_T);
      PRIME_TEMP2(pCtx) = (BNU_CHUNK_T*)ptr;

      ptr += len*sizeof(BNU_CHUNK_T);
      PRIME_TEMP3(pCtx) = (BNU_CHUNK_T*)ptr;

      ptr += len*sizeof(BNU_CHUNK_T);
      PRIME_MONT(pCtx) = (IppsMontState*)( IPP_ALIGNED_PTR((ptr), MONT_ALIGNMENT) );
      ippsMontInit(ippBinaryMethod, len32, PRIME_MONT(pCtx));

      return ippStsNoErr;
   }
}
