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
#ifndef EPID_COMMON_1_1_SRC_COMMITMENT_H_
#define EPID_COMMON_1_1_SRC_COMMITMENT_H_
/*!
 * \file
 * \brief Commitment hash interface.
 * \addtogroup EpidCommon
 * @{
 */
#include "epid/common/errors.h"
#include "epid/common/1.1/types.h"
#include "epid/common/math/ecgroup.h"
#include "epid/common/math/finitefield.h"
#include "epid/common/math/hash.h"

#pragma pack(1)
/// Storage for values to create Intel(R) EPID 1.1 commitment in Sign and Verify
/// algorithms
typedef struct Epid11CommitValues {
  BigNumStr p;         ///< Intel(R) EPID 1.1 parameter p
  Epid11G1ElemStr g1;  ///< Intel(R) EPID 1.1 parameter g1
  Epid11G2ElemStr g2;  ///< Intel(R) EPID 1.1 parameter g2
  Epid11G1ElemStr g3;  ///< Intel(R) EPID 1.1 parameter g3
  Epid11G1ElemStr h1;  ///< Group public key value h1
  Epid11G1ElemStr h2;  ///< Group public key value h2
  Epid11G2ElemStr w;   ///< Group public key value w
  Epid11G3ElemStr B;   ///< Variable B computed in algorithm
  Epid11G3ElemStr K;   ///< Variable K computed in algorithm
  Epid11G1ElemStr T1;  ///< Variable T1 computed in algorithm
  Epid11G1ElemStr T2;  ///< Variable T2 computed in algorithm
  Epid11G1ElemStr R1;  ///< Variable R1 computed in algorithm
  Epid11G1ElemStr R2;  ///< Variable R2 computed in algorithm
  Epid11G3ElemStr R3;  ///< Variable R3 computed in algorithm
  Epid11GtElemStr R4;  ///< Variable R4 computed in algorithm
} Epid11CommitValues;
#pragma pack()

/// Set Intel(R) EPID 1.1 group public key related fields to Epid11CommitValues
/// structure
/*!
  Set p, g1, g2, g3, h1, h2 and w fields of values argument.

  \param[in] pub_key
  Intel(R) EPID 1.1 Group public key
  \param[out] values
  Pointer to Epid11CommitValues structure to fill.

  \returns ::EpidStatus

  \see CalculateCommitmentHash
*/
EpidStatus SetKeySpecificEpid11CommitValues(Epid11GroupPubKey const* pub_key,
                                            Epid11CommitValues* values);

/// Set Epid11CommitValues structure fields calculated in Intel(R) EPID 1.1 Sign
/// or Verify algorithm
/*!
  Set B, K, T1, T2, R1, R2, R3 and R4 fields of values argument.

  \param[in] B
  Value of B to set
  \param[in] K
  Value of K to set
  \param[in] T1
  Value of T1 to set
  \param[in] T2
  Value of T2 to set
  \param[in] R1
  Value of R1 to set
  \param[in] R2
  Value of R2 to set
  \param[in] R3
  Value of R3 to set
  \param[in] R4
  Value of R4 to set
  \param[in] G1
  EcGroup containing element R1, R2
  \param[in] G3
  EcGroup containing element R3
  \param[in] GT
  FiniteField containing element R4
  \param[out] values
  Pointer to CommitValues structure to fill.

  \returns ::EpidStatus

  \see CalculateCommitmentHash
*/
EpidStatus SetCalculatedEpid11CommitValues(
    Epid11G3ElemStr const* B, Epid11G3ElemStr const* K,
    Epid11G1ElemStr const* T1, Epid11G1ElemStr const* T2, EcPoint const* R1,
    EcPoint const* R2, EcPoint const* R3, FfElement const* R4, EcGroup* G1,
    EcGroup* G3, FiniteField* GT, Epid11CommitValues* values);

/// Calculate Hash(t4 || nd || mSize || m) for Intel(R) EPID 1.1 Sign and Verfiy
/// algorithms
/*!
  Calculate c = Hash(t4 || nd || mSize || m) where t4 is
  Hash(p || g1 || g2 || g3 || h1 || h2 || w || B || K || T1 || T2 || R1 || R2 ||
  R3 || R4).

  \param[in] values
  Commit values to hash
  \param[in] msg
  Message to hash
  \param[in] msg_len
  Size of msg buffer in bytes
  \param[in] nd
  80-bit big integer
  \param[out] c
  Result of calculation

  \returns ::EpidStatus

  \see SetKeySpecificCommitValues
  \see SetCalculatedCommitValues
*/
EpidStatus CalculateEpid11CommitmentHash(Epid11CommitValues const* values,
                                         void const* msg, uint32_t msg_len,
                                         OctStr80 const* nd, Sha256Digest* c);

/*! @} */
#endif  // EPID_COMMON_1_1_SRC_COMMITMENT_H_
