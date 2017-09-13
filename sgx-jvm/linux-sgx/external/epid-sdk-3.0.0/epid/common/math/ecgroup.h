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
 * \brief Elliptic curve group interface.
 */

#ifndef EPID_COMMON_MATH_ECGROUP_H_
#define EPID_COMMON_MATH_ECGROUP_H_

#include "epid/common/stdtypes.h"
#include "epid/common/errors.h"
#include "epid/common/math/bignum.h"
#include "epid/common/math/finitefield.h"
#include "epid/common/types.h"

/// Elliptic curve group operations
/*!
  \defgroup EcGroupPrimitives ecgroup
  Provides APIs for working with Elliptic curve groups.
  Elliptic curve groups allow simple mathematical operations based on points
  that lie on a defined elliptic curve. The results of these operations also
  lie on the same curve.

  Curves themselves are defined based on elements (::FfElement) of a finite
  field (::FiniteField).

  \ingroup EpidMath
@{
*/

/// Elliptic curve group over finite field.
typedef struct EcGroup EcGroup;

/// Constructs a new EcGroup.
/*!

 Allocates memory and creates a new elliptic curve group.

 Use DeleteFiniteField() to free memory.

 \param[in] ff
 The finite field on which the curve is based.
 \param[in] a
 The A value of the elliptic curve.
 \param[in] b
 The B value of the elliptic curve.
 \param[in] x
 The X-coordinate of the base point of the elliptic curve.
 \param[in] y
 The Y-coordinate of the base point of the elliptic curve.
 \param[in] order
 The order of the elliptic curve group.
 \param[in] cofactor
 The co-factor of the elliptic curve.
 \param[out] g
 The newly constructed elliptic curve group.

 \returns ::EpidStatus

 \see DeleteEcGroup
*/
EpidStatus NewEcGroup(FiniteField const* ff, FfElement const* a,
                      FfElement const* b, FfElement const* x,
                      FfElement const* y, BigNum const* order,
                      BigNum const* cofactor, EcGroup** g);

/// Deletes a previously allocated EcGroup.
/*!
 Frees memory pointed to by elliptic curve group. Nulls the pointer.

 \param[in] g
 The elliptic curve group. Can be NULL.

 \see NewEcGroup
*/
void DeleteEcGroup(EcGroup** g);

/// Point on elliptic curve over finite field.
typedef struct EcPoint EcPoint;

/// Creates a new EcPoint.
/*!
 Allocates memory and creates a new point on elliptic curve group.

 Use DeleteEcPoint() to free memory.

 \param[in] g
 Elliptic curve group.
 \param[out] p
 Newly constructed point on the elliptic curve group g.

 \returns ::EpidStatus

 \see NewEcGroup
 \see DeleteEcPoint
*/
EpidStatus NewEcPoint(EcGroup const* g, EcPoint** p);

/// Deletes a previously allocated EcPoint.
/*!

 Frees memory used by a point on elliptic curve group. Nulls the pointer.

 \param[in] p
 The EcPoint. Can be NULL.

 \see NewEcPoint
*/
void DeleteEcPoint(EcPoint** p);

/// Deserializes an EcPoint from a string.
/*!
 \param[in] g
 The elliptic curve group.
 \param[in] p_str
 The serialized value.
 \param[in] strlen
 The size of p_str in bytes.
 \param[out] p
 The target EcPoint.

 \returns ::EpidStatus

 \see NewEcPoint
*/
EpidStatus ReadEcPoint(EcGroup* g, void const* p_str, size_t strlen,
                       EcPoint* p);

/// Serializes an EcPoint to a string.
/*!
 \param[in] g
 The elliptic curve group.
 \param[in] p
 The EcPoint to be serialized.
 \param[out] p_str
 The target string.
 \param[in] strlen
 the size of p_str in bytes.

 \returns ::EpidStatus

 \see NewEcPoint
*/
EpidStatus WriteEcPoint(EcGroup* g, EcPoint const* p, void* p_str,
                        size_t strlen);

/// Multiplies two elements in an elliptic curve group.
/*!
 This multiplication operation is also known as element addition for
 elliptic curve groups.

 \param[in] g
 The elliptic curve group.
 \param[in] a
 The first operand to be multiplied.
 \param[in] b
 The second operand to be multiplied.
 \param[out] r
 The result of multiplying a and b.

 \returns ::EpidStatus

 \see NewEcGroup
 \see NewEcPoint
*/
EpidStatus EcMul(EcGroup* g, EcPoint const* a, EcPoint const* b, EcPoint* r);

