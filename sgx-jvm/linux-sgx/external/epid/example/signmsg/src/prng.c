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

/*!
 * \file
 * \brief Pseudo random number generator implementation.
 */
#include <time.h>
#include <ippcp.h>
#include <stdlib.h>

#include "src/prng.h"

EpidStatus PrngCreate(void** prng) {
  // Security note:
  // Random number generator used in the samples not claimed to be a
  // cryptographically secure pseudo-random number generator.
  EpidStatus sts = kEpidErr;
  int prng_ctx_size = 0;
  IppsPRNGState* prng_ctx = NULL;
  int seed_ctx_size = 0;
  IppsBigNumState* seed_ctx = NULL;
  time_t seed_value;

  if (!prng) return kEpidBadArgErr;

  if (ippStsNoErr != ippsPRNGGetSize(&prng_ctx_size)) return kEpidErr;
  if (ippStsNoErr !=
      ippsBigNumGetSize((sizeof(seed_value) + 3) / 4, &seed_ctx_size))
    return kEpidErr;

  do {
    prng_ctx = (IppsPRNGState*)calloc(1, prng_ctx_size);

    if (!prng_ctx) {
      sts = kEpidNoMemErr;
      break;
    }
    if (ippStsNoErr != ippsPRNGInit(sizeof(seed_value) * 8, prng_ctx)) {
      sts = kEpidErr;
      break;
    }

    // seed PRNG
    seed_ctx = (IppsBigNumState*)calloc(1, seed_ctx_size);
    if (!seed_ctx) {
      sts = kEpidNoMemErr;
      break;
    }
    if (ippStsNoErr != ippsBigNumInit((sizeof(seed_value) + 3) / 4, seed_ctx)) {
      sts = kEpidErr;
      break;
    }
    time(&seed_value);
    if (ippStsNoErr !=
        ippsSetOctString_BN((void*)&seed_value, sizeof(seed_value), seed_ctx)) {
      sts = kEpidErr;
      break;
    }
    if (ippStsNoErr != ippsPRNGSetSeed(seed_ctx, prng_ctx)) {
      sts = kEpidErr;
      break;
    }

    *prng = prng_ctx;
    prng_ctx = NULL;
    sts = kEpidNoErr;
  } while (0);

  if (seed_ctx) free(seed_ctx);
  if (prng_ctx) free(prng_ctx);
  return sts;
}

void PrngDelete(void** prng) {
  if (prng && *prng) {
    free(*prng);
    *prng = NULL;
  }
}

// simple wrapper to hide IPP implementation.
int __STDCALL PrngGen(unsigned int* rand_data, int num_bits, void* user_data) {
  return ippsPRNGen(rand_data, num_bits, (IppsPRNGState*)user_data);
}
