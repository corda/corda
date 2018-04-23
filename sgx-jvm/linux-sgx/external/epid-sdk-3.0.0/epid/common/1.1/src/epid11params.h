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
#ifndef EPID_COMMON_1_1_SRC_EPID11PARAMS_H_
#define EPID_COMMON_1_1_SRC_EPID11PARAMS_H_
/*!
 * \file
 * \brief Intel(R) EPID 1.1 constant parameters interface.
 * \addtogroup EpidCommon
 * @{
 */
#include "epid/common/1.1/types.h"
#include "epid/common/math/bignum.h"
#include "epid/common/math/ecgroup.h"
#include "epid/common/math/finitefield.h"
#include "epid/common/math/tatepairing.h"

/// Internal representation of Epid11Params
typedef struct Epid11Params_ {
  BigNum* p;       ///< a prime
  BigNum* p_tick;  ///< a prime
  EcPoint* g1;     ///<  a generator (an element) of G1
  EcPoint* g2;     ///<  a generator (an element) of G2
  EcPoint* g3;     ///<  a generator (an element) of G3

  FiniteField* Fp;       ///< Finite field Fp
  FiniteField* Fq;       ///< Finite field Fq
  FiniteField* Fp_tick;  ///< Finite field Fp'
  FiniteField* Fq_tick;  ///< Finite field Fq'
  FiniteField* Fqd;      ///< Finite field Fqd, an extension of Fq
  FiniteField* GT;       ///< GT is a quadratic field extension Fqk of Fqd

  EcGroup* G1;  ///< Elliptic curve group over finite field Fq
  EcGroup* G2;  ///< Elliptic curve group over finite field Fqd
  EcGroup* G3;  ///< Elliptic curve group over finite field Fq'

  Epid11PairingState* pairing_state;  ///< Pairing state
} Epid11Params_;

/// Constructs the internal representation of Epid11Params
/*!
  Allocates memory for the internal representation of Epid11Params. Initialize
  the Epid11Params. Use DeleteEpid11Params() to deallocate memory.

  \param[in,out] params
  Internal Epid11Params

  \returns ::EpidStatus
  \see DeleteEpid11Params
*/
EpidStatus CreateEpid11Params(Epid11Params_** params);
/// Deallocates storage for internal representation of Epid11Params
/*!
  Frees the memory and nulls the pointer.

  \param[in,out] params
  params to be deallocated

  \see CreateEpid11Params
*/
void DeleteEpid11Params(Epid11Params_** params);
/*! @} */
#endif  // EPID_COMMON_1_1_SRC_EPID11PARAMS_H_