/// Raises a point in an elliptic curve group to a power.
/*!
 This exponentiation operation is also known as element multiplication
 for elliptic curve groups.
 \param[in] g
 The elliptic curve group.
 \param[in] a
 The base.
 \param[in] b
 The power. Power must be less than the order of the elliptic curve
 group.
 \param[out] r
 The result of raising a to the power b.

 \returns ::EpidStatus

 \see NewEcGroup
 \see NewEcPoint
*/
EpidStatus EcExp(EcGroup* g, EcPoint const* a, BigNumStr const* b, EcPoint* r);

/// Software side-channel mitigated implementation of EcExp.
/*!
 This exponentiation operation is also known as element multiplication
 for elliptic curve groups.

 \attention
 The reference implementation of EcSscmExp calls EcExp directly because
 the implementation of EcExp is already side channel mitigated. Implementers
 providing their own versions of this function are responsible for ensuring
 that EcSscmExp is side channel mitigated per section 8 of the
 Intel(R) EPID 2.0 spec.

 \param[in] g
 The elliptic curve group.
 \param[in] a
 The base.
 \param[in] b
 The power. Power must be less than the order of the elliptic curve
 group.
 \param[out] r
 The result of raising a to the power b.

 \returns ::EpidStatus

 \see NewEcGroup
 \see NewEcPoint
*/
EpidStatus EcSscmExp(EcGroup* g, EcPoint const* a, BigNumStr const* b,
                     EcPoint* r);

/// Multi-exponentiates elements in elliptic curve group.
/*!
 Takes a group elements a[0], ... , a[m-1] in G and positive
 integers b[0], ..., b[m-1], where m is a small positive integer.
 Outputs r (in G) = EcExp(a[0],b[0]) * ... * EcExp(a[m-1],b[m-1]).

 \param[in] g
 The elliptic curve group.
 \param[in] a
 The bases.
 \param[in] b
 The powers. Power must be less than the order of the elliptic curve
 group.
 \param[in] m
 Number of entries in a and b.
 \param[out] r
 The result of raising each a to the corresponding power b and multiplying
 the results.

 \returns ::EpidStatus

 \see NewEcGroup
 \see NewEcPoint
*/
EpidStatus EcMultiExp(EcGroup* g, EcPoint const** a, BigNumStr const** b,
                      size_t m, EcPoint* r);

/// Multi-exponentiates elements in elliptic curve group.
/*!
Takes a group elements a[0], ... , a[m-1] in G and positive
integers b[0], ..., b[m-1], where m is a small positive integer.
Outputs r (in G) = EcExp(a[0],b[0]) * ... * EcExp(a[m-1],b[m-1]).

\param[in] g
The elliptic curve group.
\param[in] a
The bases.
\param[in] b
The powers. Power must be less than the order of the elliptic curve
group.
\param[in] m
Number of entries in a and b.
\param[out] r
The result of raising each a to the corresponding power b and multiplying
the results.

\returns ::EpidStatus

\see NewEcGroup
\see NewEcPoint
*/
EpidStatus EcMultiExpBn(EcGroup* g, EcPoint const** a, BigNum const** b,
                        size_t m, EcPoint* r);

/// Software side-channel mitigated implementation of EcMultiExp.
/*!
 Takes a group elements a[0], ... , a[m-1] in G and positive
 integers b[0], ..., b[m-1], where m is a small positive integer.
 Outputs r (in G) = EcExp(a[0],b[0]) * ... * EcExp(a[m-1],b[m-1]).

 \attention
 The reference implementation of EcSscmMultiExp calls EcMultiExp
 directly because the implementation of EcMultiExp is already side channel
 mitigated. Implementers providing their own versions of this function are
 responsible for ensuring that EcSscmMultiExp is side channel mitigated per
 section 8 of the Intel(R) EPID 2.0 spec.

 \param[in] g
 The elliptic curve group.
 \param[in] a
 The bases.
 \param[in] b
 The powers. Power must be less than the order of the elliptic curve
 group.
 \param[in] m
 Number of entries in a and b.
 \param[out] r
 The result of raising each a to the corresponding power b and
 multiplying the results.

 \returns ::EpidStatus

 \see NewEcGroup
 \see NewEcPoint
*/
EpidStatus EcSscmMultiExp(EcGroup* g, EcPoint const** a, BigNumStr const** b,
                          size_t m, EcPoint* r);

