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
//     Internal Definitions and
//     Internal Pseudo Random Generator Function Prototypes
// 
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
