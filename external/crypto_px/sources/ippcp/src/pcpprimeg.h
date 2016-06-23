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