/// Generates a random element from an elliptic curve group.
/*!
 This function is only available for G1 and GT.

 \param[in] g
 The elliptic curve group.
 \param[in] rnd_func
 Random number generator.
 \param[in] rnd_func_param
 Pass through context data for rnd_func.
 \param[in,out] r
 Output random elliptic curve element.

 \returns ::EpidStatus

 \see NewEcPoint
 \see BitSupplier
*/
EpidStatus EcGetRandom(EcGroup* g, BitSupplier rnd_func, void* rnd_func_param,
                       EcPoint* r);

/// Checks if a point is in an elliptic curve group.
/*!
 \param[in] g
 The elliptic curve group.
 \param[in] p_str
 A serialized point. Must be a G1ElemStr or G2ElemStr.
 \param[in] strlen
 The size of p_str in bytes.
 \param[out] in_group
 The result of the check.

 \returns ::EpidStatus

 \see NewEcPoint
*/
EpidStatus EcInGroup(EcGroup* g, void const* p_str, size_t strlen,
                     bool* in_group);

/// Hashes an arbitrary message to an Intel(R) EPID 1.1 element in an elliptic
/// curve group.
/*!
\param[in] g
The elliptic curve group.
\param[in] msg
The message.
\param[in] msg_len
The size of msg in bytes.
\param[out] r
The hashed value.

\returns ::EpidStatus

\see NewEcGroup
\see NewEcPoint
*/
EpidStatus Epid11EcHash(EcGroup* g, void const* msg, size_t msg_len,
                        EcPoint* r);

/// Hashes an arbitrary message to an element in an elliptic curve group.
/*!
 \param[in] g
 The elliptic curve group.
 \param[in] msg
 The message.
 \param[in] msg_len
 The size of msg in bytes.
 \param[in] hash_alg
 The hash algorithm.
 \param[out] r
 The hashed value.

 \returns ::EpidStatus

 \see NewEcGroup
 \see NewEcPoint
*/
EpidStatus EcHash(EcGroup* g, void const* msg, size_t msg_len, HashAlg hash_alg,
                  EcPoint* r);

/// Sets an EcPoint variable to a point on a curve.
/*!
 This function is only available for G1.

 \param[in] g
 The elliptic curve group.
 \param[in] x
 The x coordinate.
 \param[out] r
 The point.

 \returns ::EpidStatus

 \see NewEcGroup
 \see NewEcPoint
 \see NewFfElement
*/
EpidStatus EcMakePoint(EcGroup* g, FfElement const* x, EcPoint* r);

/// Computes the additive inverse of an EcPoint.
/*!
 This inverse operation is also known as element negation
 for elliptic curve groups.

 \param[in] g
 The elliptic curve group.
 \param[in] p
 The point.
 \param[out] r
 The inverted point.

 \returns ::EpidStatus

 \see NewEcGroup
 \see NewEcPoint
*/
EpidStatus EcInverse(EcGroup* g, EcPoint const* p, EcPoint* r);

/// Checks if two EcPoints are equal.
/*!
 \param[in] g
 The elliptic curve group.
 \param[in] a
 A point to check.
 \param[in] b
 Another point to check.
 \param[out] is_equal
 The result of the check.

 \returns ::EpidStatus

 \see NewEcGroup
 \see NewEcPoint
 */
EpidStatus EcIsEqual(EcGroup* g, EcPoint const* a, EcPoint const* b,
                     bool* is_equal);

/// Checks if an EcPoint is the identity element.
/*!

 Takes a group element P as input. It outputs true if P is the
 identity element of G. Otherwise, it outputs false.

 \param[in] g
 The elliptic curve group.
 \param[in] p
 The point to check.
 \param[out] is_identity
 The result of the check.

 \returns ::EpidStatus

 \see NewEcGroup
 \see NewEcPoint
*/
EpidStatus EcIsIdentity(EcGroup* g, EcPoint const* p, bool* is_identity);

/*!
@}
*/
#endif  // EPID_COMMON_MATH_ECGROUP_H_
