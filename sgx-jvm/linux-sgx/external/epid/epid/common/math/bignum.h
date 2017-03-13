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
 * \brief Big number interface.
 */

#ifndef EPID_COMMON_MATH_BIGNUM_H_
#define EPID_COMMON_MATH_BIGNUM_H_

#include <stddef.h>
#include <stdint.h>
#include "epid/common/errors.h"
#include "epid/common/types.h"

/// Big number operations
/*!
  \defgroup BigNumPrimitives bignum
  This module provides an API for working with large numbers. BigNums
  represent non-negative integers.

  Each BigNum variable represents a number of a byte-size set when the variable
  was created. BigNum variables cannot be re-sized after they are created.


  \ingroup EpidMath
  @{
*/

/// Internal representation of large numbers
typedef struct BigNum BigNum;

/// Constructs a new BigNum.
/*!
  Allocates memory and creates a new BigNum.

  Use DeleteBigNum() to free memory.

  \param[in] data_size_bytes
  The size in bytes of the new number.
  \param[out] bignum
  The BigNum.

  \returns ::EpidStatus

  \see DeleteBigNum
*/
EpidStatus NewBigNum(size_t data_size_bytes, BigNum** bignum);

/// Deletes a previously allocated BigNum.
/*!
  Frees memory pointed to by bignum. Nulls the pointer.

  \param[in] bignum
  The BigNum. Can be NULL.

  \see NewBigNum
*/
void DeleteBigNum(BigNum** bignum);

/// Deserializes a BigNum from a string.
/*!
  \param[in] bn_str
  The serialized value.
  \param[in] strlen
  The size of bn_str in bytes.
  \param[out] bn
  The target BigNum.

  \returns ::EpidStatus
*/
EpidStatus ReadBigNum(void const* bn_str, size_t strlen, BigNum* bn);

/// Serializes a BigNum to a string.
/*!
  \param[in] bn
  The BigNum to be serialized.
  \param[in] strlen
  The size of bn_str in bytes.
  \param[out] bn_str
  The target string.

  \returns ::EpidStatus
*/
EpidStatus WriteBigNum(BigNum const* bn, size_t strlen, void const* bn_str);

/// Adds two BigNum values.
/*!
  \param[in] a
  The left hand parameter.
  \param[in] b
  The right hand parameter.
  \param[out] r
  The result of adding a and b.

  \returns ::EpidStatus
*/
EpidStatus BigNumAdd(BigNum const* a, BigNum const* b, BigNum* r);

/// Subtracts two BigNum values.
/*!
  \param[in] a
  The left hand parameter.
  \param[in] b
  The right hand parameter.
  \param[out] r
  The result of subtracting a and b.

  \returns ::EpidStatus
*/
EpidStatus BigNumSub(BigNum const* a, BigNum const* b, BigNum* r);

/// Multiplies two BigNum values.
/*!
  \param[in] a
  The left hand parameter.
  \param[in] b
  The right hand parameter.
  \param[out] r
  The result of multiplying a and b.

  \returns ::EpidStatus
*/
EpidStatus BigNumMul(BigNum const* a, BigNum const* b, BigNum* r);

/// Computes modular reduction for BigNum value by specified modulus.
/*!
\param[in] a
The BigNum value.
\param[in] b
The modulus.
\param[out] r
Modular reduction result.

\returns ::EpidStatus
*/
EpidStatus BigNumMod(BigNum const* a, BigNum const* b, BigNum* r);

/*!
  @}
*/
#endif  // EPID_COMMON_MATH_BIGNUM_H_
