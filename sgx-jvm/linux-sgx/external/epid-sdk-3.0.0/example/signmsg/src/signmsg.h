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
 * \brief Message signing interface.
 */

#ifndef EXAMPLE_SIGNMSG_SRC_SIGNMSG_H_
#define EXAMPLE_SIGNMSG_SRC_SIGNMSG_H_

#include "epid/member/api.h"
#include "epid/common/file_parser.h"

/// Check if opaque data blob containing CA certificate is authorized
bool IsCaCertAuthorizedByRootCa(void const* data, size_t size);

/// Create Intel(R) EPID signature of message
EpidStatus SignMsg(void const* msg, size_t msg_len, void const* basename,
                   size_t basename_len, unsigned char const* signed_sig_rl,
                   size_t signed_sig_rl_size,
                   unsigned char const* signed_pubkey,
                   size_t signed_pubkey_size, unsigned char const* priv_key,
                   size_t privkey_size, HashAlg hash_alg,
                   MemberPrecomp* member_precomp, bool member_precomp_is_input,
                   EpidSignature** sig, size_t* sig_len,
                   EpidCaCertificate const* cacert);

#endif  // EXAMPLE_SIGNMSG_SRC_SIGNMSG_H_
