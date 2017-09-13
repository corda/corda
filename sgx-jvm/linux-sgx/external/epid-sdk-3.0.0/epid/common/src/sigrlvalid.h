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
#ifndef EPID_COMMON_SRC_SIGRLVALID_H_
#define EPID_COMMON_SRC_SIGRLVALID_H_
/*!
 * \file
 * \brief SigRl validity checking interface.
 * \addtogroup EpidCommon
 * @{
 */

#include <stddef.h>

#include "epid/common/stdtypes.h"
#include "epid/common/types.h"

/// Function to verify if signature based revocation list is valid
/*!

  \param[in] gid
  Group id
  \param[in] sig_rl
  Signature based revocation list
  \param[in] sig_rl_size
  Size of signature based revocation list

  \returns true if revocation list is valid
  \returns false if revocation list is invalid
*/
bool IsSigRlValid(GroupId const* gid, SigRl const* sig_rl, size_t sig_rl_size);

/*! @} */
#endif  // EPID_COMMON_SRC_SIGRLVALID_H_
