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
 * \brief Hash primitives.
 */

#ifndef EPID_COMMON_MATH_HASH_H_
#define EPID_COMMON_MATH_HASH_H_

#include <stddef.h>
#include <stdint.h>
#include <limits.h>  // for CHAR_BIT
#include "epid/common/errors.h"

/// Hash primitives
/*!
  \defgroup HashPrimitives hash
  Provides APIs for computing digests of messages.

  \ingroup EpidMath
  @{
*/

#pragma pack(1)
/// SHA256 digest
typedef struct Sha256Digest {
  unsigned char data[256 / CHAR_BIT];  ///< 256 bit data
} Sha256Digest;
#pragma pack()

/// Computes SHA256 digest of a message.
/*!
  \param[in] msg
  Message to compute digest for.
  \param[in] len
  The size of msg in bytes.
  \param[out] digest
  The resulting message digest.

  \returns ::EpidStatus
*/
EpidStatus Sha256MessageDigest(void const* msg, size_t len,
                               Sha256Digest* digest);

/*!
  @}
*/
#endif  // EPID_COMMON_MATH_HASH_H_
