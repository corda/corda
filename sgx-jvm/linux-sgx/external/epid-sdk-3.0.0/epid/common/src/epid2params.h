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
#ifndef EPID_COMMON_SRC_EPID2PARAMS_H_
#define EPID_COMMON_SRC_EPID2PARAMS_H_
/*!
 * \file
 * \brief Intel(R) EPID 2.0 constant parameters interface.
 * \addtogroup EpidCommon
 * @{
 */
#include "epid/common/math/bignum.h"
#include "epid/common/math/ecgroup.h"
#include "epid/common/math/finitefield.h"
#include "epid/common/math/pairing.h"

/// Internal representation of Epid2Params
typedef struct Epid2Params_ {
  BigNum* p;      ///< a prime
  BigNum* q;      ///< a prime
  BigNum* t;      ///< an integer
  bool neg;       ///< a boolean
  FfElement* xi;  ///< array of integers between [0, q-1]
  EcPoint* g1;    ///<  a generator (an element) of G1
  EcPoint* g2;    ///<  a generator (an element) of G2

  FiniteField* Fp;  ///< Finite field Fp

  FiniteField* Fq;   ///< Finite field Fq
  FiniteField* Fq2;  ///< Finite field Fq2
  FiniteField* Fq6;  ///< Finite field Fq6
  FiniteField* GT;   ///< Finite field GT(Fq12 )

  EcGroup* G1;  ///< Elliptic curve group over finite field Fq
  EcGroup* G2;  ///< Elliptic curve group over finite field Fq2

  PairingState* pairing_state;  ///< Pairing state
} Epid2Params_;

/// Constructs the internal representation of Epid2Params
/*!
  Allocates memory for the internal representation of Epid2Params. Initialize
  the Epid2Params. Use DeleteEpid2Params() to deallocate memory.

  \param[in,out] params
  Internal Epid2Params

  \returns ::EpidStatus
  \see DeleteEpid2Params
*/
EpidStatus CreateEpid2Params(Epid2Params_** params);
/// Deallocates storage for internal representation of Epid2Params
/*!
  Frees the memory and nulls the pointer.

  \param[in,out] epid_params
  params to be deallocated

  \see CreateEpid2Params
*/
void DeleteEpid2Params(Epid2Params_** epid_params);
/*! @} */
#endif  // EPID_COMMON_SRC_EPID2PARAMS_H_
