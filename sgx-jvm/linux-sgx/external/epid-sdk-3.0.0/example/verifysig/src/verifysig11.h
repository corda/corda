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
 * \brief EPID 1.1 signature verification interface.
 */
#ifndef EXAMPLE_VERIFYSIG_SRC_VERIFYSIG11_H_
#define EXAMPLE_VERIFYSIG_SRC_VERIFYSIG11_H_

#include "epid/verifier/1.1/api.h"
#include "epid/common/1.1/file_parser.h"

/// verify EPID 1.x signature
EpidStatus Verify11(Epid11Signature const* sig, size_t sig_len, void const* msg,
                    size_t msg_len, void const* basename, size_t basename_len,
                    void const* signed_priv_rl, size_t signed_priv_rl_size,
                    void const* signed_sig_rl, size_t signed_sig_rl_size,
                    void const* signed_grp_rl, size_t signed_grp_rl_size,
                    void const* signed_pub_key, size_t signed_pub_key_size,
                    EpidCaCertificate const* cacert,
                    Epid11VerifierPrecomp* verifier_precomp,
                    bool verifier_precomp_is_input);

#endif  // EXAMPLE_VERIFYSIG_SRC_VERIFYSIG11_H_
