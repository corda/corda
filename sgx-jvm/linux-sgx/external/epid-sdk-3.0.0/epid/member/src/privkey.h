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
#ifndef EPID_MEMBER_SRC_PRIVKEY_H_
#define EPID_MEMBER_SRC_PRIVKEY_H_
/*!
* \file
 * \brief Private key private interface.
* \addtogroup EpidCommon
* @{
*/
#include "epid/common/errors.h"
#include "epid/common/math/ecgroup.h"
#include "epid/common/types.h"

/*!
 * \brief
 * Internal implementation of PrivKey
 */
typedef struct PrivKey_ {
  GroupId gid;   ///< group ID
  EcPoint* A;    ///< an element in G1
  FfElement* x;  ///< an integer between [0, p-1]
  FfElement* f;  ///< an integer between [0, p-1]
} PrivKey_;

/// Constructs internal representation of PrivKey
/*!
  This function allocates memory and initializes gid, A, x, f parameters.

  \param[in] priv_key_str
  Serialized representation of private key
  \param[in] G1
  EcGroup containing element A
  \param[in] Fp
  FiniteField containing elements x and f
  \param[out] priv_key
  Newly created private key: (gid, A, x, f)

  \returns ::EpidStatus
*/
EpidStatus CreatePrivKey(PrivKey const* priv_key_str, EcGroup* G1,
                         FiniteField* Fp, PrivKey_** priv_key);

/// Deallocate storage for internal representation of PrivKey
/*!
  Frees memory pointed to by Member private key. Nulls the pointer.

  \param[in] priv_key
  Member private key to be freed
*/
void DeletePrivKey(PrivKey_** priv_key);

/*! @} */
#endif  // EPID_MEMBER_SRC_PRIVKEY_H_
