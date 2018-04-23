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
 * \brief Finite field interface.
 */

#ifndef EPID_COMMON_MATH_FINITEFIELD_H_
#define EPID_COMMON_MATH_FINITEFIELD_H_

#include "epid/common/stdtypes.h"
#include "epid/common/bitsupplier.h"
#include "epid/common/errors.h"
#include "epid/common/math/bignum.h"
#include "epid/common/types.h"

/// Finite field operations
/*!
\defgroup FiniteFieldPrimitives finitefield
provides APIs for working with finite fields.
Finite fields allow simple mathematical operations based on a finite set of
discrete values. The results of these operations are also contained in the
same set.

A simple example of a finite field is all integers from zero that are less than
a given value.

The elements (::FfElement) of a finite field can be used in a variety of
simple mathematical operations that result in elements of the same field.

\ingroup EpidMath
@{
*/

/// A finite field.
typedef struct FiniteField FiniteField;

/// An element in a finite field.
typedef struct FfElement FfElement;

/// Creates new finite field.
/*!
 Allocates memory and creates a new finite field GF(prime).

 Use DeleteFiniteField() to free memory.

 \param[in] prime
 The order of the finite field.
 \param[out] ff
 The newly constructed finite field.

 \returns ::EpidStatus

 \see DeleteFiniteField
*/
EpidStatus NewFiniteField(BigNumStr const* prime, FiniteField** ff);

/// Creates a new finite field using binomial extension.
/*!
 Allocates memory and creates a finite field using binomial extension.

 Use DeleteFiniteField() to free memory.

 \param[in] ground_field
 The ground field.
 \param[in] ground_element
 The low-order term of the extension.
 \param[in] degree
 The degree of the extension.
 \param[out] ff
 The newly constructed finite field.

 \returns ::EpidStatus

 \see DeleteFiniteField
*/
EpidStatus NewFiniteFieldViaBinomalExtension(FiniteField const* ground_field,
                                             FfElement const* ground_element,
                                             int degree, FiniteField** ff);

/// Creates a new finite field using polynomial extension.
/*!
 Allocates memory and creates a finite field using polynomial extension.

 Use DeleteFiniteField() to free memory.

 \note Only needed for Intel(R) EPID 1.1 verification.

 \param[in] ground_field
 The ground field.
 \param[in] irr_polynomial
 Array with coefficients of the irreducible polynomial.
 Number of elements must be equal to the degree of the extension.
 \param[in] degree
 The degree of the extension.
 \param[out] ff
 The newly constructed finite field.

 \returns ::EpidStatus

 \see DeleteFiniteField
*/
EpidStatus NewFiniteFieldViaPolynomialExtension(FiniteField const* ground_field,
                                                BigNumStr const* irr_polynomial,
                                                int degree, FiniteField** ff);

/// Frees a previously allocated FiniteField.
/*!
 Frees memory pointed to by finite field. Nulls the pointer.

 \param[in] ff
 The Finite field. Can be NULL.

 \see NewFiniteField
*/
void DeleteFiniteField(FiniteField** ff);

/// Creates a new finite field element.
/*!
 Allocates memory and creates a new finite field
 element.

 Use DeleteFfElement() to free memory.

 \param[in] ff
 The finite field.
 \param[out] new_ff_elem
The Newly constructed finite field element.

 \returns ::EpidStatus

 \see NewFiniteField
 \see DeleteFfElement
 */
EpidStatus NewFfElement(FiniteField const* ff, FfElement** new_ff_elem);

/// Frees a previously allocated FfElement.
/*!
 Frees memory pointed to by ff_elem. Nulls the pointer.

 \param[in] ff_elem
 The finite field element. Can be NULL.

 \see NewFfElement
*/
void DeleteFfElement(FfElement** ff_elem);

/// Deserializes a FfElement from a string.
/*!
 \param[in] ff
 The finite field.
 \param[in] ff_elem_str
 The serialized value.
 \param[in] strlen
 The size of ff_elem_str in bytes.
 \param[out] ff_elem
 The target FfElement.

 \returns ::EpidStatus

 \see NewFfElement
 \see WriteFfElement
*/
EpidStatus ReadFfElement(FiniteField* ff, void const* ff_elem_str,
                         size_t strlen, FfElement* ff_elem);

