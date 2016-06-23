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

#if !defined(_CP_PRNG_H)
#define _CP_PRNG_H

/*
// Pseudo-random generation context
*/

#define MAX_XKEY_SIZE       512
#define DEFAULT_XKEY_SIZE   512 /* must be >=160 || <=512 */

struct _cpPRNG {
   IppCtxId    idCtx;                                 /* PRNG identifier            */
   cpSize      seedBits;                              /* secret seed-key bitsize    */
   BNU_CHUNK_T Q[BITS_BNU_CHUNK(160)];                /* modulus                    */
   BNU_CHUNK_T T[BITS_BNU_CHUNK(160)];                /* parameter of SHA_G() funct */
   BNU_CHUNK_T xAug[BITS_BNU_CHUNK(MAX_XKEY_SIZE)];   /* optional entropy augment   */
   BNU_CHUNK_T xKey[BITS_BNU_CHUNK(MAX_XKEY_SIZE)];   /* secret seed-key            */
};

/* alignment */
#define PRNG_ALIGNMENT ((int)(sizeof(void*)))

#define RAND_ID(ctx)       ((ctx)->idCtx)
#define RAND_SEEDBITS(ctx) ((ctx)->seedBits)
#define RAND_Q(ctx)        ((ctx)->Q)
#define RAND_T(ctx)        ((ctx)->T)
#define RAND_XAUGMENT(ctx) ((ctx)->xAug)
#define RAND_XKEY(ctx)     ((ctx)->xKey)

#define RAND_VALID_ID(ctx)  (RAND_ID((ctx))==idCtxPRNG)

int cpPRNGen(Ipp32u* pBuffer, cpSize bitLen, IppsPRNGState* pCtx);

#endif /* _CP_PRNG_H */
