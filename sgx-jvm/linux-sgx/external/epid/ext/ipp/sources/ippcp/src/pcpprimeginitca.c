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
//  Purpose:
//     Intel(R) Integrated Performance Primitives. Cryptographic Primitives (ippcp)
//     Prime Number Primitives.
// 
//  Contents:
//     ippsPrimeGetSize()
//     ippsPrimeInit()
// 
// 
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


void cpPackPrimeCtx(const IppsPrimeState* pCtx, Ipp8u* pBuffer)
{
   IppsPrimeState* pAlignedBuffer = (IppsPrimeState*)( IPP_ALIGNED_PTR(pBuffer, PRIME_ALIGNMENT) );

   /* max length of prime */
   cpSize nsPrime = BITS_BNU_CHUNK(PRIME_MAXBITSIZE(pCtx));

   CopyBlock(pCtx, pAlignedBuffer, sizeof(IppsPrimeState));
   PRIME_NUMBER(pAlignedBuffer)=  (BNU_CHUNK_T*)((Ipp8u*)NULL + IPP_UINT_PTR(PRIME_NUMBER(pCtx))-IPP_UINT_PTR(pCtx));
   PRIME_TEMP1(pAlignedBuffer) =  (BNU_CHUNK_T*)((Ipp8u*)NULL + IPP_UINT_PTR(PRIME_TEMP1(pCtx))-IPP_UINT_PTR(pCtx));
   PRIME_TEMP2(pAlignedBuffer) =  (BNU_CHUNK_T*)((Ipp8u*)NULL + IPP_UINT_PTR(PRIME_TEMP2(pCtx))-IPP_UINT_PTR(pCtx));
   PRIME_TEMP3(pAlignedBuffer) =  (BNU_CHUNK_T*)((Ipp8u*)NULL + IPP_UINT_PTR(PRIME_TEMP3(pCtx))-IPP_UINT_PTR(pCtx));
   PRIME_MONT(pAlignedBuffer)  =(IppsMontState*)((Ipp8u*)NULL + IPP_UINT_PTR(PRIME_MONT(pCtx))-IPP_UINT_PTR(pCtx));

   CopyBlock(PRIME_NUMBER(pCtx), (Ipp8u*)pAlignedBuffer+IPP_UINT_PTR(PRIME_NUMBER(pAlignedBuffer)), nsPrime*sizeof(BNU_CHUNK_T));
   cpPackMontCtx(PRIME_MONT(pCtx), (Ipp8u*)pAlignedBuffer+IPP_UINT_PTR(PRIME_MONT(pAlignedBuffer)));
}

void cpUnpackPrimeCtx(const Ipp8u* pBuffer, IppsPrimeState* pCtx)
{
   IppsPrimeState* pAlignedBuffer = (IppsPrimeState*)( IPP_ALIGNED_PTR(pBuffer, PRIME_ALIGNMENT) );

   /* max length of prime */
   cpSize nsPrime = BITS_BNU_CHUNK(PRIME_MAXBITSIZE(pAlignedBuffer));

   CopyBlock(pAlignedBuffer, pCtx, sizeof(IppsPrimeState));
   PRIME_NUMBER(pCtx)=   (BNU_CHUNK_T*)((Ipp8u*)pCtx+ IPP_UINT_PTR(PRIME_NUMBER(pAlignedBuffer)));
   PRIME_TEMP1(pCtx) =   (BNU_CHUNK_T*)((Ipp8u*)pCtx+ IPP_UINT_PTR(PRIME_TEMP1(pAlignedBuffer)));
   PRIME_TEMP2(pCtx) =   (BNU_CHUNK_T*)((Ipp8u*)pCtx+ IPP_UINT_PTR(PRIME_TEMP2(pAlignedBuffer)));
   PRIME_TEMP3(pCtx) =   (BNU_CHUNK_T*)((Ipp8u*)pCtx+ IPP_UINT_PTR(PRIME_TEMP3(pAlignedBuffer)));
   PRIME_MONT(pCtx)  = (IppsMontState*)((Ipp8u*)pCtx+ IPP_UINT_PTR(PRIME_MONT(pAlignedBuffer)));

   CopyBlock((Ipp8u*)pAlignedBuffer+IPP_UINT_PTR(PRIME_NUMBER(pAlignedBuffer)), PRIME_NUMBER(pCtx), nsPrime*sizeof(BNU_CHUNK_T));
   cpUnpackMontCtx((Ipp8u*)pAlignedBuffer+IPP_UINT_PTR(PRIME_MONT(pAlignedBuffer)), PRIME_MONT(pCtx));
}
