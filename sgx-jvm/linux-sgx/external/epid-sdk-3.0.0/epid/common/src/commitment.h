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
#ifndef EPID_COMMON_SRC_COMMITMENT_H_
#define EPID_COMMON_SRC_COMMITMENT_H_
/*!
 * \file
 * \brief Commitment hash interface.
 * \addtogroup EpidCommon
 * @{
 */
#include "epid/common/errors.h"
#include "epid/common/types.h"
#include "epid/common/math/ecgroup.h"
#include "epid/common/math/finitefield.h"

#pragma pack(1)
/// Storage for values to create commitment in Sign and Verify algorithms
typedef struct CommitValues {
  BigNumStr p;     ///< Intel(R) EPID2.0 parameter p
  G1ElemStr g1;    ///< Intel(R) EPID2.0 parameter g1
  G2ElemStr g2;    ///< Intel(R) EPID2.0 parameter g2
  G1ElemStr h1;    ///< Group public key value h1
  G1ElemStr h2;    ///< Group public key value h2
  G2ElemStr w;     ///< Group public key value w
  G1ElemStr B;     ///< Variable B computed in algorithm
  G1ElemStr K;     ///< Variable K computed in algorithm
  G1ElemStr T;     ///< Variable T computed in algorithm
  G1ElemStr R1;    ///< Variable R1 computed in algorithm
  Fq12ElemStr R2;  ///< Variable R2 computed in algorithm
} CommitValues;
#pragma pack()

/// Set group public key related fields from CommitValues structure
/*!
  Set p, g1, g2, h1, h2 and w fields of values argument.

  \param[in] pub_key
  Group public key
  \param[out] values
  Pointer to CommitValues structure to fill.

  \returns ::EpidStatus

  \see CalculateCommitmentHash
*/
EpidStatus SetKeySpecificCommitValues(GroupPubKey const* pub_key,
                                      CommitValues* values);

/// Set CommitValues structure fields calculated in algorithm
/*!
  Set B, K, T, R1 and R2 fields of values argument.

  \param[in] B
  Value of B to set
  \param[in] K
  Value of K to set
  \param[in] T
  Value of T to set
  \param[in] R1
  Value of R1 to set
  \param[in] G1
  EcGroup containing element R1
  \param[in] R2
  Value of R2 to set
  \param[in] GT
  FiniteField containing element R2
  \param[out] values
  Pointer to CommitValues structure to fill.

  \returns ::EpidStatus

  \see CalculateCommitmentHash
*/
EpidStatus SetCalculatedCommitValues(G1ElemStr const* B, G1ElemStr const* K,
                                     G1ElemStr const* T, EcPoint const* R1,
                                     EcGroup* G1, FfElement const* R2,
                                     FiniteField* GT, CommitValues* values);

/// Calculate Fp.hash(t3 || m) for Sign and Verfiy algorithms
/*!
  Calculate c = Fp.hash(t3 || m) where t3 is
  Fp.hash(p || g1 || g2 || h1 || h2 || w || B || K || T || R1 || R2).

  \param[in] values
  Commit values to hash
  \param[in] Fp
  Finite field to perfom hash operation in
  \param[in] hash_alg
  Hash algorithm to use
  \param[in] msg
  Message to hash
  \param[in] msg_len
  Size of msg buffer in bytes
  \param[out] c
  Result of calculation

  \returns ::EpidStatus

  \see SetKeySpecificCommitValues
  \see SetCalculatedCommitValues
*/
EpidStatus CalculateCommitmentHash(CommitValues const* values, FiniteField* Fp,
                                   HashAlg hash_alg, void const* msg,
                                   size_t msg_len, FfElement* c);

/*! @} */
#endif  // EPID_COMMON_SRC_COMMITMENT_H_
