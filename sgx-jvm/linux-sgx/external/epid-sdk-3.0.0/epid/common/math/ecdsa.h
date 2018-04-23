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
 * \brief Ecdsa interface.
 */

#ifndef EPID_COMMON_MATH_ECDSA_H_
#define EPID_COMMON_MATH_ECDSA_H_

#include <stddef.h>

#include "epid/common/errors.h"
#include "epid/common/types.h"
#include "epid/common/bitsupplier.h"

/// Elliptic Curve Digital Signature Algorithm Primitives
/*!
  \defgroup EcdsaPrimitives ecdsa
  Provides APIs for computing and checking buffer signatures using the
  Elliptic Curve Digital Signature Algorithm.

  \ingroup EpidMath
  @{
*/

/// Verifies authenticity of a digital signature over a buffer
/*!

  Uses Elliptic Curve Digital Signature Algorithm (ECDSA) to verify
  that the SHA-256 hash of the input buffer was signed with the
  private key corresponding to the provided public key.

  The operation is over the standard secp256r1 curve.

  \warning
  It is the responsibility of the caller to verify the identity of
  the public key.

  \param[in] buf
  Pointer to buffer containing message to verify.
  \param[in] buf_len
  The size of buf in bytes.
  \param[in] pubkey
  The ECDSA public key on secp256r1 curve.
  \param[in] sig
  The ECDSA signature to be verified.

  \returns ::EpidStatus

  \retval ::kEpidSigValid
  EcdsaSignature is valid for the given buffer.
  \retval ::kEpidSigInvalid
  EcdsaSignature is invalid for the given buffer.

  \see EcdsaSignBuffer
 */
EpidStatus EcdsaVerifyBuffer(void const* buf, size_t buf_len,
                             EcdsaPublicKey const* pubkey,
                             EcdsaSignature const* sig);

/// Creates ECDSA signature of buffer
/*!

  Uses Elliptic Curve Digital Signature Algorithm (ECDSA) to generate
  a signature of the SHA-256 hash of the input buffer with the provided
  private key.

  The operation is over the standard secp256r1 curve.

  \param[in] buf
  Pointer to buffer containing message to sign.
  \param[in] buf_len
  The size of buf in bytes.
  \param[in] privkey
  The ECDSA private key on secp256r1 curve.
  \param[in] rnd_func
  Random number generator.
  \param[in] rnd_param
  Pass through context data for rnd_func.
  \param[out] sig
  The resulting ECDSA signature.

  \returns ::EpidStatus

  \retval ::kEpidRandMaxIterErr
  Failed to sign after maximum number of iterations due to bad luck in
  random number generation.

  \see EcdsaSignBuffer
 */
EpidStatus EcdsaSignBuffer(void const* buf, size_t buf_len,
                           EcdsaPrivateKey const* privkey, BitSupplier rnd_func,
                           void* rnd_param, EcdsaSignature* sig);

/*!
  @}
*/

#endif  // EPID_COMMON_MATH_ECDSA_H_
