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
#ifndef EPID_COMMON_1_1_SRC_GROUPPUBKEY_H_
#define EPID_COMMON_1_1_SRC_GROUPPUBKEY_H_
/*!
 * \file
 * \brief Intel(R) EPID 1.1 group public key interface.
 * \addtogroup EpidCommon
 * @{
 */
#include "epid/common/errors.h"
#include "epid/common/math/ecgroup.h"
#include "epid/common/1.1/types.h"

/// Internal representation of Epid11GroupPubKey
typedef struct Epid11GroupPubKey_ {
  Epid11GroupId gid;  ///< group ID
  EcPoint* h1;        ///< an element in G1
  EcPoint* h2;        ///< an element in G1
  EcPoint* w;         ///< an element in G2
} Epid11GroupPubKey_;

/// Constructs internal representation of Intel(R) EPID 1.1 group public key
/*!
  Allocates memory and initializes gid, h1, h2, w parameters. Use
  DeleteEpid11GroupPubKey() to deallocate memory

  \param[in] pub_key_str
  Oct string representation of group public key
  \param[in] G1
  EcGroup containing elements h1 and h2
  \param[in] G2
  EcGroup containing element w
  \param[out] pub_key
  Group public key: (gid, h1, h2, w)

  \returns ::EpidStatus
  \see DeleteEpid11GroupPubKey
*/
EpidStatus CreateEpid11GroupPubKey(Epid11GroupPubKey const* pub_key_str,
                                   EcGroup* G1, EcGroup* G2,
                                   Epid11GroupPubKey_** pub_key);

/// Deallocates storage for internal representation Intel(R) EPID 1.1 group
/// public key
/*!
  Frees memory pointed to by Epid11GroupPubKey. Nulls the pointer.

  \param[in] pub_key
  Epid11GroupPubKey to be freed

  \see CreateEpid11GroupPubKey
*/
void DeleteEpid11GroupPubKey(Epid11GroupPubKey_** pub_key);
/*! @} */
#endif  // EPID_COMMON_1_1_SRC_GROUPPUBKEY_H_
