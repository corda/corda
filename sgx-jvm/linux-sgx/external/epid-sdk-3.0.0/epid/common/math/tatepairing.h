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
 * \brief Intel(R) EPID 1.1 Pairing interface.
 */

#ifndef EPID_COMMON_MATH_TATEPAIRING_H_
#define EPID_COMMON_MATH_TATEPAIRING_H_

#include "epid/common/errors.h"
#include "epid/common/types.h"
#include "epid/common/math/finitefield.h"
#include "epid/common/math/ecgroup.h"

/// EPID 1.1 pairing operations
/*!

  \defgroup Epid11PairingPrimitives EPID 1.1 specific pairing
  Provides APIs for defining and using a pairing relationship between two
  Elliptic curve groups.

  These pairing operations are intended to support Intel(R) EPID
  1.1 verification.

  \ingroup PairingPrimitives
  \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
  support</b></a>
  @{
*/

/// A pairing
typedef struct Epid11PairingState Epid11PairingState;

/// Constructs a new Tate pairing state.
/*!
 Allocates memory and creates a new pairing state for Tate pairing.

 Use DeleteEpid11PairingState() to free memory.

 This pairing operation is intended to support Intel(R) EPID
 1.1 verification.

 \param[in] ga
 The EcGroup from which the first parameter of the pairing will be taken.
 \param[in] gb
 The EcGroup from which the second parameter of the pairing will be taken.
 \param[in] ff
 The result finite field. Must be a Fq12 field.
 \param[out] ps
 Newly constructed pairing state.

 \returns ::EpidStatus

 \see DeleteEpid11PairingState
 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
 support</b></a>
*/
EpidStatus NewEpid11PairingState(EcGroup const* ga, EcGroup const* gb,
                                 FiniteField const* ff,
                                 Epid11PairingState** ps);

/// Frees a previously allocated by Epid11PairingState.
/*!
 Frees memory pointed to by pairing state. Nulls the pointer.

 This pairing operation is intended to support Intel(R) EPID
 1.1 verification.

 \param[in] ps
 The pairing state. Can be NULL.

 \see NewEpid11PairingState
 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
 support</b></a>
*/
void DeleteEpid11PairingState(Epid11PairingState** ps);

/// Computes a Tate Pairing for two parameters.
/*!
This pairing operation is intended to support Intel(R) EPID
1.1 verification. It frees memory pointed to by an Intel(R) EPID
1.1 pairing state.

 \param[in] ps
 The pairing state.
 \param[in] a
 The first value to pair. Must be in ga.
 \param[in] b
 The second value to pair. Must be in gb.
 \param[out] d
 The result of the pairing. Must be in ff.

 \returns ::EpidStatus

 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
 support</b></a>
*/
EpidStatus Epid11Pairing(Epid11PairingState* ps, EcPoint const* a,
                         EcPoint const* b, FfElement* d);

/*!
  @}
*/

#endif  // EPID_COMMON_MATH_TATEPAIRING_H_
