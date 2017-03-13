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
 * \brief Pseudo random number generator interface.
 */

#ifndef EXAMPLE_SIGNMSG_SRC_PRNG_H_
#define EXAMPLE_SIGNMSG_SRC_PRNG_H_

#include "epid/common/errors.h"

/// Creates Pseudo Random Number Generator for ::PrngGen()
EpidStatus PrngCreate(void** prng);

/// Delete object allocated with ::PrngCreate()
void PrngDelete(void** prng);

/// BitSupplier type of function
int __STDCALL PrngGen(unsigned int* rand_data, int num_bits, void* user_data);

#endif  // EXAMPLE_SIGNMSG_SRC_PRNG_H_
