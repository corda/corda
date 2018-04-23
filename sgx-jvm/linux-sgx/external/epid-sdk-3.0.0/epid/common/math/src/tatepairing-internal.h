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
 * \brief Intel(R) EPID 1.1 pairing private interface.
 */

#ifndef EPID_COMMON_MATH_SRC_TATEPAIRING_INTERNAL_H_
#define EPID_COMMON_MATH_SRC_TATEPAIRING_INTERNAL_H_

/// Pairing State
struct Epid11PairingState {
  EcGroup* ga;                   ///< elliptic curve group G1
  EcGroup* gb;                   ///< elliptic curve group G2
  FiniteField* ff;               ///< finite field GT
  BigNumStr p;                   ///< Intel(R) EPID 1.1 p parameter value
  size_t p_bitsize;              ///< Length of p in bits
  FfElement* a;                  ///< Intel(R) EPID 1.1 a parameter value
  BigNum* final_exp_constant;    ///< (q^2 - q + 1)/p
  FfElement* fq3_inv_constant;   ///< (inverse(qnr), 0) in Fq3
  FfElement* fq3_inv2_constant;  ///< (inverse(qnr)^2, 0) in Fq3
  FiniteField Fq;                ///< Fq
  FiniteField Fq3;               ///< Fq3
  FfElement* alpha_q[3];         ///< {t^(0*q), t^(1*q), t^(2*q)}
};

#endif  // EPID_COMMON_MATH_SRC_TATEPAIRING_INTERNAL_H_
