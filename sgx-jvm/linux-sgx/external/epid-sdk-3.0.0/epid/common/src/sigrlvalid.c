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
 * \brief SigRl validity checking implementation.
 */

#include <string.h>

#include "epid/common/src/endian_convert.h"
#include "epid/common/src/sigrlvalid.h"

bool IsSigRlValid(GroupId const* gid, SigRl const* sig_rl, size_t sig_rl_size) {
  const size_t kMinSigRlSize = sizeof(SigRl) - sizeof(SigRlEntry);
  size_t input_sig_rl_size = 0;
  if (!gid || !sig_rl || kMinSigRlSize > sig_rl_size) {
    return false;
  }
  if (ntohl(sig_rl->n2) > (SIZE_MAX - kMinSigRlSize) / sizeof(sig_rl->bk[0])) {
    return false;
  }
  // sanity check of intput SigRl size
  input_sig_rl_size = kMinSigRlSize + ntohl(sig_rl->n2) * sizeof(sig_rl->bk[0]);
  if (input_sig_rl_size != sig_rl_size) {
    return false;
  }
  // verify that gid given and gid in SigRl match
  if (0 != memcmp(gid, &sig_rl->gid, sizeof(*gid))) {
    return false;
  }
  return true;
}
