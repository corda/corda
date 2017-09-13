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
 * \brief Big number private interface.
 */

#ifndef EPID_COMMON_MATH_SRC_BIGNUM_INTERNAL_H_
#define EPID_COMMON_MATH_SRC_BIGNUM_INTERNAL_H_

#include "ext/ipp/include/ippcpepid.h"
#include "epid/common/stdtypes.h"
#include "epid/common/errors.h"

/// Big Number
struct BigNum {
  /// Internal implementation of bignum
  IppsBigNumState* ipp_bn;
};

/// convert octet string into "big number unsigned" representation
/*!

This is an internal function, used to convert an octet string (uint8_t
array) into a big number unsigned representation (uint32_t array).
For example, octet string {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
0x08} is converted to {0x05060708, 0x01020304}

\param[out] bnu_ptr
Output big number unsigned array
\param[in] octstr_ptr
Input octal string
\param[in] octstr_len
Length of octet string, should be multiple of 4

\returns length of big number unsigned in uint32_t chunks
\returns -1 in case of any error
*/
int OctStr2Bnu(uint32_t* bnu_ptr, void const* octstr_ptr, int octstr_len);

/// Get octet string size in bits
/*!
\param[in] octstr_ptr
Input octet string.
\param[in] octstr_len
Length of octet string in bytes.

\returns bit size of big number value from octet string
*/
size_t OctStrBitSize(uint8_t const* octstr_ptr, size_t octstr_len);

/// Initializes a BigNum from a BNU.
/*!
  \param[in] bnu
  The desired value as a bnu.
  \param[in] bnu_len
  The size of bnu_str in 32 bit words.
  \param[out] bn
  The target BigNum.

  \note A BNU is a big integer represented as array of 4 byte words written in
  little endian order

  \returns ::EpidStatus
*/
EpidStatus InitBigNumFromBnu(uint32_t const* bnu, size_t bnu_len,
                             struct BigNum* bn);

#endif  // EPID_COMMON_MATH_SRC_BIGNUM_INTERNAL_H_
