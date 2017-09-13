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
 * \brief EpidGetSigSize implementation.
 */
#include "epid/member/api.h"
#include "epid/common/src/endian_convert.h"

size_t EpidGetSigSize(SigRl const* sig_rl) {
  const size_t kMinSigSize = sizeof(EpidSignature) - sizeof(NrProof);
  if (!sig_rl) {
    return kMinSigSize;
  } else {
    if (ntohl(sig_rl->n2) > (SIZE_MAX - kMinSigSize) / sizeof(NrProof)) {
      return kMinSigSize;
    } else {
      return kMinSigSize + ntohl(sig_rl->n2) * sizeof(NrProof);
    }
  }
}