/// Initializes an existing FfElement from a BigNum.
/*!
 \param[in] ff
 The finite field. Must be a Prime Field.
 \param[in] bn
 The value to read.
 \param[out] ff_elem
 The target FfElement.

 \returns ::EpidStatus

 \see NewFfElement
 \see WriteFfElement
*/
EpidStatus InitFfElementFromBn(FiniteField* ff, BigNum* bn, FfElement* ff_elem);

/// Serializes a finite field element to a string.
/*!
 \param[in] ff
 The finite field.
 \param[in] ff_elem
 The FfElement to be serialized.
 \param[out] ff_elem_str
 The target string.
 \param[in] strlen
 The size of ff_elem_str in bytes.

 \returns ::EpidStatus

 \see NewFfElement
 \see FpElemStr
 \see FqElemStr
 \see GtElemStr
*/
EpidStatus WriteFfElement(FiniteField* ff, FfElement const* ff_elem,
                          void* ff_elem_str, size_t strlen);

/// Calculates the additive inverse of a finite field element.
/*!
 \param[in] ff
 The finite field.
 \param[in] a
 The element.
 \param[out] r
 The inverted element.

 \returns ::EpidStatus

 \see NewFiniteField
 \see NewFfElement
 */
EpidStatus FfNeg(FiniteField* ff, FfElement const* a, FfElement* r);

/// Calculates the multiplicative inverse of a finite field element.
/*!
 \param[in] ff
 The finite field.
 \param[in] a
 The element.
 \param[out] r
 The inverted element.

 \returns ::EpidStatus

 \see NewFiniteField
 \see NewFfElement
 */
EpidStatus FfInv(FiniteField* ff, FfElement const* a, FfElement* r);

/// Adds two finite field elements.
/*!
 \param[in] ff
 The finite field.
 \param[out] a
 The first operand to be added.
 \param[out] b
 The second operand to be added.
 \param[out] r
 The result of adding a and b.

 \returns ::EpidStatus
 */
EpidStatus FfAdd(FiniteField* ff, FfElement const* a, FfElement const* b,
                 FfElement* r);

/// Subtracts two finite field elements.
/*!

\note Only needed for Intel(R) EPID 1.1 verification.

\param[in] ff
The finite field.
\param[out] a
The first operand to use in subtraction.
\param[out] b
The second operand to use in subtraction.
\param[out] r
The result of subtracting a and b.

\returns ::EpidStatus
*/
EpidStatus FfSub(FiniteField* ff, FfElement const* a, FfElement const* b,
                 FfElement* r);

/// Multiplies two finite field elements.
/*!
 \param[in] ff
 The finite field.
 \param[out] a
 The first operand to be multplied.
 \param[out] b
 The second operand to be multiplied. If ff is an extension field of a
 field F then this parameter may be an element of either ff or F.
 \param[out] r
 The result of multiplying a and b.

 \returns ::EpidStatus

 \see NewFiniteField
 \see NewFfElement
 */
EpidStatus FfMul(FiniteField* ff, FfElement const* a, FfElement const* b,
                 FfElement* r);

/// Checks if given finite field element is the additive identity (zero).
/*!
 \param[in] ff
 The finite field.
 \param[out] a
 The element.
 \param[out] is_zero
 The result of the check.

 \returns ::EpidStatus

 \see NewFiniteField
 \see NewFfElement
 */
EpidStatus FfIsZero(FiniteField* ff, FfElement const* a, bool* is_zero);

/// Raises an element of a finite field to a power.
/*!
 \param[in] ff
 The finite field in which to perform the operation
 \param[in] a
 The base.
 \param[in] b
 The power.
 \param[out] r
 The result of raising a to the power b.

 \returns ::EpidStatus

 \see NewFiniteField
 \see NewFfElement
 */
EpidStatus FfExp(FiniteField* ff, FfElement const* a, BigNum const* b,
                 FfElement* r);

/// Multi-exponentiates finite field elements.
/*!
 Calculates FfExp(p[0],b[0]) * ... * FfExp(p[m-1],b[m-1]) for m > 1

 \param[in] ff
 The finite field in which to perform the operation
 \param[in] a
 The bases.
 \param[in] b
 The powers.
 \param[in] m
 Number of entries in a and b.
 \param[out] r
 The result of raising each a to the corresponding power b and multiplying
 the results.

 \returns ::EpidStatus

 \see NewFiniteField
 \see NewFfElement
*/
EpidStatus FfMultiExp(FiniteField* ff, FfElement const** a, BigNumStr const** b,
                      size_t m, FfElement* r);

