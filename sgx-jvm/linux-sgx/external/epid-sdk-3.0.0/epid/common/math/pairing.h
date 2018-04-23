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
 * \brief Pairing interface.
 */

#ifndef EPID_COMMON_MATH_PAIRING_H_
#define EPID_COMMON_MATH_PAIRING_H_

#include "epid/common/errors.h"
#include "epid/common/types.h"
#include "epid/common/math/finitefield.h"
#include "epid/common/math/ecgroup.h"

/// Pairing operations
/*!
  \defgroup PairingPrimitives pairing
  Provides APIs for defining and using a pairing relationship between two
  elliptic curve groups.

  \ingroup EpidMath
  @{
*/

/// A pairing
typedef struct PairingState PairingState;

/// Constructs a new pairing state.
/*!
 Allocates memory and creates a new pairing state for Optimal Ate Pairing.

 Use DeletePairingState() to free memory.

 \param[in] ga
 The EcGroup from which the first parameter of the pairing is taken.
 \param[in] gb
 The EcGroup from which the second parameter of the pairing is taken.
 \param[in] ff
 The result finite field. Must be a Fq12 field.
 \param[in] t
 A positive integer such that 6(t^2) == q - p, where p and q are parameters
 of G1.
 \param[in] neg
 Select the alternate "negate" processing path for Optimal Ate Pairing.
 \param[out] ps
 Newly constructed pairing state.

 \returns ::EpidStatus

 \see DeletePairingState
*/
EpidStatus NewPairingState(EcGroup const* ga, EcGroup const* gb,
                           FiniteField* ff, BigNumStr const* t, bool neg,
                           PairingState** ps);

/// Frees a previously allocated by PairingState.
/*!
 Frees memory pointed to by pairing state. Nulls the pointer.

 \param[in] ps
 The pairing state. Can be NULL.

 \see NewPairingState
*/
void DeletePairingState(PairingState** ps);

/// Computes an Optimal Ate Pairing for two parameters.
/*!
 \param[in] ps
 The pairing state.
 \param[out] d
 The result of the pairing. Will be in ff used to create the pairing state.
 \param[in] a
 The first value to pair. Must be in ga used to create ps.
 \param[in] b
 The second value to pair. Must be in gb used to create ps

 \returns ::EpidStatus
*/
EpidStatus Pairing(PairingState* ps, FfElement* d, EcPoint const* a,
                   EcPoint const* b);

/*!
  @}
*/

#endif  // EPID_COMMON_MATH_PAIRING_H_
