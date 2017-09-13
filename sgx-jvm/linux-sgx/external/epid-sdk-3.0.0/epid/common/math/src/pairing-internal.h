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
 * \brief Pairing private interface.
 */

#ifndef EPID_COMMON_MATH_SRC_PAIRING_INTERNAL_H_
#define EPID_COMMON_MATH_SRC_PAIRING_INTERNAL_H_

/// Pairing State
struct PairingState {
  EcGroup* ga;      ///< elliptic curve group G1
  EcGroup* gb;      ///< elliptic curve group G1
  FiniteField* ff;  ///< finite field Fq12 GT
  BigNum* t;  ///< positive integer such that 6t^2 = p-q, where p and q are
              /// parameters of G1
  bool neg;   ///< 8-bit integer representing a Boolean value
  FfElement* g[3][5];  ///< 15 elements in Fq2
  FiniteField Fq;      ///< Fq
  FiniteField Fq2;     ///< Fq2
  FiniteField Fq6;     ///< Fq6
};

#endif  // EPID_COMMON_MATH_SRC_PAIRING_INTERNAL_H_
