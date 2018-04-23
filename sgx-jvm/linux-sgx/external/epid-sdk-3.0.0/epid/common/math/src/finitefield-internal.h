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
 * \brief Finite field private interface.
 */

#ifndef EPID_COMMON_MATH_SRC_FINITEFIELD_INTERNAL_H_
#define EPID_COMMON_MATH_SRC_FINITEFIELD_INTERNAL_H_

#include "ext/ipp/include/ippcpepid.h"

/// Finite Field
struct FiniteField {
  /// Internal implementation of finite field
  IppsGFpState* ipp_ff;
  /// Information about finite field created
  IppsGFpInfo info;
  /// Prime modulus size in bytes
  size_t prime_modulus_size;
};

/// Finite Field Element
struct FfElement {
  /// Internal implementation of finite field element
  IppsGFpElement* ipp_ff_elem;
  /// Information about finite field element was created for
  IppsGFpInfo info;
};

/// Initialize FiniteField structure
EpidStatus InitFiniteFieldFromIpp(IppsGFpState* ipp_ff, FiniteField* ff);

#endif  // EPID_COMMON_MATH_SRC_FINITEFIELD_INTERNAL_H_
