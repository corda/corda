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
#ifndef EPID_COMMON_SRC_GROUPPUBKEY_H_
#define EPID_COMMON_SRC_GROUPPUBKEY_H_
/*!
 * \file
 * \brief Group public key interface.
 * \addtogroup EpidCommon
 * @{
 */
#include "epid/common/errors.h"
#include "epid/common/math/ecgroup.h"
#include "epid/common/types.h"

/// Internal representation of GroupPubKey
typedef struct GroupPubKey_ {
  GroupId gid;  ///< group ID
  EcPoint* h1;  ///< an element in G1
  EcPoint* h2;  ///< an element in G1
  EcPoint* w;   ///< an element in G2
} GroupPubKey_;

/// Constructs internal representation of GroupPubKey
/*!
  Allocates memory and initializes gid, h1, h2, w parameters. Use
  DeleteGroupPubKey() to deallocate memory

  \param[in] pub_key_str
  Oct string representation of group public key
  \param[in] G1
  EcGroup containing elements h1 and h2
  \param[in] G2
  EcGroup containing element w
  \param[out] pub_key
  Group public key: (gid, h1, h2, w)

  \returns ::EpidStatus
  \see DeleteGroupPubKey
*/
EpidStatus CreateGroupPubKey(GroupPubKey const* pub_key_str, EcGroup* G1,
                             EcGroup* G2, GroupPubKey_** pub_key);

/// Deallocates storage for internal representation of GroupPubKey
/*!
  Frees memory pointed to by Group public key. Nulls the pointer.

  \param[in] pub_key
  Group public key to be freed

  \see CreateGroupPubKey
*/
void DeleteGroupPubKey(GroupPubKey_** pub_key);
/*! @} */
#endif  // EPID_COMMON_SRC_GROUPPUBKEY_H_
