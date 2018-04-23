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
//               Intel(R) Integrated Performance Primitives
//                   Cryptographic Primitives (ippcp)
//                    Prime Number Primitives.
// 
// 
*/


#if !defined(_CP_PRIME_H)
#define _CP_PRIME_H

#include "pcpbn.h"
#include "pcpmontgomery.h"


/*
// Prime context
*/
struct _cpPrime {
   IppCtxId          idCtx;      /* Prime context identifier */
   cpSize            maxBitSize; /* max bit length             */
   BNU_CHUNK_T*      pPrime;     /* prime value   */
   BNU_CHUNK_T*      pT1;        /* temporary BNU */
   BNU_CHUNK_T*      pT2;        /* temporary BNU */
   BNU_CHUNK_T*      pT3;        /* temporary BNU */
   IppsMontState*    pMont;      /* montgomery engine        */
};

/* alignment */
#define PRIME_ALIGNMENT ((int)sizeof(void*))

/* Prime accessory macros */
#define PRIME_ID(ctx)         ((ctx)->idCtx)
#define PRIME_MAXBITSIZE(ctx) ((ctx)->maxBitSize)
#define PRIME_NUMBER(ctx)     ((ctx)->pPrime)
#define PRIME_TEMP1(ctx)      ((ctx)->pT1)
#define PRIME_TEMP2(ctx)      ((ctx)->pT2)
#define PRIME_TEMP3(ctx)      ((ctx)->pT3)
#define PRIME_MONT(ctx)       ((ctx)->pMont)

#define PRIME_VALID_ID(ctx)   (PRIME_ID((ctx))==idCtxPrimeNumber)

/* easy prime test */
int cpMimimalPrimeTest(const Ipp32u* pPrime, cpSize ns);

/* prime test */
int cpPrimeTest(const BNU_CHUNK_T* pPrime, cpSize primeLen,
                cpSize nTrials,
                IppsPrimeState* pCtx,
                IppBitSupplier rndFunc, void* pRndParam);

void cpPackPrimeCtx(const IppsPrimeState* pCtx, Ipp8u* pBuffer);
void cpUnpackPrimeCtx(const Ipp8u* pBuffer, IppsPrimeState* pCtx);

#endif /* _CP_PRIME_H */