/// Multi-exponentiates finite field elements.
/*!
 Calculates FfExp(p[0],b[0]) * ... * FfExp(p[m-1],b[m-1]) for m > 1

 \param[in] ff
 The finite field in which to perform the operation
 \param[in] a
 The bases.
 \param[in] b
 The powers.
 \param[in] m
 Number of entries in a and b.
 \param[out] r
 The result of raising each a to the corresponding power b and multiplying
 the results.

 \returns ::EpidStatus

 \see NewFiniteField
 \see NewFfElement
*/
EpidStatus FfMultiExpBn(FiniteField* ff, FfElement const** a, BigNum const** b,
                        size_t m, FfElement* r);

/// Software side-channel mitigated implementation of FfMultiExp.
/*!
 Calculates FfExp(p[0],b[0]) * ... * FfExp(p[m-1],b[m-1]) for m > 1

 \attention
 The reference implementation of FfSscmMultiExp calls FfMultiExp
 directly because the implementation of FfMultiExp is already side channel
 mitigated. Implementers providing their own versions of this function are
 responsible for ensuring that FfSscmMultiExp is side channel mitigated per
 section 8 of the Intel(R) EPID 2.0 spec.

 \param[in] ff
 The finite field in which to perform the operation.
 \param[in] a
 The bases.
 \param[in] b
 The powers.
 \param[in] m
 Number of entries in a and b.
 \param[out] r
 The result of raising each a to the corresponding power b and multiplying
 the results.

 \returns ::EpidStatus

 \see NewFiniteField
 \see NewFfElement
*/

EpidStatus FfSscmMultiExp(FiniteField* ff, FfElement const** a,
                          BigNumStr const** b, size_t m, FfElement* r);

/// Checks if two finite field elements are equal.
/*!
 \param[in] ff
 The finite field.
 \param[in] a
 An element to check.
 \param[in] b
 Another element to check.
 \param[out] is_equal
 The result of the check.

 \returns ::EpidStatus

 \see NewEcGroup
 \see NewEcPoint
 */
EpidStatus FfIsEqual(FiniteField* ff, FfElement const* a, FfElement const* b,
                     bool* is_equal);

/// Hashes an arbitrary message to an element in a finite field.
/*!
 \param[in] ff
 The finite field.
 \param[in] msg
 The message.
 \param[in] msg_len
 The size of msg in bytes.
 \param[in] hash_alg
 The hash algorithm.
 \param[out] r
 The hashed value.

 \returns ::EpidStatus

 \see NewFiniteField
 \see NewFfElement
 */
EpidStatus FfHash(FiniteField* ff, void const* msg, size_t msg_len,
                  HashAlg hash_alg, FfElement* r);

/// Generate random finite field element.
/*!
 \param[in] ff
 The finite field associated with the random finite field element.
 \param[in] low_bound
 Lower bound of the random finite field to be generated.
 \param[in] rnd_func
 Random number generator.
 \param[in] rnd_param
 Pass through context data for rnd_func.
 \param[in,out] r
 The random finite field element.

 \returns ::EpidStatus

 \retval ::kEpidRandMaxIterErr the function should be called again with
 different random data.

 \see NewFfElement
 \see BitSupplier
 */
EpidStatus FfGetRandom(FiniteField* ff, BigNumStr const* low_bound,
                       BitSupplier rnd_func, void* rnd_param, FfElement* r);

/// Finds a square root of a finite field element.
/*!
 This function calculates the square root by the method of false position.

 \param[in] ff
 The finite field in which to perform the operation
 \param[in] a
 The bases.
 \param[out] r
 The result of raising each a to the corresponding power b and multiplying
 the results.

 \retval kEpidMathQuadraticNonResidueError No square root could be found.
 \returns ::EpidStatus

 \see NewFiniteField
 \see NewFfElement
*/
EpidStatus FfSqrt(FiniteField* ff, FfElement const* a, FfElement* r);

/*!
  @}
*/

#endif  // EPID_COMMON_MATH_FINITEFIELD_H_
